package pl.cwnews;

import java.util.Arrays;

/**
 * Hears a key's buzzer in microphone samples and reports when it goes down and up. Calibrate first (key held, room quiet,
 * band playing), then feed samples. The buzzer is mixed down to baseband, so a weak or drifting tone is followed, and a
 * copy of it shifted to exactly 800 Hz is kept for a neural decoder, whatever the buzzer's pitch. No Android imports.
 */
final class KeyEar {
    interface Listener { void onKey(boolean down, long sampleIndex); }

    static final double MIN_HZ = 300, MAX_HZ = 4000, SHIFT_HZ = 800, OUT_RATE = 3200;
    static final int FFT = 4096, RING = 38400;
    private static final double MIN_MARK_S = 0.012, MIN_SPACE_S = 0.008;

    final int rate;
    private double pitch, fb, levelOn, levelOff, leak, thrOn, thrOff;
    private boolean ready;
    private final Dsp.Biquad[] lpI = {new Dsp.Biquad(), new Dsp.Biquad()}, lpQ = {new Dsp.Biquad(), new Dsp.Biquad()};
    private double mixPhase, pI, pQ, afcSum;
    private int afcN;
    private long seen, runStart;
    private boolean raw, emitted, pending;
    private long pendAt;
    private boolean pendState;
    // Shifted output.
    private final float[] ring = new float[RING];
    private int ringPos;
    private double decAcc, outPhase;

    KeyEar(int rate) {
        this.rate = rate;
        for (int k = 0; k < 2; k++) { lpI[k].lowpass(200, 0.707, rate); lpQ[k].lowpass(200, 0.707, rate); }
    }

    /** The strongest pitch between 300 and 4000 Hz: a Hann-windowed 4096-point FFT averaged over the frames, refined by parabolic interpolation. */
    static double findPitch(float[] x, int n, int rate) {
        double[] acc = new double[FFT / 2];
        float[] re = new float[FFT], im = new float[FFT];
        double[] w = new double[FFT];
        for (int i = 0; i < FFT; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT);
        int frames = 0;
        for (int at = 0; at + FFT <= n; at += FFT / 2) {
            for (int i = 0; i < FFT; i++) { re[i] = (float) (x[at + i] * w[i]); im[i] = 0; }
            Dsp.fft(re, im);
            for (int k = 0; k < FFT / 2; k++) acc[k] += re[k] * re[k] + im[k] * im[k];
            frames++;
        }
        if (frames == 0) return 0;
        int lo = (int) Math.ceil(MIN_HZ * FFT / rate), hi = (int) Math.floor(MAX_HZ * FFT / rate), best = lo;
        for (int k = lo; k <= hi && k < FFT / 2 - 1; k++) if (acc[k] > acc[best]) best = k;
        double off = 0;
        if (best > 0 && best < FFT / 2 - 1) {
            double a = Math.log(acc[best - 1] + 1e-30), b = Math.log(acc[best] + 1e-30), c = Math.log(acc[best + 1] + 1e-30), den = a - 2 * b + c;
            if (den != 0) off = Math.max(-1, Math.min(1, 0.5 * (a - c) / den));
        }
        return (best + off) * rate / FFT;
    }

    private void restart(double hz) {
        fb = hz; mixPhase = 0;
        for (int k = 0; k < 2; k++) { lpI[k].reset(); lpQ[k].reset(); }
        pI = pQ = 0; afcSum = 0; afcN = 0; seen = 0; raw = emitted = pending = false;
    }

    private double envelope(float x) {
        double c = Math.cos(mixPhase), s = Math.sin(mixPhase);
        mixPhase += 2 * Math.PI * fb / rate;
        if (mixPhase > 2 * Math.PI) mixPhase -= 2 * Math.PI;
        double i = lpI[1].step(lpI[0].step(x * c)), q = lpQ[1].step(lpQ[0].step(-x * s));
        lastI = i; lastQ = q;
        return Math.sqrt(i * i + q * q);
    }
    private double lastI, lastQ;

    private static double median(double[] v, int n) { double[] c = Arrays.copyOf(v, n); Arrays.sort(c); return n == 0 ? 0 : c[n / 2]; }

