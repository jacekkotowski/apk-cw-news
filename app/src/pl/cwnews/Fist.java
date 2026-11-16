package pl.cwnews;

/**
 * The way one operator's hand shapes the code. All values multiply the exact timing: a keyer is exact, a bug makes
 * machine dits and long hand-made dahs, a straight key varies everywhere, a sideswiper is short and rushed.
 */
final class Fist {
    static final int KEYER = 0, BUG = 1, STRAIGHT = 2, SIDESWIPER = 3;
    /** Mean length of dits and dahs, their spread, the spread of the gaps, the letter gap, and pauses between words. */
    final double dit, dah, ditJitter, dahJitter, gapJitter, charGap, wordStretch, hesitate;

    Fist(double dit, double dah, double ditJitter, double dahJitter, double gapJitter, double charGap, double wordStretch, double hesitate) {
        this.dit = dit; this.dah = dah; this.ditJitter = ditJitter; this.dahJitter = dahJitter; this.gapJitter = gapJitter;
        this.charGap = charGap; this.wordStretch = wordStretch; this.hesitate = hesitate;
    }

    /** Draws one operator's fist for a kind of key. A beginner is less even and stops to think more often. */
    static Fist of(int key, boolean novice, Dsp.Rng r) {
        double sloppy = novice ? 1.6 : 0.7 + 0.6 * r.uniform();
        switch (key) {
            case BUG: return new Fist(0.88 + 0.1 * r.uniform(), (3.5 + 0.9 * r.uniform()) / 3, 0.02, 0.1 * sloppy, 0.12 * sloppy, 0.95 + 0.25 * r.uniform(), 0.25, 0.04);
            case STRAIGHT: return new Fist(0.95 + 0.2 * r.uniform(), (2.7 + 0.7 * r.uniform()) / 3, 0.09 * sloppy, 0.09 * sloppy, 0.13 * sloppy, 0.95 + 0.3 * r.uniform(), 0.3, novice ? 0.15 : 0.06);
            case SIDESWIPER: return new Fist(1 + 0.15 * r.uniform(), (2.3 + 0.5 * r.uniform()) / 3, 0.08 * sloppy, 0.08 * sloppy, 0.1 * sloppy, 0.8 + 0.15 * r.uniform(), 0.15, 0.03);
            default: return new Fist(1, 1, 0, 0, 0.05 * sloppy, 0.97 + 0.12 * r.uniform(), 0.15, novice ? 0.1 : 0.02);
        }
    }

    double element(boolean isDah, double exact, Dsp.Rng r) {
        double j = isDah ? dahJitter : ditJitter;
        return exact * Math.max(0.45, (isDah ? dah : dit) * (j > 0 ? 1 + j * r.gauss() : 1));
    }
    /** The gap between the elements of one character. */
    double inner(double exact, Dsp.Rng r) { return ditJitter > 0 ? exact * Math.max(0.5, 1 + ditJitter * r.gauss()) : exact; }
    /** The gap between letters: never so short that letters run together. */
    double letter(double exact, Dsp.Rng r) { return exact * Math.max(0.7, charGap * (1 + gapJitter * r.gauss())); }
    double word(double exact, double dot, Dsp.Rng r) {
        return exact * (1 + wordStretch * Math.abs(r.gauss())) + (r.chance(hesitate) ? (4 + 10 * r.uniform()) * dot : 0);
    }
}
