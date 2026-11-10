package dev.zsk.wechatalert;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight native settings page; configuration is stored in SharedPreferences. */
public final class MainActivity extends Activity {
    private static final int PICK_RINGTONE = 100;
    private final Map<String, EditText> times = new LinkedHashMap<>();
    private TextView accessStatus, selectedTone, logText;
    private EditText contacts;
    private Switch enabled, hidden, debug;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        applyRecentsSetting();
    }
    @Override protected void onResume() {
        super.onResume();
        applyRecentsSetting();
        if (accessStatus != null) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            boolean granted = nm.isNotificationListenerAccessGranted(
                    new ComponentName(this, WeChatNotificationListener.class));
            accessStatus.setText(granted ? "通知读取权限：已授权" : "通知读取权限：未授权（请手动开启）");
        }
    }

    private void buildUi() {
        SharedPreferences p = Prefs.get(this);
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroller.addView(root);
        setContentView(scroller);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(18), sys.top + dp(18), dp(18), sys.bottom + dp(24));
            return insets;
        });
        root.requestApplyInsets();

        label(root, "微信特别提醒", 25, true);
        caption(root, "只监听微信，不取消、不替换、不重发微信通知。普通好友仍由微信原生铃声提醒；命中特别关心时额外响一声。");

        section(root, "基础开关");
        enabled = toggle(root, "启用特别关心提醒", p.getBoolean(Prefs.ENABLED, true));
        hidden = toggle(root, "在最近任务（多任务视图）中隐藏", p.getBoolean(Prefs.EXCLUDE_RECENTS, false));
        caption(root, "隐藏最近任务不会隐藏桌面图标，也不会停止通知监听。关闭后可通过桌面图标重新进入。");
        hidden.setOnCheckedChangeListener((CompoundButton button, boolean value) -> {
            p.edit().putBoolean(Prefs.EXCLUDE_RECENTS, value).apply();
            applyRecentsSetting();
        });

        accessStatus = label(root, "正在检查通知读取权限…", 14, false);
        button(root, "打开系统通知读取权限设置", () -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception e) { toast("未找到通知读取设置，请在系统设置中手动开启"); }
        });

        section(root, "特别关心好友");
        caption(root, "填写微信通知栏中实际显示的备注名，一行一个，精确匹配；也支持用逗号分隔。默认不匹配群聊。");
        contacts = new EditText(this);
        contacts.setText(p.getString(Prefs.CONTACTS, ""));
        contacts.setHint("例如：好友备注名");
        contacts.setSingleLine(false);
        contacts.setMinLines(2);
        contacts.setMaxLines(6);
        root.addView(contacts, margin());

        section(root, "系统提示铃声");
        selectedTone = label(root, "", 14, false);
        updateToneLabel();
        button(root, "选择 OriginOS 系统通知铃声", this::pickRingtone);
        button(root, "试听选定的铃声", () -> SoundEngine.shared(this).test());

        section(root, "时间参数（全部毫秒，保存后对新事件生效）");
        setting(root, Prefs.MERGE_MS, "新消息合并等待", 0, 10000, Prefs.DEFAULT_MERGE_MS,
                "首条命中后等待再响铃；期间的消息只触发一次");
        setting(root, Prefs.COOLDOWN_MS, "铃声完成后冷却", 0, 120000, Prefs.DEFAULT_COOLDOWN_MS,
                "冷却期新消息直接忽略，不加入待播放队列");
        setting(root, Prefs.DEDUP_MS, "通知指纹保留窗口", 1000, 3600000, Prefs.DEFAULT_DEDUP_MS,
                "同一通知 key + 内容指纹去重；超过窗口可能重新识别");
        setting(root, Prefs.CROSS_KEY_MS, "跨通知重复指纹合并", 0, 60000, Prefs.DEFAULT_CROSS_KEY_MS,
                "同一内容被微信用不同通知 key 重发时，短时间内只提醒一次；0 表示关闭");
        setting(root, Prefs.MAX_PLAY_MS, "最长铃声播放时间", 1000, 120000, Prefs.DEFAULT_MAX_PLAY_MS,
                "防止部分 ROM 误报一直播放造成永久锁定");
        setting(root, Prefs.POLL_MS, "播放结束状态检查间隔", 50, 2000, Prefs.DEFAULT_POLL_MS,
                "越小结束检测越及时，默认 200ms");
        setting(root, Prefs.START_GRACE_MS, "开始播放后宽限时间", 100, 5000, Prefs.DEFAULT_START_GRACE_MS,
                "防止铃声刚启动时 isPlaying() 短暂返回 false");

        section(root, "调试与诊断");
        debug = toggle(root, "记录未命中的通知标题（仅本机）", p.getBoolean(Prefs.LOG_TITLES, false));
        caption(root, "默认关闭。不会记录微信消息正文；日志仅保存在本机应用私有存储。");
        button(root, "保存所有设置", this::saveSettings);
        button(root, "刷新诊断日志", this::refreshLog);
        button(root, "清空诊断日志", () -> { LocalLog.clear(this); refreshLog(); });
        logText = label(root, "", 12, false);
        logText.setTextIsSelectable(true);
        refreshLog();
    }

    private void pickRingtone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Prefs.ringtone(this));
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择特别关心通知铃声");
        try { startActivityForResult(i, PICK_RINGTONE); }
        catch (Exception e) { toast("系统铃声选择器无法启动：" + e.getClass().getSimpleName()); }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != PICK_RINGTONE || result != RESULT_OK || data == null) return;
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) { toast("没有选中有效铃声"); return; }
        Prefs.get(this).edit().putString(Prefs.RINGTONE, uri.toString()).apply();
        updateToneLabel();
        toast("铃声已保存，可点击试听");
    }

    private void updateToneLabel() {
        try {
            android.media.Ringtone r = RingtoneManager.getRingtone(this, Prefs.ringtone(this));
            selectedTone.setText("当前铃声：" + (r == null ? "读取失败，请重新选择" : r.getTitle(this)));
        } catch (Exception e) { selectedTone.setText("铃声未能读取，请重新选择"); }
    }

    private void saveSettings() {
        SharedPreferences.Editor e = Prefs.get(this).edit();
        e.putBoolean(Prefs.ENABLED, enabled.isChecked());
        e.putBoolean(Prefs.LOG_TITLES, debug.isChecked());
        e.putString(Prefs.CONTACTS, contacts.getText().toString().trim());
        String[] keys = {Prefs.MERGE_MS, Prefs.COOLDOWN_MS, Prefs.DEDUP_MS,
                Prefs.CROSS_KEY_MS, Prefs.MAX_PLAY_MS, Prefs.POLL_MS, Prefs.START_GRACE_MS};
        long[] min = {0, 0, 1000, 0, 1000, 50, 100};
        long[] max = {10000, 120000, 3600000, 60000, 120000, 2000, 5000};
        for (int idx = 0; idx < keys.length; idx++) {
            String raw = times.get(keys[idx]).getText().toString().trim();
            try {
                long value = Long.parseLong(raw);
                if (value < min[idx] || value > max[idx]) {
                    toast("时间参数超出范围：" + keys[idx] + "，允许 " + min[idx] + "～" + max[idx]);
                    return;
                }
                e.putLong(keys[idx], value);
            } catch (NumberFormatException exception) {
                toast("请输入有效整数：" + keys[idx]);
                return;
            }
        }
        e.apply();
        LocalLog.add(this, "配置已更新（不会修改微信系统通知）");
        toast("已保存，新通知立即使用新参数");
        refreshLog();
    }

    private void applyRecentsSetting() {
        boolean exclude = Prefs.get(this).getBoolean(Prefs.EXCLUDE_RECENTS, false);
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        try {
            for (ActivityManager.AppTask t : am.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = t.getTaskInfo();
                if (info != null && info.taskId == getTaskId()) t.setExcludeFromRecents(exclude);
            }
        } catch (Exception e) {
            LocalLog.add(this, "切换最近任务隐藏失败: " + e.getClass().getSimpleName());
        }
    }
    private void refreshLog() { if (logText != null) logText.setText(LocalLog.read(this)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int d) { return (int) (getResources().getDisplayMetrics().density * d + .5f); }
    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        p.bottomMargin = dp(6);
        return p;
    }
    private TextView label(LinearLayout root, String s, int sp, boolean strong) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (strong) t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t, margin());
        return t;
    }
    private void caption(LinearLayout root, String s) {
        TextView t = label(root, s, 13, false);
        t.setTextColor(0xff667085);
    }
    private void section(LinearLayout root, String title) { label(root, title, 19, true); }
    private Button button(LinearLayout root, String s, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setOnClickListener(v -> action.run());
        root.addView(b, margin());
        return b;
    }
    private Switch toggle(LinearLayout root, String s, boolean checked) {
        Switch sw = new Switch(this);
        sw.setText(s);
        sw.setTextSize(15);
        sw.setChecked(checked);
        root.addView(sw, margin());
        return sw;
    }
    private void setting(LinearLayout root, String key, String title, long min, long max, long def, String hint) {
        label(root, title + "（" + min + "～" + max + "ms）", 15, false);
        EditText e = new EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setText(String.valueOf(Prefs.get(this).getLong(key, def)));
        root.addView(e, margin());
        times.put(key, e);
        caption(root, hint);
    }
}
