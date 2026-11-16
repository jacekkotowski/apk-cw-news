package pl.cwnews;

import java.util.ArrayList;
import java.util.List;

/**
 * A slice of the 40 m CW band with made-up contacts on it. Every station sends in real time on its own frequency.
 * A station is a tone whose pitch is its distance from the dial. This class writes the tones as they reach the
 * receiver, at full strength; the receiver's filter (Rx) then decides what is heard, so tuning across the band sweeps
 * signals in and out. Each operator has a fist, fading with its own rhythm, and sometimes a chirp, hard keying,
 * a rough note or a drifting rig.
 * Pure Java and thread-safe: the audio thread renders, the UI thread reads activity and the text sent so far.
 */
final class Band {
    static final double LOW = 7000, HIGH = 7040, NEWS = 7020;
    private static final double MIN_GAP_KHZ = 0.3, RAMP_S = 0.005, HARD_RAMP_S = 0.0007, PEAK = 0.6, CHIRP_S = 0.012, CLICK_REACH_HZ = 1500;
    private static final int LOG_CHARS = 700;

    interface Headlines { String next(); }

    private static final class Op {
        double offsetKhz, strength, phase;
        // Fading: two slow rhythms, a random wander and, on some paths, a fast flutter.
        double depthDb, p1, p2, w1, w2, wander, wanderTarget, flutter, pf, wf;
        /** Gain at the start and at the end of the block being rendered. */
        double g0, g1;
        // Faults of the signal.
        double chirpHz, chirpNow, ramp = RAMP_S, driftKhzPerS, drifted, hum, humPhase, humStep;
        Fist fist;
    }

    private static final class Station {
        double khz;
        Qso qso;
        Op[] op = new Op[0];
        int next, who, wait, run, pos, wordIdx, onFrames, allFrames;
        Keying key;
        String[] words = new String[0];
        final StringBuilder log = new StringBuilder();
    }

    private final int rate;
    private final Dsp.Rng rng;
    private final Headlines headlines;
    private int month;
    private double hourUtc = 12;
    private long sinceClock;
    private final List<Station> slots = new ArrayList<>();
    private double wpm = 20, eff = 12, tone = 600, lo = Rx.lo(Rx.NORMAL, 600), hi = Rx.hi(Rx.NORMAL, 600);
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

