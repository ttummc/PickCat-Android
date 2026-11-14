# PickCat 官网功能对齐审计

审计对象：`https://pickcat.dao3.fun/`
Android 项目：PickCat for Android

## 已确认的公开路由

| 路由 | 页面 | 关键功能 |
| --- | --- | --- |
| `/` | 首页 | 板块、关注、推荐、最新；话题流；分类入口；发帖入口 |
| `/forum` | 论坛 | 板块列表、分类、话题流 |
| `/forum/category/{slug}` | 分类页 | 分类话题流、分类侧栏链接 |
| `/forum/post/{id}` | 帖子详情 | 正文、标签、作者、回复、热评/最新、只看作者、收藏、举报 |
| `/forum/editor?type=create` | 发帖编辑器 | Markdown 编辑、预览、图片上传、标签、草稿 |
| `/works` | 作品页 | 精选作品、发现作品、推荐/最新、搜索 |
| `/works/{id}` | 作品详情 | 试玩、简介、统计、评论、点赞、收藏、作者其他作品 |
| `/profile/{userId}` | 用户主页 | 主页、合集、发言、作品、关系、徽章；贡献图；动态；关注 |
| `/search` | 搜索 | 全部、帖子、作品、用户、标签 |
| `/notifications` | 消息 | 需要登录 |
| `/login` | 登录 | 邮箱/手机号、密码、忘记密码、注册入口、验证码 |
| `/register` | 注册 | 邮箱域名限制、验证码 |
| `/forgot-password` | 找回密码 | 需要进一步确认 |

## 已观察到的公开 API

### 会话与基础

- `GET /api/v1/session`
- `GET /api/v1/approved-link-domain-patterns`
- `GET /api/v1/registration-email-domains`
- `GET /api/v1/avatars/{id}`
- `GET /api/v1/files/{id}`
- `GET /api/v1/emojis/{name}/image`

### 首页与论坛

- `GET /api/v1/topic-recommendations?sort=recommended&limit=20`
- `GET /api/v1/tags`
- `GET /api/v1/topics?limit=20&tag={slug}&tagMode=all`
- `GET /api/v1/tags/{slug}/sidebar-links`
- `GET /api/v1/topics/{id}`
- `GET /api/v1/topics/{id}/posts?limit=50&sort=hot`
- `GET /api/v1/users/{id}`
- `GET /api/v1/user-badge-displays?userIds={id}`

### 作品

- `GET /api/v1/featured-works`
- `GET /api/v1/work-recommendations?sort=recommended&scope=all&limit=8`
- `GET /api/v1/work-recommendations?sort=recent_top&scope=all&limit=4`
- `GET /api/v1/works/{id}`
- `GET /api/v1/works/{id}/comments?sort=hot&limit=30`
- `GET /api/v1/works/{id}/stats`
- `GET /api/v1/external-works/codemao/{id}`
- `GET /api/v1/users/{id}/works?limit=4`

### 用户主页

- `GET /api/v1/users/{id}/badges`
- `GET /api/v1/users/{id}/featured-topics`
- `GET /api/v1/users/{id}/featured-works`
- `GET /api/v1/users/{id}/level-contributions/{year}`
- `GET /api/v1/users/{id}/profile-activities?year={year}&limit=6`
- `GET /api/v1/users/{id}/topic-collections?limit=20`
- `GET /api/v1/user-badge-displays?userIds={id}`

## 需要登录后才能继续审计

- 消息中心：通知分类、全部已读、收到的赞
- 个人设置：账号、主题、动态取色、媒体库、个人作品库、退出登录
- 发帖、回复、编辑、删除、举报的实际权限和接口
- 收藏、点赞、关注、消息订阅
- 收藏夹、草稿、浏览历史、个人合集
- 登录后的首页“关注”流
- 账号安全、密码、邮箱、头像、徽章展示设置
- 站内消息、评论回复、@ 提及
- 可能存在的管理/审核页面

## Android 现有页面

- Home
- Forum
- Category
- Topic detail
- Composer
- Works
- Work detail
- Profile
- Notifications
- Likes
- Search
- Settings
- Media
- Gallery
- Library
- Official WebView login
- Codemao work player

## 初步缺口

