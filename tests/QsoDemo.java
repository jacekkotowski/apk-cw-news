package pl.cwnews;
/** Prints made-up contacts to read them over: java pl.cwnews.QsoDemo [count] [seed] [month 0-11] [UTC hour] */
public class QsoDemo {
    public static void main(String[] a) {
        int n = a.length > 0 ? Integer.parseInt(a[0]) : 10, month = a.length > 2 ? Integer.parseInt(a[2]) : 10, hour = a.length > 3 ? Integer.parseInt(a[3]) : 19;
        Dsp.Rng r = new Dsp.Rng(a.length > 1 ? Long.parseLong(a[1]) : 1);
        String[] kind = {"ragchew", "quick", "cq", "sked"}, style = {"casual", "chatty", "terse", "novice", "outdoor"};
        for (int i = 0; i < n; i++) {
            Qso q = Qso.generate(r, month, hour, i % 2 == 0 ? Qso.shortHeadline("Parliament passes the new energy law after long debate") : null);
            System.out.println("=== " + kind[q.kind] + ", " + month + "/" + hour + " UTC");
            for (int k = 0; k < q.call.length; k++)
                System.out.printf(java.util.Locale.ROOT, "    %-9s %-7s %s local %02d h, %4.1f dB down, QSB %4.1f dB%n", q.call[k], style[q.style[k]], q.yl[k] ? "YL" : "OM", q.hour[k], q.lossDb[k], q.qsbDb[k]);
            for (Qso.Tx t : q.tx) System.out.printf(java.util.Locale.ROOT, "%-9s x%.2f +%.1fs  %s%n", q.call[t.who], t.speed, t.pause, t.text);
        }
    }
}
