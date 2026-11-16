package pl.cwnews;

/**
 * The receiver the band is heard through. Stations, the bulletin, band noise and static are mixed first and then
 * pass one band-pass filter and one AGC, so they affect each other as on a real rig: a strong signal turns the gain
 * down and the noise drops with it, then rushes back in the gaps. Pure Java, no allocation while running.
 */
final class Rx {
    static final int WIDE = 0, NORMAL = 1, NARROW = 2;
    /** Signals above this level (the bulletin is 1) are compressed; the makeup gain brings the bulletin back to 1. */
    static final double AGC_THRESHOLD = 0.25, AGC_POWER = 0.7, MAKEUP = Math.pow(AGC_THRESHOLD, -AGC_POWER);
    /** With the AGC off nothing is compressed, but a ceiling at the bulletin's level stops static crashes from blasting. */
    private static final double CEILING = 1;
    private static final double RELEASE_S = 0.35, ATTACK_S = 0.0015, LOOKAHEAD_S = 0.004, DIP_S = 0.008, SETTLE_S = 0.016, NORMAL_HZ = 400;
    private static final double TICKS_PER_S = 2.5, TICK_MIN = 6, TICK_SPREAD = 22, CRASH_EVERY_S = 40, CRASH_MIN_S = 0.15, CRASH_SPREAD_S = 0.45, CRASH_DENSITY_HZ = 1300;
    private static final double DRIFT_EVERY_S = 2.5, DRIFT_DB = 1.5;
    /** The filter is a Butterworth band-pass made from a low-pass of this order, so it has this many sections. */
    private static final int ORDER = 8;

    /** The passband edges in Hz of audio pitch, for a width setting and the CW pitch. */
    static double lo(int width, double tone) { return width == WIDE ? Math.max(150, tone - 400) : width == NARROW ? Math.max(100, tone - 125) : Math.max(120, tone - NORMAL_HZ / 2); }
    static double hi(int width, double tone) { return width == WIDE ? tone + 1200 : width == NARROW ? tone + 125 : tone + NORMAL_HZ / 2; }
    /** How much of a steady tone at that pitch gets through a filter with those edges, 0 to 1. */
    static double gain(double lo, double hi, double hz) {
        if (hz <= 0) return 0;
        return 1 / Math.sqrt(1 + Math.pow((hz * hz - lo * hi) / ((hi - lo) * hz), 2 * ORDER));
    }

    private final int rate;
    private final Dsp.Rng rng;
    private final Dsp.Biquad[][] bp = new Dsp.Biquad[2][ORDER];
    private final double[][] delay;
    private final double releaseKeep, attack, dipStep, driftSmooth;
    private int at, driftTick, settle;
    private double lo = -1, hi = -1, wantLo, wantHi, dip = 1, peak, env, crashLeft, drift = 1, driftTarget = 1;
    private boolean retune;
    /** The signal level after the filter and before the AGC, held like a meter needle. The bulletin on tune reads 1. */
    volatile double level;

    Rx(int rate, long seed) {
        this.rate = rate; rng = new Dsp.Rng(seed);
        for (int c = 0; c < 2; c++) for (int k = 0; k < ORDER; k++) bp[c][k] = new Dsp.Biquad();
        delay = new double[2][Math.max(1, (int) (LOOKAHEAD_S * rate))];
        releaseKeep = Math.exp(-1 / (RELEASE_S * rate));
        attack = 1 - Math.exp(-1 / (ATTACK_S * rate));
        dipStep = 1 / (DIP_S * rate);
        driftSmooth = 1 - Math.exp(-1 / (3.0 * rate));
    }

    /** Sets the passband. A change is made in a short dip to silence, so it cannot click. */
    void tune(double loHz, double hiHz) {
        if (loHz == wantLo && hiHz == wantHi) return;
        wantLo = loHz; wantHi = hiHz;
        if (lo < 0) { apply(); settle = 0; } else retune = true;
    }

    /**
     * Designs the band-pass: each pole of the low-pass prototype becomes two poles of the band-pass, each pair with
     * its mirror image is one section, and the bilinear transform with pre-warped edges makes it digital.
     */
    private void apply() {
        lo = wantLo; hi = wantHi; retune = false;
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
     * Filters and levels the signals in place (interleaved stereo) and adds band noise.
     * @param noise0 noise level at the start of the block, as the RMS heard in the gaps with the normal filter
     * @param noise1 the same at the end of the block
     */
    void process(double[] lr, int frames, double noise0, double noise1, boolean agc) {
        if (lo < 0) tune(lo(NORMAL, 600), hi(NORMAL, 600));
        double makeup = agc ? MAKEUP : 1;
        // White noise of deviation 1 leaves a filter of that width with this RMS.
        double perUnit = Math.sqrt(NORMAL_HZ / (rate / 2.0)), scale = 1 / (makeup * perUnit);
        double tickChance = TICKS_PER_S / rate, crashChance = 1.0 / (CRASH_EVERY_S * rate), denseChance = CRASH_DENSITY_HZ / rate;
        double[] dl = delay[0], dr = delay[1];
        int n = dl.length;
        double top = level * Math.pow(releaseKeep, frames);
        for (int i = 0; i < frames; i++) {
            double sigma = (noise0 + (noise1 - noise0) * i / frames) * scale;
            double l = lr[2 * i], r = lr[2 * i + 1];
            if (sigma > 0) {
                if (driftTick++ >= DRIFT_EVERY_S * rate) { driftTick = 0; driftTarget = Math.pow(10, DRIFT_DB * (2 * rng.uniform() - 1) / 20); }
                drift += driftSmooth * (driftTarget - drift);
                double spark = 0;
                if (rng.uniform() < tickChance) spark = (TICK_MIN + TICK_SPREAD * rng.uniform() * rng.uniform()) * (rng.uniform() < 0.5 ? -1 : 1);
                if (crashLeft <= 0 && rng.uniform() < crashChance) crashLeft = (CRASH_MIN_S + CRASH_SPREAD_S * rng.uniform()) * rate;
                if (crashLeft > 0) {
                    crashLeft--;
                    if (rng.uniform() < denseChance) spark += (8 + 22 * rng.uniform()) * (rng.uniform() < 0.5 ? -1 : 1);
                }
                l += sigma * (rng.gauss() * drift + spark);
                r += sigma * (rng.gauss() * drift + spark * (0.5 + 0.5 * rng.uniform()));
            }
            for (int k = 0; k < ORDER; k++) { l = bp[0][k].step(l); r = bp[1][k].step(r); }
            // The gain is worked out from the signal a few milliseconds before it is heard, so it never overshoots.
            double a = Math.max(Math.abs(l), Math.abs(r));
            peak = Math.max(a, peak * releaseKeep);
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
            lr[2 * i] = ol * out; lr[2 * i + 1] = or * out;
        }
        level = top;
    }
}
