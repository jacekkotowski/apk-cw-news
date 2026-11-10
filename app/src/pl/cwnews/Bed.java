package pl.cwnews;

/** Synthesized background sounds, scaled to an RMS level near 1 so the level setting means the same for each. */
abstract class Bed {
    static final int OFF = 0, BAND = 1, COSMIC = 2;

    abstract void fill(double[] lr, int frames);
    /** Called with the CW pitch before each block; backgrounds that sit at the pitch retune here. */
    void setTone(double hz) { }
    /** Whether the engine should cut a gap around the CW pitch so this background cannot mask it. */
    boolean notch() { return true; }

    static Bed create(int type, int rate, long seed) {
        if (type == BAND) return new Receiver(rate, seed);
        if (type == COSMIC) return new Cosmic(rate, seed);
        return null;
    }

    /**
     * What a CW receiver plays on an empty band: noise through a narrow filter centred on the pitch, short static ticks
     * that ring at that pitch, now and then a crackle of them, and an AGC that ducks after each one and recovers.
     * Follows the receiver model of WebMorseRunner (Unlicense), written again here.
     */
    static final class Receiver extends Bed {
        private static final double BANDWIDTH_HZ = 450, TICKS_PER_SECOND = 2.5, TICK_MIN = 6, TICK_SPREAD = 22;
        private static final double CRACKLE_EVERY_S = 45, CRACKLE_MIN_S = 0.15, CRACKLE_SPREAD_S = 0.4, CRACKLE_DENSITY_HZ = 220;
        private static final double AGC_REF = 3.2, AGC_POWER = 0.6, AGC_RELEASE_S = 0.35, DRIFT_EVERY_S = 2.5, DRIFT_DB = 1.5;
        private static final double SCALE = 8.2;
        private final int rate;
        private final Dsp.Rng rng;
        private final Dsp.Biquad[][] bp = new Dsp.Biquad[2][2];
        private final double[] env = new double[2];
        private final double releaseKeep, driftSmooth;
        private double tone = -1, crackleLeft, driftTarget = 1, drift = 1, nextDrift;
        private int driftTick;

        Receiver(int rate, long seed) {
            this.rate = rate; rng = new Dsp.Rng(seed);
            for (int c = 0; c < 2; c++) for (int k = 0; k < 2; k++) bp[c][k] = new Dsp.Biquad();
            releaseKeep = Math.exp(-1 / (AGC_RELEASE_S * rate));
            driftSmooth = 1 - Math.exp(-1 / (1.0 * rate));
            setTone(600);
        }

        @Override boolean notch() { return false; }

        @Override void setTone(double hz) {
            if (hz == tone) return;
            tone = hz;
            for (int c = 0; c < 2; c++) for (int k = 0; k < 2; k++) bp[c][k].bandpass(hz, hz / BANDWIDTH_HZ, rate);
        }

        @Override void fill(double[] lr, int frames) {
            double tickChance = TICKS_PER_SECOND / rate, crackleChance = 1.0 / (CRACKLE_EVERY_S * rate), denseChance = CRACKLE_DENSITY_HZ / rate;
            for (int i = 0; i < frames; i++) {
                if (driftTick++ >= DRIFT_EVERY_S * rate) {
                    driftTick = 0;
                    driftTarget = Math.pow(10, DRIFT_DB * (2 * rng.uniform() - 1) / 20);
                }
                drift += driftSmooth * 0.3 * (driftTarget - drift);
                double spark = 0;
                if (rng.uniform() < tickChance) spark = (TICK_MIN + TICK_SPREAD * rng.uniform() * rng.uniform()) * (rng.uniform() < 0.5 ? -1 : 1);
                if (crackleLeft <= 0 && rng.uniform() < crackleChance) crackleLeft = (CRACKLE_MIN_S + CRACKLE_SPREAD_S * rng.uniform()) * rate;
                if (crackleLeft > 0) {
                    crackleLeft--;
                    if (rng.uniform() < denseChance) spark += (3 + 8 * rng.uniform()) * (rng.uniform() < 0.5 ? -1 : 1);
                }
                for (int c = 0; c < 2; c++) {
                    double x = rng.gauss() * drift + (c == 0 ? spark : spark * (0.5 + 0.5 * rng.uniform()));
                    double y = bp[c][1].step(bp[c][0].step(x));
                    env[c] = Math.max(Math.abs(y), env[c] * releaseKeep);
                    double g = env[c] > AGC_REF ? Math.pow(AGC_REF / env[c], AGC_POWER) : 1;
                    lr[2 * i + c] = SCALE * y * g;
                }
            }
        }
    }

    /** A deep-space pad: brown noise, a slowly drifting drone and long swells. */
    static final class Cosmic extends Bed {
        private static final double[] DRONE_HZ = {55, 110.3, 164.6, 220.5};
        private static final double[] DRONE_LEVEL = {0.45, 0.4, 0.22, 0.14};
        private static final double NOISE_LOW = 500, SWELL_MIN_S = 20, SWELL_MAX_S = 40, SCALE = 1.65;
        private final int rate;
        private final Dsp.Rng rng;
        private final double leak, drive;
        private final double[] brown = new double[2];
        private final Dsp.Biquad[] lp = new Dsp.Biquad[2];
        private final double[] phase = new double[DRONE_HZ.length], drift = new double[DRONE_HZ.length], driftPhase = new double[DRONE_HZ.length];
        private double swellPhase, swellStep;

        Cosmic(int rate, long seed) {
            this.rate = rate; rng = new Dsp.Rng(seed);
            leak = Math.exp(-2 * Math.PI * 60.0 / rate);
            drive = Math.sqrt(1 - leak * leak);
            for (int c = 0; c < 2; c++) lp[c] = new Dsp.Biquad().lowpass(NOISE_LOW, 0.7, rate);
            for (int k = 0; k < phase.length; k++) {
                phase[k] = 2 * Math.PI * rng.uniform();
                driftPhase[k] = 2 * Math.PI * rng.uniform();
                drift[k] = 2 * Math.PI / ((25 + 40 * rng.uniform()) * rate);
            }
            nextSwell();
        }
        private void nextSwell() { swellStep = 2 * Math.PI / ((SWELL_MIN_S + (SWELL_MAX_S - SWELL_MIN_S) * rng.uniform()) * rate); }

        @Override void fill(double[] lr, int frames) {
            for (int i = 0; i < frames; i++) {
                double drone = 0;
                for (int k = 0; k < phase.length; k++) {
                    driftPhase[k] += drift[k];
                    if (driftPhase[k] > 2 * Math.PI) driftPhase[k] -= 2 * Math.PI;
                    double cents = 6 * Math.sin(driftPhase[k]);
                    double hz = DRONE_HZ[k] * (1 + cents * 0.000578);
                    phase[k] += 2 * Math.PI * hz / rate;
                    if (phase[k] > 2 * Math.PI) phase[k] -= 2 * Math.PI;
                    drone += DRONE_LEVEL[k] * Math.sin(phase[k]);
                }
                swellPhase += swellStep;
                if (swellPhase > 2 * Math.PI) { swellPhase -= 2 * Math.PI; nextSwell(); }
                double swell = 0.8 + 0.2 * Math.sin(swellPhase);
                for (int c = 0; c < 2; c++) {
                    brown[c] = leak * brown[c] + drive * rng.gauss();
                    lr[2 * i + c] = SCALE * swell * (0.6 * lp[c].step(brown[c]) + drone);
                }
            }
        }
    }
}
