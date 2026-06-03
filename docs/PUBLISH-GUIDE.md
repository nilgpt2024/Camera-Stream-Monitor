# Camera Stream Monitor - Google Play 上架完整操作清单

---

## 已生成的资源文件（在 `docs/` 目录下）

| 文件 | 用途 | 状态 |
|------|------|------|
| `icon-512.png` | 应用图标 (512x512) | ✅ 已生成 |
| `feature-graphic.png` | 封面图 (1024x500) | ✅ 已生成 |
| `privacy-policy.html` | 隐私政策页面 | ✅ 已生成 |
| `data-safety-guide.md` | 数据安全声明填写指南 | ✅ 已生成 |

---

## 一、应用图标 ✅

**文件**: `docs/icon-512.png`
- 尺寸: 512x512 像素
- 格式: PNG（无透明度）
- **使用方式**: 在 Google Play Console → 应用信息 → 图标 → 上传此文件

> **提示**: Google Play 会自动从此图标生成所有需要的尺寸（48x48, 96x96, 144x144 等）

---

## 二、封面图 ✅

**文件**: `docs/feature-graphic.png`
- 尺寸: 1024x500 像素
- 格式: PNG 或 JPEG
- **使用方式**: 在 Google Play Console → 商店列表 → 主要图形 → 上传此文件

> **注意**: 这是商店展示页面的顶部大图，非常重要！

---

## 三、隐私政策 URL ✅

**方案 A：使用 GitHub Pages（推荐，免费）**

1. 将代码推送到 GitHub 后，隐私政策地址为：
   ```
   https://nilgpt2024.github.io/Camera-Stream-Monitor/privacy-policy.html
   ```

2. 启用 GitHub Pages：
   - 打开仓库 Settings → Pages
   - Source 选择 `main` 分支，`/docs` 目录
   - 保存后几分钟即可访问

**方案 B：使用其他托管**

将 `docs/privacy-policy.html` 部署到你自己的服务器或 Netlify/Vercel 等。

**填写位置**: Google Play Console → 政策 → 隐私政策 → 输入 URL

---

## 四、数据安全声明 ✅

**详细填写指南见**: `docs/data-safety-guide.md`

**快速摘要 - 在 Google Play Console 中勾选/填写**：

### 收集的数据：

| 数据类型 | 共享？ | 用途 | 必需？ |
|---------|-------|------|--------|
| 音频信息 | 否 | 应用功能 | 是 |
| 图像和视频 | 否 | 应用功能 | 是 |
| 文件和文档 | 否 | 应用功能 | 是 |

### 安全实践：
- ✅ 数据加密传输（推流时）
- ✅ 用户可删除数据
- ✅ 独立安全认证：无

---

## 五、内容分级 ⚠️ 需要你手动完成

### 操作步骤：

