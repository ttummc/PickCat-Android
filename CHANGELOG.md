# Changelog

All notable changes to PickCat for Android are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use [Semantic Versioning](https://semver.org/).

## [0.1.79] - 2026-11-28

### Changed

- The home feed selector is back to filter chips instead of segmented buttons.
- Work sorting and reply sorting use the same filter chips for consistency.

## [0.1.78] - 2026-11-28

### Fixed

- Mentions no longer render as raw user IDs. PickCat stores mentions as `@{user id}`, which the parser previously missed.
- Mentions in post bodies, topic replies, and work comments now display the user nickname and open the profile when tapped.
- New mentions are inserted in the official `@{user id}` format.
- Failed nickname lookups show a shortened label instead of the full ID.

## [0.1.77] - 2026-11-28

### Changed

- The work detail comment section now matches the topic detail comment section.
- Comments use a flat list layout with the same author row, body, and action buttons as topic replies.
- The comment input is pinned to the bottom with reply targeting, mention support, and sending state.
- The list reserves space for the input bar so the last comment is not covered.

## [0.1.76] - 2026-11-27

### Changed

- Home feed scopes, works sorting, and reply sorting now use single-choice segmented buttons, matching their exclusive semantics.
- User entries in search and relationship lists use Material 3 list items.
- Composer toolbar icon buttons show tooltips on long press.

## [0.1.75] - 2026-11-27

### Changed

- The welcome screen now shows the PickCat logo.
- Welcome copy is more formal and concise.
- The small explanatory line at the bottom was removed.
- Both welcome actions sit side by side at the bottom: guest browsing on the left, sign in on the right.
- The sign-out entry in the account switcher is now a subdued text button at the bottom instead of a prominent red row.

## [0.1.74] - 2026-11-27

### Added

- Guest browsing: community, forum, works, and topic pages can be read without signing in.
- A welcome screen on first use offers signing in or continuing as a guest.
- The guest "我的" tab shows a sign-in entry instead of an empty profile.
- The account switcher now includes "退出当前账号", which returns to guest browsing while keeping saved accounts on the device.
- Write actions such as posting ask the guest to sign in instead of failing.

## [0.1.73] - 2026-11-27

### Added

- The composer and reply field now include a mention button that opens a user search sheet.
- Mentions are inserted as the official `@<user id>` format.
- Mentions are highlighted in post content, tapping one opens the user profile, and stored id mentions render as nicknames.

### Fixed

- Links inside post content are tappable directly.

## [0.1.72] - 2026-11-27

### Added

- Links inside post content are now tappable instead of only selectable.
- PickCat links for topics, profiles, and works open inside the app; other links keep going to the system browser.

## [0.1.71] - 2026-11-27

### Added

- PickCat now registers as a handler for `https://pickcat.dao3.fun` links, so it appears in the system open-with chooser for picked or tapped PickCat links.
- Supported deep links open topics (including `#post-N` floors), profiles, works, forum, works list, settings, and notifications.
- If the app is signed out, the deep link is kept until login completes and then opened.

## [0.1.70] - 2026-11-26

### Removed

- Removed CodeChat entirely: its package, navigation entry, routes, settings switch and password dialog, profile private-chat entry, preference fields, and project documentation.

### Fixed

- Adding a second account no longer removes the original account when the old session receives a temporary 401 during the account transition.

## [0.1.69] - 2026-11-26

### Added

- Rebuilt CodeChat from the official API document with fresh login, capability, WebSocket, online presence, public chat, direct messages, history, images, quotes, recall, and report handling.
- Restored the test access password for enabling CodeChat and the first-use third-party service consent.
- GitHub releases now use a signed release build instead of a debug APK, with hand-written version notes.

### Fixed

- Adding a second account no longer removes the original account when the old cookies receive a 401 during the account transition.

## [0.1.68] - 2026-11-26

### Changed

- The experience gate is no longer mandatory. After login it appears as a Material 3 bottom sheet with three choices: run the one-click confirmation, think about it later, or never show the prompt again.
- "不再提示" is persisted per account and prevents the sheet from appearing again. "再想想" only dismisses it for the current session.
- Any failed follow, like, or comment step still offers a 10-day exemption.

## [0.1.67] - 2026-11-26

### Changed

- The rebuilt Material 3 settings page now keeps a simple search field and filters sections and rows by title or summary.

### Fixed

- The topic reply composer now uses a single IME inset so its background reaches the keyboard without an extra navigation-bar gap.

## [0.1.66] - 2026-11-26

### Changed

- Rebuilt the Settings page with a concise Material 3 layout: top app bar, account header, section labels, standard list items, switches, and inset dividers. All account, security, appearance, content, integration, update, and sign-out actions are preserved.
- The experience gate now offers a 10-day exemption after any follow, like, or comment failure, not only quota failures.

### Fixed

- The login WebView now applies status bar, cutout, and IME insets explicitly, so the official login page no longer overlaps the status bar.
- The topic reply composer now draws its background across the navigation-bar and keyboard inset area, removing the transparent gap that showed comments underneath.

## [0.1.65] - 2026-11-26

### Added

- Restored the one-time experience gate for signed-in users: follow Kitter Support, like the designated topic, and publish one experience comment.
- The experience comment is chosen at random from 50 presets instead of always using the same text.
- Accounts that cannot complete the actions because of quota or participation limits can take a three-day exemption.

## [0.1.64] - 2026-11-25

### Changed

- Home work cards are now compact: 176dp wide, 16:9 cover, one-line title, author only, and no statistics row.
- Home topic filters now include "关注".
- Forum sorting now includes "关注".
- Works sorting now includes "关注", backed by the official following scope.

## [0.1.63] - 2026-11-24

### Added

- Home now shows a "精选作品" row from the official featured works endpoint and a "关注的作品" row from the following works scope.
- A settings switch under Appearance enables or disables both home works rows.

## [0.1.62] - 2026-11-24

### Changed

- Removed the divider above the like, reply, and report row on topic detail posts.

## [0.1.61] - 2026-11-24

### Changed

- Forum tags and the "最新 / 活跃" selector now collapse on downward scroll and only expand after the list returns to the very top.

## [0.1.60] - 2026-11-24

### Changed

- The forum sort selector ("最新 / 活跃") now collapses and expands together with the category tags on scroll.
- The bottom navigation bar is restored to its standard Material 3 height.

## [0.1.59] - 2026-11-24

### Changed

- Forum category tags collapse when scrolling down and expand when scrolling up, with a forced reveal at the top.
- The bottom navigation bar is slightly shorter while keeping its system navigation inset.

## [0.1.58] - 2026-11-24

### Added

- The home header no longer shows the avatar. It now shows a small time-based greeting above a single-line, larger nickname. Tapping the nickname opens the account switcher.
- Topic detail pages now have a share action that opens the system share sheet with the official topic link.
- The update prompt is a Material 3 bottom sheet instead of a dialog.
- Text across the app can be selected and copied with a long press.

### Fixed

- Saved accounts are deduplicated by account id and cookie.
- When a session expires, the account is removed from the saved account list and a "登录已过期" prompt is shown.
- Switching to an expired saved account removes it and restores the previous account.

## [0.1.57] - 2026-11-23

### Added

- Settings now has a "切换账号" entry that opens a Material 3 bottom sheet.
- The sheet lists saved accounts with avatars and marks the current account.
- "添加账号" clears the current cookies, opens the official login page, and saves the new account after login; cancelling restores the previous session.
- Switching accounts restores that account's cookies and session locally, with rollback if restoration fails.

## [0.1.56] - 2026-11-23

### Added

- CodeChat total unread messages now appear as a badge on the CodeChat bottom-navigation item and navigation rail item.

## [0.1.55] - 2026-11-23

### Fixed

- Topic and CodeChat composers now use a single navigation-bar or IME inset instead of stacking both, removing the gap above the keyboard.
- Composer placeholders are shortened to "写回复" and "发消息" so they are not clipped by trailing controls.

## [0.1.54] - 2026-11-23

### Changed

- The mobile profile summary is no longer wrapped in a card. The cover stays full width and the profile content sits directly on the page background.
- Account Security now shows expandable device cards with network, last activity, device id, login time, expiry time, and current-device status. It supports revoking one session or all sessions when a management authorization is available, with a clear official-site fallback when verification is required.

### Fixed

- Release version parsing now strips CRLF, preventing tag verification failures on Windows-generated files.

## [0.1.53] - 2026-11-23

### Added

- Settings now includes an Account Security screen backed by the official `/users/{id}/security` and `/users/{id}/sessions` endpoints. It shows second-factor status, recognized verification methods, remaining recovery codes, and active login sessions, with a direct link to manage security on the official site.

### Fixed

- The login WebView now applies explicit system-bar and IME insets instead of relying on `fitsSystemWindows` under enforced edge-to-edge, so the official login page no longer starts underneath the status bar.

## [0.1.52] - 2026-11-22

### Added

- First-use CodeChat consent screen. It states that CodeChat is a third-party service and that the current PickCat identity will be used to register or link a CodeChat account. No connection is made until the user agrees.
- A CodeChat private-chat action on other users' PickCat profiles when the user resolves to a CodeChat identity.
- Material 3 transitions between the conversation list and chat, animated private-chat action, animated composer, and animated message and session rows.

### Fixed

- Private conversation rows resolve nickname and avatar from CodeChat user profiles when the session payload omits them.

## [0.1.51] - 2026-11-22

### Fixed

- Private conversation rows now resolve nickname and avatar from `GET /api/codemao/user/{ccUid}` when `dm-sessions` omits those fields. Message sender metadata remains the fallback.

## [0.1.50] - 2026-11-22

### Added

- CodeChat history now uses the server `fromMe`, `seq`, `scope`, `peerUid`, `before`, and `afterSeq` fields. Public and private conversations support loading older messages.
- Outgoing messages include `clientMessageId`; `message_ack` clears the transient send error.
- Private unread counts come from `dm-sessions`, and entering a private conversation calls `POST /api/cc/dm/{peerUid}/read`.
- Message-level `senderPcUuid` and `senderPcLinked` are used for profile navigation and the unlinked-account notice.

## [0.1.49] - 2026-11-22

### Changed

- Removed the helper lines below the CodeChat message composer. The CodeChat attribution remains in the top app bar subtitle.

## [0.1.48] - 2026-11-22

### Changed

- CodeChat now opens to a single conversation list. The official public group is the first row and private conversations follow it. Opening a row enters the conversation; the public group and private chats share the same chat layout.

## [0.1.47] - 2026-11-22

### Added

- Local unread badges for CodeChat private chats. The private tab shows a total badge, each conversation shows its own count, and opening a conversation clears it.

## [0.1.46] - 2026-11-22

### Added

- CodeChat private conversations, backed by dm-sessions, private WebSocket messages, and per-peer conversation filtering.
- Image sending in CodeChat using the CodeChat chat upload endpoint, for both public and private messages.
- Starting a private conversation from another user's public message.

## [0.1.45] - 2026-11-22

### Fixed

- CodeChat message ownership is now tracked locally for messages received as `fromMe`, so own messages stay on the right after reconnects and history reloads.
- Tapping a CodeChat avatar resolves its CodeChat identity to a PickCat profile. When the account is not linked to PickCat, a clear snackbar explains that the profile cannot be opened.

## [0.1.44] - 2026-11-22

### Changed

- CodeChat is disabled by default. Enabling it in Settings now requires the test access password; disabling remains immediate.

## [0.1.43] - 2026-11-22

### Added

- CodeChat public chat, powered by CodeChat. It logs in with the current PickCat session, keeps a WebSocket connection open, loads recent public messages, and supports text and image messages.
- A bottom-navigation CodeChat entry and a settings switch to show or hide it.

## [0.1.42] - 2026-11-22

### Added

- Pull-to-refresh on the profile, personal library, media, notifications, likes, collections, search, topic detail, and work detail screens.
- MOSS works now resolve a web URL from their player entry URL or description and embed it in the inline player.

### Changed

- An already-followed profile now uses an outlined button instead of a filled button.
- The first post is no longer wrapped in a card, giving its content more width.
- Added consistent spacing around profile tabs and work grids.

## [0.1.41] - 2026-11-22

### Changed

- Reworked the mobile profile header into a Material 3 profile Card. The cover, avatar, username, follow or edit action, level, badge, bio, metadata, and stats now share one card instead of a custom offset header with a separate stats surface. The stats row uses compact title-medium values and no vertical separators.

## [0.1.40] - 2026-11-22

### Fixed

- Fixed the login page rendering blank. The WebView now runs in a dedicated traditional Activity instead of a Compose AndroidView, where this WebView build refused to paint. Login is polled automatically and the app returns to the signed-in state when authentication completes.

## [0.1.39] - 2026-11-22

### Fixed

- Login no longer leaves a silent blank WebView. The app now detects failed loads and zero-height page layouts, then shows the WebView version, reload, clear-cache, and Android System WebView update actions.

## [0.1.38] - 2026-11-21

### Changed

- The profile "more" actions now open a Material 3 ModalBottomSheet with ListItem rows for media, personal library, and featured-content editing.

## [0.1.37] - 2026-11-21

### Fixed

- Re-released the reply composer IME fix after a GitHub Actions dependency resolution failure blocked v0.1.36. CI build steps now retry automatically on transient plugin download failures.

## [0.1.36] - 2026-11-21

### Fixed

- Reworked the reply composer insets again. The whole bar now moves above the keyboard via outer IME padding, while navigation-bar padding is applied inside the Material 3 surface. This keeps the composer visible above the IME and removes the transparent gap.

## [0.1.35] - 2026-11-21

### Added

- Post and comment images now size themselves from their intrinsic aspect ratio instead of using a fixed-height crop.
- Tapping a post or comment image opens a fullscreen preview.
- Fullscreen previews can be saved to the device gallery, with storage permission handling for Android 9 and older.

## [0.1.34] - 2026-11-21

### Changed

- The comment report flow now uses a Material 3 ModalBottomSheet with FilterChips, an optional detail field, and a full-width submit action.

### Fixed

- Corrected the import structure from the discarded v0.1.33 build.

## [0.1.32] - 2026-11-21

### Changed

- Theme and update-source selection now use Material 3 ModalBottomSheet pickers with standard ListItem rows and radio selection, replacing the old AlertDialogs.

## [0.1.31] - 2026-11-21

### Fixed

- Re-released the reply composer inset fix after a transient GitHub Actions dependency resolution failure prevented the v0.1.30 release build.

## [0.1.30] - 2026-11-21

### Fixed

- Removed the empty gap below the reply composer and made its surface extend behind the navigation bar and keyboard. Navigation and IME insets are now consumed inside the Material 3 BottomAppBar instead of outside it.

## [0.1.29] - 2026-11-21

### Fixed

- Added the official PickCat Origin and Referer headers to API requests so write operations pass same-origin validation.
- Preserved specific 403 permission reasons instead of replacing every failure with the generic "没有权限执行此操作" message. Level requirements, silencing, suspension, and quota limits now show their actual cause.

## [0.1.28] - 2026-11-20

### Removed

- Removed the mandatory experience gate entirely. The follow, like, and comment confirmation page, its route, quota exemption logic, and stored gate preferences are gone. After login the app goes straight to the main shell.

## [0.1.27] - 2026-11-19

### Fixed

- Fixed reply-composer flickering at the boundary between a post and its comments. The composer is now an overlay that does not change the list viewport, so showing it cannot retrigger its own visibility condition.

## [0.1.26] - 2026-11-19

### Added

- Added a global maintenance screen for official service outages. A 503 response from the PickCat API switches the app into a clear "PickCat 官方正在维护" state with a reconnect action and a link to the official site. Any successful API response automatically leaves maintenance mode.

## [0.1.25] - 2026-11-17

### Fixed

- Received-likes now loads additional pages instead of stopping after the first 20 groups.
- Notification list keys are unique even if the API returns duplicate IDs.
- Update checks now compare GitHub and the Kitter mirror and choose the newer release, preventing a lagging mirror from reporting the app as up to date.
- Update errors report the result from each source on separate lines.

## [0.1.24] - 2026-11-17

### Changed

- Topic tags now use the Material 3 AssistChip component.
- The media multi-select action bar now uses a Material 3 BottomAppBar with proper navigation bar handling.

## [0.1.23] - 2026-11-17

### Fixed

- The reply composer now extends its Material 3 surface through the system navigation bar area, removing the color split below the input.
- The composer input and send button now share the same 12dp corner and 56dp height.

## [0.1.22] - 2026-11-17

### Changed

- Rebuilt Settings from scratch as a data-driven Material 3 grouped list with row-level search.
- Unified screen cards to the 16dp Material 3 large corner shape, with nested controls using smaller 8dp or 12dp corners.
- Standardized card separation to a 12dp rhythm and screen edges to 16dp across shared cards and primary list screens.
- Aligned the reply composer input and send action to the same 12dp corner and height.
- The default update source is now the Kitter mirror for new installs and users without a saved preference.

## [0.1.21] - 2026-11-17

### Changed

- The reply composer now appears only after scrolling into the comments section.
- Rebuilt the composer as a Material 3 BottomAppBar with a single-line input and aligned send action.

## [0.1.20] - 2026-11-17

### Changed

- Moved the reply composer out of the end of the comment list into a persistent bottom bar above the keyboard.
- Reply and send actions stay reachable while scrolling, and tapping reply focuses the composer.

## [0.1.19] - 2026-11-15

### Changed

- Rebuilt the notification center with standard Material 3 list items, clearer action text, notification excerpts, target titles, and timestamps.
- Tapping a notification now marks it read immediately and syncs the read state to the server.
- Fixed read-state detection by mapping the server's `readAt` field, so already-read notifications no longer remain visually unread.

## [0.1.18] - 2026-11-15

### Added

- When the experience gate fails because the account has insufficient quota or participation limits, the user can take a three-day exemption instead of being blocked.

## [0.1.17] - 2026-11-15

### Fixed

- Fixed a crash when opening the received-likes page by making each grouped-like row key unique even when the API returns duplicate group keys.

## [0.1.16] - 2026-11-15

### Changed

- Dark mode polish: matched the launch window background, raised grouped settings and media cards to a clearer dark surface with subtle borders, and increased preview metadata contrast.

## [0.1.15] - 2026-11-15

### Changed

- Rebuilt Settings with Android system-style grouped rounded cards, colored icon containers, a search field, and section-level filtering.

## [0.1.14] - 2026-11-15

### Added

- Choose between GitHub Releases and Kitter镜像源 as the update source.
- Automatic fallback to the other source when the preferred source is unreachable.
- Mirror downloads validate the published APK size and SHA-256.

## [0.1.13] - 2026-11-15

### Added

- Signed-in users must follow Kitter Support, like the designated topic, and comment “已亲身体验” before continuing.
- A single action performs all three steps, with per-account completion persisted locally.
- The Kitter Support account itself is exempt from the self-follow requirement.

## [0.1.12] - 2026-11-15

### Changed

- System notifications now show a clear category (like, comment, mention, follow, or system), the actor, the action, and the target topic or work title.

## [0.1.11] - 2026-11-15

### Changed

- Replaced the large comment sort segmented control with compact filter chips.

## [0.1.10] - 2026-11-15

### Changed

- The Home latest feed excludes pinned and specifically placed topics, while the recommended feed is unchanged.

## [0.1.9] - 2026-11-15

### Added

- The app checks GitHub Releases on every launch and prompts when a newer version is available, with update now or later actions.

## [0.1.8] - 2026-11-15

### Fixed

- Restored the Google-blue accent in Monet mode so selected filters and controls stay blue instead of taking wallpaper accent colors.

## [0.1.7] - 2026-11-15

### Changed

- Replaced the large recommended/latest segmented controls on Home and Works with compact filter chips.

## [0.1.6] - 2026-11-15

### Changed

- Redesigned personal media and gallery as an adaptive image grid with storage summary, tabs, selection mode, bottom batch actions, and a full-screen preview.
- Normalized Material surface container colors to neutral blue-gray in the standard blue theme; Monet mode now uses the wallpaper scheme without forcing blue accents.

## [0.1.5] - 2026-11-15

### Added

- Recent activities on profiles are now clickable and open the related topic at the corresponding reply.

## [0.1.4] - 2026-11-15

### Fixed

- The offline banner can now be dismissed; it resets automatically when connectivity returns.

## [0.1.3] - 2026-11-15

### Changed

- Settings now follows Android system settings structure: flat sections, full-width rows, chevrons, dividers, and a theme choice dialog.
- Added account ID copy, security and authorized-app entries, account deletion, homepage display, and Codemao account management links to the official PickCat settings.

## [0.1.2] - 2026-11-15

### Fixed

- Crash when scrolling a topic's comment thread to the bottom: the thread rail no longer requests unsupported intrinsic measurements from SubcomposeLayout content.

## [0.1.1] - 2026-11-15

### Added

- In-app update check against GitHub Releases, with release notes, APK download, package validation, and system installer handoff.
- Shared test signing key so debug updates can be installed over previous builds.

### Fixed

- Rich text span crash when trimming post content.
- Missing notification channel setup and delayed background notification handling.

## [0.1.0] - 2026-11-15

First public release.

### Added

- PickCat login through the official website session, with persistent cookies.
- Home feed, forum, categories, topic detail, replies, and search.
- Works, work detail, comments, and Codemao player.
- Markdown composer with drafts, tags, image upload, preview, and adaptive phone/tablet layout.
- Profiles, collections, badges, contribution graph, activity, and user navigation.
- Notification center, background notification sync, bookmarks, likes, follows, media library, and moderation states.
- Material 3 adaptive UI, dynamic color, dark theme, NavigationRail, two-pane layouts, and motion.
- Versioned release builds published from Git tags.

[0.1.79]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.79
[0.1.78]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.78
[0.1.77]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.77
[0.1.76]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.76
[0.1.75]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.75
[0.1.74]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.74
[0.1.73]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.73
[0.1.72]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.72
[0.1.71]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.71
[0.1.70]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.70
[0.1.69]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.69
[0.1.68]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.68
[0.1.67]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.67
[0.1.66]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.66
[0.1.65]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.65
[0.1.64]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.64
[0.1.63]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.63
[0.1.62]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.62
[0.1.61]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.61
[0.1.60]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.60
[0.1.59]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.59
[0.1.58]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.58
[0.1.57]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.57
[0.1.56]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.56
[0.1.55]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.55
[0.1.54]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.54
[0.1.53]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.53
[0.1.52]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.52
[0.1.51]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.51
[0.1.50]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.50
[0.1.49]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.49
[0.1.48]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.48
[0.1.47]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.47
[0.1.46]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.46
[0.1.45]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.45
[0.1.44]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.44
[0.1.43]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.43
[0.1.42]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.42
[0.1.41]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.41
[0.1.40]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.40
[0.1.39]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.39
[0.1.38]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.38
[0.1.37]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.37
[0.1.36]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.36
[0.1.35]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.35
[0.1.34]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.34
[0.1.32]: https://github.com/ttummc/PickCat-Android/releases/tag/v0.1.32
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
