package pl.cwnews;

/** Small allocation-free audio building blocks. */
final class Dsp {
    private Dsp() {}

    /** Fast seeded random source, so a render can be repeated exactly. */
    static final class Rng {
        private long s;
        Rng(long seed) { s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }
        double uniform() {
            s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
            return (s >>> 11) * 0x1.0p-53;
        }
        /** Approximately Gaussian with mean 0 and standard deviation 1. */
        double gauss() {
            double a = 0;
            for (int i = 0; i < 6; i++) a += uniform();
            return (a - 3) * 1.4142135623730951;
        }
    }

    static final class Biquad {
        private double b0 = 1, b1, b2, a1, a2, x1, x2, y1, y2;

        Biquad lowpass(double f, double q, int rate) {
            double w = 2 * Math.PI * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
            return this;
        }
        Biquad highpass(double f, double q, int rate) {
            double w = 2 * Math.PI * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = b0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0;
            return this;
        }
        Biquad notch(double f, double q, int rate) {
            double w = 2 * Math.PI * f / rate, c = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = 1 / a0; b1 = -2 * c / a0; b2 = b0; a1 = b1; a2 = (1 - alpha) / a0;
            return this;
        }
        double step(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            if (Math.abs(y) < 1e-20) y = 0;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }

    /** Transparent below 0.9, and never reaches full scale above it. */
    static double softLimit(double x) {
        double a = Math.abs(x);
        if (a <= 0.9) return x;
        double y = 0.9 + 0.1 * tanh((a - 0.9) / 0.1);
        return x < 0 ? -y : y;
    }
    private static double tanh(double x) {
        if (x > 8) return 1;
        double e = Math.exp(2 * x);
        return (e - 1) / (e + 1);
    }
}
