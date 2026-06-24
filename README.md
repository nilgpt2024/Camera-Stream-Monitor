# Camera Stream Monitor / 摄像头流媒体监控

[![Platform](https://img.shields.io/badge/Platform-Android-green)](https://www.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%2B-orange)](https://developer.android.com/about/versions)
[![License](https://img.shields.io/badge/License-MIT-red)](LICENSE)

**English** | [中文](#中文)

---

## 📸 Project Overview / 项目简介

Camera Stream Monitor is a powerful Android application for real-time video streaming, monitoring, and recording with AI-powered detection capabilities. It provides comprehensive camera management features with support for multiple streaming protocols, cloud storage synchronization, and intelligent automation.

摄像头流媒体监控是一款功能强大的 Android 应用程序，用于实时视频流、监控和录制，具备 AI 智能检测能力。它提供全面的摄像头管理功能，支持多种流媒体协议、云存储同步和智能自动化。

---

## ✨ Features / 功能特性

### 🎥 Core Features / 核心功能

- **Multi-Camera Support / 多摄像头支持**
  - Front and rear camera management
  - 前置和后置摄像头管理
  - Dual camera simultaneous recording (front + back)
  - 双摄同时录制模式
  - Camera switching and configuration
  - 摄像头切换与配置

- **Real-time Streaming / 实时推流**
  - RTMP streaming protocol support
  - RTMP 推流协议支持
  - RTSP server for local streaming
  - RTSP 本地流媒体服务
  - SRT protocol support (via RootEncoder)
  - SRT 协议支持（通过 RootEncoder）
  - Direct P2P streaming mode (no server required)
  - 直连 P2P 推流模式（无需服务器）
  - UDP protocol support
  - UDP 协议支持

- **Video Recording / 视频录制**
  - High-quality video recording
  - 高质量视频录制
  - Background recording service
  - 后台录制服务
  - Recording management and playback
  - 录制管理和回放
  - Photo capture support
  - 拍照功能支持

- **Live Monitoring / 实时监控**
  - Real-time video preview
  - 实时视频预览
  - Stream status monitoring
  - 推流状态监控
  - Network quality indicators
  - 网络质量指示器
  - Time watermark overlay
  - 时间水印叠加显示

### 🤖 AI Detection Features / AI 检测功能

- **Face Detection / 人脸检测**
  - MediaPipe FaceLandmarker integration
  - 集成 MediaPipe FaceLandmarker
  - Facial landmark detection (478 landmarks)
  - 面部关键点检测（478个特征点）
  - Multi-face tracking support
  - 多人脸追踪支持
  - Applications: beauty filters, attention detection
  - 应用场景：美颜滤镜、注意力检测

- **Hand Detection / 手部检测**
  - MediaPipe HandLandmarker integration
  - 集成 MediaPipe HandLandmarker
  - Single/dual hand recognition
  - 单手/双手识别
  - 21 hand landmarks per hand
  - 每只手21个手部关键点
  - Voice alert on dual-hand detection
  - 双手检测语音报警
  - Auto-trigger recording/streaming
  - 自动触发录制/推流

- **Pose Detection / 姿态检测**
  - MediaPipe PoseLandmarker integration
  - 集成 MediaPipe PoseLandmarker
  - Full-body 33 skeleton keypoints
  - 全身33个骨骼关键点
  - Real-time pose overlay visualization
  - 实时姿态叠加可视化
  - Fitness motion analysis support
  - 健身动作分析支持

### 🔧 Advanced Features / 高级功能

- **Cloud Storage Sync / 云存储同步**
  - WebDAV protocol support (NAS, Nextcloud, etc.)
  - WebDAV 协议支持（NAS、Nextcloud等）
  - Auto-upload recordings after completion
  - 录制完成后自动上传
  - Manual batch upload support
  - 支持手动批量上传
  - Remote file management
  - 远程文件管理

- **Smart Automation / 智能自动化**
  - Auto-record when target detected
  - 检测到目标自动录制
  - Auto-stream when target detected
  - 检测到目标自动推流
  - Configurable stop delay (prevent false triggers)
  - 可配置停止延迟（防止误触发）
  - Custom trigger conditions
  - 自定义触发条件

- **Stream Protocols / 流媒体协议**
  - RTMP (Real-Time Messaging Protocol)
  - RTSP (Real Time Streaming Protocol)
  - SRT (Secure Reliable Transport)
  - UDP (User Datagram Protocol)
  - HLS / DASH playback support
  - HLS / DASH 播放支持

- **User Experience / 用户体验**
  - Bilingual interface (Chinese/English)
  - 双语界面（中文/英文）
  - Material Design 3 UI
  - Material Design 3 界面
  - Background recording with notification control
  - 后台录制与通知控制
  - Screen-off continuous recording
  - 熄屏持续录制
  - Web player page generation
  - 网页播放器生成

### 🎨 User Interface / 用户界面

- Material Design 3 interface
- Material Design 3 界面
- Bottom navigation for easy access
- 底部导航栏便于操作
- Real-time status indicators
- 实时状态指示器
- Responsive layout design (portrait + landscape)
- 响应式布局设计（竖屏+横屏）
- Glass morphism style panels
- 毛玻璃风格面板

---

## 📱 Screenshots / 应用截图

### 1. Main Interface / 主界面 — 摄像头列表
<p align="center">
<img src="docs/screenshot/screenshot-1.png" width="300" alt="主界面"/>
</p>

Camera list with device information — 前置/后置摄像头管理，实时状态显示

### 2. Player / 播放器 — 远程观看
<p align="center">
<img src="docs/screenshot/screenshot-2.png" width="300" alt="播放器"/>
</p>

Stream player with multi-protocol support (RTMP / RTSP / HLS / DASH) — 支持多种流媒体协议输入

### 3. Settings / 设置
<p align="center">
<img src="docs/screenshot/screenshot-3.png" width="300" alt="设置"/>
</p>

Video quality, storage, streaming, WebDAV & AI settings — 视频/存储/推流/WebDAV/AI 配置

### 4. Recordings / 录制记录
<p align="center">
<img src="docs/screenshot/screenshot-4.png" width="300" alt="录制记录"/>
</p>

Recording management with WebDAV upload — 录制文件管理与云存储上传

---

## 🚀 Getting Started / 快速开始

### Prerequisites / 环境要求

- **Android Studio**: Hedgehog or later
- **Android Studio**: Hedgehog 或更高版本
- **Min SDK**: Android 8.0 (API level 26)
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API level 34)
- **Target SDK**: Android 14 (API 34)
- **Gradle**: 8.0+
- **JDK**: 17 or higher

### Installation / 安装步骤

#### Clone the Repository / 克隆仓库

```bash
git clone https://github.com/nilgpt2024/Camera-Stream-Monitor.git
cd Camera-Stream-Monitor
```

#### Open in Android Studio / 在 Android Studio 中打开

1. Open Android Studio
   打开 Android Studio
2. Select "Open an existing project"
   选择"打开现有项目"
3. Navigate to the cloned directory
   导航到克隆的目录
4. Wait for Gradle sync to complete
   等待 Gradle 同步完成

#### Build the Project / 构建项目

```bash
# Debug build / 调试版本构建
./gradlew assembleDebug

# Release build / 发布版本构建
./gradlew assembleRelease
```

#### Install on Device / 安装到设备

```bash
# Install debug APK / 安装调试 APK
./gradlew installDebug

# Or install release APK / 或安装发布 APK
./gradlew installRelease
```

---

## 📖 Usage Guide / 使用指南

### Basic Operations / 基本操作

#### 1. Add Camera / 添加摄像头

1. Tap the "+" button in bottom navigation
   点击底部导航栏的"+"按钮
2. Configure camera settings (name, ID, etc.)
   配置摄像头设置（名称、ID等）
3. Save configuration
   保存配置

#### 2. Start Monitoring / 开始监控

1. Select camera from list
   从列表中选择摄像头
2. Tap to enter monitor interface
   点击进入监控界面
3. Preview video stream
   预览视频流

#### 3. Start Streaming / 开始推流

**Direct Mode (Recommended) / 直连模式（推荐）**
1. In monitor interface, tap "Stream" button
   在监控界面，点击"推流"按钮
2. Select "Direct Mode" (P2P, no server needed)
   选择"直连模式"（P2P，无需服务器）
3. Other devices connect via VLC or this app's player
   其他设备通过 VLC 或本应用播放器连接

**RTMP Mode / RTMP 模式**
1. In monitor interface, tap "Stream" button
   在监控界面，点击"推流"按钮
2. Select "RTMP Mode"
   选择"RTMP 推流模式"
3. Enter stream URL (RTMP address)
   输入推流地址（RTMP 地址）
4. Configure stream parameters (quality, bitrate)
   配置推流参数（质量、比特率）
5. Tap "Start" to begin streaming
   点击"开始"启动推流

#### 4. Record Video / 录制视频

1. In monitor interface, tap "Record" button
   在监控界面，点击"录制"按钮
2. Choose recording mode: single or dual camera
   选择录制模式：单摄或双摄
3. Tap "Start" to begin recording
   点击"开始"启动录制
4. Tap "Stop" when finished
   完成后点击"停止"

#### 5. Enable AI Detection / 启用 AI 检测

1. Go to Settings → Advanced Features (AI Detection)
   进入设置 → 高级功能（AI 检测）
2. Enable desired detection type:
   启用所需的检测类型：
   - Face Detection (beauty filters, attention)
     人脸检测（美颜滤镜、注意力）
   - Hand Detection (gesture control, auto-trigger)
     手部检测（手势控制、自动触发）
   - Pose Detection (fitness, motion analysis)
     姿态检测（健身、动作分析）
3. Return to monitor interface and tap "Detect" button
   返回监控界面并点击"检测"按钮
4. Configure auto-trigger settings if needed
   如需要，配置自动触发设置

### Configuration / 配置说明

#### Stream URL Examples / 推流地址示例

```bash
# RTMP Example / RTMP 示例
rtmp://your-server.com/live/stream_key

# RTSP Example / RTSP 示例
rtsp://localhost:8554/live

# SRT Example / SRT 示例
srt://your-server.com:9998?streamid=live/stream_key

# Direct Mode URL (auto-generated) / 直连模式地址（自动生成）
rtsp://[device-ip]:8554/live
```

#### WebDAV Configuration Examples / WebDAV 配置示例

```bash
# Synology NAS / 群晖 NAS
https://nas-ip:5005

# QNAP NAS / 威联通 NAS
https://nas-ip:5006

# Nextcloud
https://nc.example.com/remote.php/dav/files/username/

# Nutstore (Jianguoyun) / 坚果云
https://dav.jianguoyun.com/dav/
```

#### Recommended Settings / 推荐设置

- **Video Resolution**: 1080p or 720p
- **视频分辨率**: 1080p 或 720p
- **Frame Rate**: 30 fps
- **帧率**: 30 fps
- **Bitrate**: 2000-4000 kbps
- **比特率**: 2000-4000 kbps
- **Audio Sample Rate**: 44100 Hz
- **音频采样率**: 44100 Hz
- **Auto-stop Delay**: 3-5 seconds (for AI auto-trigger)
- **自动停止延迟**: 3-5秒（用于 AI 自动触发）

---

## 🏗️ Project Structure / 项目结构

```
Camera-Stream-Monitor/
├── app/
│   ├── src/main/
│   │   ├── java/com/andwin/video/
│   │   │   ├── MainActivity.kt              # Main activity / 主活动
│   │   │   ├── VideoMonitorApp.kt           # Application class / 应用类
│   │   │   ├── MonitorActivity.kt           # Camera monitor UI / 监控界面
│   │   │   ├── PlayerActivity.kt            # Stream player UI / 播放器界面
│   │   │   ├── SettingsActivity.kt          # Settings UI / 设置界面
│   │   │   ├── RecordingsActivity.kt        # Recordings list UI / 录制列表
│   │   │   ├── CameraListAdapter.kt         # Camera list adapter / 列表适配器
│   │   │   │
│   │   │   ├── camera/
│   │   │   │   └── CameraManager.kt         # Camera management / 摄像头管理
│   │   │   │
│   │   │   ├── model/
│   │   │   │   └── CameraConfig.kt          # Data model / 数据模型
│   │   │   │
│   │   │   ├── player/
│   │   │   │   └── StreamPlayer.kt          # Video player / 视频播放器
│   │   │   │
│   │   │   ├── recorder/
│   │   │   │   └── VideoRecorder.kt         # Video recorder / 视频录制器
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── StreamService.kt         # Streaming service / 推流服务
│   │   │   │   └── RecordService.kt         # Recording service / 录制服务
│   │   │   │
│   │   │   ├── streamer/
│   │   │   │   ├── DirectStreamServer.kt    # P2P stream server / 直连服务器
│   │   │   │   ├── StreamPublisher.kt       # Stream publisher / 推流发布者
│   │   │   │   └── TimeWatermarkView.kt     # Time watermark view / 时间水印视图
│   │   │   │
│   │   │   ├── webdav/
│   │   │   │   └── WebDavClient.kt          # WebDAV client / WebDAV 客户端
│   │   │   │
│   │   │   ├── detection/                   # AI Detection / AI 检测模块
│   │   │   │   ├── FaceDetector.kt          # Face detector / 人脸检测器
│   │   │   │   ├── HandDetector.kt          # Hand detector / 手部检测器
│   │   │   │   ├── PoseDetector.kt          # Pose detector / 姿态检测器
│   │   │   │   ├── DetectionOverlayView.kt  # Detection overlay / 检测叠加层
│   │   │   │   └── ModelDownloadManager.kt  # Model download manager / 模型下载管理
│   │   │   │
│   │   │   └── utils/
│   │   │       ├── Helpers.kt               # Utility functions / 工具函数
│   │   │       └── LocaleHelper.kt           # Localization helper / 本地化辅助
│   │   │
│   │   ├── assets/                          # ML models / ML 模型文件
│   │   │   ├── face_landmarker.task         # Face detection model / 人脸检测模型
│   │   │   ├── hand_landmarker.task         # Hand detection model / 手部检测模型
│   │   │   └── pose_landmarker.task         # Pose detection model / 姿态检测模型
│   │   │
│   │   └── res/                             # Resources / 资源文件
│   │
│   └── build.gradle.kts                     # App build config / 应用构建配置
│
├── RootEncoder/                             # Custom RootEncoder fork / 自定义 RootEncoder 分支
│
├── docs/
│   ├── screenshot/                          # App screenshots / 应用截图
│   ├── CHINA-STORES-GUIDE.md                # China app store guide / 国内应用商店指南
│   ├── PUBLISH-GUIDE.md                     # Publishing guide / 发布指南
│   ├── data-safety-guide.md                 # Data safety guide / 数据安全指南
│   ├── privacy-policy.html                  # Privacy policy (EN) / 隐私政策（英文）
│   └── privacy-policy-cn.html               # Privacy policy (CN) / 隐私政策（中文）
│
├── gradle/
│   └── libs.versions.toml                   # Version catalog / 版本目录
├── build.gradle.kts                         # Project build config / 项目构建配置
├── settings.gradle.kts                      # Project settings / 项目设置
├── gradle.properties                        # Gradle properties / Gradle 属性
└── README.md                                # This file / 本文件
```

---

## 🛠️ Technology Stack / 技术栈

### Core Dependencies / 核心依赖

| Library | Version | Description | 说明 |
|---------|---------|-------------|------|
| **CameraX** | 1.3.4 | Modern camera API | 现代化摄像头 API |
| **Media3 ExoPlayer** | 1.3.1 | Video player engine | 视频播放引擎 |
| **RootEncoder** | 2.4.6 | Audio/video encoder | 音视频编码器 |
| **RTSP Server** | 1.2.8 | Local RTSP server | 本地 RTSP 服务器 |
| **MediaPipe Tasks Vision** | 0.10.21 | AI detection models | AI 检测模型 |
| **OkHttp** | 4.12.0 | HTTP client | HTTP 客户端 |
| **Gson** | 2.10.1 | JSON parser | JSON 解析器 |
| **Coroutines** | 1.7.3 | Async programming | 异步编程库 |

### AI Models / AI 模型

| Model | File Size | Purpose | 用途 |
|-------|-----------|---------|------|
| **Face Landmarker** | ~10 MB | Face detection & landmarks | 人脸检测与关键点 |
| **Hand Landmarker** | ~20 MB | Hand gesture recognition | 手势识别 |
| **Pose Landmarker** | ~35 MB | Full-body pose estimation | 全身姿态估计 |

### Development Tools / 开发工具

- **Language**: Kotlin
- **语言**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **构建系统**: Gradle + Kotlin DSL
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM + Clean Architecture
- **架构**: MVVM + 清洁架构
- **UI Framework**: ViewBinding + Material Design 3
- **UI 框架**: ViewBinding + Material Design 3
- **Version Code**: 100
- **版本号**: 1.0.0

---

## 🔧 Development / 开发说明

### Build Variants / 构建变体

```bash
# Debug version (for development) / 调试版本（用于开发）
./gradlew assembleDebug

# Release version (for production) / 发布版本（用于生产）
./gradlew assembleRelease
```

### Code Style / 代码规范

- Follow Kotlin official coding conventions
- 遵循 Kotlin 官方编码规范
- Use meaningful variable and function names
- 使用有意义的变量和函数名
- Add comments for complex logic
- 为复杂逻辑添加注释
- Maintain consistent formatting
- 保持一致的格式化

### Contributing / 贡献指南

We welcome contributions! Please follow these steps:

我们欢迎贡献！请遵循以下步骤：

1. Fork the repository
   Fork 本仓库
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
   创建功能分支
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
   提交更改
4. Push to the branch (`git push origin feature/AmazingFeature`)
   推送到分支
5. Open a Pull Request
   创建 Pull Request

---

## ❓ FAQ / 常见问题

### Q: What permissions does this app need?
问：这个应用需要什么权限？

**A:** The app requires the following permissions:
**答：**应用需要以下权限：
- `CAMERA` - Access camera hardware / 访问摄像头硬件
- `RECORD_AUDIO` - Record audio / 录制音频
- `INTERNET` & `ACCESS_NETWORK_STATE` - Network access for streaming / 网络访问用于推流
- `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` - Save/read recordings / 保存/读取录制文件
- `MANAGE_EXTERNAL_STORAGE` - Full storage access (Android 11+) / 完整存储访问（Android 11+）
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CAMERA` / `FOREGROUND_SERVICE_MICROPHONE` - Background services / 后台服务
- `POST_NOTIFICATIONS` (Android 13+) - Show notifications / 显示通知
- `WAKE_LOCK` - Prevent sleep during recording / 录制期间防止休眠

### Q: Which streaming protocols are supported?
问：支持哪些流媒体协议？

**A:** The app supports:
**答：**应用支持：
- **Direct P2P Mode** (recommended) - No server needed, works on local WiFi
  **直连 P2P 模式**（推荐）- 无需服务器，本地 WiFi 即可工作
- **RTMP** (for live streaming to servers like SRS, nginx-rtmp)
  **RTMP**（用于直播推流到 SRS、nginx-rtmp 等服务器）
- **RTSP** (for local streaming and IP cameras)
  **RTSP**（用于本地流媒体和 IP 摄像头）
- **SRT** (for secure transmission)
  **SRT**（用于安全传输）
- **UDP** (for low-latency scenarios)
  **UDP**（用于低延迟场景）
- **Playback**: RTMP / RTSP / HLS / DASH / Local files
  **播放**: RTMP / RTSP / HLS / DASH / 本地文件

### Q: What is the minimum Android version required?
问：最低需要什么版本的 Android？

**A:** The app requires Android 8.0 (API level 26) or higher.
**答：**应用需要 Android 8.0 (API 26) 或更高版本。

### Q: How do I configure stream quality?
问：如何配置推流质量？

**A:** You can adjust stream quality in the settings menu:
**答：**你可以在设置菜单中调整推流质量：
- Video resolution (720p, 1080p)
- 视频分辨率（720p、1080p）
- Frame rate (15, 24, 30 fps)
- 帧率（15、24、30 fps）
- Bitrate (1000-8000 kbps)
- 比特率（1000-8000 kbps）
- Audio enable/disable
- 音频启用/禁用

### Q: What is WebDAV and how do I use it?
问：什么是 WebDAV？如何使用？

**A:** WebDAV is a protocol for remote file management. You can configure it in Settings → WebDAV Cloud Storage:
**答：**WebDAV 是远程文件管理协议。可以在 设置 → WebDAV 云存储 中配置：
- Supported: Synology NAS, QNAP, Nextcloud, Nutstore, etc.
  支持：群晖 NAS、威联通、Nextcloud、坚果云等
- Features: Auto-upload, manual upload, remote file browsing/deletion
  功能：自动上传、手动上传、远程文件浏览/删除
- Requires: Server address, username, password
  需要：服务器地址、用户名、密码

### Q: How does AI detection work?
问：AI 检测如何工作？

**A:** The app uses Google's MediaPipe library for on-device AI inference:
**答：**应用使用 Google 的 MediaPipe 库进行端侧 AI 推理：
- **First use**: Download AI models (~65MB total) with one click
  **首次使用**: 一键下载 AI 模型（总计约 65MB）
- **Models run locally**: No data uploaded to cloud
  **模型本地运行**: 数据不上传云端
- **Supported detections**: Face, Hand, Pose
  **支持的检测**: 人脸、手部、姿态
- **Auto-trigger**: Can automatically start/stop recording or streaming when targets are detected
  **自动触发**: 检测到目标时可自动开始/停止录制或推流

---

## 📄 License / 许可证

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件。

---

## 🙏 Acknowledgments / 致谢

- **[CameraX](https://developer.android.com/camera)** - Google's modern camera solution
- Google 的现代化摄像头解决方案
- **[ExoPlayer/Media3](https://github.com/google/ExoPlayer)** - Google's media player library
- Google 的媒体播放器库
- **[RootEncoder](https://github.com/pedroSG94/RootEncoder)** - Powerful audio/video encoding library
- 强大的音视频编码库
- **[MediaPipe](https://mediapipe.dev/)** - Google's on-device ML solution for AI detection
- Google 的端侧机器学习解决方案，用于 AI 检测
- **[Android Developers](https://developer.android.com)** - Official Android development resources
- Android 官方开发资源

---

## 📞 Contact / 联系方式

- **Project Repository**: https://github.com/nilgpt2024/Camera-Stream-Monitor
- **项目仓库**: https://github.com/nilgpt2024/Camera-Stream-Monitor
- **Issues**: [Submit issues here](https://github.com/nilgpt2024/Camera-Stream-Monitor/issues)
- **问题反馈**: [在此提交问题](https://github.com/nilgpt2024/Camera-Stream-Monitor/issues)

---

## 🌟 Star History / 星标历史

If this project helps you, please give it a ⭐!

如果这个项目对你有帮助，请给它一个 ⭐！

<a href="https://github.com/nilgpt2024/Camera-Stream-Monitor/stargazers">
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=nilgpt2024/Camera-Stream-Monitor&type=Date" />
</a>

---

**Made with ❤️ using Kotlin & Android**

**使用 Kotlin 和 Android 制作 ❤️**

---

## 中文

> 以上内容为中文版说明。如需查看英文版，请返回顶部。

### 项目概述

摄像头流媒体监控是一款专业的 Android 视频监控应用，具备 AI 智能检测能力。主要功能包括：

- **实时视频预览**：支持前置和后置摄像头的实时画面显示，双摄同时录制
- **多协议推流**：支持 RTMP、RTSP、SRT、UDP 以及直连 P2P 模式（无需服务器）
- **高清录制**：支持高质量视频录制、后台录制服务和熄屏持续录制
- **AI 智能检测**：集成 MediaPipe，支持人脸检测、手部识别、姿态分析
- **智能自动化**：检测到目标可自动触发录制或推流，支持自定义触发条件
- **云存储同步**：支持 WebDAV 协议，可自动上传到 NAS、Nextcloud 等
- **现代界面**：采用 Material Design 3 设计语言，支持中英双语切换

### 适用场景

- 家庭安防监控（配合 AI 人脸/姿态检测）
- 直播推流（RTMP/直连模式）
- 视频会议录制
- 远程监控（P2P 直连，无需公网服务器）
- 教育培训录像（配合 AI 注意力检测）
- 健身动作分析（AI 姿态检测）
- 手势控制交互（AI 手部检测）

### 技术亮点

- 使用 CameraX 实现现代化的摄像头控制，支持前后双摄同时工作
- 采用 Media3 ExoPlayer 进行专业级视频播放，支持多种流媒体协议
- 基于 RootEncoder 实现高效音视频编码，支持多种推流协议
- 集成 Google MediaPipe 实现端侧 AI 推断，保护用户隐私
- 使用 Kotlin Coroutines 实现异步编程，保证流畅的用户体验
- 支持 WebDAV 云存储协议，兼容主流 NAS 和网盘服务
- 遵循 MVVM 架构模式，代码结构清晰，易于维护和扩展

### 新增功能 (v1.0.0)

相比早期版本，v1.0.0 新增以下重要功能：

1. **AI 检测系统**：人脸、手部、姿态三种检测模式，支持可视化叠加显示
2. **P2P 直连推流**：无需服务器，局域网内直接传输视频流
3. **WebDAV 云同步**：录制文件自动备份到 NAS 或云存储
4. **智能自动化**：AI 检测到目标后自动开始/停止录制或推流
5. **双摄模式**：前置和后置摄像头同时录制
6. **后台持续录制**：应用切到后台或熄屏后继续录制
7. **时间水印**：在视频上叠加实时时间戳显示
8. **多语言支持**：中文/英文界面切换
9. **网页播放器**：生成 HTML 页面，浏览器可直接观看直播流

---

<div align="center">

**感谢使用 Camera Stream Monitor！**

**Thanks for using Camera Stream Monitor!**

⭐ 如果这个项目对你有帮助，请给个星标支持一下！⭐

</div>
