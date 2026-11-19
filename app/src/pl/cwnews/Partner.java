package pl.cwnews;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One side of a contact with the user, driven over by over. It is forgiving: nothing the user sends ends the contact,
 * it asks for one thing at a time, slows down instead of the user, and signs off kindly. Its words come from the same
 * phrase code as the stations already on the band. No Android imports.
 */
final class Partner {
    static final int CALL = 0, EXCHANGE = 1, CHAT = 2, BYE = 3, DONE = 4;
    static final int ACTIVATOR = 0, STANDARD = 1, CHATTY = 2;
    private static final int I_CALL = 0, I_REPORT = 1, I_NAME = 2, I_QTH = 3;
    private static final Pattern RST = Pattern.compile("\\b([3-5][1-9]9C?)\\b");

    /** What the log needs to know about a contact. */
    static final class Contact {
        String call = "", name = "", qth = "", rstSent = "", rstRcvd = "";
        boolean complete; int overs;
    }

    private final Dsp.Rng r;
    private final Profile me;
    private final int manners, kind;
    private final List<Qso.Tx> sink = new ArrayList<>();
    private final Qso.Gen g;
    private final Qso.Op self, you;
    private final boolean answeringFlow;

    /** The listener's speed setting, which the partner's speed is a factor of; and how many contacts the user has logged. */
    double baseWpm = 20;
    int logged;

    private double wpm;
    private int stage = CALL;
    private Qso.Tx pendingFirst;
    private boolean gotCall, gotReport, gotName, gotQth, greeted, confirmAsked, confirmed, signedOff, errorAnswered, repeatWorked;
    private String reportFromUser = "", reportToUser = "", lastText;
    private final int[] asked = new int[4];
    private final boolean[] dropped = new boolean[4];
    private int lastRequested = -1, unreadableRun, overs, userOvers, silencePrompts, totalPrompts, chatOvers, chatTarget, basicsOver = -1, lastEncOver = -10;
    private final boolean[] said = new boolean[Qso.TOPICS];
    private final List<String> pending = new ArrayList<>();
    private final Set<String> told = new HashSet<>(), askedTopics = new HashSet<>();

    private Partner(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind, boolean answering, Qso.Op existing) {
        this.r = r; this.me = me; this.manners = manners; this.kind = kind; this.answeringFlow = answering;
        g = new Qso.Gen(r, scene, null, sink);
        g.clean = true;
        int style = kind == ACTIVATOR ? Qso.OUTDOOR : kind == CHATTY ? Qso.CHATTY : answering ? (r.chance(0.5) ? Qso.CASUAL : Qso.CHATTY) : Qso.CASUAL;
        if (existing != null) style = existing.style;
        self = g.op(style);
        if (existing != null) { self.call = existing.call; self.hour = existing.hour; }
        g.plan(self, false);
        you = Qso.userOp(me, scene);
        you.name = "OM"; you.city = ""; you.rig = null; you.ant = null;   // nothing is known about the user until they say it
        wpm = Math.max(5, Math.min(me.sendWpm, baseWpm * self.speed));
        chatTarget = 6 + r.nextInt(3);
        if (answering) gotCall = true;
    }

    /** The partner calls CQ and waits for the user to answer. */
    static Partner calling(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind) {
        Partner p = new Partner(r, scene, me, manners, kind, false, null);
        p.pendingFirst = p.make(p.g.cqText(p.self), p.g.reply(p.self));
        return p;
    }

    /** The partner answers the user's CQ. */
    static Partner answering(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind) {
        Partner p = new Partner(r, scene, me, manners, kind, true, null);
        p.pendingFirst = p.make(me.call + " DE " + p.self.call + " " + p.self.call + " K", p.g.reply(p.self));
        return p;
    }

    /** Takes over a station already on the band that is calling CQ; its CQ has been heard, so there is no first over. */
    static Partner from(Qso contact, int operator, Profile me, int manners) { return from(contact, operator, new Qso.Scene(5, contact.hour[operator]), new Dsp.Rng(31L * contact.call[operator].hashCode() + 7), me, manners); }
    static Partner from(Qso contact, int operator, Qso.Scene scene, Dsp.Rng r, Profile me, int manners) {
        Qso.Op o = new Qso.Op();
        o.style = contact.style[operator]; o.call = contact.call[operator]; o.hour = contact.hour[operator];
        int kind = o.style == Qso.OUTDOOR ? ACTIVATOR : o.style == Qso.CHATTY ? CHATTY : STANDARD;
        return new Partner(r, scene, me, manners, kind, false, o);
    }

