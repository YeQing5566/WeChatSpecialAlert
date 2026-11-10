# 微信特别提醒 · iQOO 13 / OriginOS 6 (实验版 0.1.0)

一个不修改微信原生通知的纯本地 Android 通知监听应用。根据通知标题中的**微信备注名**精确匹配，额外播放一次用户在系统铃声选择器中选定的通知铃声。普通联系人照常由微信通知；命中联系人会先/同时有微信默认声音，再响自定义提醒声。

**本交付为源代码工程，不是经过手机验证的正式发行版。** 环境中没有 Android SDK/Gradle 8.14，已验证纯 Java 播放状态机；仍需在提供的服务器上真正执行 Android Gradle 构建，并在 iQOO 13 上做通知监听、后台播放、最近任务隐藏等实机测试。

## 功能与明确限制

- 无需 Root；无互联网、无读取存储、无通知发布、无免打扰修改权限。
- 不调用 `cancelNotification()`，不重建通知，不更改微信通知通道。
- 匹配一个或多个**通知标题**（每行一个），默认忽略群聊、通话、分组摘要、持续性通知。
- 合并等待、冷却时长、通知去重缓存窗口、跨通知重复指纹合并窗口、最长播放、播放状态轮询间隔、启动宽限时间均可在 APK 内保存修改，不需重新构建。
- 状态锁：合并等待/播放中/冷却期不排队，不重复播放；相同通知 key 且消息指纹不变也不重复响。
- 通过 `RingtoneManager.ACTION_RINGTONE_PICKER` 打开系统通知铃声选择器；不需要上传 MP3。
- `setExcludeFromRecents()` 动态启闭最近任务隐藏；桌面启动入口仍保留。OriginOS 实际显示效果需实测。
- 本地诊断日志不写入消息正文。调试开关可记录**通知标题**（可能是联系人姓名），注意保护隐私。

**已知限制：** 微信通知如果不暴露准确的好友备注名，无法可靠匹配；同一通知 key 中重复发送完全相同文本且未带消息级时间戳时可能无法区分；部分通知在 OriginOS 中被遮蔽或合并也可能漏提醒。通知监听是异步观察，不保证实时性。后台声音是否播放、系统铃声 URI 是否长期可访问以及多任务隐藏效果需要手机验证。

## 服务器环境

项目针对你提供的路径：

- `JAVA_HOME=/opt/jdk17`
- `ANDROID_HOME=/opt/android-sdk`
- `ANDROID_SDK_ROOT=/opt/android-sdk`
- `GRADLE_USER_HOME=/opt/gradle-cache`
- `GRADLE_DIST_ZIP=/opt/gradle-distributions/gradle-8.14-all.zip`
- AGP `8.11.1` + Gradle `8.14` + JDK `17` + compile/target SDK `36`

项目无需 Flutter 和私有的 `.env`；也**不会读取或使用**公司 `/data/client/keystore.jks`。Debug 包由 Gradle 使用独立 debug key 签名；若将来需要长期升级请确保备份签名证书，不要用不同 key 签名覆盖同一包名。

## 构建

服务器安装或确认 API 36 平台文件及 SDK Build Tools 35：

```bash
ls -l /opt/android-sdk/platforms/android-36/android.jar
ls -ld /opt/android-sdk/build-tools/35.0.0
# 缺失时（需要 SDK 镜像访问能力）：
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager 'platforms;android-36' 'build-tools;35.0.0'
```

将本 ZIP 解压到服务器，例如 `/data/WeChatSpecialAlert/`。运行：

```bash
cd /data/WeChatSpecialAlert
bash scripts/smoke-test.sh
bash scripts/build-on-server.sh
```

脚本从你已有 ZIP 离线解压 Gradle 8.14（不重复下载 Gradle 分发包），在 API 36 存在时执行 `:app:assembleDebug`；AGP 插件仍可能需要从 Maven/阿里云镜像下载（如果服务端以前没有缓存）。

产物路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装（ADB 可用时）：

```bash
/opt/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 手机上首次配置

1. 打开 APK，填入一个好友在微信通知栏**实际显示**的备注名（完全相同的字符）。
2. 点击「选择 OriginOS 系统通知铃声」，选择与微信默认声不同的系统铃声，试听。
3. 点击「保存所有设置」。
4. 点击「打开系统通知读取权限设置」，授予本 APK 通知访问权限。
5. 你计划手动配置的 OriginOS 自启动、后台电池无限制，在系统设置中完成；不要禁用微信原生通知。
6. Android 13+ 安装来源如被系统标记为“受限制设置”，可能需要在应用信息里手动允许受限制设置，才能授权通知访问，具体视 OriginOS 政策而定。
7. 初次测试建议暂时不启用“最近任务隐藏”，以便检查运行和日志；确认功能后再启用。

## 测试矩阵

| 场景 | 目标 |
|---|---|
| 普通好友 1 条 | 只响微信原声 |
| 特别关心 1 条 | 微信原声 + 一次特殊铃声 |
| 特别关心 5 条紧跟着发 | 合并等待 + 播放锁 + 冷却，最多一次特殊铃声 |
| 重复通知回调/相同消息内容更新 | 不额外重复响 |
| 铃声播放中普通好友发消息 | 不重启特殊铃声 |
| 冷却结束后新消息 | 再次提醒 |
| 锁屏息屏 10 分钟后 | 验证监听与播放是否正常 |
| 通知权限撤销再授权 | 恢复监听，避免旧通知补响 |
| 打开/关闭最近任务隐藏 | 多任务列表切换有效、桌面图标依旧可见 |
| 音量 0 / 免打扰 | 不修改系统设置，尊重系统通知音量规则 |

若出现重复铃声、漏响，请打开应用启用「记录未命中的通知标题」，复现后读取日志。无需分享微信聊天正文。

## 重要安全说明

通知读取权限通常可以读取各 App 的通知内容，但本应用代码只处理 `com.tencent.mm`，不联网且不持久化消息正文。权限仍然较敏感，建议只安装自己构建、确认签名的 APK。