    /** Sets the date and time the stations live in: month 0 to 11 and the UTC hour. The hour then runs on with the audio. */
    synchronized void setClock(int month, double hourUtc) { this.month = month; this.hourUtc = hourUtc; sinceClock = 0; }
    private int hourNow() { return (int) ((hourUtc + sinceClock / (double) rate / 3600) % 24); }

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
        Qso q = s.qso = Qso.generate(rng, month, hourNow(), h == null ? null : Qso.shortHeadline(h));
        s.khz = freeFrequency();
        s.next = 0; s.who = 0; s.key = null; s.log.setLength(0);
        // The generator decides how strong each operator is, so the reports they exchange match what is heard.
        s.op = new Op[q.call.length];
        for (int i = 0; i < s.op.length; i++) {
            Op o = s.op[i] = new Op();
            o.offsetKhz = i == 0 ? 0 : rng.gauss() * 0.04;
            o.strength = PEAK * Math.pow(10, -q.lossDb[i] / 20);
            o.depthDb = q.qsbDb[i];
            o.p1 = 2 * Math.PI * rng.uniform(); o.p2 = 2 * Math.PI * rng.uniform();
            o.w1 = 2 * Math.PI / (10 + 35 * rng.uniform()); o.w2 = 2 * Math.PI / (2 + 6 * rng.uniform());
            o.wander = o.wanderTarget = rng.uniform();
            if (rng.chance(0.07)) { o.flutter = 0.2 + 0.25 * rng.uniform(); o.wf = 2 * Math.PI * (6 + 8 * rng.uniform()); }
            o.g0 = o.g1 = gain(o);
            o.fist = Fist.of(q.key[i], q.style[i] == Qso.NOVICE, rng);
            if (q.chirp[i]) o.chirpHz = (rng.chance(0.5) ? 1 : -1) * (12 + 30 * rng.uniform());
            if (q.rough[i]) { o.hum = 0.35 + 0.35 * rng.uniform(); o.humStep = 2 * Math.PI * (rng.chance(0.5) ? 100 : 120) / rate; }
            if (q.drift[i]) o.driftKhzPerS = (rng.chance(0.5) ? 1 : -1) * (0.0003 + 0.0012 * rng.uniform());
            if (rng.chance(0.08)) o.ramp = HARD_RAMP_S; else if (rng.chance(0.2)) o.ramp = 0.008;
        }
        s.wait = (int) (q.tx.get(0).pause * rate);
    }

    /** The operator's strength right now, with fading. */
    private static double gain(Op o) {
        double down = o.depthDb * (0.55 * (0.5 - 0.5 * Math.cos(o.p1)) + 0.25 * (0.5 - 0.5 * Math.cos(o.p2)) + 0.2 * o.wander);
        return o.strength * Math.pow(10, -down / 20) * (1 - o.flutter * (0.5 + 0.5 * Math.sin(o.pf)));
    }

    private void startTx(Station s) {
        Qso.Tx t = s.qso.tx.get(s.next);
        Op o = s.op[t.who];
        s.who = t.who;
        s.run = 0; s.pos = 0; s.wordIdx = -1;
        if (t.carrier > 0) {
            s.key = Keying.carrier(t.carrier, rate);
            s.words = new String[0];
            s.log.append("\n(someone tuning up) ");
        } else {
            s.key = Keying.build(t.text, new Timing(wpm * t.speed, eff * t.speed), rate, o.fist, rng);
            s.words = t.text.trim().split("\\s+");
            s.log.append('\n').append(s.qso.call[t.who]).append(": ");
        }
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
            s.wait = (int) (s.qso.tx.get(s.next).pause * rate);
        }
    }

    /** As {@link #render(double[], int, double, double, double, double)} with the normal filter. */
    void render(double[] lr, int frames, double dialKhz, double toneHz) { render(lr, frames, dialKhz, toneHz, Rx.lo(Rx.NORMAL, toneHz), Rx.hi(Rx.NORMAL, toneHz)); }

    /**
     * Adds all stations to the stereo buffer (interleaved), as they reach a receiver set to dialKhz with a CW pitch of
     * toneHz. The tones are not filtered here; loHz and hiHz are the receiver's passband and only decide which
     * stations are worth rendering.
     */
    synchronized void render(double[] lr, int frames, double dialKhz, double toneHz, double loHz, double hiHz) {
        boolean slow = ++blocks % 50 == 0;
        sinceClock += frames;
        tone = toneHz; lo = loHz; hi = hiHz;
        double dt = frames / (double) rate, chirpKeep = Math.exp(-1 / (CHIRP_S * rate));
        for (Station s : slots) {
            if (s.qso == null) {
                s.wait -= frames;
                if (s.wait <= 0) spawn(s);
                continue;
            }
            for (Op o : s.op) {
                if (slow) o.wanderTarget = rng.uniform();
                o.wander += 0.02 * (o.wanderTarget - o.wander);
                o.p1 += o.w1 * dt; o.p2 += o.w2 * dt; o.pf += o.wf * dt;
                if (o.driftKhzPerS != 0) {
                    o.drifted += o.driftKhzPerS * dt;
                    if (Math.abs(o.drifted) > 0.06) o.driftKhzPerS = -o.driftKhzPerS;
                }
                o.g0 = o.g1; o.g1 = gain(o);
            }
            Op o = null;
            double hz = 0, step = 0, ramp = 0, left = 0.55 + 0.1 * Math.sin(s.khz * 7), right = 1.1 - left;
            boolean audible = false;
            for (int i = 0; i < frames; i++) {
                if (s.key == null) {
                    if (--s.wait > 0) continue;
                    startTx(s);
                    o = null;
                }
                if (o == null) {
                    o = s.op[s.who];
                    hz = toneHz + (s.khz + o.offsetKhz + o.drifted - dialKhz) * 1000;
                    // Hard keying splatters, so such a station is rendered even when its tone is outside the filter.
                    boolean inFilter = Math.max(o.g0, o.g1) * Rx.gain(loHz, hiHz, hz) > 0.0008;
                    audible = hz > 30 && hz < 6000 && (inFilter || (o.ramp < 0.002 && Math.abs(hz - toneHz) < CLICK_REACH_HZ));
                    step = 2 * Math.PI * hz / rate; ramp = o.ramp * rate;
                }
                s.allFrames++;
                int r = s.key.runs[s.run];
                if (r > 0) {
                    s.onFrames++;
                    if (audible) {
                        if (s.pos == 0) o.chirpNow = o.chirpHz;
                        double e = Math.min(ramp, r / 2.0), env = 1;
                        if (s.pos < e) env = 0.5 - 0.5 * Math.cos(Math.PI * (s.pos + 0.5) / e);
                        else if (s.pos >= r - e) env = 0.5 - 0.5 * Math.cos(Math.PI * (r - s.pos - 0.5) / e);
                        if (o.hum > 0) { env *= 1 - o.hum * (0.5 + 0.5 * Math.sin(o.humPhase)); o.humPhase += o.humStep; if (o.humPhase > 2 * Math.PI) o.humPhase -= 2 * Math.PI; }
                        double v = (o.g0 + (o.g1 - o.g0) * i / frames) * env * Math.sin(o.phase);
                        lr[2 * i] += v * left; lr[2 * i + 1] += v * right;
                        o.phase += step;
                        if (o.chirpNow != 0) { o.phase += 2 * Math.PI * o.chirpNow / rate; o.chirpNow *= chirpKeep; if (Math.abs(o.chirpNow) < 0.05) o.chirpNow = 0; }
                        if (o.phase > 2 * Math.PI) o.phase -= 2 * Math.PI; else if (o.phase < 0) o.phase += 2 * Math.PI;
                    }
                }
                if (++s.pos >= Math.abs(r)) {
                    s.pos = 0; s.run++;
                    advanceWords(s);
                    if (s.run >= s.key.runs.length) { endTx(s); o = null; if (s.qso == null) break; }
                }
            }
        }
    }

    private static String num(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    private static double level(Op o) { return Math.min(1, o.g1 / PEAK * 1.2); }

    /** One entry per busy station: {"k": kHz, "l": level 0..1, "on": keying right now}. */
    synchronized String activityJson() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Station s : slots) {
            if (s.qso == null) continue;
            Op o = s.op[s.who];
            if (!first) b.append(',');
            first = false;
            b.append("{\"k\":").append(num(s.khz + o.offsetKhz + o.drifted)).append(",\"l\":").append(num(level(o))).append(",\"on\":").append(s.key != null).append('}');
        }
        return b.append(']').toString();
    }

    /**
     * For a waterfall: one [kHz, level 0..1, share of the time keyed] per busy station since the last call.
     * Reading it starts a new interval.
     */
    synchronized String waterfallJson() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Station s : slots) {
            if (s.qso == null) continue;
            Op o = s.op[s.who];
            double duty = s.allFrames == 0 ? 0 : s.onFrames / (double) s.allFrames;
            s.onFrames = s.allFrames = 0;
            if (!first) b.append(',');
            first = false;
            b.append('[').append(num(s.khz + o.offsetKhz + o.drifted)).append(',').append(num(level(o))).append(',').append(num(duty)).append(']');
        }
        return b.append(']').toString();
    }

    /** The station nearest the dial among those the filter lets through. */
    private Station tuned(double dialKhz) {
        Station best = null; double bestKhz = Double.MAX_VALUE;
        for (Station s : slots) {
            if (s.qso == null) continue;
            double away = Math.abs(s.khz - dialKhz);
            if (away < bestKhz && Rx.gain(lo, hi, tone + (s.khz - dialKhz) * 1000) > 0.1) { bestKhz = away; best = s; }
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