1. 个人主页缺少“合集”标签
2. 个人主页缺少“徽章”标签
3. 个人主页的“关系”被拆成关注和粉丝两个标签，官网是统一关系页
4. 搜索缺少“标签”结果类型
5. 帖子排序只有最新/活跃，官网有热评/最新
6. 作品详情缺少作品统计接口展示和作者其他作品
7. 作品评论缺少热评/最新切换
8. 首页关注流需要登录
9. 发帖编辑器缺少官网的图片/表情/链接/草稿细节，需要逐项比对
10. 设置页功能仍不完整，需要登录后盘点
11. 登录页目前是 WebView，官网有邮箱/手机号、注册、找回密码、验证码
12. 通知中心分类和已读逻辑需要登录后核对

## 下一步

需要登录态才能完成剩余页面和 API 盘点，尤其是消息、设置、发帖、收藏和个人主页互动。登录后再继续对照并开始补实现。

## 本轮已同步

- 搜索增加“标签”结果：搜索全部、标签专属页、标签跳转分类
- 个人主页增加“徽章”标签：读取 `/users/{id}/badges`，展示徽章图、名称、说明
- 已确认合集 API：
  - `GET /users/{id}/topic-collections?limit=20`
  - `GET /topic-collections/{id}`
  - `GET /topic-collections/{id}/topics`
- 已确认作品统计 API：
  - `GET /works/{id}/stats`，返回浏览、猫币、点赞、收藏、评论
- 已确认作者其他作品接口：
  - `GET /users/{id}/works?limit=4`

## 仍待同步

- 个人页“合集”标签与合集详情页
- 作品统计、作者其他作品
- 帖子热评/最新排序
- 作品评论热评/最新
- 登录后的消息、设置、收藏、草稿、账号安全
- 登录、注册、找回密码和验证码

## 第二批已同步

- 个人主页新增“合集”标签
- 新增合集详情页，读取合集信息和合集内帖子
- 合集帖子支持点击进入帖子详情
- 合集页支持分页加载
- 个人主页徽章标签和搜索标签已在上一批完成

## 登录态盘点补充


消息中心页面：`/notifications`

- 提到我的：`GET /notifications?type=USER_MENTIONED&type=WORK_COMMENT_MENTIONED&limit=20`
- 回复我的：`GET /notifications?type=REPLY_CREATED&type=WORK_COMMENT_CREATED&type=WORK_COMMENT_REPLY_CREATED&limit=20`
- 系统通知：`GET /notifications?type=MANAGEMENT_ACTION&type=TOPIC_EVENT&type=LEVEL_CERTIFICATION&type=USER_FOLLOWED&type=TAKEDOWN_APPEAL_DECIDED&type=QUESTION_DUPLICATE_REQUEST_DECIDED&limit=20`
- 收到的赞：独立分组接口

发帖编辑器补充：

- 标题 100 字上限
- 正文 20000 字上限
- 支持链接、表情、粗体、斜体、删除线、H1/H2、引用、代码、代码块、图片、表格、列表、公式、全屏、预览
- 最多选择 3 个标签
- 帖子类型：普通帖、问答帖
- 支持帖子封面

## 系统通知同步

Android 端新增：

- WorkManager 周期同步新通知，最短 15 分钟
- 应用启动时立即执行一次同步
- 首次同步只建立基线，不推送历史通知
- 新通知通过系统通知渠道 `pickcat_notifications` 推送
- 点击通知打开消息中心
- Android 13+ 请求通知权限
- 设置页新增“后台通知”开关，默认开启
- 关闭后后台同步直接跳过；重新开启后恢复

## 登录态页面补充

- 设置页 `/settings` 已确认：
  - 个人资料
  - 账号安全
  - 编程猫互联
  - 隐私设置
  - 外观与体验
- 个人资料包含头像、首页背景、用户名、地区、简介、展示徽章、精选帖子和精选作品。
- 发帖编辑器包含标题 100 字、正文 20000 字、完整 Markdown 工具栏、最多 3 个标签、普通帖/问答帖、封面。
- 消息中心已同步为：
  - 全部
  - 提到我的
  - 回复我的
  - 系统通知
  - 收到的赞入口
- 本地 Android 已增加后台 WorkManager 同步和设置页“后台通知”开关。

## 第五批进度

- 消息中心已增加“全部、提到我的、回复我的、系统通知”分类，保留“收到的赞”入口
- 发帖编辑器标题上限对齐为 100 字
- 正文上限对齐为 20000 字
- 支持普通帖/问答帖类型并提交 `kind`
- Markdown 工具栏补充删除线、表格、公式
- 作品详情补充统计接口数据和作者其他作品
- 个人主页已增加合集、徽章标签和合集详情页

