# 更新日志

记录 PickCat for Android 的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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

[0.1.5]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.5
[0.1.4]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.4
[0.1.3]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.3
[0.1.2]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.2
[0.1.1]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.1
[0.1.0]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.0
