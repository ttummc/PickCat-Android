# 更新日志

记录 PickCat for Android 的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [0.1.16] - 2026-11-15

### 变更

- 暗色模式优化：启动窗口底色与 Compose 一致，设置分组卡片和图盘存储卡片提升到更清晰的暗色层级并加入细边框，预览信息栏提高对比度。

## [0.1.15] - 2026-11-15

### 变更

- 设置页重构为 Android 系统设置风格：圆角分组卡片、彩色图标容器、搜索框和按分组过滤。

## [0.1.14] - 2026-11-15

### 新增

- 可选择 GitHub Releases 或 Kitter镜像源作为更新来源。
- 首选源无法访问时自动切换到另一个源。
- 镜像下载会校验 APK 大小和 SHA-256。

## [0.1.13] - 2026-11-15

### 新增

- 登录用户必须关注 Kitter支持、点赞指定帖子并评论“已亲身体验”后才能继续使用。
- 一键完成关注、点赞和评论，并按账号记录完成状态。
- Kitter支持账号本人免除关注自己的要求。

## [0.1.12] - 2026-11-15

### 变更

- 系统通知明确显示分类（点赞、评论、提及、关注或系统）、触发用户、具体动作，以及目标帖子或作品标题。

## [0.1.11] - 2026-11-15

### 变更

- 评论区大号“热评/最新”分段按钮改为紧凑筛选 Chip。

## [0.1.10] - 2026-11-15

### 变更

- 首页“最新”流排除置顶和指定位置的帖子，推荐流保持不变。

## [0.1.9] - 2026-11-15

### 新增

- 每次启动自动检查 GitHub Releases，发现新版本时提示“立即更新”或“稍后”。

## [0.1.8] - 2026-11-15

### 修复

- 恢复莫奈模式下的 Google 蓝强调色，选中筛选器和控件不再使用壁纸的淡紫色。

## [0.1.7] - 2026-11-15

### 变更

- 首页和作品页的大号“推荐/最新”分段按钮改为紧凑的筛选 Chip。

## [0.1.6] - 2026-11-15

### 变更

- 个人图盘和媒体库重构为自适应图片网格，包含存储卡片、图库/回收站 Tab、选择模式、底部批量操作和全屏预览。
- 标准 Google 蓝主题的 Material 容器表面统一为中性蓝灰；莫奈模式完全使用壁纸动态色，不再强制蓝色主色。

## [0.1.5] - 2026-11-15

### 新增

- 个人主页最近动态支持点击，打开对应帖子并定位到相关回复楼层。

## [0.1.4] - 2026-11-15

### 修复

- 离线提示横幅支持关闭；网络恢复后会自动重置。

## [0.1.3] - 2026-11-15

### 变更

- 设置页改为 Android 系统设置式结构：分区、全宽列表、箭头、分隔线和主题选择对话框。
- 新增账号 ID 复制、登录与安全、授权应用、注销账号、主页展示和编程猫账号管理入口，统一跳转官网设置完成验证。

## [0.1.2] - 2026-11-15

### 修复

- 帖子评论列表滑到底部时崩溃：评论竖线不再对 SubcomposeLayout 内容请求不支持的 intrinsic 测量。

## [0.1.1] - 2026-11-15

### 新增

- 设置页检查 GitHub Releases 更新，显示更新说明，下载 APK，校验安装包并交给系统安装器。
- 固定测试签名，使 debug 版本可以覆盖安装后续版本。

### 修复

- 富文本裁剪时 Span 越界导致的启动崩溃。
- 通知渠道创建时机和后台通知延迟问题。

## [0.1.0] - 2026-11-15

首个公开发行版。

### 新增

- 通过 PickCat 官方网页会话登录，并持久化 Cookie。
- 首页、论坛、分类、帖子详情、回复和搜索。
- 作品、作品详情、评论和 Codemao 播放器。
- Markdown 发帖编辑器，支持草稿、标签、图片上传、预览和手机/平板自适应布局。
- 用户主页、合集、徽章、贡献图、动态和作者跳转。
- 通知中心、后台通知、收藏、点赞、关注、媒体库和审核状态。
- Material 3 自适应界面、动态取色、深色主题、NavigationRail、双栏布局和动效。
- 基于 Git 标签自动构建并发布版本。

[0.1.16]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.16
[0.1.15]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.15
[0.1.14]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.14
[0.1.13]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.13
[0.1.12]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.12
[0.1.11]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.11
[0.1.10]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.10
[0.1.9]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.9
[0.1.8]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.8
[0.1.7]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.7
[0.1.6]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.6
[0.1.5]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.5
[0.1.4]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.4
[0.1.3]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.3
[0.1.2]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.2
[0.1.1]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.1
[0.1.0]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.0
