# PickCat for Android

[English](README.md) | 中文

`pickcat.dao3.fun` 的第三方 Android 客户端，使用 Kotlin、Jetpack Compose 和 Material 3。

> 非官方项目，与 PickCat 站点运营方无隶属关系。登录通过站点官方页面完成，账号密码和验证码不会经过本项目服务器。

## 功能

- 首页推荐流与最新流
- 论坛、分类、帖子详情
- 搜索帖子、作品、用户和标签
- 发帖、回复、Markdown 工具栏、草稿
- 作品列表、作品详情、Codemao 作品播放器
- 用户主页、合集、徽章、贡献图、动态
- 通知中心、后台系统通知、通知分类
- 收藏、点赞、关注、个人图盘和媒体库
- 个人资料编辑、隐私列表开关、徽章展示设置
- 深色主题、莫奈动态取色、大屏 NavigationRail 和双栏布局
- Android 13+ 通知权限与 WorkManager 后台同步

## 技术栈

- Kotlin 2.2.10
- AGP 9.5.1
- Gradle 9.9.1
- compileSdk 36，minSdk 26
- Jetpack Compose + Material 3
- OkHttp + kotlinx.serialization
- DataStore
- WorkManager

模块：

```text
app/                  Activity、导航、功能页面和 ViewModel
core/network/         API、模型、OkHttp、CookieJar、上传
core/data/            Session、偏好设置、草稿、AppContainer
core/ui/              Material 3 主题、通用组件、HTML 原生渲染
```

依赖注入使用轻量 `AppContainer`，没有引入 Hilt、Room 或 Paging 3。

## 构建

需要 JDK 25 或更高版本、Android SDK Platform 36。

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 指向本机 Android SDK：

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

仓库不提交 APK、构建产物、`local.properties` 或截图。Release APK 可在 GitHub Releases 或 CI Artifacts 获取。

## 登录

站点登录依赖阿里云滑块验证码。本项目嵌入官方登录页，不重画登录表单，也不接入验证码 SDK。

流程：

1. 未登录时显示官方登录 WebView。
2. 用户在官方页面完成账号密码和滑块验证。
3. 客户端读取 WebView Cookie，并同步到持久化 OkHttp CookieJar。
4. 调用 `GET /session` 确认登录态。
5. 退出登录同时清理 WebView 和 OkHttp Cookie。

账号密码不会写入本地存储。

## 内容审核

发帖和回复提交到 `POST /api/v1/posts` 后会返回 `202 Accepted`，状态为 `PENDING_PROVIDER`。审核通过前内容不会展示，客户端会提示“已提交审核”。

## 后台通知

- WorkManager 最短 15 分钟周期检查
- 应用启动时立即检查一次
- Android 13+ 需要通知权限
- 设置中可以关闭后台通知
- 首次同步只建立基线，不推送历史通知

## 已知限制

- 第三方接口没有正式文档，站点改版可能导致功能失效
- 头像和主页背景上传、账号安全、编程猫作品同步等设置尚未完全实现
- 复杂 HTML 内容使用原生子集渲染，不如浏览器完整
- 第三方播放器由 Codemao 提供

更详细的官网功能对照和开发审计：

- [SITE-PARITY-AUDIT.zh-CN.md](docs/SITE-PARITY-AUDIT.zh-CN.md)
- [TABLET-AUDIT.zh-CN.md](docs/TABLET-AUDIT.zh-CN.md)
- [UI-AUDIT.zh-CN.md](docs/UI-AUDIT.zh-CN.md)
- [UI-SIMPLIFY-AUDIT.zh-CN.md](docs/UI-SIMPLIFY-AUDIT.zh-CN.md)

## 使用限制

本项目不接受外部贡献，不允许商业使用。

参与开发或提交内容前请阅读 [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md)。

## 许可证

[PolyForm Noncommercial 1.0.0](LICENSE)

禁止商业使用。本项目属于源码公开项目，不是 OSI 定义的开源项目。

Copyright (c) 2026 PickCat Android contributors

PickCat 名称、站点内容、API 和第三方播放器归各自权利人所有。
