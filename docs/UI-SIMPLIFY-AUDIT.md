# PickCat UI Simplification Audit

English | [Chinese](UI-SIMPLIFY-AUDIT.zh-CN.md)

Goal: fewer decorations, less repeated copy, and clearer layout.

## Top bars and navigation

1. Remove the Forum subtitle “All topics, organized by category.”
2. Remove the Works subtitle “KittenN and BoxPlay community works.”
3. Remove the divider below the Home top bar.
4. Remove the divider below the Forum top bar.
5. Remove the divider below the Works top bar.
6. Remove decorative layers below the Notifications top bar.
7. Reduce explanatory copy in bottom navigation.
8. Use a single-line title on every page instead of stacked subtitles.
9. Stop repeating the app-name introduction in Settings.
10. Remove duplicate technology-stack copy from About.

## Home

11. Keep only the username in the Home greeting.
12. Shorten the Home empty-state description.
13. Remove repeated composer button copy on Home.
14. Tighten Home card spacing from 12dp to 10dp.
15. Remove decorative section-header treatments on Home.

## Forum and categories

16. Keep category and sort controls on one line.
17. Remove repeated sort explanations from Category.
18. Reduce the Category empty state to one sentence.
19. Remove repeated stat-icon explanations from topic cards.
20. Remove secondary descriptions from the category entry.

## Works

21. Shorten the Works empty state.
22. Remove repeated author information from work cards.
23. Remove the “Overview” heading from Work detail.
24. Remove the “Instructions” heading from Work detail.
25. Show “More by this author” only when data exists.

## Topic detail

26. Remove the duplicate bookmark label next to the bookmark icon.
27. Keep only essential statistics above replies.
28. Shorten the report dialog title.
29. Remove the “Markdown supported” hint from the reply field.
30. Use short messages for reply failures.

## Search

31. Remove the repeated “start searching” explanation.
32. Show only the name and arrow in tag results.
33. Simplify search section headings.
34. Reduce the no-results state to one sentence.
35. Turn clear-history into an icon or short label.

## Notifications

36. Keep only avatar, content, and time in notification cards.
37. Keep one unread dot only.
38. Remove notification-type icons.
39. Shorten notification empty states.
40. Keep the five notification categories used by the site.

## Settings and profile

41. Remove the non-clickable avatar preset gallery.
42. Remove the duplicate “View profile” button from the account card.
43. Keep only version and sign-in method in About.
44. Shorten the background-notification description to one line.
45. Reduce explanatory copy on Edit profile.
46. Reduce the profile tab count from 8 to 6.

## Composer and shared UI

47. Remove the “Drafts save automatically” footer.
48. Remove the repeated “N tags selected” footer.
49. Tighten SectionHeader vertical padding from 9dp.
50. Standardize empty-state title and description lengths.

## Completed

1, 2, 3, 4, 5, 8, 10, 11, 12, 21, 26, 29, 31, 34, 37, 38, 40, 41, 43, 44, 46, 47, 48, 49

## Current result

- Home, Forum, and Works top bars are single-line titles.
- Dividers below Home, Forum, and Works top bars were removed.
- Profile tabs were reduced from 8 to 7; Following and Followers were merged into Relation.
- Notification cards no longer show type icons.
- The non-clickable avatar preset gallery was removed from Settings.
- Duplicate technology-stack copy was removed from About.
- The composer footer no longer repeats tag counts or draft messaging.
- Empty states and secondary descriptions were shortened.
- SectionHeader spacing was tightened.
