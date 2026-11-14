# PickCat Tablet Experience Audit

English | [Chinese](TABLET-AUDIT.zh-CN.md)

Target: tablets, foldables, landscape phones, split-screen, and multi-window.

Status values:

- Done: implemented and built
- Reviewed: checked and the current behavior is acceptable
- Next: planned for a later batch

## Navigation

| # | Item | Status |
| --- | --- | --- |
| 1 | Add a branded header to the navigation rail | Done |
| 2 | Add a Settings entry at the bottom of the rail | Done |
| 3 | Use one selected-state visual across rail and bottom bar | Done |
| 4 | Verify rail item height, spacing, and touch targets | Reviewed |
| 5 | Handle display cutouts and status-bar insets around the rail | Next |
| 6 | Decide whether the rail should expand on medium widths | Next |
| 7 | Position the offline banner above content instead of the bottom bar | Done |
| 8 | Re-selecting the active rail item scrolls the current page to the top | Done |

## Content width and readability

| # | Item | Status |
| --- | --- | --- |
| 9 | Cap the Home feed at a readable line length | Done |
| 10 | Cap Settings content width | Done |
| 11 | Cap Notifications content width | Done |
| 12 | Cap Search results width | Done |
| 13 | Cap Profile content width | Done |
| 14 | Use a fixed, readable list width in Forum two-pane | Done |
| 15 | Use a fixed, readable list width in Works two-pane | Done |
| 16 | Keep Work detail near 860dp on large screens | Done |
| 17 | Keep Topic detail near 860dp on large screens | Done |
| 18 | Cap Composer width on tablets | Next |
| 19 | Cap Edit profile width on tablets | Done |
| 20 | Choose adaptive column counts for media grids | Reviewed |

## List-detail behavior

| # | Item | Status |
| --- | --- | --- |
| 21 | Hardware back clears the selected Forum topic | Done |
| 22 | Hardware back clears the selected Work | Done |
| 23 | Shorten the empty right-pane state | Done |
| 24 | Highlight the selected row in the left pane | Next |
| 25 | Align divider and selected-row colors with Material 3 | Next |
| 26 | Preserve pane selection across resize and rotation | Done |
| 27 | Decide whether deep links open in the right pane | Next |
| 28 | Make the author clickable in the embedded Work detail | Done |
| 29 | Make author and tag links work in the Forum pane | Next |
| 30 | Add a notification list-detail layout | Next |
| 31 | Add a persistent search filter rail or side panel | Next |
| 32 | Add an optional Home side panel on expanded widths | Next |

## Layout and spacing

| # | Item | Status |
| --- | --- | --- |
| 33 | Limit card line length inside wide feeds | Next |
| 34 | Normalize section spacing between phone and tablet | Next |
| 35 | Keep dialogs at a readable maximum width | Reviewed |
| 36 | Prevent top app bars from stretching content hierarchy | Next |
| 37 | Keep FABs inside the content area next to the rail | Next |
| 38 | Constrain Snackbar width on tablets | Next |
| 39 | Add extra bottom padding for rail layouts | Next |
| 40 | Verify pull-to-refresh inside each pane | Next |
| 41 | Remove duplicate top bars in two-pane detail views | Next |

## Adaptation and input

| # | Item | Status |
| --- | --- | --- |
| 42 | Verify landscape safe areas and cutouts | Next |
| 43 | Avoid placing critical controls across a foldable hinge | Next |
| 44 | Recompute layout on multi-window resize | Next |
| 45 | Verify keyboard insets in the composer and reply fields | Reviewed |
| 46 | Keep the player fullscreen flow working in landscape | Reviewed |
| 47 | Constrain media picker and gallery dialogs | Next |
| 48 | Use tablet-appropriate typography without oversized headings | Next |
| 49 | Add a tablet testing matrix to CI or manual QA | Next |
| 50 | Test RTL and long localized labels on wide screens | Next |

## First batch completed

- Navigation rail header and Settings entry
- Offline banner moved above content
- Readability caps for Home, Settings, Notifications, Search, and Profile
- Readability caps for Edit profile, collection detail, and personal library
- Fixed list widths for Forum and Works two-pane layouts
- Back handling and selection preservation in both two-pane views
- Author links restored in embedded Work detail
- Shorter empty-pane copy
