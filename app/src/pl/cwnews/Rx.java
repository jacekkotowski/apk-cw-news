package pl.cwnews;

/**
 * The receiver the band is heard through. Stations, the bulletin, band noise and static are mixed first and then
 * pass one band-pass filter and one AGC, so they affect each other as on a real rig: a strong signal turns the gain
 * down and the noise drops with it, then rushes back in the gaps. Pure Java, no allocation while running.
 */
final class Rx {
    static final int WIDE = 0, NORMAL = 1, NARROW = 2;
    /** AGC modes: off, slow release, fast release. */
    static final int AGC_OFF = 0, AGC_SLOW = 1, AGC_FAST = 2;
    /** Signals above this level (the bulletin is 1) are compressed; the makeup gain brings the bulletin back to 1. */
    static final double AGC_THRESHOLD = 0.25, AGC_POWER = 0.7, MAKEUP = Math.pow(AGC_THRESHOLD, -AGC_POWER);
    /** With the AGC off nothing is compressed, but a ceiling at the bulletin's level stops static crashes from blasting. */
    private static final double CEILING = 1;
    private static final double SLOW_RELEASE_S = 0.5, FAST_RELEASE_S = 0.12, ATTACK_S = 0.0015, LOOKAHEAD_S = 0.004, DIP_S = 0.008, SETTLE_S = 0.016, NORMAL_HZ = 400;
    /** After a signal drops the AGC holds its gain this long before it starts to release (set from the letter gap). */
    static final double DEFAULT_HANG_S = 0.35;
    /** The audio-stage floor: white noise 200 to 2700 Hz, this far under the band-noise density (60 dB, as an amplitude ratio). */
    private static final double FLOOR_LO_HZ = 200, FLOOR_HI_HZ = 2700, FLOOR_DB = -60;
    private static final double DRIFT_EVERY_S = 2.5, DRIFT_DB = 1.5;
    /** The filter is a Butterworth band-pass made from a low-pass of this order, so it has this many sections. */
    private static final int ORDER = 8;

    /** The passband edges in Hz of audio pitch, for a width setting and the CW pitch. */
    static double lo(int width, double tone) { return width == WIDE ? Math.max(150, tone - 400) : width == NARROW ? Math.max(100, tone - 125) : Math.max(120, tone - NORMAL_HZ / 2); }
    static double hi(int width, double tone) { return width == WIDE ? tone + 1200 : width == NARROW ? tone + 125 : tone + NORMAL_HZ / 2; }
    /**
     * The range of station frequencies that are heard, in Hz relative to where the receiver listens. sign +1 is CW
     * (stations above the dial sound higher), -1 is CW-R (they sound lower, so the passband lies below the dial).
     */
    static double[] heard(int width, double tone, int sign) {
        double l = lo(width, tone), h = hi(width, tone);
        return sign < 0 ? new double[]{tone - h, tone - l} : new double[]{l - tone, h - tone};
    }
    /** The passband edges {lo, hi} with the passband moved up or down by shift Hz; the lower edge never goes below 100 Hz. */
    static double[] edges(int width, double tone, int shift) {
        double l = Math.max(100, lo(width, tone) + shift);
        return new double[]{l, Math.max(l + 50, hi(width, tone) + shift)};
    }
    /** As {@link #heard(int, double, int)} with the passband shifted. */
    static double[] heard(int width, double tone, int sign, int shift) {
        double[] e = edges(width, tone, shift);
        return sign < 0 ? new double[]{tone - e[1], tone - e[0]} : new double[]{e[0] - tone, e[1] - tone};
    }
    /** How much of a steady tone at that pitch gets through a filter with those edges, 0 to 1. */
    static double gain(double lo, double hi, double hz) {
        if (hz <= 0) return 0;
        return 1 / Math.sqrt(1 + Math.pow((hz * hz - lo * hi) / ((hi - lo) * hz), 2 * ORDER));
    }

