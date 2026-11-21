# 更新日志

记录 PickCat for Android 的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [0.1.31] - 2026-11-21

### 修复

- v0.1.30 的发布构建因 GitHub Actions 临时依赖解析失败而中断，本版本重新发布输入栏 inset 修复。

## [0.1.30] - 2026-11-21

### 修复

- 修复回复输入栏下方的大间隔。导航栏和输入法 inset 现在在 Material 3 BottomAppBar 内部处理，输入栏背景会完整覆盖到导航栏和键盘区域，不再透明。

## [0.1.29] - 2026-11-21

### 修复

- API 请求补充官方 PickCat 的 Origin 和 Referer 请求头，避免写操作被同源校验拦截。
- 不再把所有 403 都覆盖成“没有权限执行此操作”，现在会显示具体原因，包括社区等级不足、禁言、停用和额度限制。

## [0.1.28] - 2026-11-20

### 移除

- 完全移除强制体验门槛：关注、点赞、评论确认页，对应路由，额度豁免逻辑以及本地门槛记录全部删除。登录后直接进入主界面。

## [0.1.27] - 2026-11-19

### 修复

- 修复帖子和评论区交界处回复输入栏反复闪烁的问题。输入栏改为不参与列表测量的底部浮层，显示时不会改变列表视口，因此不会再次触发显示条件。

## [0.1.26] - 2026-11-19

### 新增

- 增加官方服务维护状态页。PickCat API 返回 503 时，应用会明确显示“PickCat 官方正在维护”，提供重新连接和打开官方网站入口；任一 API 请求恢复成功后自动退出维护状态。

## [0.1.25] - 2026-11-17

### 修复

- “收到的赞”现在会继续加载后续分页，不再只显示前 20 组。
- 通知列表即使接口返回重复 ID，也会使用唯一 key，避免列表崩溃。
- 更新检查现在会同时比较 GitHub 和 Kitter镜像源并选择较新的版本，镜像滞后时不会误报已是最新。
- 更新失败时分别显示两个来源的错误信息。

## [0.1.24] - 2026-11-17

### 变更

- 帖子标签改用 Material 3 的 AssistChip 组件。
- 媒体多选操作栏改用 Material 3 的 BottomAppBar，并正确处理系统导航栏区域。

## [0.1.23] - 2026-11-17

### 修复

- 回复输入栏的 Material 3 背景现在延伸到系统导航栏区域，消除输入框下方导航条的颜色断层。
- 输入框与发送按钮统一为 12dp 圆角和 56dp 高度。

## [0.1.22] - 2026-11-17

### 变更

- 设置页完全重构为数据驱动的 Material 3 分组列表，支持按行搜索。
- 全站内容卡片统一为 Material 3 的 16dp 大圆角，内部控件使用更小的 8dp 或 12dp 圆角。
- 共享卡片和主要列表页统一为 12dp 卡片间距、16dp 屏幕边距。
- 评论输入框与发送按钮统一为 12dp 圆角和相同高度。
- 新安装及未保存过偏好的用户默认使用 Kitter镜像源检查更新。

## [0.1.21] - 2026-11-17

### 变更

- 回复输入栏改为滚动到评论区后才显示。
- 使用 Material 3 BottomAppBar 重构输入栏，单行输入框与发送按钮居中对齐。

## [0.1.20] - 2026-11-17

### 变更

- 评论输入框从评论列表末尾移到固定底栏，键盘弹出时会随之上移。
- 滚动时回复和发送按钮始终可用，点击“回复”会自动聚焦输入框。

## [0.1.19] - 2026-11-15

### 变更

- 通知中心改为标准 Material 3 列表结构，补充动作说明、通知正文摘要、目标标题和时间。
- 点击通知后立即标记为已读，并同步服务端已读状态。
- 修复服务端 `readAt` 字段未映射导致已读通知仍显示未读的问题。

## [0.1.18] - 2026-11-15

### 新增

- 体验门槛因账号额度或参与等级限制失败时，用户可以选择豁免 3 天，而不是被永久卡住。

## [0.1.17] - 2026-11-15

### 修复

- 修复打开“收到的赞”页面时，接口返回重复分组 key 导致的 LazyColumn 崩溃；现在每行使用唯一 key。

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

[0.1.31]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.31
[0.1.30]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.30
[0.1.29]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.29
[0.1.28]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.28
[0.1.27]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.27
[0.1.26]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.26
[0.1.25]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.25
[0.1.24]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.24
[0.1.23]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.23
[0.1.22]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.22
[0.1.21]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.21
[0.1.20]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.20
[0.1.19]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.19
[0.1.18]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.18
[0.1.17]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.17
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
