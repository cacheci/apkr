# APK 基础信息查看器（Kotlin Multiplatform）

一个使用 Kotlin Multiplatform 与 Compose Multiplatform 重写的桌面 APK 信息查看器。Windows 和 macOS 是两个独立应用目标，共享 APK 解析、状态模型和 Compose UI。

原 Tauri 2 + TypeScript + Rust 实现完整保留在 `legacy` 分支；`main` 分支仅包含 KMP 桌面实现。

## 功能

- 选择、拖拽或通过系统文件关联打开 APK
- 直接读取 APK/ZIP（包括由 JDK ZIP 实现支持的 ZIP64），无需 Android SDK
- 解析二进制 `AndroidManifest.xml` 和 `resources.arsc`
- 查看应用名、包名、版本、SDK、权限、组件、ABI、原生库、签名文件及文件数量
- 解析本地化应用名和常见位图、Vector Drawable、Adaptive Icon 信息
- 检测 Kotlin、Compose、Gradle、Coroutines、Room 和 native so 等技术特征
- 中英文界面以及浅色、深色、跟随系统主题
- 自动查找或手动配置 ADB，并通过 `adb install -r` 安装 APK
- Windows 与 macOS 独立入口、安装包格式、应用图标和 `.apk` 文件关联

## 工程结构

```text
shared/       KMP 共享模型、国际化、APK 解析和桌面服务
compose-ui/   Compose Multiplatform 共享桌面界面
windowsApp/   Windows 独立入口及 MSI/EXE 打包配置
macosApp/     macOS 独立入口及 DMG/PKG 打包配置
packaging/    Windows 与 macOS 原生打包资源
```

## 环境要求

- JDK 17 或更高版本
- 构建 Windows 安装包时使用 Windows；构建 macOS 安装包时使用 macOS

项目采用 Kotlin 2.4.20、Compose Multiplatform 1.12.1，并通过 Gradle Wrapper 固定使用 Gradle 9.7.1，无需另外安装 Gradle。

## 开发任务

以下命令仅供人工验证，迁移提交本身不自动执行：

```bash
./gradlew :macosApp:run
./gradlew :shared:desktopTest

# Windows PowerShell / CMD
gradlew.bat :windowsApp:run
gradlew.bat :shared:desktopTest
```

## 平台分发

```bash
# Windows 主机
gradlew.bat :windowsApp:packageMsi
gradlew.bat :windowsApp:packageExe

# macOS 主机
./gradlew :macosApp:packageDmg
./gradlew :macosApp:packagePkg
```

macOS 对外发布仍需配置开发者证书、公证和 stapling；Windows 对外发布建议配置代码签名证书。
