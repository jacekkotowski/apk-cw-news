package pl.cwnews;

/**
 * The audio mix: CW plus background, sample by sample. It has no Android code, so it can be tested on a PC.
 * Morse position advances only from the CW sample count, so the background cannot change Morse timing.
 * With a band, the bulletin, the stations and the band noise all go through one receiver (Rx): its filter and AGC
 * shape them together. Without a band the bulletin plays alone over the chosen background.
 */
final class Engine {
    static final double CW_PEAK = 0.5;
    private static final double RAMP_S = 0.005, GATE_S = 0.008, BED_IN_S = 2.0, BED_OUT_S = 0.3;
    private static final double NOTCH_WIDTH_HZ = 300;
    private final int rate;
    private final long seed;
    private final double[] bedBuf;
    private final Dsp.Biquad[][] notch = new Dsp.Biquad[2][2];
    private double notchFor = -1;

    volatile double toneHz = 600, volume = 0.8, bedDb = -24, dialKhz = Band.NEWS;
    /** Receiver filter width (an Rx constant) and whether its AGC is on. Both only matter with a band. */
    volatile int width = Rx.NORMAL;
    volatile int agc = Rx.AGC_SLOW;
    /** Passband shift in Hz, the peak filter and the noise blanker. Only matter with a band. */
    volatile int shift;
    volatile boolean apf, nb;
    /** 0 for CW (listening on the upper side of a signal), 1 for CW-R (the lower side). */
    volatile int side;
    private volatile Band band;
    private final Rx rx;
    private final double[] sig;
    private final double[] wx = new double[3];
    private double bandGate, noiseFade;
    private int newsOn, newsAll;
    private volatile int wantBed = Bed.OFF;
    private int bedType = Bed.OFF;
    private Bed bed;
    private long seeds;
    private double bedFade;
    private boolean audible;

    private Keying keying;
    private int run, pos, word;
    private boolean held;
    private double gate = 1, phase;

    Engine(int rate, long seed, int maxFrames) {
        this.rate = rate; this.seed = seed; bedBuf = new double[2 * maxFrames]; sig = new double[2 * maxFrames];
        rx = new Rx(rate, seed ^ 0x5DEECE66DL);
        for (int c = 0; c < 2; c++) for (int k = 0; k < 2; k++) notch[c][k] = new Dsp.Biquad();
    }

    /** With a band, the news is one station on it and the dial decides what is heard. Without one, the news plays alone. */
    void setBand(Band b) { band = b; }
    void setBed(int type) { wantBed = type; }
    /** For tests: plays this background instead of a built-in one. */
    void setBedInstance(Bed b) { bed = b; bedType = wantBed = -1; }
    int bed() { return wantBed; }
    /** While false, the mix fades to silence. */
    void setAudible(boolean a) { audible = a; }
    boolean silent() { return !audible && bedFade <= 0 && noiseFade <= 0 && bandGate <= 0 && (gate <= 0 || keying == null || held); }
    /** The receiver's meter: signal level in its filter, where the bulletin on tune reads 1. Zero without a band. */
    double meter() { return band == null ? 0 : rx.level; }
    /** For a waterfall: the share of the time the bulletin was keyed since the last call. */
    double takeNewsDuty() { double d = newsAll == 0 ? 0 : newsOn / (double) newsAll; newsOn = newsAll = 0; return d; }

    void load(Keying k, int startWord) {
        keying = k; held = false; gate = 1;
        word = Math.max(0, Math.min(startWord, Math.max(0, k.wordStart.length - 1)));
        run = k.wordStart.length == 0 ? k.runs.length : k.wordStart[word];
        pos = 0;
    }
    void clear() { keying = null; }
    /** Whether the bulletin is playing on the band: a story is loaded and not finished, and it is inside the filter. */
    boolean newsHeard() { return band != null && keying != null && run < keying.runs.length && !off; }
    boolean hasStory() { return keying != null; }
    boolean storyDone() { return keying != null && run >= keying.runs.length; }
    int word() { return word; }
    int words() { return keying == null ? 0 : keying.wordStart.length; }
    /** A held story stops at once and later restarts from the beginning of the current word. */
    void hold(boolean h) {
        if (h == held) return;
        held = h;
    }
    boolean held() { return held; }

    private void tuneNotch() {
        if (toneHz == notchFor) return;
        notchFor = toneHz;
        // Two offset notches give a wider cut around the tone than one deep notch.
        double q = toneHz / NOTCH_WIDTH_HZ;
        for (int c = 0; c < 2; c++) {
            notch[c][0].notch(toneHz - 40, q, rate);
            notch[c][1].notch(toneHz + 40, q, rate);
        }
    }

    private boolean off;
    private double pitch = 600;

