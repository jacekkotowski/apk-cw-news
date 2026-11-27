package pl.cwnews;

/**
 * The weather on the band: thunderstorm static (calm, static, a storm nearby), an aurora that makes signals on
 * northern paths flutter and fade, and a solar flare that blacks out the sunlit side. In Auto it follows season,
 * time of day and chance; the other modes hold one state. Every output moves smoothly towards its target.
 * Pure Java, driven by a seeded random source.
 */
final class Sky {
    static final int AUTO = 0, CALM = 1, STATIC = 2, STORM = 3, AURORA = 4, FLARE = 5;
    static final int T_CALM = 0, T_STATIC = 1, T_STORM = 2;
    private static final double SMOOTH_S = 5;

    private final Dsp.Rng rng;
    private int thunder = T_CALM;
    private double thunderLeft;                    // seconds until the current state ends
    private double auroraAge = -1, auroraLen;      // seconds into an aurora, or -1 when none
    private double flareAge = -1, flareHold;
    private double ticks = 2.5, crash = 40, crashGain = 1, aurora, flare;
    private int lastMode = -1;
    /** The latest real K index, or NaN. From 5 up, Auto uses it for the aurora instead of chance. */
    private double kp = Double.NaN;

    Sky(long seed) {
        // Small seeds make a xorshift source start with tiny numbers, which would trigger every event at once.
        long z = seed * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L; z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL; z ^= z >>> 31;
        rng = new Dsp.Rng(z);
        for (int i = 0; i < 8; i++) rng.uniform();
    }

    /** Share of the year's thunderstorm season at that latitude and month: 1 in summer, 0.1 in winter, 1 all year in the tropics. */
    static double season(int month, double latitude) {
        if (Math.abs(latitude) < 25) return 1;
        int m = latitude < 0 ? (month + 6) % 12 : month;
        return m >= 5 && m <= 7 ? 1 : (m >= 2 && m <= 4) || (m >= 8 && m <= 10) ? 0.5 : 0.1;
    }

    private double perHour(double chancePerHour, double seconds) { return rng.uniform() < chancePerHour * seconds / 3600 ? 1 : 0; }

    void setKp(double kp) { this.kp = kp; }

    /** Moves time on. month 0-11, localHour 0-24 at the listener, latitude in degrees. */
    void advance(double seconds, int month, double localHour, double latitude, int mode) {
        if (mode != lastMode) { lastMode = mode; if (mode != AUTO) hold(mode); else { thunder = T_CALM; auroraAge = flareAge = -1; } }
        double h = ((localHour % 24) + 24) % 24;
        double tTicks, tCrash, tGain, tAurora, tFlare;
        if (mode == AUTO) {
            if (thunder == T_CALM) {
                double p = 0.05 + 0.5 * season(month, latitude) * (h >= 13 && h < 23 ? 1 : 0.3);
                if (perHour(p, seconds) > 0) { thunder = T_STATIC; thunderLeft = (20 + 70 * rng.uniform()) * 60; }
            } else {
                thunderLeft -= seconds;
                if (thunder == T_STATIC) {
                    if (thunderLeft <= 0) thunder = T_CALM;
                    else if (perHour(0.3, seconds) > 0) { thunder = T_STORM; thunderLeft = (5 + 20 * rng.uniform()) * 60; }
                } else if (thunderLeft <= 0) { thunder = T_STATIC; thunderLeft = (20 + 70 * rng.uniform()) * 60; }
            }
            if (kp >= 5) auroraAge = -1;
            else if (auroraAge < 0) { if (perHour(0.004, seconds) > 0) { auroraAge = 0; auroraLen = (2 + 4 * rng.uniform()) * 3600; } }
            else { auroraAge += seconds; if (auroraAge > auroraLen) auroraAge = -1; }
            if (flareAge < 0) { if (h >= 7 && h <= 17 && perHour(0.003, seconds) > 0) { flareAge = 0; flareHold = (5 + 10 * rng.uniform()) * 60; } }
            else { flareAge += seconds; if (flareAge > 180 + flareHold + 1800) flareAge = -1; }
        } else if (mode == STORM && thunder != T_STORM || mode == STATIC && thunder != T_STATIC) hold(mode);
        tAurora = mode == AURORA ? 1 : mode == AUTO && kp >= 5 ? Math.min(1, (kp - 4) / 5) : auroraAge < 0 ? 0 : Math.min(1, Math.min(auroraAge, auroraLen - auroraAge) / 1800);
        tFlare = mode == FLARE ? 1 : flareAge < 0 ? 0 : flareAge < 180 ? flareAge / 180 : flareAge < 180 + flareHold ? 1 : Math.max(0, 1 - (flareAge - 180 - flareHold) / 1800);
        tTicks = thunder == T_STORM ? 80 : thunder == T_STATIC ? 25 : 2.5;
        tCrash = thunder == T_STORM ? 3 : thunder == T_STATIC ? 6 : 40;
        tGain = thunder == T_STORM ? 2.5 : 1;
        double a = 1 - Math.exp(-seconds / SMOOTH_S);
        ticks += a * (tTicks - ticks); crash += a * (tCrash - crash); crashGain += a * (tGain - crashGain);
        aurora += a * (tAurora - aurora); flare += a * (tFlare - flare);
    }

    private void hold(int mode) {
        thunder = mode == STORM ? T_STORM : mode == STATIC ? T_STATIC : T_CALM;
        thunderLeft = 1e9;
        auroraAge = flareAge = -1;
    }

    /** 0 calm, 1 static, 2 storm: the thunderstorm state the sky is heading for. */
    int thunder() { return thunder; }
    double ticksPerSecond() { return ticks; }
    double crashEverySeconds() { return crash; }
    double crashGain() { return crashGain; }
    double aurora() { return aurora; }
    double flare() { return flare; }
    /** How much static there is, 0 to 1: 0 calm, about 0.5 static, 1 storm. */
    double noise() { return Math.max(0, Math.min(1, (ticks - 2.5) / 37.5)); }

    /** A short sentence for the Band tab, or an empty string when the band is calm. */
    String label() {
        if (flare > 0.3) return "Solar flare: signals on the sunlit side have faded out.";
        if (ticks > 32) return "A thunderstorm is close: loud crashes.";
        if (aurora > 0.3) return "Aurora: signals from the north flutter and fade.";
        if (ticks > 12) return "Thunderstorms in the distance: crackling static.";
        return "";
    }
}
