# Camera Stream Monitor - 国内应用商店上架完整指南

> **所有材料已准备在 `docs/` 目录下，按本指南操作即可提交**

---

## 一、通用提交材料清单

| 材料 | 文件 | 状态 |
|------|------|------|
| 应用图标 512x512 | `docs/icon-512.png` | ✅ 已生成 |
| 封面图/推广图 | `docs/feature-graphic.png` | ✅ 已生成 |
| 隐私政策（中文版） | `docs/privacy-policy-cn.html` | ✅ 已创建 |
| 应用介绍文案 | 见下方"二、应用介绍" | ✅ 已准备 |
| APK/AAB 安装包 | `app/build/outputs/apk/debug/app-debug.apk` 或 `app/build/outputs/bundle/release/app-release.aab` | ✅ 已构建 |
| 签名密钥 | `andwin-release.keystore`（本地备份，不上传） | ⚠️ 务必备份 |
| 应用截图 | 需要自己截取 | ⏳ 待完成 |

---

## 二、应用介绍文案

### 简短介绍（30 字以内）
```
闲置旧手机秒变专业监控摄像头。免费、无需额外设备。
```

### 完整介绍
```
【旧手机监控助手】家里有闲置的旧手机？别扔！装上本App，立刻变身专业监控摄像头。

📱 核心功能：
• 旧机再利用 — 任何安卓手机都能当监控用
• 多机同时管 — 一台手机同时监控多个摄像头
• 实时远程看 — RTMP/RTSP推流，随时随地手机/电脑看画面
• 24小时录制 — 后台持续录像，不漏掉任何画面
• 双摄同时录 — 前后摄像头一起录，无死角
• 免费不花钱 — 不需要买监控设备，零成本

💡 使用场景：
→ 家里老人小孩看护（旧手机放客厅，上班也能看）
→ 门口防盗监控（旧手机放门口，有人经过就录像）
→ 店铺安防监控（多台旧手机全方位覆盖）
→ 宠物看家（出门在外也能看宠物）
→ 仓库车库看守（免费24小时监控）

🌐 中英双语界面 | 无需注册登录 | 隐私数据不上传

让每一台旧手机都发挥价值！

技术特性：采用 CameraX 现代化摄像头框架 + Media3 ExoPlayer 专业播放引擎 + RootEncoder 高效音视频编码 + Material Design 3 现代界面设计

版本：1.0.0 | 开发者：AndWin
```

### 关键词（标签）
```
旧手机, 监控, 摄像头, 安防, 录制, 推流, RTMP, 远程看护, 免费, 家庭监控, 宠物看家
```

---

## 三、各平台详细提交指南

---

### 平台一：应用宝（腾讯）⭐ 最推荐

**网址**: https://open.qq.com/

#### 注册流程：
1. 用 QQ 号码登录
2. 选择"移动应用"
3. 填写开发者信息（个人开发者即可）
4. 实名认证（需要身份证）

#### 提交所需材料：

| 项目 | 填写内容 |
|------|---------|
| **应用名称** | 旧手机监控助手 |
| **包名** | com.andwin.video |
| **应用类型** | 工具 / 生活 |
| **应用简介** | 复制上方"简短介绍" |
| **应用介绍** | 复制上方"完整介绍" |
| **应用图标** | 上传 `docs/icon-512.png` |
| **应用截图** | 2-5 张（需自己截取） |
| **应用分类** | 工具 > 效率 / 生活实用 |
| **隐私政策 URL** | 见下方部署说明 |
| **测试账号** | 无需（无登录功能） |

#### 隐私政策 URL 获取方式：
```
方式 1: GitHub Pages (推荐)
→ 仓库 Settings → Pages → Source 选 main 分支的 /docs 目录
→ 几分钟后生效，URL 为:
https://nilgpt2024.github.io/Camera-Stream-Monitor/privacy-policy-cn.html

方式 2: Gitee Pages
→ 将仓库镜像到 Gitee → 开启 Pages 服务
→ 同样指向 docs/privacy-policy-cn.html

方式 3: 其他免费托管
→ Netlify / Vercel / Coding Pages 等
```

