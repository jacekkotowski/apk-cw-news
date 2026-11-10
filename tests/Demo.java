package pl.cwnews;
import java.io.*;
/** Writes 40-second WAV previews: java pl.cwnews.Demo <folder> */
public class Demo {
    public static void main(String[] a) throws Exception {
        String[] names = {"none", "band", "cosmic"};
        for (int type = 0; type < 3; type++) {
            int rate = 48000, block = 480, secs = 40;
            Engine e = new Engine(rate, 42, block);
            e.setBed(type); e.bedDb = -24; e.volume = 1; e.setAudible(true);
            Keying k = Keying.build(TextNorm.story("Hacker News (HN)", "Thanks for listening, good news from the weather station", "", TextNorm.HEADLINE, true, Abbrev.LIGHT), new Timing(20, 12), rate);
            float[] b = new float[2 * block];
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            for (int n = 0; n < secs * rate; n += block) {
                if (n == 3 * rate) e.load(k, 0);
                e.render(b, block);
                for (float v : b) { int s = Math.round(v * 32767); pcm.write(s & 255); pcm.write((s >> 8) & 255); }
            }
            byte[] d = pcm.toByteArray();
            try (DataOutputStream o = new DataOutputStream(new FileOutputStream(a[0] + "/cw_news_preview_" + names[type] + ".wav"))) {
                o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + d.length)); o.writeBytes("WAVEfmt "); o.writeInt(Integer.reverseBytes(16));
                o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 2)); o.writeInt(Integer.reverseBytes(rate)); o.writeInt(Integer.reverseBytes(rate * 4));
                o.writeShort(Short.reverseBytes((short) 4)); o.writeShort(Short.reverseBytes((short) 16)); o.writeBytes("data"); o.writeInt(Integer.reverseBytes(d.length)); o.write(d);
            }
        }
    }
}
