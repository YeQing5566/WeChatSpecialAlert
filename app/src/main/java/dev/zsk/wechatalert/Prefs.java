package dev.zsk.wechatalert;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** All timeouts are editable from the UI and read per incoming event. */
public final class Prefs {
    static final String FILE = "settings";
    static final String ENABLED = "enabled";
    static final String EXCLUDE_RECENTS = "exclude_recents";
    static final String CONTACTS = "contacts";
    static final String RINGTONE = "ringtone";
    static final String MERGE_MS = "merge_ms";
    static final String COOLDOWN_MS = "cooldown_ms";
    static final String DEDUP_MS = "dedup_ms";
    static final String CROSS_KEY_MS = "cross_key_ms";
    static final String MAX_PLAY_MS = "max_play_ms";
    static final String POLL_MS = "poll_ms";
    static final String START_GRACE_MS = "start_grace_ms";
    static final String LOG_TITLES = "log_titles";

    public static final long DEFAULT_MERGE_MS = 500;
    public static final long DEFAULT_COOLDOWN_MS = 2000;
    public static final long DEFAULT_DEDUP_MS = 120000;
    public static final long DEFAULT_CROSS_KEY_MS = 5000;
    public static final long DEFAULT_MAX_PLAY_MS = 15000;
    public static final long DEFAULT_POLL_MS = 200;
    public static final long DEFAULT_START_GRACE_MS = 500;

    private Prefs() {}
    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    static long time(Context c, String key, long defaultValue, long min, long max) {
        return Math.max(min, Math.min(max, get(c).getLong(key, defaultValue)));
    }
    static long merge(Context c) { return time(c, MERGE_MS, DEFAULT_MERGE_MS, 0, 10000); }
    static long cooldown(Context c) { return time(c, COOLDOWN_MS, DEFAULT_COOLDOWN_MS, 0, 120000); }
    static long dedup(Context c) { return time(c, DEDUP_MS, DEFAULT_DEDUP_MS, 1000, 3600000); }
    static long crossKey(Context c) { return time(c, CROSS_KEY_MS, DEFAULT_CROSS_KEY_MS, 0, 60000); }
    static long maxPlay(Context c) { return time(c, MAX_PLAY_MS, DEFAULT_MAX_PLAY_MS, 1000, 120000); }
    static long poll(Context c) { return time(c, POLL_MS, DEFAULT_POLL_MS, 50, 2000); }
    static long grace(Context c) { return time(c, START_GRACE_MS, DEFAULT_START_GRACE_MS, 100, 5000); }
    static Uri ringtone(Context c) {
        String s = get(c).getString(RINGTONE, "");
        return s.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(s);
    }
    static Set<String> contacts(Context c) {
        String raw = get(c).getString(CONTACTS, "");
        if (raw.isEmpty()) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (String token : raw.split("[\\r\\n,，;；]+")) {
            String name = token.trim();
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }
}