#### 广告变现（广点通）：
应用审核通过后：
1. 在 [腾讯广告](https://e.qq.com/) 注册
2. 创建应用 → 填写包名 `com.andwin.video`
3. 获取广告位 ID
4. 集成广点通 SDK（类似 AdMob 流程）
5. 开始获得收益

---

### 平台二：小米应用商店

**网址**: https://dev.mi.com/

#### 注册流程：
1. 注册小米账号
2. 登录开发者控制台
3. 选择"发布应用"
4. 个人开发者实名认证

#### 提交材料：

| 项目 | 内容 |
|------|------|
| 应用名称 | 旧手机监控助手 |
| 包名 | com.andwin.video |
| 分类 | 工具 / 生活实用 |
| 简介 | 复制上方文案 |
| 图标 | `docs/icon-512.png` |
| 截图 | 2-5 张 |
| 隐私政策 | `privacy-policy-cn.html` 的 URL |
| 目标 SDK | 34 |
| 最低 SDK | 26 |

#### 广告变现（米盟）：
- 在 [米盟](https://union.mi.com/) 注册
- 集成米盟广告 SDK
- 米盟 eCPM 通常较高，推荐优先接入

---

### 平台三：华为 AppGallery Connect

**网址**: https://developer.huawei.com/consumer/cn/

#### 特别优势：
- **2024年起免年费**（之前需要年费）
- 华为手机预装 AppGallery
- 海外也有市场覆盖

#### 注册流程：
1. 注册华为开发者账号
2. 进入 AppGallery Connect
3. 创建应用
4. 配置签名证书 SHA256 指纹

#### 提交材料：

| 项目 | 内容 |
|------|------|
| 应用名称 | Camera Stream Monitor |
| 包名 | com.andwin.video |
| 分类 | 工具 / 摄影 |
| 默认语言 | 中文（简体） |
| 简介 | 复制上方文案 |
| 图标 | `docs/icon-512.png` |
| 截图 | 2-5 张 |
| 隐私政策 | URL |
| 内容分级 | 3+（PG，家长指导） |
| 目标受众年龄 | 18+ 主要 |

#### 获取 SHA256 签名指纹：
```bash
# 在项目目录执行
keytool -list -v -keystore andwin-release.keystore -alias andwin
# 找到 "SHA256:" 行，复制指纹值
# 填写到华为控制台的签名证书配置中
```

---

### 平台四：OPPO 软件商店

**网址**: https://open.oppomobile.com/

#### 特点：
- OPPO + 一加用户量大
- 审核较快（通常 1 天）

#### 提交材料：
| 项目 | 内容 |
|------|------|
| 应用名称 | Camera Stream Monitor |
| 包名 | com.andwin.video |
| 分类 | 效率 / 摄影摄像 |
| 关键词 | 摄像头, 监控, 录制, 推流, RTMP |
| 简介 | 复制上方文案 |
| 图标 | `docs/icon-512.png` |
| 截图 | 2-5 张 |
| 隐私政策 | URL |

---

### 平台五：vivo 开放平台

**网址**: https://dev.vivo.com.cn/

#### 提交材料：
| 项目 | 内容 |
|------|------|
| 应用名称 | Camera Stream Monitor |
| 包名 | com.andwin.video |
| 分类 | 工具 / 摄影 |
| 简介 | 复制上方文案 |
| 图标 | `docs/icon-512.png` |
| 截图 | 2-5 张 |
| 隐私政策 | URL |

---

## 四、截图获取方法（必须手动完成）

### 方法 1：ADB 截图（最清晰）

```bash
# 1. 手机开启 USB 调试，连接电脑
adb devices

# 2. 安装 Debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. 打开应用，进入以下页面并截图：

# 截图1: 主界面
adb shell screencap -p /sdcard/screen_main.png
adb pull /sdcard/screen_main.png docs/screenshot-1-main.png

# 截图2: 监控预览界面
adb shell screencap -p /sdcard/screen_monitor.png
adb pull /sdcard/screen_monitor.png docs/screenshot-2-monitor.png

# 截图3: 正在录制状态
adb shell screencap -p /sdcard/screen_recording.png
adb pull /sdcard/screen_recording.png docs/screenshot-3-recording.png

# 截图4: 设置界面
adb shell screencap -p /sdcard/screen_settings.png
adb pull /sdcard/screen_settings.png docs/screenshot-4-settings.png

# 截图5: 录制文件列表
adb shell screencap -p /sdcard/screen_recordings.png
adb pull /sdcard/screen_recordings.png docs/screenshot-5-recordings.png
```

### 方法 2：手机直接截图
- 大部分安卓手机：**电源键 + 音量下键** 同时按 1-2 秒
- 华为/荣耀：**电源键 + 音量上键**
- 小米/红米：**电源键 + 音量下键**
- 截图后在相册中找到，用数据线传到电脑

### 截图要求：
- 格式：PNG 或 JPEG
- 数量：每个平台至少 **2 张**，建议 **5 张**
- 内容建议顺序：
  1. 主界面（摄像头列表 + 底部导航栏）
  2. 监控界面（实时预览画面）
  3. 录制中的状态
  4. 设置界面
  5. 录制文件列表

---

## 五、提交前检查清单

### 通用检查项：
- [ ] APK/AAB 已正式签名（非 debug 签名）
- [ ] 版本号和版本名称正确（当前 v1.0.0）
- [ ] 包名为 `com.andwin.video`
- [ ] 图标 512x512 PNG
- [ ] 至少 2 张应用截图
- [ ] 隐私政策 URL 可访问
- [ ] 应用介绍文字填写完整
- [ ] 应用分类选择正确

### 各平台特殊要求：
- [ ] **应用宝**: 实名认证已完成
- [ ] **小米**: 开发者资质审核通过
- [ ] **华为**: SHA256 签名指纹已配置
- [ ] **OPPO/vivo**: 开发者账号已激活

---

## 六、推荐提交顺序

```
第 1 步: 应用宝（用户量最大，审核约 1-2 天）
    ↓
第 2 步: 小米商店（米盟广告收益高）
    ↓
第 3 步: 华为 AppGallery（免年费，国内外覆盖）
    ↓
第 4 步: OPPO 商店（一加用户也覆盖）
    ↓
第 5 步: vivo 商店
    ↓
第 6 步: GitHub Releases（已有，作为备用下载渠道）
```

---

## 七、文件总览

```
f:\android\andwin\
├── docs/
│   ├── icon-512.png              ← 应用图标（所有平台通用）
│   ├── feature-graphic.png       ← 封面/推广图
│   ├── privacy-policy.html       ← 英文隐私政策（Google Play 用）
│   ├── privacy-policy-cn.html     ← 中文隐私政策（国内平台用）✨ 新增
│   ├── data-safety-guide.md      ← 数据安全声明参考
│   └── PUBLISH-GUIDE.md          ← 本文件
│
├── app/build/outputs/
│   ├── bundle/release/
│   │   └── app-release.aab      ← 正式签名 AAB（Google Play / 华为）
│   └── apk/debug/
│       └── app-debug.apk        ← 测试 APK（国内平台大多接受）
│
├── andwin-release.keystore       ← 签名密钥（备份！勿上传！）
│
└── README.md                     ← 项目文档
```

---

准备好截图后，按照以上指南逐个平台提交即可！所有文字材料和图标都已经准备好了。 🚀
