package pl.cwnews;

/**
 * Finds a key's buzzer in microphone samples and reports when it goes down and up. Calibrate first with the key held,
 * the room quiet and the band playing; then feed samples to {@link #process}. No Android imports.
 */
final class ToneDetector {
    interface Listener { void key(boolean down, long sample); }

    static final double MIN_HZ = 300, MAX_HZ = 4000, MIN_MARK_S = 0.008;
    final int rate;
    private double pitch;
    private double on, hi, lo, noisePk, leakPk;
    private boolean ready;
    private final Dsp.Biquad bp = new Dsp.Biquad(), bp2 = new Dsp.Biquad();
    private double env, rise, fall;
    private boolean down, pending;
    private long pendingAt, seen;
    private final long minMark;

    ToneDetector(int rate) {
        this.rate = rate;
        rise = 1 - Math.exp(-1.0 / (0.002 * rate));
        fall = 1 - Math.exp(-1.0 / (0.004 * rate));
        minMark = Math.round(MIN_MARK_S * rate);
    }

    /** The strongest pitch between 300 and 4000 Hz, found by sweeping a Goertzel filter and refining the peak. */
    static double findPitch(float[] x, int n, int rate) {
        double best = 0, bestP = -1;
        int bins = (int) ((MAX_HZ - MIN_HZ) / 10) + 1;
        double[] p = new double[bins];
        for (int b = 0; b < bins; b++) {
            p[b] = goertzel(x, n, MIN_HZ + 10.0 * b, rate);
            if (p[b] > bestP) { bestP = p[b]; best = b; }
        }
        int b = (int) best;
        double off = 0;
        if (b > 0 && b < bins - 1) {
            double a = p[b - 1], c = p[b], d = p[b + 1], den = a - 2 * c + d;
            if (den != 0) off = 0.5 * (a - d) / den;
        }
        return MIN_HZ + 10.0 * (b + Math.max(-1, Math.min(1, off)));
    }
    private static double goertzel(float[] x, int n, double f, int rate) {
        double w = 2 * Math.PI * f / rate, k = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < n; i++) { double s = x[i] + k * s1 - s2; s2 = s1; s1 = s; }
        return s1 * s1 + s2 * s2 - k * s1 * s2;
    }

    private void tune(double hz) {
        double bw = Math.max(80, hz / 6);
        bp.bandpass(hz, hz / bw, rate); bp2.bandpass(hz, hz / bw, rate);
        resetState();
    }
    private void resetState() { bp.reset(); bp2.reset(); env = 0; down = false; pending = false; seen = 0; }
    private double follow(double x) {
        double a = Math.abs(bp2.step(bp.step(x)));
        env += (a - env) * (a > env ? rise : fall);
        return env;
    }

    /**
     * Sets the pitch and thresholds from three recordings: the key held down, the room with the key up, and the band
     * playing with the key up (may be null). Returns false when no steady buzzer was heard.
     */
    boolean calibrate(float[] held, int nHeld, float[] quiet, int nQuiet, float[] band, int nBand) {
        pitch = findPitch(held, nHeld, rate);
        tune(pitch);
        int skip = rate / 20;
        double peak = 0;
        double[] e = new double[nHeld];
        for (int i = 0; i < nHeld; i++) { e[i] = follow(held[i]); if (i >= skip) peak = Math.max(peak, e[i]); }
        double sum = 0; int c = 0;
        for (int i = skip; i < nHeld; i++) if (e[i] >= 0.8 * peak) { sum += e[i]; c++; }
        on = c > 0 ? sum / c : 0;
        noisePk = peakOf(quiet, nQuiet);
        leakPk = band == null ? 0 : peakOf(band, nBand);
        double floor = Math.max(noisePk, leakPk);
        hi = Math.max(0.5 * on, 2 * floor);
        lo = 0.7 * hi;
        ready = on > 0 && on > 3 * noisePk && hi < 0.85 * on;
        resetState();
        return ready;
    }
    private double peakOf(float[] x, int n) {
        if (x == null) return 0;
        tune(pitch);
        double pk = 0;
        for (int i = 0; i < n; i++) { double v = follow(x[i]); if (i > rate / 20) pk = Math.max(pk, v); }
        return pk;
    }

    double pitch() { return pitch; }
    boolean ready() { return ready; }
    /** The speaker leaks into the microphone within about 9 dB of the buzzer: use headphones or a lower volume. */
    boolean leakTooClose() { return on > 0 && leakPk > 0.35 * on; }
    /** Level of the buzzer over the loudest the room reached, in dB. */
    double marginDb() { return noisePk > 0 ? 20 * Math.log10(on / noisePk) : 99; }

    /** Feeds samples. The listener hears each key-down and key-up with its sample number counted from the first sample fed. */
    void process(float[] x, int n, Listener l) {
        if (!ready) { seen += n; return; }
        for (int i = 0; i < n; i++, seen++) {
            double v = follow(x[i]);
            if (!down) {
                if (v > hi) {
                    if (!pending) { pending = true; pendingAt = seen; }
                    if (seen - pendingAt >= minMark) { down = true; pending = false; l.key(true, pendingAt); }
                } else if (v < lo) pending = false;
            } else if (v < lo) { down = false; l.key(false, seen); }
        }
    }

    /** Ends a mark that is still held, e.g. when listening stops. */
    void finish(Listener l) { if (down) { down = false; l.key(false, seen); } pending = false; }
}
