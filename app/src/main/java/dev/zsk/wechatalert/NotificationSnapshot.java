package dev.zsk.wechatalert;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Extracts only in memory. Raw message content is never written to logs or disk. */
final class NotificationSnapshot {
    final String title;
    final String fingerprint;
    final boolean looksLikeMessage;
    final boolean groupConversation;

    private NotificationSnapshot(String title, String fingerprint, boolean message, boolean group) {
        this.title = title;
        this.fingerprint = fingerprint;
        this.looksLikeMessage = message;
        this.groupConversation = group;
    }

    static NotificationSnapshot from(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        if (e == null) return new NotificationSnapshot("", "", false, false);
        CharSequence titleValue = e.getCharSequence(Notification.EXTRA_TITLE);
        String title = titleValue == null ? "" : titleValue.toString().trim();
        StringBuilder digestSource = new StringBuilder(256);
        boolean hasPayload = false;

        // MessagingStyle: each bundled message generally includes a text and a timestamp.
        Parcelable[] messages = e.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            digestSource.append("M:").append(messages.length);
            for (Parcelable parcel : messages) {
                if (!(parcel instanceof Bundle)) continue;
                Bundle b = (Bundle) parcel;
                CharSequence text = b.getCharSequence("text");
                digestSource.append('|').append(b.getLong("time", 0L)).append(':');
                if (text != null) digestSource.append(text);
                hasPayload = true;
            }
        }
        if (!hasPayload) {
            CharSequence content = e.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            // Prefer stable single-line text; supplemental expanded text can change
            // during notification rendering without a genuinely new message.
            if (content != null && content.length() != 0) {
                digestSource.append("T:").append(content); hasPayload = true;
            } else if (bigText != null && bigText.length() != 0) {
                digestSource.append("B:").append(bigText); hasPayload = true;
            } else if (lines != null && lines.length > 0) {
                for (CharSequence line : lines) {
                    if (line != null) digestSource.append("L:").append(line);
                }
                hasPayload = true;
            }
        }
        boolean isGroup = e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
        String fingerprint = sha256(title + "|" + digestSource);
        return new NotificationSnapshot(title, fingerprint, hasPayload, isGroup);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(d.length * 2);
            for (byte b : d) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }
}
