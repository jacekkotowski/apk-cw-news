package pl.cwnews;

/** Synthesized background sounds, scaled to an RMS level near 1 so the level setting means the same for each. */
abstract class Bed {
    static final int OFF = 0, IONOSPHERE = 1, COSMIC = 2;

    abstract void fill(double[] lr, int frames);

    static Bed create(int type, int rate, long seed) {
        if (type == IONOSPHERE) return new Ionosphere(rate, seed);
        if (type == COSMIC) return new Cosmic(rate, seed);
        return null;
    }

    /** A quiet shortwave band: passband hiss, slow Rayleigh fading and rare distant static. */
    static final class Ionosphere extends Bed {
        private static final double HISS_LOW = 200, HISS_HIGH = 2400, TILT = 900;
        private static final double FADE_HZ = 0.1, FADE_MIN_DB = -6, FADE_MAX_DB = 3;
        private static final int FADE_EVERY = 32;
        private static final double CRACKLES_PER_SECOND = 0.7, CRACKLE_LOW = 1200, CRACKLE_LEVEL = 1.6;
        private static final double SCALE = 12.5;
        private final int rate;
        private final Dsp.Rng rng;
        private final Dsp.Biquad[] hp = new Dsp.Biquad[2], lp1 = new Dsp.Biquad[2], lp2 = new Dsp.Biquad[2], tilt = new Dsp.Biquad[2], crackle = new Dsp.Biquad[2];
        private final double fadeA, fadeNorm, fadeSmooth;
        private double fi, fq, gainTarget = 1, gain = 1, burst, burstDecay, panL = 0.5, panR = 0.5;
        private int tick;

        Ionosphere(int rate, long seed) {
            this.rate = rate; rng = new Dsp.Rng(seed);
            for (int c = 0; c < 2; c++) {
                hp[c] = new Dsp.Biquad().highpass(HISS_LOW, 0.7, rate);
                lp1[c] = new Dsp.Biquad().lowpass(HISS_HIGH, 0.7, rate);
                lp2[c] = new Dsp.Biquad().lowpass(HISS_HIGH, 0.7, rate);
                tilt[c] = new Dsp.Biquad().lowpass(TILT, 0.7, rate);
                crackle[c] = new Dsp.Biquad().lowpass(CRACKLE_LOW, 0.7, rate);
            }
            double fadeRate = (double) rate / FADE_EVERY;
            fadeA = Math.min(1, 2 * Math.PI * FADE_HZ / fadeRate);
            fadeNorm = Math.sqrt(fadeA / (2 - fadeA));
            fadeSmooth = 1 - Math.exp(-2 * Math.PI * 5.0 / rate);
            fi = rng.gauss(); fq = rng.gauss();
        }

        @Override void fill(double[] lr, int frames) {
            double crackleChance = CRACKLES_PER_SECOND / rate;
            for (int i = 0; i < frames; i++) {
                if (tick++ % FADE_EVERY == 0) {
                    fi += fadeA * (rng.gauss() / fadeNorm - fi);
                    fq += fadeA * (rng.gauss() / fadeNorm - fq);
                    double power = (fi * fi + fq * fq) / 2;
                    double db = Math.max(FADE_MIN_DB, Math.min(FADE_MAX_DB, 10 * Math.log10(Math.max(1e-9, power))));
                    gainTarget = Math.pow(10, db / 20);
                }
                gain += fadeSmooth * (gainTarget - gain);
                if (burst < 1e-4 && rng.uniform() < crackleChance) {
                    burst = CRACKLE_LEVEL * (0.4 + rng.uniform());
                    burstDecay = Math.exp(-1 / ((0.008 + 0.017 * rng.uniform()) * rate));
                    panL = 0.25 + 0.5 * rng.uniform(); panR = 1 - panL;
                }
                double spark = burst * rng.gauss();
                burst *= burstDecay;
                for (int c = 0; c < 2; c++) {
                    double h = lp2[c].step(lp1[c].step(hp[c].step(rng.gauss())));
                    double soft = 0.55 * h + 0.45 * tilt[c].step(h);
                    double cr = crackle[c].step(spark * (c == 0 ? panL : panR));
                    lr[2 * i + c] = SCALE * soft * gain * 0.48 + cr;
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
