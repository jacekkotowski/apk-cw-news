package pl.cwnews;

import java.util.ArrayList;
import java.util.List;

/**
 * A slice of the 40 m CW band with made-up contacts on it. Every station sends in real time on its own frequency.
 * What you hear depends on the dial: a station is a tone whose pitch is its distance from the dial, shaped by a
 * narrow CW filter, so tuning across the band sweeps signals in and out like a real receiver.
 * Pure Java and thread-safe: the audio thread renders, the UI thread reads activity and the text sent so far.
 */
final class Band {
    static final double LOW = 7000, HIGH = 7040, NEWS = 7020, FILTER_HZ = 320;
    private static final double MIN_GAP_KHZ = 0.3, RAMP_S = 0.005, PEAK = 0.6;
    private static final int LOG_CHARS = 700;

    interface Headlines { String next(); }

    /** How loud a signal that far (in Hz) from the dial is, from 0 to 1. */
    static double response(double offsetHz) {
        double x = offsetHz / FILTER_HZ;
        return x > 4 || x < -4 ? 0 : Math.exp(-x * x);
    }

    private static final class Op {
        double offsetKhz, wpmFactor, strength, depthDb, fade = 1, fadeTarget = 1, phase;
    }

    private static final class Station {
        double khz;
        Qso qso;
        final Op[] op = {new Op(), new Op()};
        int next, who, wait, run, pos, wordIdx;
        Keying key;
        String[] words = new String[0];
        final StringBuilder log = new StringBuilder();
    }

    private final int rate;
    private final Dsp.Rng rng;
    private final Headlines headlines;
    private final int month;
    private final List<Station> slots = new ArrayList<>();
    private double wpm = 20, eff = 12;
    private int blocks;

    Band(int rate, long seed, int month, Headlines headlines) {
        this.rate = rate; this.rng = new Dsp.Rng(seed); this.month = month; this.headlines = headlines;
        setActivity(1);
    }

    /** 0 quiet, 1 normal, 2 busy. */
    synchronized void setActivity(int level) {
        int n = new int[]{3, 6, 9}[Math.max(0, Math.min(2, level))];
        while (slots.size() < n) { Station s = new Station(); s.wait = (int) ((1 + 20 * rng.uniform()) * rate); slots.add(s); }
        while (slots.size() > n) slots.remove(slots.size() - 1);
    }

    synchronized void setSpeed(double wpm, double eff) { this.wpm = wpm; this.eff = Math.min(eff, wpm); }

    private double freeFrequency() {
        for (int tries = 0; tries < 30; tries++) {
            double f = LOW + 0.3 + (HIGH - LOW - 0.6) * rng.uniform();
            boolean ok = Math.abs(f - NEWS) > 0.4;
            for (Station s : slots) if (s.qso != null && Math.abs(s.khz - f) < MIN_GAP_KHZ) ok = false;
            if (ok) return f;
        }
        return LOW + 0.3 + (HIGH - LOW - 0.6) * rng.uniform();
    }

    private void spawn(Station s) {
        String h = headlines == null ? null : headlines.next();
        s.qso = Qso.generate(rng, month, h == null ? null : Qso.shortHeadline(h));
        s.khz = freeFrequency();
        s.next = 0; s.key = null; s.log.setLength(0);
        for (int i = 0; i < 2; i++) {
            Op o = s.op[i];
            o.offsetKhz = i == 0 ? 0 : rng.gauss() * 0.04;
            o.wpmFactor = 0.8 + 0.55 * rng.uniform();
            double u = rng.uniform();
            o.strength = PEAK * Math.pow(10, -(2 + 28 * u * u) / 20);
            o.depthDb = 2 + 12 * rng.uniform();
            o.fade = 1; o.fadeTarget = 1;
        }
        s.wait = (int) ((0.5 + 2 * rng.uniform()) * rate);
    }

    private void startTx(Station s) {
        Qso.Tx t = s.qso.tx.get(s.next);
        Op o = s.op[t.who];
        s.who = t.who;
        double f = o.wpmFactor;
        s.key = Keying.build(t.text, new Timing(wpm * f, eff * f), rate);
        s.words = t.text.trim().split("\\s+");
        s.run = 0; s.pos = 0; s.wordIdx = -1;
        s.log.append('\n').append(s.qso.call[t.who]).append(": ");
        advanceWords(s);
        if (s.log.length() > 2 * LOG_CHARS) s.log.delete(0, s.log.length() - LOG_CHARS);
    }

    private void advanceWords(Station s) {
        if (s.words.length != s.key.wordStart.length) return;
        while (s.wordIdx + 1 < s.key.wordStart.length && s.run >= s.key.wordStart[s.wordIdx + 1]) s.log.append(s.words[++s.wordIdx]).append(' ');
    }

