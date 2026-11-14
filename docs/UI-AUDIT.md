# PickCat Android UI Audit

English | [Chinese](UI-AUDIT.zh-CN.md)

This audit records 50 UI issues that were reviewed. Final state: 39 were changed and 11 were reviewed and intentionally kept as-is.

## Motion

1. Floating action buttons on Home, Forum, and Media covered content and did not hide on scroll. Fixed.
2. The NavHost used the same transition for bottom-tab changes and detail navigation. Replaced with fade-and-scale transitions.
3. Splash, login, and main-screen changes were hard cuts. Added root-level fade transitions.
4. Notifications switched from skeleton to content without a fade. Fixed.
5. Search history and result states switched too abruptly. Added state fade.
6. Media library READY and UPLOADING tabs switched without animation. Fixed.
7. Profile featured, topic, and work tab content had no fade. Added item fade and offset animations.
8. Composer edit and preview switched instantly. Fixed.
9. Notification rows jumped after mark-as-read. Added item animation.
10. The grouped likes list lacked item animation. Fixed.
11. Search results lacked item animation. Fixed.
12. The media grid lacked add/remove motion. Fixed.

## Visual hierarchy

13. Profile header text lacked a stable contrast guarantee. Reviewed: text sits below the image and is not overlaid, kept as-is.
14. SectionHeader and subtitle combinations were inconsistent across pages. Reviewed: page titles and SectionHeaders serve different levels, kept as-is.
15. Work-type chips had low contrast. Changed to secondaryContainer and onSecondaryContainer.
16. SearchBar container color did not match page cards. Unified to surfaceContainerLow.
17. The Forum category button still looked too much like a form control. Changed to FilledTonalButton.
18. Composer toolbar icons had uneven density and spacing. Changed to a horizontally scrollable toolbar with a consistent surface.
19. Profile stat dividers were too strong. Reduced strength and enlarged the number hierarchy.
20. Long usernames were truncated differently in notifications and comments. Unified to single-line ellipsis.
21. Topic header metadata was too dense. Changed to a wrapping FlowRow.
22. Work detail tags, author, and stats were not grouped clearly. Added separation between stats and actions.
23. Empty-state icons were repeated and lacked page semantics. Added specific icons for profile activity, topics, works, replies, and follows.
24. Destructive settings actions lacked visual distinction. Sign-out now uses onErrorContainer.
25. FAB size and content-block proportions were inconsistent. Reviewed: using a standard ExtendedFloatingActionButton, kept as-is.
26. Content cards had nearly identical hierarchy. Reviewed: hierarchy comes from titles, spacing, and pinned labels, kept as-is.
27. Comment-thread vertical lines were too dark. Reduced opacity.
28. The report dialog had no scrolling container for long content. Added a height-limited scroll area.

## Layout and insets

29. Composer bottom buttons needed review against the keyboard and gesture bar. Reviewed: outer navigation insets and imePadding cover it, kept as-is.
30. Notifications had insufficient bottom padding. Added bottom padding.
31. Forum category and sort controls were crowded on small screens. Reduced the sort control width.
32. Category and Forum top controls differed in alignment. Reviewed: Category keeps the sort label and Forum keeps the category entry, kept as-is.
33. The fixed-ratio profile header cropped too much on wide screens. Reviewed: 156dp is acceptable on phones and wide screens, kept as-is.
34. Only Works used a two-pane layout; Forum and topic detail remained single-column. Forum and Works use two panes; standalone topic detail keeps a constrained single column.
35. Forum lacked a list/detail split on tablets. Added a Forum two-pane layout.
36. Topic detail had no maximum width on tablets. Limited to 860dp; Work detail uses the same treatment.
37. Search results were too close to the search bar. Adjusted top spacing.
38. Media grid captions were easily truncated. Time text now uses weight and ellipsis.
39. The comment input was buried at the end of long threads. Added a reply FAB that scrolls to the composer.
40. The offline banner could overlap the FAB or Snackbar. Moved it above the FAB.

## Interaction and accessibility

41. Re-tapping the active bottom tab did not return to the top. Added a reselect signal and animated scroll-to-top.
42. Back on Home exited the app immediately. Added a double-back confirmation.
43. Unread dots had no accessibility semantics. Added an “Unread” description.
44. Notification scroll position needed to survive navigation. Connected to LazyListState.
45. Pull-to-refresh had no haptic feedback. Added haptics on Home, Forum, and Works.
46. Like and bookmark state changes lacked scale or color transitions. Added icon fades.
47. Bookmark icon changes had no animation. Added icon fades.
48. Long-press actions were not discoverable. Reviewed: no essential hidden long-press actions currently, kept as-is.
49. Composer focus order needed verification. Reviewed: title, category, body, publish, kept as-is.
50. Some icon buttons lacked contentDescription. Reviewed: all icon buttons have descriptions or visible text that carries the meaning.
