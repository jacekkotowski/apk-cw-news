package pl.cwnews;

/**
 * A simple picture of 40 m propagation and of when people are on the air. By day the band carries a few hundred to
 * about 1500 km and long paths are absorbed; by night long paths open and nearby stations fall into the skip zone.
 * Darkness is worked out from the sun's height at both ends and the middle of the path. Pure Java.
 */
final class Prop {
    private Prop() {}

    /** Where the listener can be: name, latitude, longitude. */
    static final String[] REGION_NAME = {"Europe", "North America, east", "North America, west", "Japan and East Asia", "Australia and New Zealand", "South America", "Southern Africa"};
    static final double[][] REGION = {{50, 15}, {40, -77}, {40, -120}, {36, 138}, {-34, 150}, {-25, -50}, {-30, 25}};

    private static final double RAD = Math.PI / 180, EARTH_KM = 6371;

    /** The sun's height above the horizon in degrees, for a month (0 to 11, taken at its middle) and UTC hour. */
    static double sunElevation(double lat, double lon, int month, double hourUtc) {
        double day = 15 + 30.4 * month, decl = -23.44 * Math.cos(2 * Math.PI * (day + 10) / 365);
        double hourAngle = 15 * (hourUtc - 12) + lon;
        double s = Math.sin(lat * RAD) * Math.sin(decl * RAD) + Math.cos(lat * RAD) * Math.cos(decl * RAD) * Math.cos(hourAngle * RAD);
        return Math.asin(Math.max(-1, Math.min(1, s))) / RAD;
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.sin((lat2 - lat1) * RAD / 2), b = Math.sin((lon2 - lon1) * RAD / 2);
        double h = a * a + Math.cos(lat1 * RAD) * Math.cos(lat2 * RAD) * b * b;
        return 2 * EARTH_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** 0 in daylight, 1 when the sun is 6 degrees or more below the horizon, in between during twilight. */
    private static double dark(double elevation) { return Math.max(0, Math.min(1, -elevation / 6)); }

    private static double ramp(double x, double x0, double x1, double y0, double y1) { return x <= x0 ? y0 : x >= x1 ? y1 : y0 + (y1 - y0) * (x - x0) / (x1 - x0); }

    private static double dayLoss(double km) {
        if (km < 100) return 4;
        if (km < 300) return 2;
        if (km < 1500) return 0;
        if (km < 2500) return ramp(km, 1500, 2500, 0, 14);
        if (km < 4000) return ramp(km, 2500, 4000, 14, 36);
        return Math.min(60, 36 + (km - 4000) / 250);
    }

    private static double nightLoss(double km) {
        if (km < 60) return 6;                      // ground wave
        if (km < 500) return 16;                    // skip zone
        if (km < 900) return ramp(km, 500, 900, 16, 2);
        if (km < 3000) return ramp(km, 900, 1300, 2, 0);
        if (km < 6000) return ramp(km, 3000, 6000, 0, 8);          // DX gets through, but weaker than the next country
        if (km < 12000) return ramp(km, 6000, 12000, 8, 14);
        return ramp(km, 12000, 19000, 14, 20);
    }

    /** Middle of the great circle between two places, from the average of the two direction vectors: {lat, lon}. */
    static double[] midpoint(double lat1, double lon1, double lat2, double lon2) {
        double x = Math.cos(lat1 * RAD) * Math.cos(lon1 * RAD) + Math.cos(lat2 * RAD) * Math.cos(lon2 * RAD);
        double y = Math.cos(lat1 * RAD) * Math.sin(lon1 * RAD) + Math.cos(lat2 * RAD) * Math.sin(lon2 * RAD);
        double z = Math.sin(lat1 * RAD) + Math.sin(lat2 * RAD), len = Math.sqrt(x * x + y * y + z * z);
        return new double[]{len < 1e-6 ? lat1 : Math.asin(z / len) / RAD, len < 1e-6 ? lon1 : Math.atan2(y, x) / RAD};
    }

    /** Whether a path touches the auroral zone: either end, or its middle, is above 55 degrees north or south. */
    static boolean highLatitude(double lat1, double lon1, double lat2, double lon2) {
        return Math.abs(lat1) > 55 || Math.abs(lat2) > 55 || Math.abs(midpoint(lat1, lon1, lat2, lon2)[0]) > 55;
    }

    /** Extra loss in dB on the path between two places, on top of what power and antenna give. */
    static double pathLossDb(double lat1, double lon1, double lat2, double lon2, int month, double hourUtc) {
        double km = distanceKm(lat1, lon1, lat2, lon2);
        double[] mid = midpoint(lat1, lon1, lat2, lon2);
        double midLat = mid[0], midLon = mid[1];
        double e1 = sunElevation(lat1, lon1, month, hourUtc), e2 = sunElevation(lat2, lon2, month, hourUtc), em = sunElevation(midLat, midLon, month, hourUtc);
        double night = (dark(e1) + dark(e2) + dark(em)) / 3;
        // Daylight on any part of the path absorbs more than its share, so it counts for more than a straight mix.
        double day = Math.pow(1 - night, 0.6), nl = nightLoss(km), loss = nl + day * (dayLoss(km) - nl);
        // Around sunrise and sunset at either end, long paths get a lift.
        if (km > 3000 && Math.min(Math.abs(e1), Math.abs(e2)) < 6) loss -= 4;
        return Math.max(0, loss);
    }

    /** The major CW contest that fills the band on this date, or null. dow is 0 for Sunday to 6 for Saturday. */
    static String contest(int month, int day, int dow) {
        int saturday = dow == 6 ? day : dow == 0 ? day - 1 : -1;
        if (saturday < 1) return null;
        if (month == 10 && saturday >= 24 && saturday <= 29) return "CQ WW CW";
        if (month == 1 && saturday >= 15 && saturday <= 21) return "ARRL DX CW";
        if (month == 4 && saturday >= 24 && saturday <= 30) return "CQ WPX CW";
        if (month == 6 && saturday >= 8 && saturday <= 14) return "IARU HF Championship";
        if (month == 7 && saturday >= 8 && saturday <= 14) return "WAE DX CW";
        if (month == 3 && saturday >= 1 && saturday <= 7) return "SP DX Contest";
        return null;
    }

    /** How likely someone is to be at the key at that local hour: little in the small hours, less during work. */
    static double awake(double localHour, boolean weekend) {
        double h = ((localHour % 24) + 24) % 24;
        if (h >= 1 && h < 5) return 0.3;
        if (h < 7 || h >= 23) return 0.6;
        if (!weekend && h >= 9 && h < 16) return 0.6;
        return 1;
    }

    /** How busy the band is where the listener is, as a share of the activity setting. */
    static double activity(double localHour, boolean weekend, boolean contest) {
        if (contest) return 1.25;
        double h = ((localHour % 24) + 24) % 24;
        double a = h >= 17 && h < 23 ? 1 : h >= 7 && h < 17 ? 0.7 : h >= 5 || h < 1 ? 0.6 : 0.45;
        return Math.min(1.1, a * (weekend ? 1.25 : 1));
    }
}
