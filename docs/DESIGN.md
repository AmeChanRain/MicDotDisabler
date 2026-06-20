# Mic Dot Disabler — 设计与实现文档

## 1. 项目概述

### 1.1 目标
创建一个 Android 应用程序，通过 Shizuku 以 ADB 权限执行 shell 命令，禁用 Android 12+ 系统中使用麦克风时状态栏出现的绿色提示点（Privacy Indicators / Mic Dot）。

### 1.2 平台要求
- **最低 SDK**: 31 (Android 12)
- **目标 SDK**: 34 (Android 14)
- **技术栈**: Kotlin + Jetpack Compose + Material 3 + Shizuku API

### 1.3 核心原理
Android 12 引入了隐私指示器（Privacy Indicators），当应用使用麦克风或摄像头时，状态栏会显示一个绿点。该功能由 `device_config` 中的 `privacy.camera_mic_icons_enabled` 标志位控制。通过以下两条命令可禁用：

```bash
adb shell cmd device_config put privacy camera_mic_icons_enabled false default
adb shell cmd device_config set_sync_disabled_for_tests persistent
```

- **第一条命令**：将 `camera_mic_icons_enabled` 设为 `false`，命名空间为 `privacy`，写入 `default` 桶。
- **第二条命令**：调用 `set_sync_disabled_for_tests persistent` 阻止系统在重启后从服务端重新同步配置，使设置持久化。

> **注意**：`set_sync_disabled_for_tests` 会阻止所有 device_config 同步，不仅仅是 `privacy` 命名空间。这意味着系统更新可能覆盖此设置，用户需在系统 OTA 后重新执行。

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────┐
│              MainActivity                │
│  (Single Activity, Compose Host)         │
├─────────────────────────────────────────┤
│          AppNavigation                   │
│  (根据 Shizuku 状态路由界面)              │
├──────────────────┬──────────────────────┤
│   SetupScreen    │   MainScreen         │
│  (引导激活       │  (Disable Mic Dot    │
│   Shizuku)       │   按钮 + 结果)        │
├──────────────────┴──────────────────────┤
│          ShizukuManager                  │
│  (封装所有 Shizuku 交互逻辑)              │
├─────────────────────────────────────────┤
│          Shizuku API                     │
│  (dev.rikka.shizuku:api)                │
├─────────────────────────────────────────┤
│       Android OS (ADB/Shell UID)        │
└─────────────────────────────────────────┘
```

### 2.2 组件树

```
MainActivity
├── MicDotDisablerTheme (Material 3 动态主题)
│   └── MicDotDisablerApp
│       ├── SetupScreen (Shizuku 未就绪时)
│       │   ├── AppIcon & Title
│       │   ├── InstructionCards (引导步骤卡片)
│       │   ├── StatusIndicator (当前状态)
│       │   └── ActionButtons (跳转 Shizuku / 重新检查)
│       └── MainScreen (Shizuku 已就绪时)
│           ├── StatusBadge (Shizuku 运行状态)
│           ├── DisableButton ("Disable Mic Dot")
│           ├── ResultPanel (成功/失败信息)
│           │   ├── SuccessMessage + ConfettiOverlay
│           │   └── ErrorMessage (完整错误信息 + 复制按钮)
│           └── RestoreButton (可选：恢复绿点)
```

### 2.3 数据流

```
启动应用
    │
    ▼
检查 Shizuku 状态
    │
    ├── Binder 不存在 / 权限未授予
    │       │
    │       ▼
    │   SetupScreen
    │       │
    │       ├── 提供跳转 Shizuku 应用的按钮
    │       ├── 提供打开无线调试的说明
    │       └── 用户返回后，重新检查状态
    │
    └── Binder 存活 + 权限已授予
            │
            ▼
        MainScreen
            │
            ├── 显示 "Disable Mic Dot" 按钮
            │
            └── 用户点击按钮
                    │
                    ▼
                执行两条 shell 命令
                    │
                    ├── 成功 (exitCode == 0)
                    │       └── 显示成功文字 + 撒纸片动画
                    │
                    └── 失败 (exitCode != 0 或异常)
                            └── 显示完整错误信息
```

### 2.4 状态定义

```kotlin
sealed interface AppState {
    /** Shizuku 未安装 */
    data object ShizukuNotInstalled : AppState

    /** Shizuku 未运行（已安装但未启动） */
    data object ShizukuNotRunning : AppState

    /** Shizuku 运行中但未授权 */
    data object PermissionRequired : AppState

    /** 已就绪，可执行命令 */
    data object Ready : AppState

    /** 命令执行中 */
    data object Executing : AppState

    /** 命令执行成功 */
    data class Success(val message: String) : AppState

    /** 命令执行失败 */
    data class Error(val message: String) : AppState
}
```

---

## 3. Shizuku 集成方案

### 3.1 依赖配置

在 `libs.versions.toml` 中添加：

```toml
[versions]
shizuku = "12.2.0"