## 设置页完整盘点

- 个人资料
  - 更换头像
  - 个人主页背景图，可从图盘选择或恢复默认
  - 用户名
  - 地区
  - 个人简介，120 字
  - 展示徽章：自动展示最新、不展示、指定徽章
  - 精选帖子最多 2 篇
  - 精选作品最多 2 个
- 账号安全
  - 账号 ID
  - 邮箱和验证状态
  - 更换邮箱
  - 绑定/更换手机号
  - 重置密码
  - OIDC 授权应用
  - 注销账号
  - 退出登录
- 编程猫互联
  - 编程猫账号绑定
  - 同步全部作品
  - 从编程猫导入
  - 全部/已公开/未公开筛选
- 隐私设置
  - 展示关注列表
  - 展示粉丝列表
- 外观与体验
  - 主题：跟随系统、亮色、暗色
  - 网页端互动效果设置

## 本轮已实现

- 消息中心分类
- 发帖标题 100 字、正文 20000 字
- 普通帖/问答帖
- 删除线、表格、公式工具栏
- 工作统计与作者其他作品
- 后台系统通知同步和设置开关
- 个人主页合集、徽章
- 搜索标签

## 尚未实现

- 个人资料编辑表单和保存接口
- 更换头像、主页背景
- 展示徽章选择
- 账号安全邮箱/手机号/密码/注销
- 编程猫互联页面
- 隐私设置开关
- 发帖封面、表情选择器、全屏编辑

## 个人资料编辑

已确认保存接口：

```http
PATCH /api/v1/users/{userId}
{"region": "...", "bio": "..."}
```

Android 已新增“编辑个人资料”页：

- 展示头像和用户名
- 可编辑地区
- 可编辑个人简介，上限 120 字
- 保存后返回设置页
- 用户名修改仍需网页端验证码流程

已完成构建和 lint。

## 个人资料与隐私设置进展

- 编辑资料页支持：
  - 地区
  - 个人简介
  - 展示关注列表
  - 展示粉丝列表
  - 展示徽章：自动展示最新、不展示、指定徽章
- 保存接口：
  - `PATCH /users/{userId}`，字段 `region`、`bio`、`showFollowingList`、`showFollowersList`
  - `PUT /users/{userId}/badge-display`，字段 `mode`，指定徽章时带 `badgeId`
- 已确认徽章展示模式为 `AUTO_LATEST`、`NONE`、`SPECIFIC`。
- 代码已编译并通过 lint。

## 账号信息展示进展

- 设置页显示已验证邮箱：`GET /users/{userId}/email`
- 设置页显示编程猫互联状态：`GET /official-account`
  - 编程猫 ID
  - 最近同步时间
- 个人资料编辑页继续支持：
  - 地区和简介
  - 关注/粉丝列表展示
  - 徽章展示模式

## 仍未同步

- 头像、背景图实际上传和保存
- 更换邮箱、手机号、密码
- OIDC 授权列表
- 注销账号
- 编程猫作品同步和导入
- 发帖封面、表情选择器、全屏编辑

## 消息中心最终分类

- 全部
- 提到我的
- 回复我的
- 系统通知
- 收到的赞（进入分组点赞页）

## 最终 APK 状态

- 代码已通过 assembleDebug 和 lintDebug
- 最新 APK 已更新到 artifacts/PickCat-debug.apk
- 最后一次构建未安装到模拟器

## 发帖和回复审核

已实测：

- 发帖和回复都使用 `POST /api/v1/posts`
- 成功响应为 `202 Accepted`
- 返回字段：
  - `submissionId`
  - `topicId`
  - `postId`
  - `status: PENDING_PROVIDER`
- 发帖提交字段包含：
  - `title`
  - `markdown`
  - `kind`
  - `headerFileId`
  - `tagIds`
- 帖子发布后进入个人主页“审核中”，不会直接展示
- 测试帖子和测试回复已通过 `DELETE /api/v1/posts/{postId}` 清理，均返回 204

Android 已同步：

- 发帖成功后显示“已提交审核”，不再显示“已发布”
- 帖子审核通过前不会出现在话题列表
- 回复成功提示改为“已提交审核，通过后展示”
- 作品评论提示同步为审核后展示
- 保存审核响应字段，已为后续“审核中”列表预留数据模型