1. 打开 [IARC 内容分级问卷](https://www.iarc.org/)
2. 或直接在 Google Play Console 中完成（推荐）

### 你的应用应该这样回答：

| 问题 | 选择答案 |
|------|---------|
| **是否包含暴力内容？** | 否 |
| **是否包含性暗示内容？** | 否 |
| **是否包含赌博元素？** | 否 |
| **是否包含毒品/烟草？** | 否 |
| **是否包含粗俗语言？** | 否 |
| **是否包含儿童安全相关？** | 是（说明：应用需要摄像头权限但非儿童定向） |
| **是否收集用户地理位置？** | 否 |
| **是否收集用户联系信息？** | 否 |
| **是否允许用户之间互动？** | 否 |
| **是否允许用户分享信息？** | 否 |

### 预期分级结果：**Everyone / Everyone 10+**

> 这是一个工具类/摄影类应用，没有不当内容，应该获得最低年龄限制的分级。

---

## 六、目标受众设置 ⚠️ 需要你手动完成

### 在 Google Play Console 中填写：

| 设置项 | 推荐选择 |
|--------|---------|
| **目标受众年龄组** | 18+（主要）、13-17（次要） |
| **是否面向儿童？** | ❌ 否 |
| **儿童定向原因** | 不适用 |
| **核心用户群体** | 摄影爱好者、安防监控需求者、直播主播、开发者 |

### 说明：
- 本应用需要摄像头和麦克风权限
- 功能涉及视频录制和网络推流
- 不适合作为儿童应用推广

---

## 七、应用截图 ⚠️ 需要你从设备获取

这是唯一无法自动生成的资源。你需要：

### 方法 1：使用 Android 模拟器截图

```bash
# 1. 启动模拟器并安装 Debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 2. 打开应用，导航到各个页面

# 3. 截图命令
adb shell screencap -p /sdcard/screenshot_main.png
adb pull /sdcard/screenshot_main.png docs/screenshot-1-main.png

# 4. 对每个页面重复截图
# - 主界面（摄像头列表）
# - 监控界面（预览画面）
# - 设置界面
# - 录制列表界面
```

### 方法 2：真机截图

1. 安装 `app-debug.apk` 到手机
2. 在各个页面按 **电源键 + 音量下键** 截图
3. 用数据线传输到电脑

### 截图要求：

| 要求 | 规格 |
|------|------|
| **最少数量** | 2 张 |
| **最多数量** | 8 张 |
| **尺寸** | 手机屏幕尺寸（最长边 3840px 以内） |
| **格式** | PNG 或 JPEG（无透明度） |
| **建议顺序** | ①主界面 ②监控预览 ③录制中 ④设置页 ⑤播放器 |

### 截图内容建议：

```
截图 1: 主界面 - 显示摄像头列表和底部导航栏
截图 2: 监控界面 - 实时摄像头预览画面
截图 3: 监控界面 - 正在录制的状态
截图 4: 监控界面 - 推流进行中的状态
截图 5: 设置界面 - 显示各种配置选项
```

---

## 八、完整的 Google Play Console 发布流程

```
步骤 1: 注册开发者账号 ($25)
    ↓
步骤 2: 创建新应用
    ↓
步骤 3: 填写商店列表（Store Listing）
    ├─ 应用名称: Camera Stream Monitor
    ├─ 简短描述: 专业视频监控与推流工具...
    ├─ 完整描述: （复制 README 内容）
    ├─ 📎 上传图标: docs/icon-512.png
    ├─ 📎 上传封面图: docs/feature-graphic.png
    └─ 📎 上传截图: （需要你自己截）
    ↓
步骤 4: 内容分级（Content Rating）
    └─ 完成 IARC 问卷（约 5 分钟）
    ↓
步骤 5: 数据安全（Data Safety）
    └─ 按照 data-safety-guide.md 填写
    ↓
步骤 6: 目标受众和内容
    ├─ 目标受众: 18+ 主要
    └─ 面向儿童: 否
    ↓
步骤 7: 设置定价和分发范围
    ├─ 定价: 免费
    └─ 国家: 全部可用国家
    ↓
步骤 8: 上传 AAB 包
    └─ 上传: app/build/outputs/bundle/release/app-release.aab
    ↓
步骤 9: 提交审核
    └─ 通常 1-3 天通过
    ↓
🎉 应用上线！
```

---

## 九、文件总览

```
f:\android\andwin\
├── docs/
│   ├── icon-512.png              ← 应用图标 (上传到 GP)
│   ├── feature-graphic.png       ← 封面图 (上传到 GP)
│   ├── privacy-policy.html       ← 隐私政策 (部署后填 URL)
│   └── data-safety-guide.md      ← 数据安全填写参考
│
├── app/build/outputs/
│   ├── bundle/release/
│   │   └── app-release.aab      ← 上架包 (上传到 GP)
│   └── apk/debug/
│       └── app-debug.apk        ← 测试用 APK
│
└── andwin-release.keystore       ← 签名密钥 (务必备份!)
```

---

**准备好以上所有材料后，就可以去 Google Play Console 提交发布了！**