[libraries]
shizuku-api = { group = "dev.rikka.shizuku", name = "api", version.ref = "shizuku" }
shizuku-provider = { group = "dev.rikka.shizuku", name = "provider", version.ref = "shizuku" }
```

> **版本选择说明**：Shizuku v13.1.x 将 `newProcess` 方法设为 private，要求开发者迁移至 UserService + AIDL 方案。考虑到本应用仅需执行两条简单 shell 命令，UserService 引入的复杂度（AIDL 定义、Service 实现、绑定生命周期管理）远超收益。因此选用 v12.2.0，该版本 `newProcess` 标记为 deprecated 但仍为 public，功能完全可用。

在 `app/build.gradle.kts` 中添加：

```kotlin
implementation(libs.shizuku.api)
implementation(libs.shizuku.provider)
```

### 3.2 AndroidManifest 配置

```xml
<!-- Shizuku Provider (必需) -->
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

**所有应用仅需声明的权限**：
```xml
<uses-permission android:name="android.permission.INTERNET" /> <!-- 仅用于可能的错误上报，非核心功能 -->
```

> **关键约束**：此应用**不申请**任何与麦克风、摄像头、root 或其他系统级权限。所有特权操作通过 Shizuku 的 ADB Shell UID 代理完成。

### 3.3 ShizukuManager 设计

```kotlin
class ShizukuManager(private val context: Context) {

    // ── 状态监听 ──
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        // Binder 已连接，重新评估状态
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // Binder 断开，恢复到未连接状态
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            // 处理权限请求结果
        }

    // ── 核心 API ──

    /** 检查 Shizuku 是否可用（Binder 存活 + 版本兼容）*/
    fun isShizukuAvailable(): Boolean

    /** 检查是否已获得权限 */
    fun hasPermission(): Boolean

    /** 请求 Shizuku 权限 */
    fun requestPermission(requestCode: Int)

    /** 执行 shell 命令（通过 Shizuku newProcess） */
    fun execCommand(command: String): CommandResult

    /** 执行禁用麦克风绿点的两条命令 */
    fun disableMicDot(): CommandResult

    // ── 生命周期 ──
    fun registerListeners()
    fun unregisterListeners()
}
```

### 3.4 命令执行方案：`Shizuku.newProcess()`

使用 Shizuku 的 `newProcess` 方法以 ADB Shell 身份执行命令：

```kotlin
fun execCommand(command: String): CommandResult {
    val process = Shizuku.newProcess(
        arrayOf("sh", "-c", command),
        null,  // env: 继承当前环境
        null   // dir: 继承当前工作目录
    )

    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val stderr = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    return CommandResult(exitCode, stdout, stderr)
}
```

> **关于 `newProcess` 被标记为 deprecated**：Shizuku 官方推荐使用 `UserService` 替代，但 `newProcess` 在 API 13.x 中仍然可用且功能正常。对于本应用这种简单命令执行场景，`UserService` 引入的复杂度（AIDL 定义、Service 实现、绑定管理）远超收益。如果未来 Shizuku API 完全移除 `newProcess`，再迁移至 `UserService` 方案。

### 3.5 版本兼容说明

- **Shizuku v11 之前**：包名为 `moe.shizuku.api`，与本应用不兼容。若检测到 `Shizuku.isPreV11() == true`，提示用户升级 Shizuku。
- **Shizuku v11+ (API 11.x)**：包名迁移至 `rikka.shizuku`，完全兼容。
- **Shizuku v12.1+**：内置 Sui 自动初始化。
- **Shizuku v13.x**：当前推荐版本。`newProcess` 标记为 deprecated 但仍可用。

---

## 4. UI/UX 设计

### 4.1 设计语言
- **Material 3 (Material You)** 动态配色
- Material 3 组件：`TopAppBar`、`Card`、`Button`、`Chip`、`CircularProgressIndicator`
- 支持亮色/暗色主题自动切换
- 遵循 8dp 网格系统
- 无障碍：所有按钮有 contentDescription，颜色对比度满足 WCAG AA

### 4.2 SetupScreen（引导页面）

```
┌─────────────────────────────────────────┐
│                                         │
│         🎤🚫  (App Icon, Large)         │
│                                         │
│       Mic Dot Disabler                  │
│       Disable the annoying green        │
│       privacy indicator dot             │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Step 1: Install Shizuku       │    │
│  │  Download from Google Play or   │    │
│  │  GitHub releases                │    │
│  │                        [Open]  │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Step 2: Start Shizuku         │    │
│  │  Follow the in-app instructions │    │
│  │  to start via Wireless Debugging│    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Step 3: Authorize this app    │    │
│  │  Grant permission when prompted │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Status: Shizuku not running 🔴│    │
│  │  [Check Again]                 │    │
│  └─────────────────────────────────┘    │
│                                         │
└─────────────────────────────────────────┘
```