    /** The partner's first over, once; null when there is none. */
    Qso.Tx first() { Qso.Tx t = pendingFirst; pendingFirst = null; if (t != null) lastText = t.text; return t; }
    boolean done() { return stage == DONE; }
    int stage() { return stage; }
    String call() { return self.call; }
    /** The operator behind the partner, for tests. */
    Qso.Op op() { return self; }
    double wpm() { return wpm; }
    Heard.Expect expect() { return Heard.Expect.of(self.call, self.name, self.city, me); }

    private Qso.Tx make(String text, double pause) {
        sink.clear();
        g.spd[self.id] = wpm / baseWpm;
        g.send(self, text, pause);
        Qso.Tx t = sink.get(sink.size() - 1);
        lastText = t.text;
        Matcher m = RST.matcher(t.text);
        if (reportToUser.isEmpty() && m.find()) reportToUser = m.group(1);
        return t;
    }
    private String turn(String body, String end) {
        String y = me.call, s = self.call;
        return y + " DE " + s + " <BT> " + body + " <BT> " + y + " DE " + s + " " + end;
    }

    // ---- what the user said ----

    private int limit(int item) { return item == I_CALL ? 3 : 2; }
    private boolean has(int item) { return item == I_CALL ? gotCall : item == I_REPORT ? gotReport : item == I_NAME ? gotName : gotQth; }
    private static final String[] ITEM_ASK = {"CALL?", "UR RST?", "UR NAME?", "UR QTH?"};