    /**
     * Sets the pitch and thresholds from three recordings: the key held, the room with the key up, and the band playing
     * with the key up (may be null). Returns false when no buzzer stood out from the room.
     */
    boolean calibrate(float[] held, int nHeld, float[] quiet, int nQuiet, float[] band, int nBand) {
        pitch = findPitch(held, nHeld, rate);
        int skip = rate / 10;
        restart(pitch);
        double[] e = new double[nHeld]; double peak = 0;
        for (int i = 0; i < nHeld; i++) { e[i] = envelope(held[i]); if (i >= skip) peak = Math.max(peak, e[i]); }
        double[] on = new double[nHeld]; int c = 0;
        for (int i = skip; i < nHeld; i++) if (e[i] >= 0.5 * peak) on[c++] = e[i];
        levelOn = median(on, c);
        levelOff = envMedian(quiet, nQuiet);
        leak = band == null ? 0 : envMedian(band, nBand);
        thresholds();
        ready = pitch > 0 && levelOn > 3 * levelOff;
        restart(pitch);
        return ready;
    }
    private void thresholds() {
        double floor = Math.max(Math.max(levelOff, leak), 1e-4 * levelOn);
        // The geometric mean of the buzzer and the room, but never below 45% of the buzzer: a room echo then cannot hold a mark open.
        thrOn = Math.min(0.85 * levelOn, Math.max(Math.sqrt(levelOn * floor), 0.45 * levelOn));
        thrOff = 0.8 * thrOn;
    }

    /** The calibration as text, to keep between sessions. */
    String save() { return String.format(java.util.Locale.ROOT, "%.2f,%.6f,%.6f,%.6f,%d", pitch, levelOn, levelOff, leak, rate); }
    /** Restores a saved calibration made at the same sample rate; false if the text is no good. */
    boolean restore(String t) {
        try {
            String[] p = t.split(",");
            if (p.length != 5 || Integer.parseInt(p[4]) != rate) return false;
            pitch = Double.parseDouble(p[0]); levelOn = Double.parseDouble(p[1]); levelOff = Double.parseDouble(p[2]); leak = Double.parseDouble(p[3]);
            if (!(pitch > 0 && levelOn > 0)) return false;
            thresholds(); ready = true; restart(pitch);
            return true;
        } catch (RuntimeException e) { return false; }
    }

    private double envMedian(float[] x, int n) {
        if (x == null || n == 0) return 0;
        restart(pitch);
        double[] e = new double[n]; int c = 0, skip = rate / 10;
        for (int i = 0; i < n; i++) { double v = envelope(x[i]); if (i >= skip) e[c++] = v; }
        return median(e, c);
    }

    double pitch() { return pitch; }
    double trackedPitch() { return fb; }
    boolean ready() { return ready; }
    double levelOn() { return levelOn; }
    double thresholdOn() { return thrOn; }
    /** The buzzer's level over the room's, in dB. */
    double snrDb() { return levelOff > 0 ? 20 * Math.log10(levelOn / levelOff) : 99; }
    /** False when the speaker leaks into the microphone too close to the buzzer: use headphones or a lower volume. */
    boolean leakOk() { return leak < 0.5 * thrOn; }

    /** Feeds samples; sample numbers count from the first sample fed after calibration. */
    void process(float[] x, int n, Listener l) {
        if (!ready) { seen += n; return; }
        long minMark = Math.round(MIN_MARK_S * rate), minSpace = Math.round(MIN_SPACE_S * rate);
        double step = rate / OUT_RATE;
        for (int i = 0; i < n; i++, seen++) {
            double env = envelope(x[i]);
            // The shifted copy: decimate the baseband to 3200 Hz and move it to 800 Hz.
            decAcc += 1;
            if (decAcc >= step) {
                decAcc -= step;
                ring[ringPos] = (float) (lastI * Math.cos(outPhase) - lastQ * Math.sin(outPhase));
                ringPos = (ringPos + 1) % RING;
                outPhase += 2 * Math.PI * SHIFT_HZ / OUT_RATE;
                if (outPhase > 2 * Math.PI) outPhase -= 2 * Math.PI;
            }
            // Hysteresis, then drop marks shorter than 12 ms and spaces shorter than 8 ms.
            boolean was = raw;
            if (!raw && env > thrOn) raw = true; else if (raw && env < thrOff) raw = false;
            if (raw != was) { pending = true; pendAt = seen; pendState = raw; if (raw) { runStart = seen; afcSum = 0; afcN = 0; } else endRun(); }
            if (pending && raw == pendState && seen - pendAt >= (pendState ? minMark : minSpace)) {
                pending = false;
                if (pendState != emitted) { emitted = pendState; l.onKey(emitted, pendAt); }
            }
            if (raw) afc();
        }
    }
    private void afc() {
        double m = pI * pI + pQ * pQ;
        if (m > 0 && afcN >= 0) {
            double d = Math.atan2(lastQ * pI - lastI * pQ, lastI * pI + lastQ * pQ);
            afcSum += d * rate / (2 * Math.PI); afcN++;
        }
        pI = lastI; pQ = lastQ;
    }
    /** After a mark longer than 40 ms, nudge the mixer toward the buzzer's real pitch. */
    private void endRun() {
        if (seen - runStart > 0.04 * rate && afcN > 0) fb += 0.1 * (afcSum / afcN);
        afcN = 0; afcSum = 0;
    }

    /** The shifted 800 Hz stream, newest sample last, into out (RING samples). */
    void shifted(float[] out) {
        for (int i = 0; i < RING; i++) out[i] = ring[(ringPos + i) % RING];
    }
}
