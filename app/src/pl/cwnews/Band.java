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
    /** The story station: a net from a Script, keyed only while the listener is tuned to it. */
    static final double STORY = 7010;
    private static final int STORY_CHUNK = 20;
    private static final int[] STORY_KEYS = {Fist.KEYER, Fist.BUG, Fist.STRAIGHT, Fist.SIDESWIPER};
    private static final double MIN_GAP_KHZ = 0.3, RAMP_S = 0.005, HARD_RAMP_S = 0.0007, PEAK = 0.6, CHIRP_S = 0.012, CLICK_REACH_HZ = 1500;
    private static final int LOG_CHARS = 700;
    /** Below this strength (about 40 dB under a full signal) a station cannot be copied. */
    private static final double UNHEARD = PEAK * 0.01;

    interface Headlines { String next(); }

    private static final class Op {
        double offsetKhz, strength, phase;
        // Fading: two slow rhythms, a random wander and, on some paths, a fast flutter.
        double depthDb, p1, p2, w1, w2, wander, wanderTarget, flutter, pf, wf;
        /** Gain at the start and at the end of the block being rendered. */
        double g0, g1;
        /** Whether the path to this operator reaches the auroral zone or lies in daylight, and the rate of the aurora's flutter. */
        boolean northern, day;
        double auroraW, auroraPhase;
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
        /** The user's partner when this is a live contact, the words of the over being sent, finished lines, and frames waiting for the user. */
        Partner live;
        final StringBuilder partial = new StringBuilder();
        final List<String> lines = new ArrayList<>();
        long idle;
        /** The story station's own random source (null: the band's), whether it holds its next over, the run it stops at, and where its current over begins in the log. */
        Dsp.Rng own;
        boolean hold;
        int stopRun = Integer.MAX_VALUE, logMark;
    }

    private final int rate;
    private final Dsp.Rng rng;
    private final Headlines headlines;
    private int month;
    private double hourUtc = 12;
    private long sinceClock;
    // Where the listener is, and what kind of day it is. Without a place there is no propagation.
    private boolean placed, weekend;
    private double lat, lon;
    private String contest;
    private final List<Station> slots = new ArrayList<>();
    private double wpm = 20, eff = 12, tone = 600, lo = Rx.lo(Rx.NORMAL, 600), hi = Rx.hi(Rx.NORMAL, 600);
    private int blocks;
    private final Sky sky;
    private int weather = Sky.AUTO;
    /** +1 when stations above the dial sound higher (CW), -1 when they sound lower (CW-R). */
    private int sign = 1;
    /** For follow(): frames since a station was last keyed inside the filter, and whether a contact is audible in it now. */
    private long quietFrames;
    private boolean busyHere;
    // The story: the script, its station (outside slots), the index of each call, the first line of the loaded chunk,
    // the line to go to once the current over stops (-1 none), and whether its next or current over is in the filter.
    private Script story;
    private Station st;
    private java.util.Map<String, Integer> storyIdx;
    private int chunkStart, cutTo = -1;
    private boolean storyTuned;

    Band(int rate, long seed, int month, Headlines headlines) {
        this.rate = rate; this.rng = new Dsp.Rng(seed); this.month = month; this.headlines = headlines;
        sky = new Sky(seed * 31 + 7);
        setActivity(1);
    }

    /** 0 quiet, 1 normal, 2 busy. */
    synchronized void setActivity(int level) {
        int n = new int[]{3, 6, 9}[Math.max(0, Math.min(2, level))];
        while (slots.size() < n) { Station s = new Station(); s.wait = (int) ((1 + 20 * rng.uniform()) * rate); slots.add(s); }
        while (slots.size() > n) slots.remove(slots.size() - 1);
    }

    /** Band weather: a Sky mode, Sky.AUTO to follow season, time and chance. */
    synchronized void setWeather(int mode) { weather = Math.max(Sky.AUTO, Math.min(Sky.FLARE, mode)); }
    /** The three noise numbers for the receiver, written into out: ticks per second, seconds between crashes, crash gain. */
    synchronized void weather(double[] out) { out[0] = sky.ticksPerSecond(); out[1] = sky.crashEverySeconds(); out[2] = sky.crashGain(); }
    /** One line about the weather for the Band tab, or an empty string. */
    synchronized String sky() { return sky.label(); }

    /** The latest real K index, or NaN for none. */
    synchronized void setKp(double kp) { sky.setKp(kp); }

    synchronized void setSpeed(double wpm, double eff) { this.wpm = wpm; this.eff = Math.min(eff, wpm); }

    /** Sets the date and time the stations live in: month 0 to 11 and the UTC hour. The hour then runs on with the audio. */
    synchronized void setClock(int month, double hourUtc) { this.month = month; this.hourUtc = hourUtc; sinceClock = 0; }
    /** As above with the date: day of the month and day of the week (0 Sunday to 6 Saturday), for weekends and contests. */
    synchronized void setClock(int month, int day, int dow, double hourUtc) {
        setClock(month, hourUtc);
        weekend = dow == 0 || dow == 6;
        contest = Prop.contest(month, day, dow);
    }
    /** Puts the listener somewhere on Earth: from then on, who is heard and how strongly follows propagation. */
    synchronized void setPlace(double lat, double lon) { placed = true; this.lat = lat; this.lon = lon; }
    /** The contest that fills the band today, or null. */
    synchronized String contest() { return contest; }
    private double hourUtcNow() { return (hourUtc + sinceClock / (double) rate / 3600) % 24; }
    private int hourNow() { return (int) ((hourUtc + sinceClock / (double) rate / 3600) % 24); }

    /** How many of the slots are in use at this time of day: evenings and weekends are busier. */
    private int active() {
        if (!placed) return slots.size();
        double f = Prop.activity(hourNow() + lon / 15, weekend, contest != null);
        return Math.max(2, Math.min(slots.size() + (contest != null ? 2 : 0), (int) Math.round(slots.size() * f)));
    }

    /** The part of the band, in kHz, where that kind of contact gathers. */
    static double[] zoneRange(int zone) {
        return zone == Qso.FAST_END ? new double[]{LOW + 0.3, 7012} : zone == Qso.QRP_CORNER ? new double[]{7027, 7034} : zone == Qso.SLOW_END ? new double[]{7033, HIGH - 0.3} : new double[]{LOW + 0.3, HIGH - 0.3};
    }

    private double freeFrequency(int zone) {
        for (int tries = 0; tries < 30; tries++) {
            // Most stations go where their kind gathers; some do not care, and a full corner spills over.
            double[] z = zoneRange(tries < 12 && rng.chance(0.85) ? zone : Qso.ANYWHERE);
            double f = z[0] + (z[1] - z[0]) * rng.uniform();
            boolean ok = Math.abs(f - NEWS) > 0.4 && (st == null || Math.abs(f - STORY) > 0.4);
            for (Station s : slots) if (s.qso != null && Math.abs(s.khz - f) < MIN_GAP_KHZ) ok = false;
            if (ok) return f;
        }
        double f = LOW + 0.3 + (HIGH - LOW - 0.6) * rng.uniform();
        // No extra draw for this, so the band's random sequence is the same with and without a story.
        if (st != null && Math.abs(f - STORY) <= 0.4) f = f < STORY ? STORY - 0.41 : STORY + 0.41;
        return f;
    }

    private void spawn(Station s) {
        String h = headlines == null ? null : headlines.next();
        Qso.Scene scene = placed ? new Qso.Scene(month, hourNow(), lat, lon, weekend, contest != null) : new Qso.Scene(month, hourNow());
        scene.qrn = sky.noise(); scene.aurora = sky.aurora() > 0.3;
        Qso q = s.qso = Qso.generate(rng, scene, h == null ? null : Qso.shortHeadline(h));
        s.khz = freeFrequency(q.zone);
        s.next = 0; s.who = 0; s.key = null; s.log.setLength(0);
        // The generator decides how strong each operator is, so the reports they exchange match what is heard.
        s.op = new Op[q.call.length];
        for (int i = 0; i < s.op.length; i++) s.op[i] = initOp(q, i);
        s.wait = (int) (q.tx.get(0).pause * rate);
    }

    /** Makes operator i of a contact: strength, fading, fist and faults. The order of the random draws is part of the band's behaviour. */
    private Op initOp(Qso q, int i) {
        Op o = new Op();
        o.offsetKhz = i == 0 ? 0 : rng.gauss() * 0.04;
        // Weak signals that can still be copied are lifted by a fifth of their loss (8 dB at the edge), because a
        // signal that far down was inaudible under the noise. Beyond 40 dB nothing is heard, as before.
        double loss = q.lossDb[i];
        o.strength = PEAK * Math.pow(10, -(loss < 40 ? 0.8 * loss : loss) / 20);
        o.depthDb = q.qsbDb[i];
        o.p1 = 2 * Math.PI * rng.uniform(); o.p2 = 2 * Math.PI * rng.uniform();
        o.w1 = 2 * Math.PI / (10 + 35 * rng.uniform()); o.w2 = 2 * Math.PI / (2 + 6 * rng.uniform());
        o.wander = o.wanderTarget = rng.uniform();
        if (rng.chance(0.07)) { o.flutter = 0.2 + 0.25 * rng.uniform(); o.wf = 2 * Math.PI * (6 + 8 * rng.uniform()); }
        o.auroraW = 2 * Math.PI * (12 + 8 * o.p1 / (2 * Math.PI));
        if (placed) {
            o.northern = Prop.highLatitude(lat, lon, q.lat[i], q.lon[i]);
            double[] mid = Prop.midpoint(lat, lon, q.lat[i], q.lon[i]);
            o.day = Prop.sunElevation(mid[0], mid[1], month, hourUtcNow()) > 0;
        }
        o.g0 = o.g1 = gain(o);
        o.fist = Fist.of(q.key[i], q.style[i] == Qso.NOVICE, rng);
        if (q.chirp[i]) o.chirpHz = (rng.chance(0.5) ? 1 : -1) * (12 + 30 * rng.uniform());
        if (q.rough[i]) { o.hum = 0.35 + 0.35 * rng.uniform(); o.humStep = 2 * Math.PI * (rng.chance(0.5) ? 100 : 120) / rate; }
        if (q.drift[i]) o.driftKhzPerS = (rng.chance(0.5) ? 1 : -1) * (0.0003 + 0.0012 * rng.uniform());
        if (rng.chance(0.08)) o.ramp = HARD_RAMP_S; else if (rng.chance(0.2)) o.ramp = 0.008;
        return o;
    }

    /** The operator's strength right now, with fading. */
    private double gain(Op o) {
        double down = o.depthDb * (0.55 * (0.5 - 0.5 * Math.cos(o.p1)) + 0.25 * (0.5 - 0.5 * Math.cos(o.p2)) + 0.2 * o.wander);
        return o.strength * Math.pow(10, -down / 20) * (1 - o.flutter * (0.5 + 0.5 * Math.sin(o.pf))) * skyFactor(o.northern, o.day, sky.aurora(), sky.flare(), o.auroraPhase);
    }

    /**
     * What the weather does to one operator's signal. An aurora makes a path through the auroral zone flutter and
     * lose 12 dB; a solar flare takes 30 dB off every path through daylight.
     */
    static double skyFactor(boolean northern, boolean day, double aurora, double flare, double phase) {
        double f = 1;
        if (northern) f *= (1 - 0.5 * aurora * (0.5 + 0.5 * Math.sin(phase))) * Math.pow(10, -12 * aurora / 20);
        if (day) f *= Math.pow(10, -30 * flare / 20);
        return f;
    }

    private void startTx(Station s) {
        Qso.Tx t = s.qso.tx.get(s.next);
        Op o = s.op[t.who];
        s.who = t.who;
        s.run = 0; s.pos = 0; s.wordIdx = -1; s.partial.setLength(0);
        s.logMark = s.log.length();
        if (t.carrier > 0) {
            s.key = Keying.carrier(t.carrier, rate);
            s.words = new String[0];
            s.log.append("\n(someone tuning up) ");
        } else {
            s.key = Keying.build(t.text, new Timing(wpm * t.speed, eff * t.speed), rate, o.fist, s.own != null ? s.own : rng);
            if (o.strength < UNHEARD) {
                // The other half of a contact, beyond the skip or too far away: nothing to copy here.
                s.words = new String[0];
                s.log.append("\n(the other station, not heard here) ");
            } else {
                s.words = t.text.trim().split("\\s+");
                s.log.append('\n').append(s.qso.call[t.who]).append(": ");
            }
        }
        advanceWords(s);
        if (s.log.length() > 2 * LOG_CHARS) { int cut = s.log.length() - LOG_CHARS; s.log.delete(0, cut); s.logMark = Math.max(0, s.logMark - cut); }
    }

    private void advanceWords(Station s) {
        if (s.words.length != s.key.wordStart.length) return;
        while (s.wordIdx + 1 < s.key.wordStart.length && s.run >= s.key.wordStart[s.wordIdx + 1]) { s.log.append(s.words[++s.wordIdx]).append(' '); if (s.live != null) s.partial.append(s.words[s.wordIdx]).append(' '); }
    }

    private void endTx(Station s) {
        if (s == st) { storyEnd(s); return; }
        s.key = null;
        s.next++;
        if (s.live != null) {
            // A live contact waits for the user: the station is not replaced, and what it just sent goes into the transcript.
            if (s.partial.length() > 0) { s.lines.add(s.qso.call[0] + ": " + s.qso.tx.get(s.next - 1).text); s.partial.setLength(0); }
            else s.lines.add(s.qso.call[0] + ": (not heard here)");
            s.idle = 0;
            if (s.next < s.qso.tx.size()) s.wait = (int) (s.qso.tx.get(s.next).pause * rate);
            else if (s.live.done()) { s.live = null; s.qso = null; s.lines.clear(); s.wait = (int) ((6 + 34 * rng.uniform()) * rate); }
            else s.wait = 1 << 30;
            return;
        }
        if (s.next >= s.qso.tx.size()) {
            s.qso = null;
            s.wait = (int) ((6 + 34 * rng.uniform()) * rate);
        } else {
            s.wait = (int) (s.qso.tx.get(s.next).pause * rate);
        }
    }

    /** As {@link #render(double[], int, double, double, double, double)} with the normal filter. */
    void render(double[] lr, int frames, double dialKhz, double toneHz) { render(lr, frames, dialKhz, toneHz, Rx.lo(Rx.NORMAL, toneHz), Rx.hi(Rx.NORMAL, toneHz)); }

    /** As {@link #render(double[], int, double, double, double, double, int)} on the upper side (CW). */
    void render(double[] lr, int frames, double dialKhz, double toneHz, double loHz, double hiHz) { render(lr, frames, dialKhz, toneHz, loHz, hiHz, 1); }

    /**
     * Adds all stations to the stereo buffer (interleaved), as they reach a receiver set to dialKhz with a CW pitch of
     * toneHz. The tones are not filtered here; loHz and hiHz are the receiver's passband and only decide which
     * stations are worth rendering. With sign -1 (CW-R) a station above the dial sounds below the pitch instead of above it.
     */
    synchronized void render(double[] lr, int frames, double dialKhz, double toneHz, double loHz, double hiHz, int sign) {
        this.sign = sign < 0 ? -1 : 1;
        sign = this.sign;
        boolean slow = ++blocks % 50 == 0;
        sinceClock += frames;
        sky.advance(frames / (double) rate, month, (hourNow() + (placed ? lon / 15 : 0) + 24) % 24, placed ? lat : 50, weather);
        tone = toneHz; lo = loHz; hi = hiHz;
        double dt = frames / (double) rate, chirpKeep = Math.exp(-1 / (CHIRP_S * rate));
        int active = active(), index = 0;
        boolean keyedHere = false, contactHere = false;
        for (Station s : slots) {
            index++;
            if (s.qso == null) {
                s.wait -= frames;
                if (s.wait <= 0) { if (index <= active) spawn(s); else s.wait = 20 * rate; }
                continue;
            }
            int on0 = s.onFrames;
            renderStation(s, lr, frames, dialKhz, toneHz, loHz, hiHz, sign, slow, dt, chirpKeep);
            if (s.qso != null) {
                Op cur = s.op[s.who];
                boolean inPass = Rx.gain(loHz, hiHz, toneHz + sign * (s.khz + cur.offsetKhz + cur.drifted - dialKhz) * 1000) > 0.25;
                if (inPass && heard(s)) contactHere = true;
                if (inPass && cur.strength >= UNHEARD && s.onFrames > on0) keyedHere = true;
            }
        }
        if (st != null) {
            // The story keys only while its sender is in the filter (the same test as inPass above). Tuned away in the
            // middle of an over, it stops at the end of the word and sends that over again once you are back.
            Op so = st.op[st.qso.tx.get(st.next).who];
            boolean in = Rx.gain(loHz, hiHz, toneHz + sign * (st.khz + so.offsetKhz + so.drifted - dialKhz) * 1000) > 0.25;
            storyTuned = in;
            if (st.key == null) st.hold = !in;
            else if (!in && cutTo < 0) cut(st, chunkStart + st.next);
            int on0 = st.onFrames;
            renderStation(st, lr, frames, dialKhz, toneHz, loHz, hiHz, sign, slow, dt, chirpKeep);
            if (in) { contactHere = true; if (st.onFrames > on0) keyedHere = true; }
        }
        quietFrames = keyedHere ? 0 : quietFrames + frames;
        busyHere = contactHere;
    }

    /** One busy station's block: its fading and drift, then its keying sample by sample. The story station uses it too. */
    private void renderStation(Station s, double[] lr, int frames, double dialKhz, double toneHz, double loHz, double hiHz, int sign, boolean slow, double dt, double chirpKeep) {
        Dsp.Rng r0 = s.own != null ? s.own : rng;
        for (Op o : s.op) {
            if (slow) o.wanderTarget = r0.uniform();
            o.wander += 0.02 * (o.wanderTarget - o.wander);
            o.p1 += o.w1 * dt; o.p2 += o.w2 * dt; o.pf += o.wf * dt; o.auroraPhase += o.auroraW * dt;
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
                if (s.hold) { s.wait = 0; continue; }
                startTx(s);
                o = null;
            }
            if (o == null) {
                o = s.op[s.who];
                hz = toneHz + sign * (s.khz + o.offsetKhz + o.drifted - dialKhz) * 1000;
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
                // stopRun is only set on the story station, to stop an over at the end of a word.
                if (s.run >= s.key.runs.length || s.run >= s.stopRun) { endTx(s); o = null; if (s.qso == null) break; }
            }
        }
        if (s.live != null) s.idle = s.key == null && s.next >= s.qso.tx.size() ? s.idle + frames : 0;
    }

    // ---- the story station ----

    /**
     * Plays a story on STORY, from line (an index in s.lines), or turns it off with null. The same story again keeps
     * its position. The pitch offsets are worked out before the lock is taken, as that takes a moment for a big net.
     */
    void setStory(Script s, int line) {
        int[] off = s == null || s.lines.isEmpty() || s.calls.length == 0 ? null : Script.offsetsHz(s);
        setStory(s, line, off);
    }

    private synchronized void setStory(Script s, int line, int[] offsetsHz) {
        if (offsetsHz == null) { story = null; st = null; storyIdx = null; cutTo = -1; storyTuned = false; return; }
        if (story != null && same(story, s)) return;
        story = s;
        storyIdx = new java.util.HashMap<>();
        for (int i = 0; i < s.calls.length; i++) storyIdx.put(s.calls[i], i);
        // Its own random source, seeded from the title: a character always sounds the same, and the band's own draws do not move.
        Dsp.Rng r = new Dsp.Rng(s.title.hashCode() * 0x9E3779B97F4A7C15L + 0x5EED);
        Station x = new Station();
        x.khz = STORY; x.own = r;
        x.op = new Op[s.calls.length];
        for (int i = 0; i < x.op.length; i++) x.op[i] = storyOp(i, offsetsHz[i], r);
        st = x; cutTo = -1;
        loadStory(Math.max(0, Math.min(s.lines.size() - 1, line)));
        x.wait = rate;
        // A contact already on top of the story makes room.
        for (Station o : slots) if (o.qso != null && o.live == null && Math.abs(o.khz - STORY) <= 0.4) { o.qso = null; o.key = null; o.wait = 5 * rate; }
    }

    private static boolean same(Script a, Script b) {
        if (a == b) return true;
        int n = a.lines.size();
        return a.title.equals(b.title) && n == b.lines.size() && a.calls.length == b.calls.length
            && a.lines.get(0).text.equals(b.lines.get(0).text) && a.lines.get(n - 1).text.equals(b.lines.get(n - 1).text);
    }

    /** One of the story's operators: S7 to S9, gentle fading, a clean note, and a fist by turns: keyer, bug, straight key, sideswiper. */
    private Op storyOp(int i, int offsetHz, Dsp.Rng r) {
        Op o = new Op();
        o.offsetKhz = offsetHz / 1000.0;
        double loss = 2 + 10 * r.uniform();
        o.strength = PEAK * Math.pow(10, -0.8 * loss / 20);
        o.depthDb = 2 + 2 * r.uniform();
        o.p1 = 2 * Math.PI * r.uniform(); o.p2 = 2 * Math.PI * r.uniform();
        o.w1 = 2 * Math.PI / (20 + 40 * r.uniform()); o.w2 = 2 * Math.PI / (5 + 10 * r.uniform());
        o.wander = o.wanderTarget = r.uniform();
        o.g0 = o.g1 = gain(o);
        o.fist = Fist.of(STORY_KEYS[i % STORY_KEYS.length], false, r);
        return o;
    }

    /** Loads the overs from line on, a chunk at a time, as the station's contact. */
    private void loadStory(int line) {
        int n = Math.min(STORY_CHUNK, story.lines.size() - line);
        int[] who = new int[n]; String[] text = new String[n]; double[] pause = new double[n];
        for (int i = 0; i < n; i++) {
            Script.Line l = story.lines.get(line + i);
            Integer w = storyIdx.get(l.speaker);
            who[i] = w == null ? 0 : w; text[i] = l.text; pause[i] = l.pauseS;
        }
        st.qso = Qso.story(story.calls, who, text, pause);
        chunkStart = line; st.next = 0; st.who = who[0];
    }

    /** Stops the over being sent at the end of its current word; then line comes next. */
    private void cut(Station s, int line) {
        cutTo = line;
        int stop = s.key.runs.length;
        // The run before a word's first element is the gap after the word before.
        for (int w : s.key.wordStart) if (w > s.run) { stop = w - 1; break; }
        s.stopRun = stop;
    }

    private void storyEnd(Station s) {
        s.key = null; s.stopRun = Integer.MAX_VALUE;
        if (cutTo >= 0) {
            int to = cutTo; cutTo = -1;
            // An over cut off by tuning away is sent again in full, so the broken one leaves no trace in the copy.
            if (to == chunkStart + s.next) s.log.setLength(Math.min(s.log.length(), s.logMark));
            loadStory(to);
            s.wait = rate;
            return;
        }
        s.next++;
        int line = chunkStart + s.next;
        if (line >= story.lines.size()) { loadStory(0); s.wait = 60 * rate; return; }
        if (s.next >= s.qso.tx.size()) loadStory(line);
        s.wait = (int) (Math.max(0.5, story.lines.get(line).pauseS) * rate);
    }

    /** The story's line being sent, or the next one; 0 without a story. */
    synchronized int storyLine() { return st == null ? 0 : cutTo >= 0 ? cutTo : chunkStart + st.next; }

    /** Goes to a line: the over being sent stops at the end of its word, with the normal ramp, and that line comes next. */
    synchronized void setStoryLine(int line) {
        if (st == null) return;
        line = Math.max(0, Math.min(story.lines.size() - 1, line));
        if (st.key != null) cut(st, line);
        else { loadStory(line); st.wait = rate; }
    }

    /**
     * The story for the page: {"on":false}, or its title, episode (1-based, of eps) and episode title, line and total,
     * the call and full name of who sends now (or next), whether it is keying, and whether it waits for you to tune in.
     */
    synchronized String storyJson() {
        if (st == null) return "{\"on\":false}";
        int n = storyLine();
        Script.Line l = story.lines.get(n);
        Integer w = storyIdx.get(l.speaker);
        String name = w == null ? l.speaker : story.names[w];
        String ep = l.episode >= 0 && l.episode < story.episodes.size() ? story.episodes.get(l.episode).title : "";
        return "{\"on\":true,\"khz\":" + num(STORY) + ",\"title\":" + quote(story.title) + ",\"ep\":" + (l.episode + 1) + ",\"eps\":" + story.episodes.size()
            + ",\"epTitle\":" + quote(ep) + ",\"line\":" + n + ",\"total\":" + story.lines.size() + ",\"call\":" + quote(l.speaker) + ",\"now\":" + quote(name)
            + ",\"sending\":" + (st.key != null) + ",\"waiting\":" + (st.key == null && st.hold) + "}";
    }

    /** For the tests: the frequency of each busy random station, without the story. */
    synchronized double[] slotKhz() {
        int n = 0;
        for (Station s : slots) if (s.qso != null) n++;
        double[] f = new double[n];
        n = 0;
        for (Station s : slots) if (s.qso != null) f[n++] = s.khz;
        return f;
    }

    private static String num(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }
    private static double level(Op o) { return Math.min(1, o.g1 / PEAK * 1.2); }

    /** One entry per busy station: {"k": kHz, "l": level 0..1, "on": keying right now}. */
    synchronized String activityJson() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (int k = 0; k <= slots.size(); k++) {
            Station s = k < slots.size() ? slots.get(k) : st;
            if (s == null || s.qso == null) continue;
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
        for (int k = 0; k <= slots.size(); k++) {
            Station s = k < slots.size() ? slots.get(k) : st;
            if (s == null || s.qso == null) continue;
            Op o = s.op[s.who];
            double duty = s.allFrames == 0 ? 0 : s.onFrames / (double) s.allFrames;
            s.onFrames = s.allFrames = 0;
            if (!first) b.append(',');
            first = false;
            b.append('[').append(num(s.khz + o.offsetKhz + o.drifted)).append(',').append(num(level(o))).append(',').append(num(duty)).append(']');
        }
        return b.append(']').toString();
    }

    /** The station nearest the dial among those the filter lets through, not counting the story. */
    private Station tuned(double dialKhz) { return tuned(dialKhz, false); }

    private Station tuned(double dialKhz, boolean withStory) {
        Station best = null; double bestKhz = Double.MAX_VALUE;
        for (int k = 0; k <= slots.size(); k++) {
            Station s = k < slots.size() ? slots.get(k) : withStory ? st : null;
            if (s == null || s.qso == null) continue;
            double away = Math.abs(s.khz - dialKhz);
            if (away < bestKhz && Rx.gain(lo, hi, tone + sign * (s.khz - dialKhz) * 1000) > 0.1) { bestKhz = away; best = s; }
        }
        return best;
    }

    /** What the station nearest the dial has sent so far, or an empty string. The story's prosigns are shown as written, such as {@code <BT>}. */
    synchronized String copy(double dialKhz) {
        Station s = tuned(dialKhz, true);
        if (s == null) return "";
        String t = s.log.toString().trim();
        return t.length() > LOG_CHARS ? t.substring(t.length() - LOG_CHARS) : t;
    }

    /** Whether at least one side of the contact can be copied here. */
    private static boolean heard(Station s) { for (Op o : s.op) if (o.strength >= UNHEARD) return true; return false; }

    /** The frequency of the next busy station above (dir 1) or below (dir -1) the dial, wrapping round. News, and the story when on, count as stations. */
    synchronized double next(double dialKhz, int dir) {
        List<Double> f = new ArrayList<>();
        f.add(NEWS);
        if (st != null) f.add(STORY);
        for (Station s : slots) if (s.qso != null && heard(s)) f.add(s.khz);
        double best = Double.NaN, wrap = Double.NaN;
        for (double x : f) {
            if (dir > 0 ? x > dialKhz + 0.05 : x < dialKhz - 0.05) {
                if (Double.isNaN(best) || (dir > 0 ? x < best : x > best)) best = x;
            }
            if (Double.isNaN(wrap) || (dir > 0 ? x < wrap : x > wrap)) wrap = x;
        }
        return Double.isNaN(best) ? wrap : best;
    }

    /**
     * Where to go when this frequency has gone quiet, in kHz, or NaN to stay. It stays while a contact can be heard in
     * the filter, unless nothing has been keyed there for deadS seconds. Otherwise, after quietS seconds of silence it
     * names the strongest station that can be heard; a lone CQ or a contact about to end counts for less.
     */
    synchronized double follow(double rxKhz, double quietS, double deadS) {
        for (Station s : slots) if (s.live != null) return Double.NaN;
        // Tuned to the story, it stays: the story is never left by itself.
        if (st != null && storyTuned) return Double.NaN;
        if (!(quietFrames >= quietS * rate && !busyHere) && quietFrames < deadS * rate) return Double.NaN;
        Station best = null; double bestScore = 0;
        for (Station s : slots) {
            if (s.qso == null || !heard(s) || Math.abs(s.khz - rxKhz) <= 0.25) continue;
            double score = 0;
            for (Op o : s.op) if (o.strength >= UNHEARD) score = Math.max(score, level(o));
            if (s.qso.kind == Qso.CQ || s.qso.kind == Qso.TUNE) score *= 0.3;
            if (s.qso.tx.size() - s.next < 2) score *= 0.5;
            if (best == null || score > bestScore) { best = s; bestScore = score; }
        }
        if (best == null) return Double.NaN;
        quietFrames = 0;
        return best.khz;
    }

    // ---- a live contact with the user ----

    /** The scene the stations are generated in right now, for building a live partner that fits the band. */
    synchronized Qso.Scene scene() {
        Qso.Scene sc = placed ? new Qso.Scene(month, hourNow(), lat, lon, weekend, contest != null) : new Qso.Scene(month, hourNow());
        sc.qrn = sky.noise(); sc.aurora = sky.aurora() > 0.3;
        return sc;
    }

    /** The contact of a station near the dial that is calling CQ and is not yet in a live contact, or null. */
    synchronized Qso cqNear(double rxKhz) {
        Station s = tuned(rxKhz);
        return s != null && s.live == null && s.qso.kind == Qso.CQ && heard(s) ? s.qso : null;
    }

    /** Whether no station is within the usual spacing of this frequency, so the user may call CQ there. */
    synchronized boolean clearAt(double khz) {
        if (Math.abs(khz - NEWS) <= 0.4) return false;
        if (st != null && Math.abs(khz - STORY) <= 0.4) return false;
        for (Station s : slots) if (s.qso != null && Math.abs(s.khz - khz) < MIN_GAP_KHZ) return false;
        return true;
    }

    /** Whether the live partner is keying right now. */
    synchronized boolean liveSending() { Station s = liveStation(); return s != null && s.key != null; }

    /** The live station's frequency in kHz, or NaN. */
    synchronized double liveKhz() { Station s = liveStation(); return s == null ? Double.NaN : s.khz; }

    /** Whether a live contact is running. */
    synchronized boolean hasLive() { for (Station s : slots) if (s.live != null) return true; return false; }

    private Station liveStation() { for (Station s : slots) if (s.live != null) return s; return null; }

    /**
     * Turns the CQ station near the dial into a live one. It keeps its frequency, strength and fist; what it still has to
     * send of its CQ finishes, and then it waits for the user. Returns false if there is no such station or one is already live.
     */
    synchronized boolean takeOver(double rxKhz, Partner p) {
        if (liveStation() != null) return false;
        Station s = tuned(rxKhz);
        if (s == null || s.live != null || s.qso.kind != Qso.CQ || !s.qso.call[0].equals(p.call())) return false;
        s.live = p;
        s.lines.clear(); s.partial.setLength(0); s.idle = 0;
        // The CQs still to come are not sent: the station waits to be answered after the one on the air.
        int keep = s.key != null ? s.next + 1 : s.next;
        while (s.qso.tx.size() > keep) s.qso.tx.remove(s.qso.tx.size() - 1);
        if (s.key == null) s.wait = 1 << 30;
        return true;
    }

    /** A new live station appears on the user's frequency and answers the user's CQ. */
    synchronized boolean answerMe(double txKhz, Partner p) { return answerMe(txKhz, p, 0); }

    /** As above, but the partner waits this many seconds first, for the user's own CQ to finish. */
    synchronized boolean answerMe(double txKhz, Partner p, double afterS) {
        if (liveStation() != null) return false;
        Station s = null;
        for (Station x : slots) if (x.qso == null) { s = x; break; }
        if (s == null) for (Station x : slots) { s = x; break; }
        if (s == null) return false;
        Qso q = Qso.live(p.op());
        Qso.Tx first = p.first();
        if (first != null) q.tx.add(first);
        s.qso = q; s.khz = txKhz; s.live = p;
        s.next = 0; s.who = 0; s.key = null; s.log.setLength(0); s.lines.clear(); s.partial.setLength(0); s.idle = 0;
        s.op = new Op[]{initOp(q, 0)};
        s.op[0].offsetKhz = 0;
        s.wait = first == null ? 1 << 30 : (int) ((first.pause + afterS) * rate);
        return true;
    }

    /** The user's over has ended: the live partner hears it and queues its reply after a natural pause. */
    synchronized void userOver(Heard h, double seconds, double wpm, double txKhz) { userOver(h, seconds, wpm, txKhz, 0); }

    /** As above, with the seconds the user's own sending takes: the reply comes after them. */
    synchronized void userOver(Heard h, double seconds, double wpm, double txKhz, double afterS) {
        Station s = liveStation();
        if (s == null) return;
        s.idle = 0;
        s.lines.add("YOU: " + h.text.trim());
        Qso.Tx t = s.live.hear(h, seconds, (txKhz - s.khz) * 1000, wpm);
        queue(s, t == null || afterS <= 0 ? t : new Qso.Tx(t.who, t.text, t.speed, t.pause + afterS, t.carrier));
    }

    /** Silence is passed to the partner once a second: a gentle prompt, and in the end a kind sign-off. */
    synchronized void tickLive() {
        Station s = liveStation();
        if (s == null || s.key != null || s.next < s.qso.tx.size()) return;
        queue(s, s.live.silence(s.idle / (double) rate));
    }

    private void queue(Station s, Qso.Tx t) {
        if (t == null) return;
        s.qso.tx.add(t);
        if (s.key == null && s.next == s.qso.tx.size() - 1) s.wait = Math.max(1, (int) (t.pause * rate));
    }

    /** Ends the live contact: the station goes quiet and its slot is free again. */
    synchronized void endLive() {
        Station s = liveStation();
        if (s == null) return;
        s.live = null; s.qso = null; s.key = null; s.lines.clear(); s.partial.setLength(0);
        s.wait = (int) ((6 + 34 * rng.uniform()) * rate);
    }

    /** State of the live contact for the page. The transcript is split so the page can show it after each over, as it is sent, or not at all. */
    synchronized String liveJson() {
        Station s = liveStation();
        if (s == null) return "{\"on\":false}";
        StringBuilder l = new StringBuilder("[");
        int from = Math.max(0, s.lines.size() - 14);
        for (int i = from; i < s.lines.size(); i++) { if (i > from) l.append(','); l.append(quote(s.lines.get(i))); }
        l.append(']');
        Partner p = s.live;
        return "{\"on\":true,\"call\":" + quote(p.call()) + ",\"stage\":" + p.stage() + ",\"done\":" + p.done() + ",\"sending\":" + (s.key != null)
            + ",\"khz\":" + num(s.khz) + ",\"hint\":" + quote(p.hint()) + ",\"suggest\":" + quote(p.suggest()) + ",\"lines\":" + l + ",\"partial\":" + quote(s.partial.toString().trim()) + "}";
    }

    private static String quote(String t) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < t.length(); i++) { char c = t.charAt(i); if (c == '"' || c == '\\') b.append('\\'); if (c >= ' ') b.append(c); }
        return b.append('"').toString();
    }

    /** The finished live contact's result for the log, or null. */
    synchronized Partner livePartner() { Station s = liveStation(); return s == null ? null : s.live; }
}
