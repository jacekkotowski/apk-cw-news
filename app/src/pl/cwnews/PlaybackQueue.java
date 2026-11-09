package pl.cwnews;

import java.util.ArrayDeque;

/** Story order. New stories come from the provider, and only Previous or a replay repeats one. */
final class PlaybackQueue {
    interface Provider { long nextUnheard(); }
    static final long NONE = -1;
    private static final int HISTORY = 200;
    private final ArrayDeque<Long> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
    private long current = NONE;

    long current() { return current; }
    void setCurrent(long id) { current = id; }

    long next(Provider p) {
        if (current != NONE) { back.push(current); while (back.size() > HISTORY) back.removeLast(); }
        current = forward.isEmpty() ? p.nextUnheard() : forward.pop();
        return current;
    }

    long previous() {
        if (back.isEmpty()) return current;
        if (current != NONE) forward.push(current);
        current = back.pop();
        return current;
    }

    long jump(long id) {
        if (current != NONE && current != id) back.push(current);
        forward.clear();
        current = id;
        return id;
    }

    /** Called after a replay is requested, so old positions don't mix with it. */
    void reset() { back.clear(); forward.clear(); current = NONE; }

    static final String NO_FEEDS = "no_feeds", FETCH_ERROR = "fetch_error", OFFLINE_EMPTY = "offline_empty", EMPTY_FEEDS = "empty_feeds", NEVER_FETCHED = "never_fetched", ALL_HEARD = "all_heard", OFFLINE_CACHE = "offline_cache", HAS_NEWS = "has_news";

    /** Which no-news message to show. */
    static String newsState(int enabledFeeds, int articles, int unheard, int feedsFetched, int feedsFailed, boolean online) {
        if (enabledFeeds == 0) return NO_FEEDS;
        if (articles == 0) {
            if (!online) return OFFLINE_EMPTY;
            if (feedsFailed > 0) return FETCH_ERROR;
            return feedsFetched == 0 ? NEVER_FETCHED : EMPTY_FEEDS;
        }
        if (unheard == 0) return ALL_HEARD;
        return online ? HAS_NEWS : OFFLINE_CACHE;
    }
}
