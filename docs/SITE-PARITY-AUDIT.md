# PickCat Site Parity Audit

English | [Chinese](SITE-PARITY-AUDIT.zh-CN.md)

This document tracks the Android client's coverage of the public and authenticated features of `pickcat.dao3.fun`. It is a development note, not an official API specification.

## Scope

The audit covers public pages, authenticated pages, settings, the composer, and the APIs used by those pages. The site has no public API contract, so endpoints and payloads were verified against live responses.

## Pages reviewed

### Public

- Home feed
- Forum feed
- Category feed
- Topic detail
- Search
- Work list
- Work detail and Codemao player
- User profile
- Login page
- Settings entry points

### Authenticated

- Notifications
- Profile editing
- Account security
- Codemao account linking
- Privacy settings
- Appearance settings
- Personal media
- Media library
- Personal library
- Composer
- Moderation state in the profile

## API inventory

Base path: `/api/v1`

### Session and users

- `GET /session`
- `DELETE /session`
- `GET /users/{id}`
- `PATCH /users/{id}`
- `PUT /users/{id}/avatar`
- `PUT /users/{id}/background`
- `GET /users/{id}/email`
- `GET /users/{id}/phone`
- `GET /users/{id}/oidc-grants`
- `GET /users/{id}/badge-display`
- `PUT /users/{id}/badge-display`
- `GET /users/{id}/badges`
- `GET /following`
- `GET /followers`

### Forum and topics

- `GET /topics`
- `GET /topics/{id}`
- `GET /topics/{id}/posts`
- `GET /topics/{id}/collections`
- `GET /tags`
- `GET /categories`
- `POST /posts`
- `DELETE /posts/{id}`
- `PUT /posts/{id}/like`
- `DELETE /posts/{id}/like`
- `POST /posts/{id}/reports`
- `PUT /topics/{id}/bookmark`
- `DELETE /topics/{id}/bookmark`

### Works

- `GET /works`
- `GET /works/{id}`
- `GET /works/{id}/stats`
- `GET /works/{id}/comments`
- `POST /works/{id}/comments`
- `GET /users/{id}/works`

### Notifications

- `GET /notifications`
- `GET /notifications/unread-count`
- `PUT /notifications/read-all`
- `PUT /notifications/{id}/read`
- Grouped likes endpoint

Notification filters:

- Mentions: `USER_MENTIONED`, `WORK_COMMENT_MENTIONED`
- Replies: `REPLY_CREATED`, `WORK_COMMENT_CREATED`, `WORK_COMMENT_REPLY_CREATED`
- System: `MANAGEMENT_ACTION`, `TOPIC_EVENT`, `LEVEL_CERTIFICATION`, `USER_FOLLOWED`, `TAKEDOWN_APPEAL_DECIDED`, `QUESTION_DUPLICATE_REQUEST_DECIDED`

### Media and files

- `GET /files`
- `POST /files`
- `GET /file-storage`
- `GET /media-library`
- `DELETE /files/{id}`

### Collections and profile content

- `GET /users/{id}/topics`
- `GET /users/{id}/posts`
- `GET /users/{id}/collections`
- `GET /collections/{id}`
- `GET /collections/{id}/topics`
- `GET /users/{id}/favorites`
- `GET /users/{id}/activity`
- `GET /users/{id}/contributions`

### Codemao integration

- `GET /official-account`
- `GET /official-account/works`

## Moderation behavior

`POST /api/v1/posts` is used for both new topics and replies.

A successful submission returns `202 Accepted`:

```json
{
  "submissionId": "…",
  "topicId": "…",
  "postId": "…",
  "status": "PENDING_PROVIDER"
}
```

Topic submission fields observed:

```json
{
  "title": "…",
  "markdown": "…",
  "kind": "DISCUSSION",
  "headerFileId": null,
  "tagIds": ["…"]
}
```

Pending content is not shown as published. The Android client displays a review notice and waits for the server to expose the content.

## Implemented in Android

- Public feed, forum, category, topic, search, work, and profile screens
- Authenticated notifications and grouped likes
- Composer with Markdown toolbar, drafts, tags, and uploads
- Topic post sorting between hot and latest
- Reply and comment moderation notices
- Profile region, bio, privacy lists, and badge display
- Email and Codemao account information
- Background notification sync
- Dynamic color and dark theme
- Wide-screen navigation and two-pane layouts

## Remaining gaps

- Avatar and profile background upload/save
- Username change flow
- Email change, phone binding, password reset, OIDC grants, and account deletion
- Codemao work sync and import
- Topic cover picker, emoji picker, and fullscreen editor preview
- Complete profile review-status list
- Some notifications and comment lists only load the first page

## Verification notes

- The site's login uses an Aliyun captcha and must be completed manually.
- API behavior was verified through an authenticated browser session.
- Test topics and replies were removed with `DELETE /posts/{id}` after verification.
- Android builds are verified with `assembleDebug` and `lintDebug`.
