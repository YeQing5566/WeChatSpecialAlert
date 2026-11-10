package dev.zsk.wechatalert;

import android.content.Context;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A bounded on-device diagnostic log. Never records message bodies. */
final class LocalLog {
    private static final String FILE = "diagnostics";
    private static final String KEY = "history";
    private LocalLog() {}

    static synchronized void add(Context c, String line) {
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(new Date());
        String value = stamp + "  " + line;
        Log.i("WeChatSpecialAlert", line);
        String previous = c.getApplicationContext().getSharedPreferences(FILE, 0).getString(KEY, "");
        String all = value + "\n" + previous;
        if (all.length() > 16000) all = all.substring(0, 16000);
        c.getApplicationContext().getSharedPreferences(FILE, 0).edit().putString(KEY, all).apply();
    }
    static String read(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, 0).getString(KEY, "暂无日志");
    }
    static void clear(Context c) { c.getApplicationContext().getSharedPreferences(FILE, 0).edit().remove(KEY).apply(); }
}