    private double cwSample() {
        double out = 0;
        if (keying != null && run < keying.runs.length) {
            boolean stopped = (held || off) && gate <= 0;
            if (!stopped) {
                int r = keying.runs[run];
                if (r > 0) {
                    double ramp = Math.min(RAMP_S * rate, r / 2.0);
                    double env = 1;
                    if (pos < ramp) env = 0.5 - 0.5 * Math.cos(Math.PI * (pos + 0.5) / ramp);
                    else if (pos >= r - ramp) env = 0.5 - 0.5 * Math.cos(Math.PI * (r - pos - 0.5) / ramp);
                    out = env * Math.sin(phase);
                    newsOn++;
                }
                newsAll++;
                if (++pos >= Math.abs(r)) {
                    pos = 0; run++;
                    while (word + 1 < keying.wordStart.length && run >= keying.wordStart[word + 1]) word++;
                }
            }
        }
        phase += 2 * Math.PI * pitch / rate;
        if (phase > 2 * Math.PI) phase -= 2 * Math.PI; else if (phase < 0) phase += 2 * Math.PI;
        return out;
    }

    /** Writes interleaved stereo. */
    void render(float[] out, int frames) {
        tuneNotch();
        Band bd = band;
        boolean rxOn = bd != null;
        pitch = toneHz; off = false;
        if (rxOn) {
            double[] edge = Rx.edges(width, toneHz, shift);
            double lo = edge[0], hi = edge[1];
            rx.tune(lo, hi, toneHz, apf);
            rx.blanker(nb);
            bd.weather(wx);
            rx.weather(wx[0], wx[1], wx[2]);
            // The bulletin is one station on the band. Its pitch is its distance from the dial, kept inside what a
            // sampled tone can be; far outside the filter it waits, from the start of the current word, until you tune back.
            int sign = side == 1 ? -1 : 1;
            pitch = Math.max(-rate / 2.2, Math.min(rate / 2.2, toneHz + sign * (Band.NEWS - dialKhz) * 1000));
            off = Rx.gain(lo, hi, pitch) < 0.03;
            java.util.Arrays.fill(sig, 0, 2 * frames, 0);
            if (bandGate > 0 || (!held && audible)) bd.render(sig, frames, dialKhz, toneHz, lo, hi, sign);
        }
        // With a band, its noise is made inside the receiver, so the Ionosphere background object is not needed.
        boolean rxNoise = rxOn && wantBed == Bed.BAND;
        int target = rxNoise ? Bed.OFF : wantBed;
        if (bedType != target && (bedFade <= 0 || bed == null)) {
            bedType = target;
            bed = Bed.create(bedType, rate, seed + seeds++);
            bedFade = 0;
        }
        boolean bedOn = bed != null;
        if (bedOn) { bed.setTone(toneHz); bed.fill(bedBuf, frames); }
        boolean cut = bedOn && bed.notch();
        double bedGain = Math.pow(10, bedDb / 20);
        double gateStep = 1 / (GATE_S * rate), inStep = 1 / (BED_IN_S * rate), outStep = 1 / (BED_OUT_S * rate);
        boolean bedUp = audible && bedType == target;
        for (int i = 0; i < frames; i++) {
            boolean wasStopped = (held || off) && gate <= 0;
            double cw = cwSample();
            boolean hold = held || off;
            gate = hold ? Math.max(0, gate - gateStep) : Math.min(1, gate + gateStep);
            bandGate = (held || !audible) ? Math.max(0, bandGate - gateStep) : Math.min(1, bandGate + gateStep);
            if (!wasStopped && hold && gate <= 0 && keying != null && keying.wordStart.length > 0 && run < keying.runs.length) {
                run = keying.wordStart[word]; pos = 0;
            }
            if (rxOn) { sig[2 * i] = sig[2 * i] * bandGate + cw * gate; sig[2 * i + 1] = sig[2 * i + 1] * bandGate + cw * gate; }
            else sig[2 * i] = sig[2 * i + 1] = cw * gate;
        }
        if (rxOn) {
            double before = noiseFade;
            noiseFade = audible && rxNoise ? Math.min(1, noiseFade + frames * inStep) : Math.max(0, noiseFade - frames * outStep);
            rx.process(sig, frames, bedGain * before, bedGain * noiseFade, agc);
        } else noiseFade = 0;
        double scale = CW_PEAK * volume;
        for (int i = 0; i < frames; i++) {
            double l = sig[2 * i], r = sig[2 * i + 1];
            if (bedOn) {
                bedFade = bedUp ? Math.min(1, bedFade + inStep) : Math.max(0, bedFade - outStep);
                double a = bedGain * bedFade;
                double bl = bedBuf[2 * i], br = bedBuf[2 * i + 1];
                if (cut) { bl = notch[0][1].step(notch[0][0].step(bl)); br = notch[1][1].step(notch[1][0].step(br)); }
                l += a * bl;
                r += a * br;
            }
            out[2 * i] = (float) Dsp.softLimit(l * scale);
            out[2 * i + 1] = (float) Dsp.softLimit(r * scale);
        }
    }
}