    private void endTx(Station s) {
        s.key = null;
        s.next++;
        if (s.next >= s.qso.tx.size()) {
            s.qso = null;
            s.wait = (int) ((6 + 34 * rng.uniform()) * rate);
        } else {
            double gap = s.qso.kind == Qso.CQ ? 3 + 4 * rng.uniform() : 0.6 + 2 * rng.uniform();
            s.wait = (int) (gap * rate);
        }
    }

    /** Adds all stations to the stereo buffer (interleaved), as heard with the receiver set to dialKhz and a pitch of toneHz. */
    synchronized void render(double[] lr, int frames, double dialKhz, double toneHz) {
        boolean slow = ++blocks % 50 == 0;
        for (Station s : slots) {
            if (s.qso == null) {
                s.wait -= frames;
                if (s.wait <= 0) spawn(s);
                continue;
            }
            if (slow) for (Op o : s.op) o.fadeTarget = Math.pow(10, -o.depthDb * rng.uniform() * rng.uniform() / 20);
            for (Op o : s.op) o.fade += 0.02 * (o.fadeTarget - o.fade);
            Op o = s.op[s.who];
            double offsetHz = (s.khz + o.offsetKhz - dialKhz) * 1000, hz = toneHz + offsetHz;
            double gain = o.strength * o.fade * response(offsetHz);
            boolean audible = gain > 0.002 && hz > 120 && hz < 2000;
            double step = 2 * Math.PI * hz / rate, ramp = RAMP_S * rate;
            double left = 0.55 + 0.1 * Math.sin(s.khz * 7), right = 1.1 - left;
            for (int i = 0; i < frames; i++) {
                if (s.key == null) {
                    if (--s.wait > 0) continue;
                    startTx(s);
                    o = s.op[s.who];
                    offsetHz = (s.khz + o.offsetKhz - dialKhz) * 1000; hz = toneHz + offsetHz;
                    gain = o.strength * o.fade * response(offsetHz);
                    audible = gain > 0.002 && hz > 120 && hz < 2000;
                    step = 2 * Math.PI * hz / rate;
                }
                int r = s.key.runs[s.run];
                if (r > 0 && audible) {
                    double e = Math.min(ramp, r / 2.0), env = 1;
                    if (s.pos < e) env = 0.5 - 0.5 * Math.cos(Math.PI * (s.pos + 0.5) / e);
                    else if (s.pos >= r - e) env = 0.5 - 0.5 * Math.cos(Math.PI * (r - s.pos - 0.5) / e);
                    double v = gain * env * Math.sin(o.phase);
                    lr[2 * i] += v * left; lr[2 * i + 1] += v * right;
                }
                o.phase += step;
                if (o.phase > 2 * Math.PI) o.phase -= 2 * Math.PI;
                if (++s.pos >= Math.abs(r)) {
                    s.pos = 0; s.run++;
                    advanceWords(s);
                    if (s.run >= s.key.runs.length) { endTx(s); if (s.qso == null) break; }
                }
            }
        }
    }

    /** One entry per busy station: {"k": kHz, "l": level 0..1, "on": keying right now}. */
    synchronized String activityJson() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Station s : slots) {
            if (s.qso == null) continue;
            Op o = s.op[s.who];
            if (!first) b.append(',');
            first = false;
            b.append("{\"k\":").append(String.format(java.util.Locale.ROOT, "%.2f", s.khz + o.offsetKhz))
                .append(",\"l\":").append(String.format(java.util.Locale.ROOT, "%.2f", Math.min(1, o.strength * o.fade / PEAK * 1.2)))
                .append(",\"on\":").append(s.key != null).append('}');
        }
        return b.append(']').toString();
    }

    private Station tuned(double dialKhz) {
        Station best = null; double bestR = 0.05;
        for (Station s : slots) {
            if (s.qso == null) continue;
            double r = response((s.khz - dialKhz) * 1000);
            if (r > bestR) { bestR = r; best = s; }
        }
        return best;
    }

    /** What the station nearest the dial has sent so far, or an empty string. */
    synchronized String copy(double dialKhz) {
        Station s = tuned(dialKhz);
        if (s == null) return "";
        String t = s.log.toString().trim();
        return t.length() > LOG_CHARS ? t.substring(t.length() - LOG_CHARS) : t;
    }

    /** The frequency of the next busy station above (dir 1) or below (dir -1) the dial, wrapping round. News counts as a station. */
    synchronized double next(double dialKhz, int dir) {
        List<Double> f = new ArrayList<>();
        f.add(NEWS);
        for (Station s : slots) if (s.qso != null) f.add(s.khz);
        double best = Double.NaN, wrap = Double.NaN;
        for (double x : f) {
            if (dir > 0 ? x > dialKhz + 0.05 : x < dialKhz - 0.05) {
                if (Double.isNaN(best) || (dir > 0 ? x < best : x > best)) best = x;
            }
            if (Double.isNaN(wrap) || (dir > 0 ? x < wrap : x > wrap)) wrap = x;
        }
        return Double.isNaN(best) ? wrap : best;
    }
}
