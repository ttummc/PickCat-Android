# Changelog

All notable changes to PickCat for Android are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use [Semantic Versioning](https://semver.org/).

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