    private final int rate;
    private final Dsp.Rng rng;
    private final Dsp.Biquad[][] bp = new Dsp.Biquad[2][ORDER];
    private final double[][] delay;
    private final Dsp.Biquad[] apfBq = {new Dsp.Biquad(), new Dsp.Biquad()};
    private final double attack, dipStep, driftSmooth, nbSmooth;
    private double releaseKeep;
    private int agcSet = -1;
    private boolean apf, wantApf, nb;
    private double wantTone = 600, nbE;
    private final Lightning storm;
    private final Dsp.Biquad[][] floor = new Dsp.Biquad[2][3];
    private final double floorSigma;
    private double hangS = DEFAULT_HANG_S;
    private int hangLeft, hangLen;
    /** For tests: leaves out the white band noise, so only the static and the floor remain. */
    boolean whiteOff;
    private final double[] nbPrev = new double[2], nbOut = new double[2];
    private int nbWarm = 2;
    private static final double NB_THRESHOLD = 5, NB_TAU_S = 0.05;
    private int at, driftTick, settle;
    private double lo = -1, hi = -1, wantLo, wantHi, dip = 1, peak, env, drift = 1, driftTarget = 1;
    private boolean retune;
    /** The signal level after the filter and before the AGC, held like a meter needle. The bulletin on tune reads 1. */
    volatile double level;

    Rx(int rate, long seed) {
        this.rate = rate; rng = new Dsp.Rng(seed);
        for (int c = 0; c < 2; c++) for (int k = 0; k < ORDER; k++) bp[c][k] = new Dsp.Biquad();
        double perUnit = Math.sqrt(NORMAL_HZ / (rate / 2.0));
        storm = new Lightning(rng, rate, perUnit);
        // Density 60 dB under the band noise (RMS noise^2 over NORMAL_HZ): the white source has deviation noise * sqrt(rate/2 / NORMAL_HZ) * 10^(-60/20).
        floorSigma = Math.sqrt(rate / 2.0 / NORMAL_HZ) * Math.pow(10, FLOOR_DB / 20);
        for (int c = 0; c < 2; c++) {
            floor[c][0] = new Dsp.Biquad().highpass(FLOOR_LO_HZ, 0.7071, rate);
            floor[c][1] = new Dsp.Biquad().lowpass(FLOOR_HI_HZ, 0.5412, rate);
            floor[c][2] = new Dsp.Biquad().lowpass(FLOOR_HI_HZ, 1.3066, rate);
        }
        setHang(DEFAULT_HANG_S);
        delay = new double[2][Math.max(1, (int) (LOOKAHEAD_S * rate))];
        setRelease(SLOW_RELEASE_S);
        nbSmooth = 1 - Math.exp(-1 / (NB_TAU_S * rate));
        attack = 1 - Math.exp(-1 / (ATTACK_S * rate));
        dipStep = 1 / (DIP_S * rate);
        driftSmooth = 1 - Math.exp(-1 / (3.0 * rate));
    }

    /** Sets the static: crackle clusters per second, mean seconds between lightning flashes, and the flash gain (1 normal, 2.5 for a storm nearby). */
    void weather(double ticksPerS, double crashEveryS, double crashGain) { storm.weather(ticksPerS, crashEveryS, crashGain); }

    /** For tests: the static source. */
    Lightning storm() { return storm; }

    /** How long the slow AGC keeps its gain after a signal drops, before it starts to release (the fast one has no hang). */
    void setHang(double seconds) { hangS = Math.max(0, seconds); hangLen = (int) (hangS * rate); }
    double hang() { return hangS; }

    private void setRelease(double s) { releaseKeep = Math.exp(-1 / (s * rate)); }

    /** Switches the noise blanker. It takes effect at once: it only decides which samples are replaced. */
    void blanker(boolean on) { nb = on; }

