package pl.cwnews;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Parses RSS 2.0 and Atom. Documents with a DOCTYPE are refused, which blocks external-entity attacks. */
final class FeedParser {
    private FeedParser() {}

    static final class Item { String uid, title, summary, link; long published; }
    static final class Feed { String title = ""; final List<Item> items = new ArrayList<>(); }
    static final class FeedException extends Exception { FeedException(String m) { super(m); } }

    static String validateUrl(String raw) throws FeedException {
        String s = raw == null ? "" : raw.trim();
        URI u;
        try { u = new URI(s); } catch (Exception e) { throw new FeedException("That is not a valid address"); }
        if (!"https".equalsIgnoreCase(u.getScheme())) throw new FeedException("The address must start with https://");
        if (u.getHost() == null || u.getHost().indexOf('.') < 0) throw new FeedException("The address has no valid host name");
        return s;
    }

    static Feed parse(byte[] xml, String feedUrl) throws FeedException {
        String probe = new String(xml, StandardCharsets.ISO_8859_1).replace("\0", "").toUpperCase(Locale.ROOT);
        if (probe.contains("<!DOCTYPE") || probe.contains("<!ENTITY")) throw new FeedException("Refused: the feed contains a DOCTYPE");
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            doc = b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new FeedException("The feed is not valid XML");
        }
        Element root = doc.getDocumentElement();
        String kind = local(root).toLowerCase(Locale.ROOT);
        Feed out = new Feed();
        if (kind.equals("feed")) {
            out.title = text(root, "title");
            for (Element e : children(root, "entry")) add(out, atom(e), feedUrl);
        } else if (kind.equals("rss") || kind.equals("rdf")) {
            Element channel = first(root, "channel");
            if (channel == null) throw new FeedException("The RSS feed has no channel");
            out.title = text(channel, "title");
            List<Element> items = children(channel, "item");
            if (items.isEmpty()) items = children(root, "item"); // RSS 1.0 keeps items beside the channel
            for (Element e : items) add(out, rss(e), feedUrl);
        } else {
            throw new FeedException("This is not an RSS or Atom feed");
        }
        out.title = TextNorm.cleanHtml(out.title);
        return out;
    }

    private static Item rss(Element e) {
        Item i = new Item();
        i.title = text(e, "title");
        i.link = text(e, "link");
        i.uid = text(e, "guid");
        String content = text(e, "encoded");
        i.summary = content.isEmpty() ? text(e, "description") : content;
        i.published = date(text(e, "pubDate"));
        if (i.published == 0) i.published = date(text(e, "date"));
        return i;
    }

    private static Item atom(Element e) {
        Item i = new Item();
        i.title = text(e, "title");
        i.uid = text(e, "id");
        String summary = text(e, "summary");
        i.summary = summary.isEmpty() ? text(e, "content") : summary;
        i.link = "";
        for (Element l : children(e, "link")) {
            String rel = l.getAttribute("rel");
            if (rel.isEmpty() || rel.equals("alternate")) { i.link = l.getAttribute("href"); break; }
        }
        i.published = date(text(e, "published"));
        if (i.published == 0) i.published = date(text(e, "updated"));
        return i;
    }

    private static void add(Feed f, Item i, String feedUrl) {
        i.title = TextNorm.cleanHtml(i.title);
        i.summary = TextNorm.cleanHtml(i.summary);
        if (i.title.isEmpty()) return;
        i.uid = uid(i, feedUrl);
        for (Item o : f.items) if (o.uid.equals(i.uid)) return;
        f.items.add(i);
    }

    /** GUID when present, then normalized link, then a stable value built from the feed and headline. */
    static String uid(Item i, String feedUrl) {
        if (i.uid != null && !i.uid.trim().isEmpty()) return "g:" + i.uid.trim();
        String l = normalizeLink(i.link);
        if (!l.isEmpty()) return "l:" + l;
        return "t:" + feedUrl + "|" + i.title.toLowerCase(Locale.ROOT);
    }

    static String normalizeLink(String link) {
        if (link == null) return "";
        String s = link.trim();
        if (s.isEmpty()) return "";
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        s = s.replaceAll("(?i)^https?://", "").replaceAll("(?i)^www\\.", "");
        s = s.replaceAll("(?i)([?&])(utm_[a-z]+|fbclid|gclid)=[^&]*", "$1").replaceAll("[?&]+$", "").replaceAll("\\?&", "?");
        return s.replaceAll("/+$", "");
    }

    private static final String[] DATES = {"EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd"};

    static long date(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        String v = s.trim();
        for (String p : DATES) {
            SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition at = new ParsePosition(0);
            try {
                Date d = f.parse(v, at);
                if (d != null && at.getIndex() == v.length()) return d.getTime();
            } catch (RuntimeException ignored) { }
        }
        return 0;
    }

    private static String local(Node n) {
        String s = n.getNodeName();
        int c = s.indexOf(':');
        return c < 0 ? s : s.substring(c + 1);
    }
    private static List<Element> children(Element p, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling())
            if (n.getNodeType() == Node.ELEMENT_NODE && local(n).equalsIgnoreCase(name)) out.add((Element) n);
        return out;
    }
    private static Element first(Element p, String name) {
        List<Element> c = children(p, name);
        return c.isEmpty() ? null : c.get(0);
    }
    private static String text(Element p, String name) {
        Element e = first(p, name);
        if (e == null) return "";
        String s = e.getTextContent();
        return s == null ? "" : s.trim();
    }
}
