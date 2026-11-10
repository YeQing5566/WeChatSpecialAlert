package dev.zsk.wechatalert;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Plays built-in notification tones only. No interruption / zen policy modification. */
final class SoundEngine {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PlaybackGate gate = new PlaybackGate();
    private Ringtone active;

    private static SoundEngine instance;
    static synchronized SoundEngine shared(Context c) {
        if (instance == null) instance = new SoundEngine(c);
        return instance;
    }
    private SoundEngine(Context c) { context = c.getApplicationContext(); }

    void receive() {
        if (!Prefs.get(context).getBoolean(Prefs.ENABLED, true)) return;
        long now = SystemClock.elapsedRealtime();
        if (!gate.accept(now)) {
            LocalLog.add(context, "新消息被播放锁/冷却期合并: " + gate.state(now));
            return;
        }
        long wait = Prefs.merge(context);
        LocalLog.add(context, "已命中；等待 " + wait + "ms 合并新消息");
        handler.postDelayed(() -> {
            if (!Prefs.get(context).getBoolean(Prefs.ENABLED, true)) {
                gate.reset();
                return;
            }
            if (!gate.beginPlayback()) return;
            playRingtone(Prefs.ringtone(context), false);
        }, wait);
    }

    /** The test button intentionally bypasses the message gate, but never interrupts an alert. */
    void test() {
        if (gate.state(SystemClock.elapsedRealtime()) != PlaybackGate.State.IDLE) {
            LocalLog.add(context, "试听略过：当前正在播放或冷却");
            return;
        }
        if (gate.accept(SystemClock.elapsedRealtime()) && gate.beginPlayback()) {
            playRingtone(Prefs.ringtone(context), true);
        }
    }

    private void playRingtone(Uri uri, boolean test) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
            if (ringtone == null) throw new IllegalStateException("系统无法读取所选铃声 URI");
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ringtone.setLooping(false);
            active = ringtone;
            ringtone.play();
            LocalLog.add(context, test ? "测试铃声开始" : "特别铃声开始");
            long started = SystemClock.elapsedRealtime();
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (active != ringtone) return;
                    long elapsed = SystemClock.elapsedRealtime() - started;
                    if (elapsed >= Prefs.maxPlay(context)) {
                        LocalLog.add(context, "达到最长播放时间；主动停止");
                        stop(ringtone);
                        return;
                    }
                    // Some ROMs briefly return isPlaying=false just after play(), hence grace.
                    if (elapsed >= Prefs.grace(context)) {
                        boolean isPlaying;
                        try { isPlaying = ringtone.isPlaying(); }
                        catch (Exception ex) {
                            LocalLog.add(context, "铃声状态检测异常");
                            stop(ringtone);
                            return;
                        }
                        if (!isPlaying) {
                            stop(ringtone);
                            return;
                        }
                    }
                    handler.postDelayed(this, Prefs.poll(context));
                }
            }, Prefs.poll(context));
        } catch (Exception e) {
            LocalLog.add(context, "铃声播放失败: " + e.getClass().getSimpleName());
            stop(active);
        }
    }

    private void stop(Ringtone ringtone) {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); }
        catch (Exception ignored) { }
        if (active == ringtone) active = null;
        long cooldown = Prefs.cooldown(context);
        gate.playbackFinished(SystemClock.elapsedRealtime(), cooldown);
        LocalLog.add(context, "铃声结束，冷却 " + cooldown + "ms");
    }
}
