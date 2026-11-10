package pl.cwnews;
import java.io.*;
/** Writes a 3-minute WAV of the band with the dial sweeping 7000 to 7040 kHz: java pl.cwnews.BandDemo <folder> */
public class BandDemo {
    public static void main(String[] a) throws Exception {
        int rate = 48000, block = 480, secs = 180;
        Engine e = new Engine(rate, 42, block);
        Band b = new Band(rate, 7, 10, () -> "Parliament passes the new energy law after long debate");
        b.setActivity(2); b.setSpeed(20, 14);
        e.setBand(b); e.setBed(Bed.BAND); e.bedDb = -26; e.volume = 1; e.setAudible(true);
        e.load(Keying.build(TextNorm.story("Hacker News (HN)", "Morse code news is back on the air tonight", "", TextNorm.HEADLINE, true, Abbrev.LIGHT) + " " + TextNorm.story("DW", "Good weather ahead for the weekend in central Europe", "", TextNorm.HEADLINE, false, Abbrev.LIGHT), new Timing(20, 14), rate), 0);
        float[] buf = new float[2 * block];
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (int n = 0; n < secs * rate; n += block) {
            double t = n / (double) rate;
            e.dialKhz = t < 40 ? 7020 + 0.02 * Math.sin(t) : 7000 + 40 * (t - 40) / (secs - 40);
            e.render(buf, block);
            for (float v : buf) { int s = Math.round(v * 32767); pcm.write(s & 255); pcm.write((s >> 8) & 255); }
        }
        byte[] d = pcm.toByteArray();
        try (DataOutputStream o = new DataOutputStream(new FileOutputStream(a[0] + "/cw_news_band_sweep.wav"))) {
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + d.length)); o.writeBytes("WAVEfmt "); o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 2)); o.writeInt(Integer.reverseBytes(rate)); o.writeInt(Integer.reverseBytes(rate * 4));
            o.writeShort(Short.reverseBytes((short) 4)); o.writeShort(Short.reverseBytes((short) 16)); o.writeBytes("data"); o.writeInt(Integer.reverseBytes(d.length)); o.write(d);
        }
    }
}
