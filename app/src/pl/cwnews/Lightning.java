package pl.cwnews;

/**
 * Atmospheric static made of lightning, as impulses to add on the antenna side of the receiver. Close flashes have a
 * few return strokes (each a cluster of impulses a few milliseconds long) over a train of small discharges that lasts
 * the whole flash; distant ones arrive as crackle, short clusters of weak impulses. Levels are chosen as the peak an
 * impulse cluster makes through the normal-width filter, in dB above the band-noise RMS there, and turned into
 * amplitudes in units of the white-noise deviation. Pure Java, no allocation while running.
 */
final class Lightning {
    static final int MAX_STROKES = 12;
    /** Crackle: 2 to 8 impulses over 2 ms, median peak and spread in dB, cap in dB. */
    private static final double CRACKLE_CALM_DB = -3, CRACKLE_STORM_DB = 6, CRACKLE_SIGMA_DB = 7, CRACKLE_CAP_DB = 25, CRACKLE_S = 0.002;
    /** Flashes: first-stroke median at crashGain 1 and 2.5, spread, cap. */
    private static final double STROKE_DB = 18, STROKE_STORM_DB = 32, STROKE_SIGMA_DB = 6, STROKE_CAP_DB = 45;
    /** A flash is not started during another, so the wait for the next one is shortened by about a flash's length to keep the rate at one per crashEveryS. */
    private static final double MEAN_FLASH_S = 0.33;
    private static final double KEEP_P = 5.0 / 7;
    private static final double FLASH_GM_S = 0.3, FLASH_SIGMA = 0.4, FLASH_MIN_S = 0.15, FLASH_MAX_S = 0.6;
    private static final double GAP_GM_S = 0.06, GAP_SIGMA = 0.7, GAP_MIN_S = 0.01, GAP_MAX_S = 0.3;
    private static final double K_GM_S = 0.0025, K_SIGMA = 0.4, K_MIN_S = 0.0007, K_MAX_S = 0.02, K_DB_SIGMA = 4;
    /** Brings the peak of a cluster of random-sign impulses through the filter back to the intended dB. */
    private static final double CLUSTER_CAL = 1.07;

    /** A short burst of impulses spread evenly over a time. */
    private static final class Cluster {
        int left, count; double amp;
        void start(int len, int n, double a) {
            if (left > 0) { left = Math.max(left, len); count += n; amp = Math.max(amp, a); } else { left = len; count = n; amp = a; }
        }
    }

    /** What the current sample adds to each channel, in units of the white-noise deviation. */
    double l, r;

    private final Dsp.Rng rng;
    private final int rate;
    private final double perUnit;
    private final Cluster crackle = new Cluster(), stroke = new Cluster();
    private double ticksPerS = 2.5, crashEveryS = 40, crashGain = 1;
    private int flashLeft, kLeft, nextStroke, strokesLeft;
    private double strokeDb, kMedianDb;
    private final int[] gaps = new int[MAX_STROKES];
    private int gapAt;

    // What happened, for the tests.
    long flashes, intraCloud, strokeFlashes, strokeCount, gapCount, kCount, ticks;
    double gapLogSum, kLogSum, lastCrackleDb, lastStrokeDb; int lastFlashSamples;

    /** @param perUnit the RMS that white noise of deviation 1 leaves in the normal filter */
    Lightning(Dsp.Rng rng, int rate, double perUnit) { this.rng = rng; this.rate = rate; this.perUnit = perUnit; }

    void weather(double ticksPerS, double crashEveryS, double crashGain) { this.ticksPerS = ticksPerS; this.crashEveryS = crashEveryS; this.crashGain = crashGain; }

    /** Whether a flash is going on, and how many samples of it are left. */
    int flashLeft() { return flashLeft; }

