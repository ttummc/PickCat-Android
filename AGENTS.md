# PickCat Android UI Rules

These rules are mandatory for every UI change in this repository.

## Visual language

- Never use gradients. Use solid Material 3 color roles only.
- Never use Emoji in UI copy, buttons, labels, empty states, or composables.
- Never join parallel pieces of information with `·`, `|`, `\`, `/`, `+`, or similar separator symbols.
  Put them in separate `Text` composables, separate rows, or use a standard Material 3 layout.
- Follow the official Material 3 component examples and defaults from Google/Android:
  TopAppBar, NavigationBar, NavigationRail, Card, ListItem, SegmentedButton, SearchBar,
  Snackbar, FAB, Dialog, etc.
- All colors must come from `MaterialTheme.colorScheme` or the app's theme; no hard-coded UI colors.
- Keep dynamic color and dark theme support working.
- Prefer standard M3 spacing, typography roles, shapes, and elevation over custom decoration.
- Information hierarchy should be expressed with typography, spacing, and standard surfaces,
  not decorative lines, emoji, or separator characters.

## Review checklist

- No `Brush`, `linearGradient`, `verticalGradient`, `horizontalGradient`, or `radialGradient`.
- No Emoji characters.
- No separator characters between parallel labels.
- Build and lint pass before delivery.

## Release process

- The GitHub release APK must be built with `:app:assembleRelease`, not `assembleDebug`.
- Before tagging a release, add `release-notes/vX.Y.Z.md` with concise Chinese, user-facing release notes.
- The release workflow uses that file as the GitHub Release body; do not rely on generated notes.