    /** Sets the passband. A change is made in a short dip to silence, so it cannot click. */
    void tune(double loHz, double hiHz) { tune(loHz, hiHz, wantTone, false); }
    void tune(double loHz, double hiHz, double toneHz) { tune(loHz, hiHz, toneHz, false); }
    /** With the peak filter (peak), a narrow boost at the pitch, which is retuned together with the passband. */
    void tune(double loHz, double hiHz, double toneHz, boolean peak) {
        if (loHz == wantLo && hiHz == wantHi && (toneHz == wantTone || !peak) && peak == wantApf) return;
        wantLo = loHz; wantHi = hiHz; wantTone = toneHz; wantApf = peak;
        if (lo < 0) { apply(); settle = 0; } else retune = true;
    }

    /**
     * Designs the band-pass: each pole of the low-pass prototype becomes two poles of the band-pass, each pair with
     * its mirror image is one section, and the bilinear transform with pre-warped edges makes it digital.
     */
    private void apply() {
        lo = wantLo; hi = wantHi; retune = false;
        apf = wantApf;
        for (int c = 0; c < 2; c++) { apfBq[c].bandpass(wantTone, wantTone / 80.0, rate); apfBq[c].reset(); }
        nbWarm = 2;
        double k = 2.0 * rate, w1 = k * Math.tan(Math.PI * lo / rate), w2 = k * Math.tan(Math.PI * hi / rate), bw = w2 - w1, w0sq = w1 * w2;
        int n = 0;
        for (int i = 0; i < ORDER / 2; i++) {
            double th = Math.PI * (2 * i + 1) / (2 * ORDER), pr = -Math.sin(th) * bw, pi = Math.cos(th) * bw;
            // Roots of s^2 - (bw p) s + w0^2: the square root of a complex number, done by hand.
            double dr = pr * pr - pi * pi - 4 * w0sq, di = 2 * pr * pi, mag = Math.hypot(dr, di);
            double sr = Math.sqrt((mag + dr) / 2), si = Math.sqrt((mag - dr) / 2) * (di < 0 ? -1 : 1);
            for (int sign = -1; sign <= 1; sign += 2) {
                double re = (pr + sign * sr) / 2, im = (pi + sign * si) / 2, m = re * re + im * im;
                double a0 = k * k - 2 * re * k + m, a1 = 2 * (m - k * k) / a0, a2 = (k * k + 2 * re * k + m) / a0, b0 = bw * k / a0;
                for (int c = 0; c < 2; c++) { bp[c][n].set(b0, 0, -b0, a1, a2); bp[c][n].reset(); }
                n++;
            }
        }
        java.util.Arrays.fill(delay[0], 0); java.util.Arrays.fill(delay[1], 0);
        settle = (int) (SETTLE_S * rate);
    }

    /**
     * One channel of the noise blanker. nbOut holds the sample being judged, nbPrev the one before it (as sent on),
     * x is the next one. A sample is a spike when it stands out from both neighbours in the same direction by much
     * more than the usual such distance; it is then replaced by the neighbours' average. Judging by the nearer
     * neighbour keeps the sample next to a spike from being taken for one. A blanked sample counts towards the usual
     * distance only up to the threshold, so the blanker cannot lock itself on after a quiet spell.
     */
    private double blank(int c, double x) {
        double mid = nbOut[c], a = mid - nbPrev[c], b = mid - x;
        double d = a * b > 0 ? Math.min(Math.abs(a), Math.abs(b)) : 0, y = mid;
        if (nb && nbE > 0 && d > NB_THRESHOLD * nbE) { y = 0.5 * (nbPrev[c] + x); d = NB_THRESHOLD * nbE; }
        if (c == 0) nbE = nbE == 0 ? d : nbE + nbSmooth * (d - nbE);
        nbPrev[c] = y; nbOut[c] = x;
        return y;
    }