### 4.3 MainScreen（主页）

```
┌─────────────────────────────────────────┐
│  Mic Dot Disabler                       │
├─────────────────────────────────────────┤
│                                         │
│         🎤🚫                            │
│                                         │
│   Status: Shizuku Ready ✅ (ADB)        │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │                                 │    │
│  │     [ Disable Mic Dot ]        │    │
│  │                                 │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ✓ Mic dot has been disabled            │
│    successfully!                        │
│                                         │
└─────────────────────────────────────────┘
```

### 4.4 庆祝纸片（Confetti）动画

使用 Compose `Canvas` 绘制彩色纸片（drawable 代码绘制，无需额外资源）：

- **纸片形状**：小矩形和圆形，随机大小（4-10dp）、随机颜色（Material 主题色 + 绿色 + 金色）
- **动画参数**：
  - 个数：约 60-80 片
  - 初始位置：屏幕顶部分散
  - 运动：Y 轴自由落体（重力加速度），X 轴正弦摆动
  - 旋转：随机角速度
  - 透明度：逐渐消失，5-6 秒后完全透明
  - 缓动：使用 `Animatable` + `tween` 或自定义 `suspend` 函数
- **触发时机**：两条命令均执行成功（exitCode == 0）后立即触发
- **性能**：使用 `Canvas` 而非逐个 Composable，单次 `drawRect`/`drawCircle` 批量绘制

伪代码结构：

```kotlin
@Composable
fun ConfettiOverlay(isVisible: Boolean) {
    if (!isVisible) return

    data class ConfettiPiece(
        val x: Float, val y: Float,
        val w: Float, val h: Float,
        val color: Color, val rotation: Float,
        val xSpeed: Float, val ySpeed: Float,
        val rotationSpeed: Float
    )

    val pieces = remember { generateConfettiPieces(count = 70) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dt = progress.value
        pieces.forEach { piece ->
            val currentY = piece.y + piece.ySpeed * dt * canvasHeight
            val currentX = piece.x + piece.xSpeed * sin(dt * PI * 2) * 50
            val alpha = (1f - dt * dt).coerceAtLeast(0f)
            rotate(piece.rotation + piece.rotationSpeed * dt * 360) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(currentX, currentY),
                    size = Size(piece.w, piece.h)
                )
            }
        }
    }
}
```

### 4.5 动画过渡

- **页面切换**：使用 `AnimatedContent` + `fadeIn/fadeOut` 过渡，确保 SetupScreen ↔ MainScreen 切换流畅
- **按钮交互**：按下放大 95%，松开回弹（`animateFloatAsState` + `pointerInput`）
- **状态切换**：Shizuku 状态变化时使用 `Crossfade` 平滑过渡

---

## 5. 错误处理

### 5.1 错误分类

| 场景 | 处理方式 |
|------|----------|
| Shizuku 未安装 | 检测并提示，提供跳转链接 |
| Shizuku 未运行 | 提示用户开启无线调试并启动 Shizuku |
| 权限未授予 | 引导用户点击授权按钮 |
| Shizuku Binder 断开 | 监听 `OnBinderDeadListener`，立即回到 SetupScreen |
| 命令执行失败（非零退出码） | 在 MainScreen 显示完整 stdout + stderr，并提供复制按钮一键复制错误详情 |
| 命令执行抛出异常 | 显示异常 message + stacktrace 前 5 行 |
| 设备不支持（Android < 12） | 在启动时检测，显示升级提示 |
| `isPreV11()` 为 true | 提示用户升级 Shizuku 到最新版本 |

### 5.2 用户可见的错误信息格式

```
❌ Execution Failed
Command: cmd device_config put privacy camera_mic_icons_enabled false default
Exit code: 255
Output: (stdout/stderr content)

[当检测到 allowlist 错误时，自动附加]
NOTE: This device enforces a write-allowlist...
Your device requires root + LSPosed (e.g. GreenDotHide).
```

---

## 6. 文件结构

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── java/io/ame/micdotdisabler/
        │   ├── MainActivity.kt          # 入口 Activity
        │   ├── AppNavigation.kt          # 页面路由
        │   ├── shizuku/
        │   │   └── ShizukuManager.kt     # Shizuku 状态管理 + 命令执行
        │   ├── ui/
        │   │   ├── Screen.kt             # 屏幕状态定义
        │   │   ├── setup/
        │   │   │   └── SetupScreen.kt    # 引导页面
        │   │   ├── main/
        │   │   │   └── MainScreen.kt     # 主页
        │   │   ├── components/
        │   │   │   ├── ConfettiOverlay.kt # 庆祝纸片动画
        │   │   │   └── StatusBadge.kt    # 状态标签组件
        │   │   └── theme/
        │   │       ├── Color.kt          # (已有，可调整)
        │   │       ├── Type.kt           # (已有，可调整)
        │   │       └── Theme.kt          # (已有，可调整)
        │   └── util/
        │       └── ConfettiUtil.kt       # 纸片生成工具函数
        └── res/
            ├── values/
            │   ├── strings.xml           # 字符串资源
            │   ├── colors.xml            # (移除，Compose 中定义)
            │   └── themes.xml            # (保留基础主题)
            ├── drawable/
            │   └── ic_launcher_*.xml     # 启动图标 (已有)
            └── mipmap-anydpi/
                └── ic_launcher*.xml      # (已有)
