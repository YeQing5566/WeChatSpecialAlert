package dev.zsk.wechatalert;

import android.app.Notification;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only observer; it never cancels, reposts or modifies a WeChat notification. */
public final class WeChatNotificationListener extends NotificationListenerService {
    private static final String WECHAT = "com.tencent.mm";
    private final LinkedHashMap<String, Seen> seen = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> recentFingerprints = new LinkedHashMap<>();
    private SoundEngine sound;

    private static class Seen {
        final String fingerprint;
        long lastObserved;
        Seen(String fp, long ms) { fingerprint = fp; lastObserved = ms; }
    }

    @Override public void onCreate() {
        super.onCreate();
        sound = SoundEngine.shared(this);
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        seen.clear();
        recentFingerprints.clear();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification s : active) {
                    if (!WECHAT.equals(s.getPackageName())) continue;
                    NotificationSnapshot ns = NotificationSnapshot.from(s);
                    seen.put(s.getKey(), new Seen(ns.fingerprint, SystemClock.elapsedRealtime()));
                }
            }
        } catch (Exception e) {
            LocalLog.add(this, "预读现存通知失败: " + e.getClass().getSimpleName());
        }
        LocalLog.add(this, "系统通知监听已连接，已预读现存微信通知（不会补响旧消息）");
    }

    @Override public void onListenerDisconnected() {
        LocalLog.add(this, "通知监听连接断开；等待系统重新连接");
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WECHAT.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        if (Notification.CATEGORY_CALL.equals(n.category)) return;

        NotificationSnapshot snapshot = NotificationSnapshot.from(sbn);
        long now = SystemClock.elapsedRealtime();
        cleanup(now);
        Seen previous = seen.get(sbn.getKey());
        // Update the dedup baseline even during audio playback/cooldown.
        seen.put(sbn.getKey(), new Seen(snapshot.fingerprint, now));
        if (previous != null && previous.fingerprint.equals(snapshot.fingerprint)) {
            if (Prefs.get(this).getBoolean(Prefs.LOG_TITLES, false))
                LocalLog.add(this, "已去重：微信通知仅更新，内容未变");
            return;
        }
        if (!Prefs.get(this).getBoolean(Prefs.ENABLED, true)) return;
        if (!snapshot.looksLikeMessage || snapshot.groupConversation) return;
        if (!Prefs.contacts(this).contains(snapshot.title)) {
            if (Prefs.get(this).getBoolean(Prefs.LOG_TITLES, false))
                LocalLog.add(this, "未命中备注名: " + safeTitle(snapshot.title));
            return;
        }
        long crossWindow = Prefs.crossKey(this);
        Long lastFingerprint = recentFingerprints.put(snapshot.fingerprint, now);
        if (crossWindow > 0 && lastFingerprint != null && now - lastFingerprint < crossWindow) {
            LocalLog.add(this, "跨通知指纹去重：跳过可能重复投递的同一消息");
            return;
        }
        LocalLog.add(this, "命中特别关心好友: " + safeTitle(snapshot.title));
        sound.receive();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (WECHAT.equals(sbn.getPackageName())) seen.remove(sbn.getKey());
    }

    private void cleanup(long now) {
        long ttl = Prefs.dedup(this);
        Iterator<Map.Entry<String,Seen>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastObserved > ttl) it.remove();
        }
        long crossKeep = Math.max(1000, Prefs.crossKey(this));
        Iterator<Map.Entry<String,Long>> f = recentFingerprints.entrySet().iterator();
        while (f.hasNext()) {
            if (now - f.next().getValue() > crossKeep) f.remove();
        }
        while (recentFingerprints.size() > 200) {
            Iterator<String> oldestFingerprint = recentFingerprints.keySet().iterator();
            oldestFingerprint.next(); oldestFingerprint.remove();
        }
        // Bound memory even when WeChat creates many notification keys.
        while (seen.size() > 500) {
            Iterator<String> oldest = seen.keySet().iterator();
            oldest.next(); oldest.remove();
        }
    }
    private String safeTitle(String title) {
        String s = title.replace('\n',' ').replace('\r',' ');
        return s.length() > 36 ? s.substring(0, 36) + "…" : s;
    }
}