    /**
     * Filters and levels the signals in place (interleaved stereo) and adds band noise.
     * @param noise0 noise level at the start of the block, as the RMS heard in the gaps with the normal filter
     * @param noise1 the same at the end of the block
     * @param agcMode AGC_OFF, AGC_SLOW or AGC_FAST
     */
    void process(double[] lr, int frames, double noise0, double noise1, int agcMode) {
        if (lo < 0) tune(lo(NORMAL, 600), hi(NORMAL, 600), 600, false);
        if (agcMode != agcSet) { agcSet = agcMode; setRelease(agcMode == AGC_FAST ? FAST_RELEASE_S : SLOW_RELEASE_S); }
        boolean agc = agcMode != AGC_OFF, hold = agcMode == AGC_SLOW;
        double makeup = agc ? MAKEUP : 1;
        // White noise of deviation 1 leaves a filter of that width with this RMS.
        double perUnit = Math.sqrt(NORMAL_HZ / (rate / 2.0)), scale = 1 / (makeup * perUnit);
        double[] dl = delay[0], dr = delay[1];
        int n = dl.length;
        double top = level * Math.pow(releaseKeep, frames);
        for (int i = 0; i < frames; i++) {
            double noise = noise0 + (noise1 - noise0) * i / frames, sigma = noise * scale;
            double l = lr[2 * i], r = lr[2 * i + 1];
            if (sigma > 0) {
                if (driftTick++ >= DRIFT_EVERY_S * rate) { driftTick = 0; driftTarget = Math.pow(10, DRIFT_DB * (2 * rng.uniform() - 1) / 20); }
                drift += driftSmooth * (driftTarget - drift);
                storm.step();
                if (whiteOff) { l += sigma * storm.l; r += sigma * storm.r; }
                else { l += sigma * (rng.gauss() * drift + storm.l); r += sigma * (rng.gauss() * drift + storm.r); }
            }
            // The noise blanker works on the raw signal, where a static tick is one stray sample. The one-sample
            // delay is there whether it is on or off, so switching it cannot move the audio.
            if (nbWarm > 0) { nbWarm--; double t = nbOut[0], u = nbOut[1]; nbOut[0] = nbPrev[0] = l; nbOut[1] = nbPrev[1] = r; l = t; r = u; }
            else { l = blank(0, l); r = blank(1, r); }
            for (int k = 0; k < ORDER; k++) { l = bp[0][k].step(l); r = bp[1][k].step(r); }
            if (apf) { l += apfBq[0].step(l); r += apfBq[1].step(r); }
            // The gain is worked out from the signal a few milliseconds before it is heard, so it never overshoots.
            double a = Math.max(Math.abs(l), Math.abs(r));
            // After a signal the peak is held for the hang time; only a level the AGC works on is held, not the noise floor.
            if (hold && a >= 0.97 * peak && peak > AGC_THRESHOLD) hangLeft = hangLen;
            if (hangLeft > 0) { hangLeft--; peak = Math.max(a, peak); } else peak = Math.max(a, peak * releaseKeep);
            env = peak > env ? env + attack * (peak - env) : peak;
            if (env > top) top = env;
            double g = agc ? (env > AGC_THRESHOLD ? Math.pow(AGC_THRESHOLD / env, AGC_POWER) : 1) : env > CEILING ? CEILING / env : 1;
            double ol = dl[at], or = dr[at];
            dl[at] = l; dr[at] = r;
            if (++at >= n) at = 0;
            // A change of filter: fade out, swap, wait until the new filter has settled, fade in.
            if (retune) { dip -= dipStep; if (dip <= 0) { dip = 0; apply(); } }
            else if (settle > 0) settle--;
            else if (dip < 1) dip = Math.min(1, dip + dipStep);
            double out = makeup * g * dip;
            double fs = noise * floorSigma;
            double fl = 0, fr = 0;
            if (fs > 0) {
                fl = rng.gauss() * fs; fr = rng.gauss() * fs;
                for (int k = 0; k < 3; k++) { fl = floor[0][k].step(fl); fr = floor[1][k].step(fr); }
            }
            lr[2 * i] = ol * out + fl; lr[2 * i + 1] = or * out + fr;
        }
        level = top;
    }
}