    /** The user's over has ended. Returns the partner's reply, or null when it does not answer (not heard). */
    Qso.Tx hear(Heard h, double seconds, double offsetHz, double userWpm) {
        if (stage == DONE) return null;
        if (manners == Profile.REALISTIC && Math.abs(offsetHz) > 200) return null;
        overs++; silencePrompts = 0;
        if (userWpm > 0) wpm = Math.min(wpm, userWpm);
        wpm = Math.max(5, wpm);
        if (h.unreadable) return unreadable();
        unreadableRun = 0;
        userOvers++;

        // Learn what the user sent.
        if (h.fromCall != null || h.userCallHeard) gotCall = true;
        if (h.callUnsure && !confirmed && !confirmAsked) { /* asked below */ }
        else if (confirmAsked && !confirmed && (!h.callUnsure && h.userCallHeard || h.tokens.contains("R") || h.tokens.contains("C") || h.tokens.contains("YES") || h.tokens.contains("OK"))) confirmed = true;
        if (h.report != null) { gotReport = true; reportFromUser = h.report; }
        if (h.values.containsKey("NAME")) { gotName = true; you.name = h.values.get("NAME"); told.add("NAME"); }
        if (h.values.containsKey("QTH")) { gotQth = true; you.city = h.values.get("QTH"); told.add("QTH"); }
        if (h.values.containsKey("RIG")) { you.rig = h.values.get("RIG"); you.qrp = me.power <= 10; you.power = me.power; told.add("RIG"); }
        if (h.values.containsKey("ANT")) { you.ant = h.values.get("ANT"); told.add("RIG"); told.add("ANT"); }
        if (h.values.containsKey("WX")) { you.wx = h.values.get("WX"); told.add("WX"); }
        for (String k : new String[]{"HOBBY", "JOB", "AGE", "KEY"}) if (h.tokens.contains(k) && !h.questions.contains(k)) told.add(k);
        for (String q : h.questions) { String t = q.equals("PWR") ? "RIG" : q; if (!pending.contains(t)) pending.add(t); }

        // Did the thing we asked for last time arrive? If not often enough, let it go.
        boolean dropNote = false;
        repeatWorked = false;
        if (lastRequested >= 0) {
            if (has(lastRequested)) repeatWorked = true;
            else if (asked[lastRequested] >= limit(lastRequested)) { dropped[lastRequested] = true; dropNote = true; }
            lastRequested = -1;
        }

        if (h.requests.contains("QRS")) { wpm = Math.max(5, wpm * 0.75); }
        boolean repeatPlease = (h.requests.contains("AGN") || h.requests.contains("?") || h.requests.contains("RPT")) && lastText != null && h.questions.isEmpty() && userOvers > 1;
        if (repeatPlease) return make("R " + lastText, g.reply(self));

        boolean closing = h.closings.contains("73") || h.closings.contains("SK");
        boolean chance = answeringFlow || basicsOver >= 0 && basicsOver < overs;   // has the user had a turn since the partner's exchange?
        if (closing) return bye(h);

        List<String> seg = new ArrayList<>();
        String enc = null;
        if (dropNote) enc = "NO PROBLEM";
        else if (h.errorSign && !errorAnswered) { enc = g.pick("NO PROBLEM", "NP"); errorAnswered = true; }
        else if (repeatWorked) enc = "GUD COPY NW";
        else if (logged < 5 && userOvers >= 2 && overs - lastEncOver >= 3) enc = g.pick("UR CW IS FB", "NICE FIST", "TAKE UR TIME");
        if (h.errorSign) errorAnswered = true;

        // 1. Acknowledge, or send the exchange.
        if (!greeted) {
            greeted = true; basicsOver = overs; stage = EXCHANGE;
            if (kind == ACTIVATOR) seg.add(Qso.greeting(you.hour) + " " + g.report(self, you));
            else seg.add(g.basics(self, you, h.report == null));
        } else {
            String nm = you.name;
            seg.add(h.report != null && !repeatWorked ? g.pick("TNX FER RPT", "R TNX RPT") + " " + nm : g.pick("R R ", "R ", "OK ") + nm);
        }
        if (enc != null) { seg.add(enc); lastEncOver = overs; }

        // 2. Confirm a callsign that may be one letter off. This replaces a request in the same over.
        boolean confirm = h.callUnsure && !confirmed && !confirmAsked;
        if (confirm) { seg.add(me.call + "?"); confirmAsked = true; }

        // 3. Answers, at most two, the rest wait for the next over.
        int answered = 0;
        while (answered < 2 && !pending.isEmpty()) { String a = answer(pending.remove(0)); if (a != null) { seg.add(a); answered++; } }

        // 4. Reactions to what the user told.
        int reacts = self.style == Qso.CHATTY ? 2 : 1;
        for (String k : new String[]{"RIG", "ANT", "WX"}) {
            if (reacts == 0 || !h.values.containsKey(k)) continue;
            String c = g.react(k.equals("RIG") ? Qso.RIG : k.equals("ANT") ? Qso.ANT : Qso.WX, self, you);
            if (c != null) { seg.add(c); reacts--; }
        }

        // Where are we in the contact?
        boolean exchangeDone = gotCall && (gotReport || dropped[I_REPORT]) && (kind == ACTIVATOR || (gotName || dropped[I_NAME]) && (gotQth || dropped[I_QTH]));
        if (stage == EXCHANGE && exchangeDone && chance) {
            if (kind == CHATTY) stage = CHAT;
            else return bye(h);
        }
        if (stage == CHAT) {
            if (chatOvers >= chatTarget) return bye(h);
            chatOvers++;
            // 5. Something of its own.
            int own = 0;
            while (own < 1 && !self.plan.isEmpty()) {
                int t = self.plan.remove(0);
                if (said[t]) continue;
                String s = g.say(t, self, you, said);
                if (s != null) { seg.add(s); own++; }
            }
        }

        // 6. At most one request, the most important thing still missing.
        boolean requested = false;
        if (!confirm) for (int item = 0; item < 4 && !requested; item++) {
            if (has(item) || dropped[item] || asked[item] >= limit(item)) continue;
            if (item != I_CALL && !chance) continue;
            if (kind == ACTIVATOR && (item == I_NAME || item == I_QTH)) continue;
            seg.add(ITEM_ASK[item]); asked[item]++; lastRequested = item; requested = true;
        }
        // 7. At most one question, to keep the chat going.
        if (stage == CHAT && !requested && !confirm) {
            String[][] qs = {{"WX", "UR WX?"}, {"RIG", "WHAT RIG ES ANT?"}, {"HOBBY", "ANY OTHER HOBBIES?"}, {"JOB", "WHAT DO U DO FER WORK?"}, {"AGE", "HW LONG HAM?"}};
            for (String[] q : qs) if (!told.contains(q[0]) && askedTopics.add(q[0])) { seg.add(q[1]); break; }
        }
        return make(turn(Qso.Gen.join(seg), self.style == Qso.TERSE ? "K" : "<KN>"), g.reply(self));
    }

