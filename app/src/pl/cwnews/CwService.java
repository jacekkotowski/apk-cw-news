package pl.cwnews;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Foreground media playback: keeps CW running with the screen off and answers headset and lock-screen controls. */
public class CwService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static final String PLAY = "play", PAUSE = "pause", TOGGLE = "toggle", STOP = "stop", NEXT = "next", PREV = "prev", PLAY_ID = "playId", APPLY = "apply", REPLAY = "replay";
    static final String STOPPED = "stopped", PLAYING = "playing", PAUSED = "paused", WAITING = "waiting";
    private static final int RATE = 48000, BLOCK = 480, BETWEEN_STORIES_S = 2, WAIT_POLL_S = 5, SAVE_EVERY_S = 10;
    private static final double DUCK = 0.3;
    private static final String CH = "cw";

    static volatile String status = STOPPED, title = "", source = "", error = "";
    static volatile long storyId = PlaybackQueue.NONE;
    static volatile int word, words;

    private final ConcurrentLinkedQueue<Runnable> cmds = new ConcurrentLinkedQueue<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PlaybackQueue queue = new PlaybackQueue();
    private Db db;
    private SharedPreferences prefs;
    private Engine engine;
    private Thread thread;
    private volatile boolean alive, paused;
    private boolean resumeOnGain, autoAdvance = true, announce = true, finishedRestore;
    private int mode, gap, abbr;
    private double wpm = 20, eff = 12, volume = 0.8, duck = 1;
    private long notBefore = 0, lastRefresh, lastSave;
    private int refreshMin = 30;
    private MediaSession session;
    private AudioManager am;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private boolean noisyOn;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { setPaused(true); } // headphones removed: don't switch to the speaker
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        db = Db.get(this);
        prefs = getSharedPreferences("cw", MODE_PRIVATE);
        am = getSystemService(AudioManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "cwnews");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { command(PLAY, -1); }
            @Override public void onPause() { command(PAUSE, -1); }
            @Override public void onStop() { command(STOP, -1); }
            @Override public void onSkipToNext() { command(NEXT, -1); }
            @Override public void onSkipToPrevious() { command(PREV, -1); }
        }, main);
        session.setActive(true);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs()).setOnAudioFocusChangeListener(this, main).setWillPauseWhenDucked(false).build();
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cwnews:play");
    }

    private static AudioAttributes attrs() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        goForeground();
        String a = in == null ? null : in.getAction();
        if (a == null) { if (thread == null) stopSelf(); return START_NOT_STICKY; }
        command(a, in.getLongExtra("id", -1));
        return START_NOT_STICKY;
    }

    private void goForeground() {
        Notification n = notification();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(1, n);
        } catch (RuntimeException e) { error = "Android refused background playback: " + e.getMessage(); }
    }

    private void command(String a, long id) {
        switch (a) {
            case PLAY: start(); setPaused(false); break;
            case PAUSE: setPaused(true); break;
            case TOGGLE: if (thread == null || paused) { start(); setPaused(false); } else setPaused(true); break;
            case STOP: stopAll(); break;
            case NEXT: if (thread != null) cmds.add(() -> skip(true)); else stopIfIdle(); break;
            case PREV: if (thread != null) cmds.add(() -> skip(false)); else stopIfIdle(); break;
            case PLAY_ID: start(); setPaused(false); cmds.add(() -> { engine.clear(); gap = 0; load(queue.jump(id), 0); }); break;
            case REPLAY: start(); setPaused(false); cmds.add(() -> { engine.clear(); db.replayAll(); queue.reset(); gap = 0; }); break;
            case APPLY: if (thread != null) cmds.add(this::readSettings); else stopIfIdle(); break;
            default: break;
        }
    }
    private void stopIfIdle() { if (thread == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); } }

    private synchronized void start() {
        if (thread != null) return;
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Another app is holding the audio (a call?)."; stopIfIdle(); return; }
        error = "";
        wake.acquire();
        if (!noisyOn) { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); noisyOn = true; }
        alive = true; paused = false; finishedRestore = false;
        thread = new Thread(this::loop, "cw-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void setPaused(boolean p) {
        if (thread == null) return;
        paused = p;
        if (p) { resumeOnGain = false; am.abandonAudioFocusRequest(focus); }
        else am.requestAudioFocus(focus);
        applyPause(p);
    }
    private void applyPause(boolean p) {
        cmds.add(() -> { engine.hold(p); engine.setAudible(!p); savePosition(); publish(); });
    }

    private void stopAll() {
        alive = false;
        Thread t;
        synchronized (this) { t = thread; }
        if (t == null) { finishService(); return; }
        new Thread(() -> {
            try { t.join(1500); } catch (InterruptedException ignored) { }
            main.post(this::finishService);
        }, "cw-stop").start();
    }
    private void finishService() {
        synchronized (this) { thread = null; }
        status = STOPPED; storyId = PlaybackQueue.NONE; title = ""; source = ""; word = 0; words = 0;
        am.abandonAudioFocusRequest(focus);
        if (noisyOn) { unregisterReceiver(noisy); noisyOn = false; }
        if (wake.isHeld()) wake.release();
        session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0).build());
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onAudioFocusChange(int change) {
        if (thread == null) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                setPaused(true); break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (!paused) { paused = true; resumeOnGain = true; applyPause(true); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                duck = DUCK; cmds.add(() -> engine.volume = volume * duck); break;
            case AudioManager.AUDIOFOCUS_GAIN:
                duck = 1; cmds.add(() -> engine.volume = volume * duck);
                if (resumeOnGain) { resumeOnGain = false; paused = false; applyPause(false); }
                break;
            default: break;
        }
    }

    private void readSettings() {
        double w = prefs.getInt("wpm", 20), e = Math.min(w, prefs.getInt("eff", 12));
        int m = prefs.getInt("mode", TextNorm.HEADLINE);
        boolean ann = prefs.getBoolean("announce", true);
        int ab = prefs.getInt("abbr", Abbrev.LIGHT);
        boolean timingChanged = w != wpm || e != eff, textChanged = m != mode || ann != announce || ab != abbr;
        wpm = w; eff = e; mode = m; announce = ann; abbr = ab;
        autoAdvance = prefs.getBoolean("auto", true);
        volume = prefs.getInt("volume", 80) / 100.0;
        refreshMin = prefs.getInt("refresh", 30);
        notBefore = Refresher.notBefore(prefs);
        engine.toneHz = prefs.getInt("pitch", 600);
        engine.volume = volume * duck;
        engine.bedDb = prefs.getInt("bedDb", -24);
        engine.setBed(prefs.getInt("bed", Bed.BAND));
        if ((timingChanged || textChanged) && engine.hasStory() && storyId != PlaybackQueue.NONE) {
            load(storyId, textChanged ? 0 : engine.word());
        }
    }

    private boolean load(long id, int startWord) {
        Db.Story s = id == PlaybackQueue.NONE ? null : db.story(id);
        if (s == null) return false;
        if (TextNorm.forMorse(s.title).isEmpty()) { db.markHeard(id); return false; } // nothing in this headline can be sent
        Keying k = Keying.build(TextNorm.story(s.source, s.title, s.summary, mode, announce, abbr), new Timing(wpm, eff), RATE);
        boolean wasHeld = paused;
        engine.load(k, startWord);
        engine.hold(wasHeld);
        storyId = id; title = s.title; source = s.source;
        words = engine.words(); word = engine.word();
        savePosition();
        publish();
        return true;
    }

    private void restoreOrNext() {
        if (!finishedRestore) {
            finishedRestore = true;
            long last = prefs.getLong("lastId", PlaybackQueue.NONE);
            if (last != PlaybackQueue.NONE && !db.isHeard(last)) {
                queue.setCurrent(last);
                if (load(last, prefs.getInt("lastWord", 0))) return;
            }
        }
        long id = queue.next(db_next);
        if (id == PlaybackQueue.NONE || !load(id, 0)) {
            if (id == PlaybackQueue.NONE) {
                storyId = PlaybackQueue.NONE; title = ""; source = ""; word = 0; words = 0;
                gap = WAIT_POLL_S * RATE;
                if (status != WAITING) publish();
            }
        }
    }
    private final PlaybackQueue.Provider db_next = () -> db.nextUnheard(notBefore);

    private void skip(boolean forward) {
        long cur = queue.current();
        if (forward && cur != PlaybackQueue.NONE) db.markHeard(cur);
        engine.clear();
        gap = 0;
        if (forward) restoreOrNextAfterSkip();
        else if (!load(queue.previous(), 0)) gap = 0;
    }
    private void restoreOrNextAfterSkip() { finishedRestore = true; restoreOrNext(); }

    private void finishStory() {
        if (storyId != PlaybackQueue.NONE) db.markHeard(storyId);
        engine.clear();
        if (autoAdvance) gap = BETWEEN_STORIES_S * RATE;
        else { paused = true; engine.setAudible(false); long id = queue.next(db_next); if (id != PlaybackQueue.NONE) load(id, 0); publish(); }
    }

    private void savePosition() {
        if (storyId != PlaybackQueue.NONE) prefs.edit().putLong("lastId", storyId).putInt("lastWord", word).apply();
        lastSave = System.currentTimeMillis();
    }

    private void loop() {
        AudioTrack track = null;
        try {
            engine = new Engine(RATE, System.nanoTime(), BLOCK);
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
            track = new AudioTrack.Builder().setAudioAttributes(attrs())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setBufferSizeInBytes(Math.max(min, RATE * 8 * 15 / 100)).setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("audio output did not start");
            readSettings();
            engine.setAudible(true);
            track.play();
            lastRefresh = System.currentTimeMillis();
            float[] buf = new float[2 * BLOCK];
            boolean trackOn = true;
            String shown = "";
            while (alive) {
                for (Runnable c; (c = cmds.poll()) != null; ) c.run();
                if (!paused) {
                    if (engine.storyDone()) finishStory();
                    if (!paused && !engine.hasStory()) {
                        if (gap > 0) gap -= BLOCK; else restoreOrNext();
                    }
                }
                if (paused && engine.silent()) {
                    if (trackOn) { track.pause(); trackOn = false; }
                    status = PAUSED;
                    if (!shown.equals(PAUSED)) { shown = PAUSED; publish(); }
                    Thread.sleep(30);
                    continue;
                }
                if (!trackOn) { track.play(); trackOn = true; }
                engine.render(buf, BLOCK);
                int off = 0;
                while (off < buf.length && alive) {
                    int n = track.write(buf, off, buf.length - off, AudioTrack.WRITE_BLOCKING);
                    if (n < 0) throw new IllegalStateException("audio write error " + n);
                    off += n;
                }
                word = engine.word();
                String now = paused ? PAUSED : engine.hasStory() ? PLAYING : storyId == PlaybackQueue.NONE && gap > 0 ? WAITING : PLAYING;
                status = now;
                if (!shown.equals(now)) { shown = now; publish(); }
                long t = System.currentTimeMillis();
                if (t - lastSave > SAVE_EVERY_S * 1000L) savePosition();
                if (refreshMin > 0 && t - lastRefresh > refreshMin * 60000L) {
                    lastRefresh = t;
                    new Thread(() -> Refresher.all(this), "refresh").start();
                }
            }
            // Fade out instead of cutting the waveform.
            engine.hold(true); engine.setAudible(false);
            for (int i = 0; i < 40 && !engine.silent(); i++) { engine.render(buf, BLOCK); track.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING); }
            savePosition();
        } catch (Exception e) {
            error = "Playback stopped: " + e.getMessage();
            main.post(this::stopAll);
        } finally {
            if (track != null) { try { track.stop(); } catch (Exception ignored) { } track.release(); }
        }
    }

    private void publish() { main.post(this::updateSession); }

    private void updateSession() {
        if (thread == null) return;
        boolean playing = !paused;
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title.isEmpty() ? "CW News" : title).putString(MediaMetadata.METADATA_KEY_ARTIST, source).build());
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1 : 0).build());
        getSystemService(NotificationManager.class).notify(1, notification());
    }

    private int drawable(String n) { return getResources().getIdentifier(n, "drawable", getPackageName()); }
    private Notification.Action action(String icon, String label, String a, int code) {
        PendingIntent p = PendingIntent.getService(this, code, new Intent(this, CwService.class).setAction(a), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(this, drawable(icon)), label, p).build();
    }
    private Notification notification() {
        boolean playing = thread != null && !paused;
        PendingIntent open = PendingIntent.getActivity(this, 9, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH).setSmallIcon(drawable("ic_play"))
            .setContentTitle(title.isEmpty() ? "CW News" : title).setContentText(source.isEmpty() ? (playing ? "Listening for new stories" : "Paused") : source)
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(playing).setShowWhen(false)
            .addAction(action("ic_prev", "Previous", PREV, 1))
            .addAction(action(playing ? "ic_pause" : "ic_play", playing ? "Pause" : "Play", TOGGLE, 2))
            .addAction(action("ic_next", "Next", NEXT, 3))
            .addAction(action("ic_stop", "Stop", STOP, 4))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }

    @Override public void onDestroy() {
        alive = false;
        session.release();
        if (noisyOn) { try { unregisterReceiver(noisy); } catch (RuntimeException ignored) { } noisyOn = false; }
        if (wake.isHeld()) wake.release();
        super.onDestroy();
    }
}
