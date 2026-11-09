package pl.cwnews;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/** Feed list and article cache, so stories play offline. */
final class Db extends SQLiteOpenHelper {
    private static Db instance;
    private static final String WHEN = "CASE WHEN a.pub>0 THEN a.pub ELSE a.fetched END";
    private static final int KEEP_ARTICLES = 3000;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }
    private Db(Context c) { super(c, "cwnews.db", null, 1); }

    @Override public void onConfigure(SQLiteDatabase d) { d.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE feeds(id INTEGER PRIMARY KEY, url TEXT UNIQUE NOT NULL, name TEXT NOT NULL DEFAULT '', custom INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, last_ok INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE TABLE articles(id INTEGER PRIMARY KEY, feed_id INTEGER NOT NULL REFERENCES feeds(id) ON DELETE CASCADE, uid TEXT NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, link TEXT NOT NULL, pub INTEGER NOT NULL, fetched INTEGER NOT NULL, heard INTEGER NOT NULL DEFAULT 0, UNIQUE(feed_id, uid))");
        d.execSQL("CREATE INDEX articles_heard ON articles(heard)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) { }

    static final class Story { long id; String source, title, summary; }

    long addFeed(String url, String name) {
        ContentValues v = new ContentValues();
        v.put("url", url); v.put("name", name); v.put("custom", name.isEmpty() ? 0 : 1);
        return getWritableDatabase().insertWithOnConflict("feeds", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    boolean editFeed(long id, String url, String name) {
        ContentValues v = new ContentValues();
        v.put("url", url); v.put("name", name); v.put("custom", name.isEmpty() ? 0 : 1);
        return getWritableDatabase().updateWithOnConflict("feeds", v, "id=?", new String[]{"" + id}, SQLiteDatabase.CONFLICT_IGNORE) > 0;
    }
    void setEnabled(long id, boolean on) {
        ContentValues v = new ContentValues(); v.put("enabled", on ? 1 : 0);
        getWritableDatabase().update("feeds", v, "id=?", new String[]{"" + id});
    }
    void removeFeed(long id) { getWritableDatabase().delete("feeds", "id=?", new String[]{"" + id}); }

    /** Feeds to refresh: id, url. */
    long[] feedIds(boolean enabledOnly) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM feeds" + (enabledOnly ? " WHERE enabled=1" : "") + " ORDER BY id", null)) {
            long[] out = new long[c.getCount()];
            for (int i = 0; c.moveToNext(); i++) out[i] = c.getLong(0);
            return out;
        }
    }
    String feedUrl(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT url FROM feeds WHERE id=?", new String[]{"" + id})) { return c.moveToFirst() ? c.getString(0) : null; }
    }
    void feedFailed(long id, String message) {
        ContentValues v = new ContentValues(); v.put("last_error", message);
        getWritableDatabase().update("feeds", v, "id=?", new String[]{"" + id});
    }

    /** Stores new stories, and leaves already cached ones untouched so they are never re-queued. */
    int store(long feedId, FeedParser.Feed f, long now) {
        SQLiteDatabase d = getWritableDatabase();
        int added = 0;
        d.beginTransaction();
        try {
            ContentValues u = new ContentValues();
            u.put("last_ok", now); u.put("last_error", "");
            d.update("feeds", u, "id=?", new String[]{"" + feedId});
            ContentValues nm = new ContentValues(); nm.put("name", f.title);
            if (!f.title.isEmpty()) d.update("feeds", nm, "id=? AND custom=0", new String[]{"" + feedId});
            for (FeedParser.Item i : f.items) {
                ContentValues v = new ContentValues();
                v.put("feed_id", feedId); v.put("uid", i.uid); v.put("title", i.title); v.put("summary", i.summary);
                v.put("link", i.link == null ? "" : i.link); v.put("pub", i.published); v.put("fetched", now);
                if (d.insertWithOnConflict("articles", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) added++;
            }
            d.execSQL("DELETE FROM articles WHERE heard=1 AND id NOT IN (SELECT id FROM articles ORDER BY id DESC LIMIT " + KEEP_ARTICLES + ")");
            d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
        return added;
    }

    long nextUnheard(long notBefore) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT a.id FROM articles a JOIN feeds f ON f.id=a.feed_id WHERE a.heard=0 AND f.enabled=1 AND " + WHEN + ">=" + notBefore + " ORDER BY " + WHEN + ", a.id LIMIT 1", null)) {
            return c.moveToFirst() ? c.getLong(0) : PlaybackQueue.NONE;
        }
    }
    Story story(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT a.title, a.summary, f.name, f.url FROM articles a JOIN feeds f ON f.id=a.feed_id WHERE a.id=?", new String[]{"" + id})) {
            if (!c.moveToFirst()) return null;
            Story s = new Story();
            s.id = id; s.title = c.getString(0); s.summary = c.getString(1);
            s.source = c.getString(2).isEmpty() ? "RSS" : c.getString(2);
            return s;
        }
    }
    boolean isHeard(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT heard FROM articles WHERE id=?", new String[]{"" + id})) { return !c.moveToFirst() || c.getInt(0) != 0; }
    }
    void markHeard(long id) {
        ContentValues v = new ContentValues(); v.put("heard", 1);
        getWritableDatabase().update("articles", v, "id=?", new String[]{"" + id});
    }
    void replayAll() {
        ContentValues v = new ContentValues(); v.put("heard", 0);
        getWritableDatabase().update("articles", v, "feed_id IN (SELECT id FROM feeds WHERE enabled=1)", null);
    }

    private int count(String sql, String... args) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }

    JSONObject counts(long notBefore, boolean online) throws Exception {
        int enabled = count("SELECT COUNT(*) FROM feeds WHERE enabled=1");
        String base = "SELECT COUNT(*) FROM articles a JOIN feeds f ON f.id=a.feed_id WHERE f.enabled=1";
        int articles = count(base);
        int unheard = count(base + " AND a.heard=0 AND " + WHEN + ">=" + notBefore);
        int fetched = count("SELECT COUNT(*) FROM feeds WHERE enabled=1 AND last_ok>0");
        int failed = count("SELECT COUNT(*) FROM feeds WHERE enabled=1 AND last_error<>''");
        JSONObject o = new JSONObject();
        o.put("state", PlaybackQueue.newsState(enabled, articles, unheard, fetched, failed, online));
        o.put("feeds", enabled); o.put("articles", articles); o.put("unheard", unheard); o.put("failed", failed);
        return o;
    }

    String feedsJson() throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT f.id, f.url, f.name, f.custom, f.enabled, f.last_ok, f.last_error, (SELECT COUNT(*) FROM articles x WHERE x.feed_id=f.id) FROM feeds f ORDER BY f.id", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("url", c.getString(1)); o.put("name", c.getString(2)); o.put("custom", c.getInt(3) != 0);
                o.put("enabled", c.getInt(4) != 0); o.put("lastOk", c.getLong(5)); o.put("error", c.getString(6)); o.put("count", c.getInt(7));
                a.put(o);
            }
        }
        return a.toString();
    }

    String recentJson(int limit) throws Exception {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT a.id, a.title, f.name, a.heard FROM articles a JOIN feeds f ON f.id=a.feed_id WHERE f.enabled=1 ORDER BY " + WHEN + " DESC, a.id DESC LIMIT " + limit, null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", "" + c.getLong(0)); o.put("title", c.getString(1)); o.put("source", c.getString(2)); o.put("heard", c.getInt(3) != 0);
                a.put(o);
            }
        }
        return a.toString();
    }
}
