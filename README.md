# 浮译 FuYi

> Android 上的日文游戏**局内悬浮截屏翻译**：点一下悬浮球 → 冻结当前画面 → 框选或点选日文 → 中文译文直接弹在游戏上方，不用切应用、不用来回截图。

<!-- 有截图后放到 docs/screenshot.jpg 并取消注释 -->
<!-- ![截图](docs/screenshot.jpg) -->

## 特性

- **按需截屏**：只有点悬浮球时才抓一帧，不做后台录屏、不持续 OCR。
- **两种取词方式**：拖动框选任意区域；或直接点自动识别出的文字块。
- **离线 OCR**：ML Kit 日文模型内置，国行手机没有 Google 服务也能用。
- **任意翻译 API**：OpenAI 兼容接口即可，内置 DeepSeek / OpenAI / Gemini / 硅基流动预设，也能填自建或中转地址。
- **术语表**：`原文=译文` 每行一条，固定人名、技能名。
- **译文卡片**：正文可滚动，右上角 `×` 与底部按钮永远可见，可拖动位置。
- **无需 root**，不注入、不修改游戏。

## 环境要求

- Android 11+（API 30+），arm64 / armeabi-v7a / x86_64
- 需要联网调用翻译 API
- 小米 MIUI / HyperOS 需要额外保活设置，见下文

## 安装

1. 从 [Releases](../../releases) 下载 `FuYi-vX.Y.apk`。
2. 传到手机安装，允许「未知来源应用」。
3. 打开 App，依次授予：
   - 显示悬浮窗
   - 无障碍服务（设置 → 更多设置 → 无障碍 → 已下载的应用 → 浮译）
   - 通知（用于常驻通知，防后台被杀）
4. 填翻译 API：点预设 → 填 Key → 点「测试连接」。
5. 点「启动悬浮球」，回到游戏，点「译」。

### 小米必做

| 设置 | 位置 |
|---|---|
| 显示悬浮窗 | 应用管理 → 浮译 → 权限管理 |
| 后台弹出界面 | 同上 |
| 省电策略 → 无限制 | 应用信息 |
| 自启动 → 允许 | 应用信息 |
| 锁定后台 | 最近任务里下拉浮译卡片 |

游戏加速（Game Turbo）里建议把浮译加入白名单，或关闭「防打扰」。

## 使用

1. 点悬浮球，画面冻结并压暗。
2. 约半秒后日文文字块被细框标出。
3. 点某个文字块直接翻译；或拖一个矩形只识别框内。
4. 译文卡片出现后：
   - `复制`：复制译文
   - `重译`：重新请求
   - `关闭` / 右上角 `×`：关闭
   - 按住标题栏可拖动卡片
5. 返回键或右上角 `×` 可取消选区。

更详细的图文说明见 [docs/USAGE_zh.md](docs/USAGE_zh.md)。

## 构建

要求：JDK 17、Android SDK 35、Gradle 8.9（仓库自带 wrapper）。

```bash
git clone https://github.com/312310070303/FuYi.git
cd FuYi
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

> 仓库里的 `settings.gradle.kts` 优先使用阿里云 Maven 镜像、wrapper 使用腾讯镜像，方便国内下载；海外网络可自行删掉镜像行、把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换回 `https://services.gradle.org/distributions/gradle-8.9-bin.zip`。

### 签名（可选）

在根目录新建 `keystore.properties`（已被 .gitignore 忽略）：

```properties
storeFile=keystore/your.jks
storePassword=******
keyAlias=your-alias
keyPassword=******
```

没有该文件时，`assembleRelease` 会产出未签名包，可直接用于本地调试。

## 工程结构

```
app/src/main/java/com/fuyi/overlay/
├── MainActivity.kt        设置页、权限引导、测试入口
├── OverlayService.kt      前台服务：悬浮球、选区层、译文卡片
├── CaptureService.kt      无障碍截屏（takeScreenshot）
├── SelectView.kt          冻结帧上的框选 / 文字块点选
├── ResultCardView.kt      译文卡片
├── OcrEngine.kt           ML Kit 日文 OCR
├── Translator.kt          OpenAI 兼容翻译客户端
├── Prefs.kt               本地配置
└── Util.kt
```

## 隐私

- 截图只在手机本地做 OCR，**不上传图片**。
- 只有识别出的文本会发送到你自己配置的翻译 API。
- API Key 保存在应用私有目录，不经过本项目的任何服务器（项目本身没有服务器）。

## 已知限制

- 游戏若启用 `FLAG_SECURE`，任何截屏 API 都只能拿到黑屏，无 root 无解。
- 少数游戏会调用 `hideOverlayWindows` 隐藏悬浮窗，无法绕过。
- 竖排日文、艺术字体的识别率会下降，可改用框选并尽量贴紧文字。
- 不建议在 PvP / 排位场景使用：虽然只读屏幕，但个别反作弊会检测无障碍或悬浮窗。

## 免责声明

本项目仅供个人学习与自用。请遵守你所玩游戏的用户协议；游戏文本、画面版权归原厂所有，本项目不包含任何游戏资源。使用本工具产生的账号风险由使用者自行承担。

## License

[MIT](LICENSE)
