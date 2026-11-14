package dao3.pickcat.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val PICKCAT_ORIGIN = "https://pickcat.dao3.fun"
const val PICKCAT_API_BASE = "$PICKCAT_ORIGIN/api/v1"

@Serializable
data class Avatar(
    val type: String? = null,
    val id: JsonElement? = null,
    val url: String? = null,
)

@Serializable
data class BadgeImage(
    val fileId: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class Badge(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val image: BadgeImage? = null,
)

@Serializable
data class UserRef(
    val id: String = "",
    val username: String = "",
    val avatar: Avatar? = null,
    val displayedBadge: Badge? = null,
)

@Serializable
data class Tag(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val description: String? = null,
)

@Serializable
data class PageInfo(
    val hasNextPage: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val pageInfo: PageInfo? = null,
    // A couple of endpoints have historically returned either the cursor at the
    // top level or used `nextCursor` directly. Support both without changing the UI.
    val nextCursor: String? = null,
    val hasNextPage: Boolean? = null,
) {
    val cursor: String?
        get() = pageInfo?.nextCursor ?: nextCursor
    val canLoadMore: Boolean
        get() = pageInfo?.hasNextPage ?: hasNextPage ?: (cursor != null)
}

@Serializable
data class ViewerCapabilities(
    val canEdit: Boolean? = null,
    val canReply: Boolean? = null,
    val canDelete: Boolean? = null,
    val canBookmark: Boolean? = null,
    val canClose: Boolean? = null,
    val canPin: Boolean? = null,
    val canLike: Boolean? = null,
    val canReport: Boolean? = null,
    val canSelectAnswer: Boolean? = null,
    val canUnpin: Boolean? = null,
    val canPinGlobally: Boolean? = null,
    val canReopen: Boolean? = null,
    val canUnselectAnswer: Boolean? = null,
    val canClearDuplicate: Boolean? = null,
    val canMarkDuplicate: Boolean? = null,
    val pinnableTagIds: List<String> = emptyList(),
)

@Serializable
data class ViewerState(
    val liked: Boolean? = null,
    val bookmarked: Boolean? = null,
    val bookmarkId: String? = null,
    val favorited: Boolean? = null,
    val following: Boolean? = null,
    val followedBy: Boolean? = null,
    val canFollow: Boolean? = null,
    val canReport: Boolean? = null,
    val selectedAnswer: Boolean? = null,
)

@Serializable
data class Topic(
    val id: String = "",
    val title: String = "",
    val kind: String = "DISCUSSION",
    val excerpt: String? = null,
    val author: UserRef = UserRef(),
    val tags: List<Tag> = emptyList(),
    val replyCount: Int = 0,
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val bookmarkCount: Int = 0,
    val closedAt: String? = null,
    val pinned: Boolean = false,
    val pinnedGlobally: Boolean = false,
    val pinnedTagId: String? = null,
    val pinnedAt: String? = null,
    val pinnedUntil: String? = null,
    val lastActivityAt: String? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val collection: TopicCollectionRef? = null,
    val match: TopicMatch? = null,
)

@Serializable
data class TopicCollectionRef(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class TopicCollection(
    val id: String = "",
    val authorId: String = "",
    val name: String = "",
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SubmissionResult(
    val submissionId: String = "",
    val topicId: String = "",
    val postId: String = "",
    val status: String = "",
) {
    val pendingReview: Boolean
        get() = status == "PENDING_PROVIDER" || status.startsWith("PENDING")
}

@Serializable
data class TopicMatch(
    val postId: String? = null,
    val postNumber: Int? = null,
    val author: UserRef? = null,
    val createdAt: String? = null,
    val excerpt: String? = null,
)

@Serializable
data class Post(
    val id: String = "",
    val topicId: String = "",
    val postNumber: Int = 0,
    val replyToPostNumber: Int? = null,
    val deleted: Boolean = false,
    val children: List<Post> = emptyList(),
    val currentRevision: Int? = null,
    val likeCount: Int = 0,
    val pinned: Boolean = false,
    val cookedHtml: String = "",
    val author: UserRef = UserRef(),
    val createdAt: String = "",
    val editedAt: String? = null,
    val viewerCapabilities: ViewerCapabilities? = null,
    val viewerState: ViewerState? = null,
    val replyCount: Int? = null,
    val viewCount: Int? = null,
    val bookmarkCount: Int? = null,
    val topic: TopicRef? = null,
)

@Serializable
data class TopicRef(
    val id: String = "",
    val title: String = "",
)

@Serializable
data class TopicDetail(
    val id: String = "",
    val title: String = "",
    val kind: String = "DISCUSSION",
    val excerpt: String? = null,
    val author: UserRef = UserRef(),
    val tags: List<Tag> = emptyList(),
    val replyCount: Int = 0,
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val bookmarkCount: Int = 0,
    val closedAt: String? = null,
    val pinned: Boolean = false,
    val pinnedGlobally: Boolean = false,
    val lastActivityAt: String? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val firstPost: Post? = null,
    val viewerCapabilities: ViewerCapabilities? = null,
    val viewerState: ViewerState? = null,
    val repliesTruncated: Boolean? = null,
    val collection: TopicCollectionRef? = null,
) {
    fun asTopic(): Topic = Topic(
        id = id,
        title = title,
        kind = kind,
        excerpt = excerpt,
        author = author,
        tags = tags,
        replyCount = replyCount,
        viewCount = viewCount,
        likeCount = likeCount,
        bookmarkCount = bookmarkCount,
        closedAt = closedAt,
        pinned = pinned,
        pinnedGlobally = pinnedGlobally,
        lastActivityAt = lastActivityAt,
        createdAt = createdAt,
        editedAt = editedAt,
        collection = collection,
    )
}

@Serializable
data class FollowEdge(
    val user: UserProfile = UserProfile(),
    val followedAt: String? = null,
)

@Serializable
data class UserLevel(
    val current: Int = 1,
)

@Serializable
data class UserStats(
    val followers: Int = 0,
    val following: Int = 0,
    val topics: Int = 0,
    val replies: Int = 0,
)

@Serializable
data class UserProfile(
    val id: String = "",
    val username: String = "",
    val avatar: Avatar? = null,
    val bio: String? = null,
    val region: String? = null,
    val showFollowingList: Boolean? = null,
    val showFollowersList: Boolean? = null,
    val backgroundFileId: String? = null,
    val profileVersion: String? = null,
    val createdAt: String? = null,
    val level: UserLevel = UserLevel(),
    val stats: UserStats = UserStats(),
    val viewerState: ViewerState? = null,
    val displayedBadge: Badge? = null,
)

@Serializable
data class SessionUser(
    val id: String = "",
    val username: String = "",
    val createdAt: String? = null,
    val avatar: Avatar? = null,
    val tokenBalance: Int? = null,
    val level: UserLevel = UserLevel(),
    val effectivePermissions: List<SessionPermission> = emptyList(),
    val displayedBadge: Badge? = null,
)

@Serializable
data class SessionPermission(
    val key: String = "",
    val scopes: List<JsonElement> = emptyList(),
)

@Serializable
data class Session(
    val user: SessionUser? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val silence: JsonElement? = null,
)

@Serializable
data class Work(
    val id: String = "",
    val sourceWorkId: String = "",
    val sourceUserId: String? = null,
    val author: UserRef = UserRef(),
    val workName: String = "",
    val type: String = "",
    val forkEnable: Boolean? = null,
    val isForked: Boolean? = null,
    val parentUuid: String? = null,
    val operation: String? = null,
    val description: String? = null,
    val preview: String? = null,
    val screenshotCoverUrl: String? = null,
    val viewCount: Int = 0,
    val coinCount: Int = 0,
    val likeCount: Int = 0,
    val favoriteCount: Int = 0,
    val commentCount: Int = 0,
    val published: Boolean? = null,
    val lastSyncedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val excerpt: String? = null,
    val viewerState: ViewerState? = null,
    val reason: String? = null,
) {
    val coverUrl: String?
        get() = screenshotCoverUrl?.takeIf { it.isNotBlank() } ?: preview?.takeIf { it.isNotBlank() }
}

@Serializable
data class WorkStats(
    val id: String = "",
    val viewCount: Int = 0,
    val coinCount: Int = 0,
    val likeCount: Int = 0,
    val favoriteCount: Int = 0,
    val commentCount: Int = 0,
)

@Serializable
data class WorkComment(
    val commentNumber: Int = 0,
    val workId: String? = null,
    val markdown: String? = null,
    val cookedHtml: String? = null,
    val content: String? = null,
    val author: UserRef = UserRef(),
    val likeCount: Int = 0,
    val replyToCommentNumber: Int? = null,
    val createdAt: String = "",
    val editedAt: String? = null,
    val deleted: Boolean = false,
    val children: List<WorkComment> = emptyList(),
    val viewerState: ViewerState? = null,
    val viewerCapabilities: ViewerCapabilities? = null,
) {
    val displayHtml: String
        get() = cookedHtml?.takeIf { it.isNotBlank() }
            ?: markdown?.takeIf { it.isNotBlank() }
            ?: content.orEmpty()
}

@Serializable
data class NotificationSummary(
    val unreadCount: Int = 0,
    val unreadCountByType: Map<String, Int> = emptyMap(),
)

@Serializable
data class NotificationDetails(
    val actor: UserRef? = null,
    val topic: TopicRef? = null,
    val post: PostRef? = null,
    val work: WorkRef? = null,
    val payload: JsonElement? = null,
)

@Serializable
data class PostRef(
    val id: String? = null,
    val postNumber: Int? = null,
)

@Serializable
data class WorkRef(
    val id: String = "",
    val workName: String = "",
)

@Serializable
data class NotificationItem(
    val id: String = "",
    val type: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
    val actor: UserRef? = null,
    val topic: TopicRef? = null,
    val post: PostRef? = null,
    val work: WorkRef? = null,
    val payload: JsonElement? = null,
    val details: NotificationDetails? = null,
) {
    fun normalized(): NotificationItem = copy(
        actor = actor ?: details?.actor,
        topic = topic ?: details?.topic,
        post = post ?: details?.post,
        work = work ?: details?.work,
    )
}

@Serializable
data class LikeGroup(
    val groupKey: String = "",
    val topicTitle: String? = null,
    val workName: String? = null,
    val count: Int = 0,
    val likers: List<UserRef> = emptyList(),
    val createdAt: String? = null,
    val topic: TopicRef? = null,
    val work: WorkRef? = null,
)

@Serializable
data class LevelRequirement(
    val current: Double = 0.0,
    val required: Double = 0.0,
    val met: Boolean = false,
)

@Serializable
data class NextLevel(
    val level: Int = 0,
    val eligible: Boolean = false,
    val scores: Map<String, LevelRequirement> = emptyMap(),
    val familiarity: Map<String, LevelRequirement> = emptyMap(),
)

@Serializable
data class Familiarity(
    val daysSinceEntranceExam: Int = 0,
    val validVisitDays: Int = 0,
    val topicsEntered: Int = 0,
    val postsRead: Int = 0,
    val effectiveReadingSeconds: Long = 0,
)

@Serializable
data class LevelScores(
    val ability: Int = 0,
    val responsibility: Int = 0,
    val care: Int = 0,
)

@Serializable
data class LevelProgress(
    val currentLevel: Int = 1,
    val scores: LevelScores = LevelScores(),
    val nextLevel: NextLevel? = null,
    val familiarity: Familiarity? = null,
)

@Serializable
data class ProfileActivity(
    val id: String = "",
    val kind: String = "",
    val occurredAt: String = "",
    val title: String = "",
    val level: Int? = null,
)

@Serializable
data class LevelContribution(
    val date: String? = null,
    val count: Int? = null,
    val level: Int? = null,
    val ability: Int = 0,
    val responsibility: Int = 0,
    val care: Int = 0,
) {
    val total: Int
        get() = count ?: (ability + responsibility + care).takeIf { it > 0 } ?: level ?: 0
}

@Serializable
data class LevelContributions(
    val days: List<LevelContribution> = emptyList(),
    val year: Int? = null,
)

@Serializable
data class BadgeGrant(
    val badgeId: String = "",
    val badge: Badge? = null,
)

@Serializable
data class BadgeDisplay(
    val mode: String = "AUTO_LATEST",
    val badgeId: String? = null,
    val displayedBadge: Badge? = null,
)

@Serializable
data class UserEmail(
    val email: String = "",
    val verifiedAt: String? = null,
    val nextChangeAt: String? = null,
    val rebindCooldownDays: Int = 0,
)

@Serializable
data class OfficialAccount(
    val sourceUserId: String = "",
    val lastSyncedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AvatarPreset(
    val id: JsonElement? = null,
    val url: String? = null,
    val type: String? = null,
)

@Serializable
data class StoredFile(
    val id: String = "",
    val name: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val createdAt: String? = null,
    val deletedAt: String? = null,
    val canRestore: Boolean? = null,
)

@Serializable
data class FileStorage(
    val limitBytes: Long = 0,
    val remainingBytes: Long = 0,
    val usedBytes: Long? = null,
) {
    companion object {
        const val UNLIMITED = 9_007_199_254_740_991L // 2^53 - 1
    }
}

@Serializable
data class UploadedFile(
    val id: String,
    val url: String? = null,
    val name: String? = null,
)

data class PickedImage(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)