    /** How loud ticks are, 0 for calm to 1 for a storm: the same measure as Sky.noise(). */
    static double storminess(double ticksPerS) { return Math.max(0, Math.min(1, (ticksPerS - 2.5) / 37.5)); }
    static double crackleMedianDb(double ticksPerS) { return CRACKLE_CALM_DB + (CRACKLE_STORM_DB - CRACKLE_CALM_DB) * storminess(ticksPerS); }
    static double strokeMedianDb(double crashGain) { return STROKE_DB + (STROKE_STORM_DB - STROKE_DB) * Math.max(0, Math.min(1, (crashGain - 1) / 1.5)); }

    private double logNormal(double gm, double sigma, double lo, double hi) { return Math.max(lo, Math.min(hi, gm * Math.exp(sigma * rng.gauss()))); }
    private double amp(double db, int n) { return CLUSTER_CAL * Math.pow(10, db / 20) / (perUnit * Math.sqrt(n)); }

    private double pulse(Cluster c) {
        if (c.left <= 0) return 0;
        double v = 0;
        if (c.count > 0 && rng.uniform() * c.left < c.count) { c.count--; v = c.amp * (0.5 + rng.uniform()) * (rng.uniform() < 0.5 ? -1 : 1); }
        c.left--;
        return v;
    }

    private void startFlash() {
        flashes++;
        boolean ic = rng.uniform() < 0.5;
        double med = strokeMedianDb(crashGain);
        int strokes = 0;
        if (ic) intraCloud++;
        else {
            strokes = rng.uniform() < 0.2 ? 1 : 2;
            if (strokes == 2) while (strokes < MAX_STROKES && rng.uniform() < KEEP_P) strokes++;
            strokeFlashes++; strokeCount += strokes;
        }
        double len = logNormal(FLASH_GM_S, FLASH_SIGMA, FLASH_MIN_S, FLASH_MAX_S) * rate, total = 0;
        for (int i = 0; i + 1 < strokes; i++) {
            double g = logNormal(GAP_GM_S, GAP_SIGMA, GAP_MIN_S, GAP_MAX_S);
            gaps[i] = (int) (g * rate); total += gaps[i];
            gapLogSum += Math.log(g); gapCount++;
        }
        flashLeft = lastFlashSamples = (int) Math.max(len, total + 0.05 * rate);
        strokesLeft = strokes; gapAt = 0; nextStroke = 0;
        strokeDb = Math.min(STROKE_CAP_DB, med + STROKE_SIGMA_DB * rng.gauss());
        kMedianDb = med - (7 + 6 * rng.uniform());
        kLeft = 0;
    }

    /** Works out l and r for the next sample. */
    void step() {
        double v = 0;
        if (rng.uniform() < ticksPerS / rate) {
            int n = 2 + rng.nextInt(7);
            double db = Math.min(CRACKLE_CAP_DB, crackleMedianDb(ticksPerS) + CRACKLE_SIGMA_DB * rng.gauss());
            lastCrackleDb = db; ticks++;
            crackle.start((int) (CRACKLE_S * rate), n, amp(db, n));
        }
        if (flashLeft <= 0) { if (rng.uniform() < 1.0 / (Math.max(0.5 * crashEveryS, crashEveryS - MEAN_FLASH_S) * rate)) startFlash(); }
        if (flashLeft > 0) {
            flashLeft--;
            if (strokesLeft > 0 && nextStroke-- <= 0) {
                int n = 5 + rng.nextInt(16);
                stroke.start((int) ((1 + 4 * rng.uniform()) * 0.001 * rate), n, amp(strokeDb, n));
                lastStrokeDb = strokeDb;
                strokeDb -= 3 + 5 * rng.uniform();
                if (--strokesLeft > 0) nextStroke = gaps[gapAt++];
            }
            if (kLeft-- <= 0) {
                double g = logNormal(K_GM_S, K_SIGMA, K_MIN_S, K_MAX_S);
                kLeft = (int) (g * rate); kLogSum += Math.log(g); kCount++;
                v += amp(kMedianDb + K_DB_SIGMA * rng.gauss(), 1) * (rng.uniform() < 0.5 ? -1 : 1);
            }
        }
        v += pulse(crackle) + pulse(stroke);
        l = v;
        r = v == 0 ? 0 : v * (0.5 + 0.5 * rng.uniform());
    }
}
