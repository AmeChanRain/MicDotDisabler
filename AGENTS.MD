# AGENTS.MD — Mic Dot Disabler 项目规范

## 项目身份

- **项目名称**: Mic Dot Disabler
- **包名**: `io.ame.micdotdisabler`
- **项目类型**: Android 应用（单 Activity, Jetpack Compose）
- **最低 SDK**: 31 (Android 12)
- **目标 SDK**: 34 (Android 14)
- **开发语言**: Kotlin 2.0+
- **UI 框架**: Jetpack Compose + Material 3 (Material You)
- **特殊依赖**: Shizuku API (dev.rikka.shizuku:api) + Shizuku Provider (dev.rikka.shizuku:provider)
- **参考设计文档**: `docs/DESIGN.md`

---

## 核心约束（非协商）

### 权限约束
1. **禁止**声明任何非绝对必要的 Android 权限。应用不申请 `RECORD_AUDIO`、`CAMERA`、`INTERNET`、`ACCESS_NETWORK_STATE` 等权限。
2. **禁止**申请 root 权限。所有特权操作通过 Shizuku ADB Shell UID (2000) 代理。
3. AndroidManifest 中仅允许存在：
   - `INTERACT_ACROSS_USERS_FULL` — ShizukuProvider 的 `android:permission` 属性值（用于保护 Provider，非授予本应用）

### Shizuku 依赖
4. 应用**必须**在 `AndroidManifest.xml` 中注册 `rikka.shizuku.ShizukuProvider`
5. 应用**必须**同时依赖 `dev.rikka.shizuku:api` 和 `dev.rikka.shizuku:provider`
6. Shizuku 版本锁定在 `12.2.0`，升级需在 `libs.versions.toml` 中统一变更
   - **为何不用 v13.x**：v13.1.x 已将 `Shizuku.newProcess()` 设为 private，要求使用 UserService + AIDL。本应用仅需执行两条简单命令，AIDL 方案复杂度不划算。v12.2.0 的 `newProcess` 虽标记 deprecated 但仍为 public，功能完全可用。
7. 禁止直接调用 `Runtime.exec()` 或 `ProcessBuilder` 执行特权命令——必须通过 `Shizuku.newProcess()`

### 命令约束
8. 执行的两条核心命令**完全硬编码**，不拼接任何用户输入：
   ```
   cmd device_config put privacy camera_mic_icons_enabled false default
   cmd device_config set_sync_disabled_for_tests persistent
   ```
9. 任何命令字符串修改必须经过代码审查，且更新 `docs/DESIGN.md` 中的对应说明

### 执行流程
10. 应用启动时**先**检查 Shizuku 状态，**再**决定展示哪个界面。不允许先展示 MainScreen 再弹出权限请求
11. 命令执行结果必须明确区分成功（exitCode == 0）与失败（exitCode != 0 或异常）
12. 失败时必须向用户展示完整的错误信息（exit code + stdout + stderr）

---

## 代码规范

### Kotlin
- 使用 Kotlin 官方编码规范（PascalCase 类名、camelCase 函数/变量、UPPER_SNAKE_CASE 常量）
- 优先使用 `val` 而非 `var`；不可变数据结构优先
- `sealed interface` 用于状态建模，`data class`/`data object` 用于状态值

### Compose
- `@Composable` 函数命名使用 PascalCase，且不以 `get`/`set` 开头
- Composable 函数的 `modifier` 参数默认为 `Modifier = Modifier`，放在参数列表第一位（可选参数）或最后（必选参数）
- 禁止在 Composable 函数中执行副作用——使用 `LaunchedEffect` / `SideEffect` / `DisposableEffect`
- 状态提升：可变的 UI 状态放在 ViewModel 或顶层 Composable 的 `remember { mutableStateOf() }` 中，子 Composable 通过回调通信

### 包结构
```
io.ame.micdotdisabler/         # 根包
  ├── MainActivity.kt           # 唯一 Activity
  ├── shizuku/                  # Shizuku 相关逻辑
  ├── ui/                       # Compose UI
  │   ├── Screen.kt             # 状态定义
  │   ├── setup/SetupScreen.kt
  │   ├── main/MainScreen.kt
  │   ├── components/           # 可复用组件
  │   └── theme/                # Material 3 主题
  └── util/                     # 工具函数
```

### 资源
- 所有面向用户的文本必须定义在 `res/values/strings.xml` 中（不使用硬编码字符串）
- 颜色定义在 Compose `Color.kt` 中（不使用 `res/values/colors.xml` 中的已有颜色）

---

## 文件职责边界

| 文件 | 职责 | 禁止 |
|------|------|------|
| `MainActivity.kt` | Activity 入口，setContent，生命周期管理 | 不可包含 UI 逻辑 |
| `ShizukuManager.kt` | Shizuku 状态查询、权限请求、命令执行 | 不可引用 Android View / Compose |
| `Screen.kt` | 定义 `AppState` sealed interface | 仅状态定义，无逻辑 |
| `SetupScreen.kt` | 引导用户安装/启动/授权 Shizuku | 不可直接调用 Shizuku API |
| `MainScreen.kt` | 展示按钮、触发命令、显示结果 | 不可直接调用 Shizuku API |
| `ConfettiOverlay.kt` | 纸片动画绘制与生命周期 | 仅动画 UI，无业务逻辑 |
| `StatusBadge.kt` | 显示 Shizuku 连接状态 Chip | 纯展示组件 |

---

## 兼容性说明

- Android 12–13：`device_config put privacy camera_mic_icons_enabled false default` 完全可用
- Android 14+ (Pixel / 纯 AOSP)：该 flag 受 allowlist 保护，shell UID 无法写入。应用会检测此错误并显示 Root/LSPosed 替代方案指引
- Android 14+ (Samsung OneUI / Xiaomi HyperOS 等 OEM ROM)：通常未启用 allowlist，命令可用

## 测试要点

- 确保 `ShizukuManager` 可在单元测试中通过 mock Shizuku 静态方法进行测试
- ConfettiOverlay 不应阻塞 UI 线程——验证在 60fps 条件下 70 个纸片不掉帧
- 在以下设备条件下验证：
  - Android 12 (API 31) — 最低支持版本
  - Android 13/14/15 — 主流版本
  - 有/无 Google Play Services 的设备

---

## 设计参考

详细设计见 `docs/DESIGN.md`。所有架构决策以此文档及本文档为权威来源。

---

## 变更日志

| 日期 | 变更内容 |
|------|----------|
| 2026-06-20 | 初始版本，定义项目约束与规范 |
