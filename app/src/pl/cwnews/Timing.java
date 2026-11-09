package pl.cwnews;

/**
 * PARIS timing in seconds. The word PARIS is 31 dot units of elements and inner gaps plus 19 units of
 * letter and word gaps. Farnsworth keeps the 31 units at character speed and stretches only the 19.
 */
final class Timing {
    static final double MIN_WPM = 5, MAX_WPM = 50;
    private static final double PARIS_ELEMENT_UNITS = 31, PARIS_GAP_UNITS = 19;
    final double dot, charGap, wordGap;

    Timing(double characterWpm, double effectiveWpm) {
        double c = Math.max(MIN_WPM, Math.min(MAX_WPM, characterWpm));
        double s = Math.max(MIN_WPM, Math.min(c, effectiveWpm));
        dot = 1.2 / c;
        double gapUnit = s >= c ? dot : (60 / s - PARIS_ELEMENT_UNITS * dot) / PARIS_GAP_UNITS;
        charGap = 3 * gapUnit;
        wordGap = 7 * gapUnit;
    }
}
