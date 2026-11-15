# Changelog

All notable changes to PickCat for Android are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use [Semantic Versioning](https://semver.org/).

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
