# PickCat for Android

English | [Chinese](README.zh-CN.md)

A source-available native Android client for [pickcat.dao3.fun](https://pickcat.dao3.fun), built with Kotlin, Jetpack Compose, and Material 3.

[Download the latest APK](https://github.com/ttummc/PickCat-Android/releases/latest)

> This is an unofficial project and is not affiliated with the PickCat site operators. Sign-in happens on the official site. Account credentials and captchas never pass through a project-operated server.

## Status

This project is under active development. Core browsing, publishing, notifications, and account features are implemented. Some settings and editor features are still incomplete.

### Implemented

- Home feed with recommended and latest sorting
- Forum, categories, topic detail, replies, and pagination
- Search across topics, works, users, and tags
- Composer with Markdown toolbar, preview, drafts, tags, and image upload
- Work list, work detail, comments, and Codemao player
- User profiles, collections, badges, contribution graph, and activity
- Notification center, unread state, filters, and grouped likes
- Background notification sync with WorkManager
- In-app update check against GitHub Releases, with APK validation and system installer handoff
- Bookmarks, likes, follows, personal media, and media library
- Profile editing for region, bio, privacy lists, and badge display
- Moderation-aware publishing and replies
- Dynamic color, dark theme, NavigationRail, and two-pane layouts

### Not yet implemented

- Avatar and profile background upload/save
- Email change, phone binding, password reset, OIDC grants, and account deletion
- Codemao work sync and import
- Topic cover picker, emoji picker, and fullscreen editor preview
- Complete “pending review” list in the Android profile

## Tech stack

- Kotlin 2.2.10
- Android Gradle Plugin 9.5.1
- Gradle 9.9.1
- compileSdk 36, minSdk 26
- Jetpack Compose with Material 3
- OkHttp and kotlinx.serialization
- DataStore
- WorkManager

## Modules

```text
app/                  Activities, navigation, feature screens, and ViewModels
core/network/         API client, models, OkHttp, cookie jar, uploads
core/data/            Session, preferences, drafts, AppContainer
core/ui/              Material 3 theme, shared components, native HTML renderer
```

The app uses a small `AppContainer` service locator instead of Hilt. Persistence uses DataStore and a custom cookie jar. Paging is implemented with a cursor pager rather than Paging 3.

## Build

Requirements:

- JDK 25 or newer
- Android SDK Platform 36
- The Gradle wrapper included in this repository

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases and versioning

Published builds are available on [GitHub Releases](https://github.com/ttummc/PickCat-Android/releases). See [CHANGELOG.md](CHANGELOG.md) for release notes.

- `version.properties` is the single source of truth for the release version.
- Versions follow Semantic Versioning: `major.minor.patch`.
- `versionCode` is derived as `major * 10000 + minor * 100 + patch`.
- Release tags use `vX.Y.Z` and must match `version.properties`.
- Pushing a matching tag runs the release workflow, builds and lints the app, and publishes the installable debug-signed APK.
- After the GitHub release is created, the workflow calls the Kitter mirror's `/sync` endpoint so the mirror picks up the new release. The `KITTER_SYNC_TOKEN` repository secret is required for this step.
- In-app updates can use GitHub Releases or the Kitter镜像源. Choose the preferred source in Settings; if the preferred source is unreachable, the app falls back to the other one. Mirror downloads are checked against the published size and SHA-256.

In-app updates only accept APKs signed with the same certificate. Debug builds and CI releases use the shared test key in `keystore/pickcat-debug.jks`; see its README before distributing production builds.

To prepare a release:

1. Update `version.major`, `version.minor`, and `version.patch` in `version.properties`.
2. Update `CHANGELOG.md` and `CHANGELOG.zh-CN.md`.
3. Commit the changes.
4. Create and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.

`local.properties` must point to your local Android SDK:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Build outputs, APKs, screenshots, `local.properties`, and `artifacts/` are intentionally not committed.

## Login

The site requires an Aliyun slider captcha. The app embeds the official login page and does not reimplement the login form or captcha flow.

1. Open the official login page in a WebView.
2. Complete the username, password, and slider challenge on the official page.
3. Copy the WebView cookies into the persistent OkHttp cookie jar.
4. Confirm the session with `GET /session`.
5. Clear both WebView and OkHttp cookies on sign-out.

Credentials are never stored by the app.

## Moderation

Topics and replies are submitted to `POST /api/v1/posts`. The server returns `202 Accepted` with `status: PENDING_PROVIDER` when the content enters review.

The Android client does not display pending content as published. It shows a review notice and only displays content after the server exposes it.

## Background notifications

- WorkManager periodic sync, minimum interval of 15 minutes
- One immediate sync at startup
- Notification permission required on Android 13 and newer
- Can be disabled in Settings
- The first sync only establishes a baseline and does not replay old notifications

## Known limitations

- The site has no public API contract; changes may break the client
- Native HTML rendering supports a subset of the site's rich content
- Codemao player behavior depends on Codemao's own pages
- Tests are currently limited to build, lint, and manual flows

## Documentation

- [Site parity audit](docs/SITE-PARITY-AUDIT.md)
- [Tablet experience audit](docs/TABLET-AUDIT.md)
- [UI audit](docs/UI-AUDIT.md)
- [UI simplification audit](docs/UI-SIMPLIFY-AUDIT.md)
- [Contribution policy](CONTRIBUTING.md)

## License

[PolyForm Noncommercial License 1.0.0](LICENSE)

Commercial use is prohibited. This is a source-available project, not an OSI-approved open-source project.

Copyright (c) 2026 PickCat Android contributors

PickCat names, site content, APIs, and third-party players remain the property of their respective owners.