    private String answer(String topic) {
        switch (topic) {
            case "WX": return g.say(Qso.WX, self, you, said);
            case "ANT": return g.say(Qso.ANT, self, you, said);
            case "RIG": return g.say(Qso.RIG, self, you, said);
            case "HOBBY": return g.say(Qso.HOBBY, self, you, said);
            case "JOB": return g.say(Qso.WORK, self, you, said);
            case "AGE": return g.say(Qso.AGE, self, you, said);
            case "KEY": return g.say(Qso.KEY, self, you, said);
            case "QSL": return g.say(Qso.QSL, self, you, said);
            case "CONDX": return g.say(Qso.CONDX, self, you, said);
            case "NAME": return g.name(self);
            case "QTH": return g.qth(self);
            case "RST": return g.report(self, you);
            default: return null;
        }
    }

    private Qso.Tx unreadable() {
        unreadableRun++;
        String body;
        if (unreadableRun == 1) body = "SRI NO COPY PSE AGN";
        else if (unreadableRun == 2) { wpm = Math.max(5, wpm * 0.75); body = "PSE AGN SLOWLY"; }
        else body = "PSE UR CALL?";
        return make(turn(body, "K"), g.reply(self));
    }

    private Qso.Tx bye(Heard h) {
        List<String> seg = new ArrayList<>();
        String[] why = g.reason(self, you);
        if (why != null) seg.add(why[0]);
        seg.add(kind == ACTIVATOR ? "TU " + you.name : "TNX FER QSO " + you.name);
        seg.add(g.pick("TNX FER QSO UR DOING FB", "HPE CU AGN"));
        seg.add(g.bye73(self, you));
        stage = DONE;
        return make(turn(Qso.Gen.join(seg), "<SK>"), g.reply(self));
    }

    /** Called with the seconds of silence since the user's last over, or since the partner stopped. Null means keep waiting. */
    Qso.Tx silence(double seconds) {
        if (stage == DONE || stage == CALL && !answeringFlow && !greeted) return null;
        double wait = manners == Profile.PATIENT ? 25 : manners == Profile.NORMAL ? 15 : 12;
        if (seconds < wait * (silencePrompts + 1)) return null;
        silencePrompts++;
        if (totalPrompts >= 3) {
            signedOff = true; stage = DONE;
            return make(me.call + " DE " + self.call + " <BT> NIL HR <BT> 73 CUL <BT> " + self.call + " <SK>", 0.5);
        }
        totalPrompts++;
        return make(me.call + "? " + me.call + " DE " + self.call + " K", 0.5);
    }

    /** What would be natural to send now, in plain words. */
    String hint() {
        String t = self.call;
        if (stage == DONE) return "The contact is over. Thank you for the contact.";
        if (stage == CALL && !answeringFlow) return "Answer " + t + ". Send their call, then yours, then K.";
        if (stage == CALL) return "Send your call, then a report such as UR RST 579, then your name and QTH.";
        if (!gotReport) return "Give " + t + " a report, for example UR RST 579 579.";
        if (!gotName) return "Tell " + t + " your name: NAME " + me.name + ".";
        if (!gotQth) return "Tell " + t + " where you are: QTH " + me.qth + ".";
        if (stage == CHAT) return "Chat a little. Ask about the weather or the rig, or tell about yours. Say 73 when you want to finish.";
        return "Say thanks and 73 to finish.";
    }

    Contact result() {
        Contact c = new Contact();
        c.call = self.call; c.name = self.name; c.qth = self.city;
        c.rstSent = reportFromUser; c.rstRcvd = reportToUser;
        c.complete = gotCall && gotReport && !signedOff; c.overs = userOvers;
        return c;
    }
}
