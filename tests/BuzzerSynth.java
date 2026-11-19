package pl.cwnews;

/** Test sound: a key's buzzer as a microphone hears it. Not shipped. */
final class BuzzerSynth {
    int rate = 16000;
    double hz = 700, amp = 0.3, snrDb = 20, echoGain = 0.3, echoMs = 23, rampMs = 3;
    /** The band leaking from the speaker at 600 Hz, in dB relative to the buzzer, or NaN for none; what it keys. */
    double leakDb = Double.NaN;
    Keying leakKeying;
    double leadS = 0.5, tailS = 1.0;
    /** A door slam: position in seconds, or negative for none. */
    double slamAt = -1;

    /** The buzzer's own sound for a keying: square-ish, the fundamental with the 2nd and 3rd harmonics at -10 and -16 dB. */
    float[] render(Keying k, Dsp.Rng r) {
        int lead = (int) (leadS * rate), n = lead + (k == null ? 0 : (int) k.totalSamples) + (int) (tailS * rate);
        double[] y = new double[n];
        double ph = 0, w = 2 * Math.PI * hz / rate;
        int ramp = Math.max(1, (int) (rampMs / 1000 * rate)), at = lead;
        if (k != null) for (int run : k.runs) {
            int len = Math.abs(run);
            for (int i = 0; i < len && at + i < n; i++) {
                if (run > 0) {
                    double g = 1;
                    if (i < ramp) g = i / (double) ramp; else if (len - i <= ramp) g = (len - i) / (double) ramp;
                    y[at + i] = amp * g * (Math.sin(ph) + 0.316 * Math.sin(2 * ph) + 0.158 * Math.sin(3 * ph));
                }
                ph += w;
            }
            at += len;
        }
        double[] out = y.clone();
        int d = (int) (echoMs / 1000 * rate);
        if (echoGain > 0) for (int i = d; i < n; i++) out[i] += echoGain * y[i - d];
        if (!Double.isNaN(leakDb) && leakKeying != null) {
            double la = amp * Math.pow(10, leakDb / 20), lp = 0, lw = 2 * Math.PI * 600.0 / rate;
            int p = 0;
            for (int run : leakKeying.runs) { int len = Math.abs(run); for (int i = 0; i < len && p + i < n; i++) { if (run > 0) out[p + i] += la * Math.sin(lp); lp += lw; } p += len; }
        }
        double sigma = amp * 0.75 / Math.pow(10, snrDb / 20);
        float[] x = new float[n];
        for (int i = 0; i < n; i++) x[i] = (float) (out[i] + sigma * r.gauss());
        if (slamAt >= 0) { int s0 = (int) (slamAt * rate); for (int i = 0; i < rate / 1000 && s0 + i < n; i++) x[s0 + i] += (float) (0.9 * r.gauss()); }
        return x;
    }

    /** A steady buzzer (key held) of that many seconds. */
    float[] held(double seconds, Dsp.Rng r) { return render(Keying.carrier(seconds, rate), r); }
    float[] quiet(double seconds, Dsp.Rng r) { double l = leadS, t = tailS; leadS = seconds; tailS = 0; float[] x = render(null, r); leadS = l; tailS = t; return x; }
    /** The room with the band leaking and the key up (a steady carrier: the worst case). */
    float[] leaking(double seconds, Dsp.Rng r) {
        BuzzerSynth b = copy(); b.leadS = seconds; b.tailS = 0; b.leakKeying = Keying.carrier(seconds, rate);
        return b.render(null, r);
    }
    /** The room with the key up and the band's own keying leaking in for as long as that keying lasts. */
    float[] render(Keying k, Dsp.Rng r, Keying leakOver) {
        double l = leadS, t = tailS; leadS = leakOver.totalSamples / (double) rate; tailS = 0;
        Keying keep = leakKeying; leakKeying = leakOver;
        float[] x = render(k, r);
        leadS = l; tailS = t; leakKeying = keep;
        return x;
    }
    BuzzerSynth copy() { BuzzerSynth b = new BuzzerSynth(); b.rate = rate; b.hz = hz; b.amp = amp; b.snrDb = snrDb; b.echoGain = echoGain; b.echoMs = echoMs; b.rampMs = rampMs; b.leakDb = leakDb; b.leakKeying = leakKeying; return b; }
}