```

---

## 7. 实现步骤

### Phase 1: 基础设施 (Infrastructure)
1. 在 `libs.versions.toml` 中添加 Shizuku 依赖版本
2. 在 `app/build.gradle.kts` 中添加 `shizuku-api` 和 `shizuku-provider` 依赖
3. 在 `AndroidManifest.xml` 中添加 `ShizukuProvider` 声明
4. 确认无任何非必要权限声明

### Phase 2: ShizukuManager (Core Logic)
1. 实现 `ShizukuManager` 类
2. 实现 Binder 生命周期监听
3. 实现权限检查与请求
4. 实现 `execCommand()` 方法（基于 `Shizuku.newProcess()`）
5. 实现 `disableMicDot()` 方法（串联两条命令）

### Phase 3: UI 实现
1. 实现 `Screen.kt` 状态定义
2. 实现 `SetupScreen.kt`
3. 实现 `MainScreen.kt`
4. 实现 `ConfettiOverlay.kt`
5. 实现 `StatusBadge.kt`
6. 实现 `AppNavigation.kt` 页面路由

### Phase 4: MainActivity 集成
1. 重构 `MainActivity.kt` 集成 ShizukuManager + AppNavigation
2. 处理 Activity 生命周期（注册/注销 Shizuku 监听器）

### Phase 5: 完善与测试
1. 字符串资源提取到 `strings.xml`
2. 更新应用图标
3. 真机测试（Shizuku 启动流程 + 权限授权 + 命令执行）
4. 边界情况测试（未安装 Shizuku、权限拒绝、命令失败）

---

## 8. 安全性考量

1. **最小权限原则**：不申请任何 Android 系统权限（仅声明 `ShizukuProvider` 所需的 `INTERACT_ACROSS_USERS_FULL` signature 级权限，该权限仅供系统签名应用使用，在此处仅作为 Provider 的保护级别，不实际授予本应用）。
2. **无 Root 依赖**：通过 Shizuku 的 ADB 模式运行（UID 2000），不需要设备 root。
3. **命令注入防护**：执行的命令完全硬编码，不拼接任何用户输入。
4. **无网络通信**：应用完全不访问网络，不上传任何数据。
5. **代码混淆**：Release 构建启用 ProGuard/R8。

---

## 9. 替代方案与风险

### 9.1 Android 版本兼容性（重要）

| Android 版本 | `device_config put` 可行性 | 说明 |
|---|---|---|
| **12–13** | ✅ 可用 | 无 allowlist 限制，命令完全生效 |
| **14+ (Pixel / 纯 AOSP)** | ❌ 不可用 | `camera_mic_icons_enabled` flag 被写入 allowlist 保护，shell UID 无法修改 |
| **14+ (Samsung OneUI)** | ✅ 可用 | Samsung 未启用该 allowlist |
| **14+ (Xiaomi HyperOS)** | ✅ 可用 | Xiaomi 未启用该 allowlist |

当检测到 allowlist 错误时，应用会在错误面板中显示清晰的指引信息，告知用户设备需要 Root + LSPosed 方案（如 GreenDotHide）。

### 9.2 `set_sync_disabled_for_tests` 的副作用
该命令阻止所有 device_config 同步，可能影响其他系统功能的配置更新。但这是一种温和的副作用——系统 OTA 时配置会被重新同步，不会造成永久性影响。

### 9.3 系统 OTA 后的行为
系统更新后，device_config 会被重置。用户需要重新执行一次禁用操作。这在 UI 中应有说明文字提示。

### 9.4 恢复功能（可选，非第一版）
可提供一个 "Restore Mic Dot" 按钮，执行：
```bash
adb shell cmd device_config put privacy camera_mic_icons_enabled true default
adb shell cmd device_config set_sync_disabled_for_tests none
```

---

## 10. 总结

本应用采用简洁的单 Activity + Compose 架构，通过 Shizuku API 代理执行 ADB Shell 命令。整体代码量预估在 500-800 行 Kotlin 代码内，无复杂依赖，易于维护。UI 遵循 Material Design 3 规范，提供流畅直观的用户体验。
