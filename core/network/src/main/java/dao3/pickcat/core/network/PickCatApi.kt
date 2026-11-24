package dao3.pickcat.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(
    val status: Int,
    override val message: String,
    val code: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val FORM_MEDIA_TYPE = "multipart/form-data".toMediaType()

class PickCatApi(
    cookieJar: PersistentCookieJar,
    private val onUnauthorized: () -> Unit = {},
    private val onMaintenanceChanged: (Boolean) -> Unit = {},
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ------------------------------------------------------------------ session

    suspend fun getSession(): Session = getJson("/session")

    suspend fun deleteSession() {
        execute("DELETE", "/session")
    }

    // ------------------------------------------------------------------ topics

    suspend fun getTopics(
        sort: String? = null,
        tag: String? = null,
        q: String? = null,
        limit: Int = 20,
        cursor: String? = null,
    ): Page<Topic> = getJson(
        "/topics",
        query = mapOf(
            "sort" to sort,
            "tag" to tag,
            "q" to q,
            "limit" to limit.toString(),
            "cursor" to cursor,
        ),
    )

    suspend fun getTopicRecommendations(
        limit: Int = 20,
        cursor: String? = null,
        scope: String = "all",
    ): Page<Topic> =
        getJson(
            "/topic-recommendations",
            query = mapOf(
                "limit" to limit.toString(),
                "cursor" to cursor,
                "scope" to scope,
            ),
        )

    suspend fun getTopic(id: String): TopicDetail = getJson("/topics/$id")

    suspend fun getTopicPosts(
        topicId: String,
        limit: Int = 50,
        sort: String = "earliest",
        cursor: String? = null,
    ): Page<Post> = getJson(
        "/topics/$topicId/posts",
        query = mapOf("limit" to limit.toString(), "sort" to sort, "cursor" to cursor),
    )

    suspend fun createTopic(
        title: String,
        markdown: String,
        tagIds: List<String>,
        kind: String? = null,
    ): SubmissionResult {
        val response = execute(
            "POST",
            "/posts",
            jsonBody = buildJsonObject {
                put("title", title)
                put("markdown", markdown)
                put("tagIds", buildJsonArray { tagIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                if (!kind.isNullOrBlank()) put("kind", kind)
            },
        )
        return json.decodeFromString(SubmissionResult.serializer(), response)
    }

    suspend fun createReply(
        topicId: String,
        markdown: String,
        replyToPostNumber: Int? = null,
    ): SubmissionResult {
        val response = execute(
            "POST",
            "/posts",
            jsonBody = buildJsonObject {
                put("topicId", topicId)
                put("markdown", markdown)
                if (replyToPostNumber != null) put("replyToPostNumber", replyToPostNumber)
            },
        )
        return json.decodeFromString(SubmissionResult.serializer(), response)
    }

    suspend fun likePost(postId: String, liked: Boolean) {
        execute(if (liked) "PUT" else "DELETE", "/posts/$postId/like")
    }

    suspend fun reportPost(postId: String, reasonCode: String, detail: String? = null) {
        execute(
            "POST",
            "/posts/$postId/reports",
            jsonBody = buildJsonObject {
                put("targetKind", "POST")
                put("reasonCode", reasonCode)
                if (!detail.isNullOrBlank()) put("detail", detail)
            },
        )
    }

    suspend fun createBookmark(bookmarkableType: String, bookmarkableId: String): String {
        val text = execute(
            "POST",
            "/bookmarks",
            jsonBody = buildJsonObject {
                put("bookmarkableType", bookmarkableType)
                put("bookmarkableId", bookmarkableId)
            },
        )
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            root.stringOrNull("id") ?: root.stringOrNull("bookmarkId") ?: ""
        }.getOrDefault("")
    }

    suspend fun deleteBookmark(bookmarkId: String) {
        execute("DELETE", "/bookmarks/$bookmarkId")
    }

    // -------------------------------------------------------------------- users

    suspend fun getUser(id: String): UserProfile = getJson("/users/$id")

    suspend fun updateUserProfile(
        id: String,
        region: String,
        bio: String,
        showFollowingList: Boolean? = null,
        showFollowersList: Boolean? = null,
    ) {
        execute(
            "PATCH",
            "/users/$id",
            jsonBody = buildJsonObject {
                put("region", region)
                put("bio", bio)
                showFollowingList?.let { put("showFollowingList", it) }
                showFollowersList?.let { put("showFollowersList", it) }
            },
        )
    }

    suspend fun followUser(id: String, following: Boolean) {
        execute(if (following) "PUT" else "DELETE", "/users/$id/follow")
    }

    suspend fun getFollowers(id: String, limit: Int = 50, cursor: String? = null): Page<FollowEdge> =
        getJson("/users/$id/followers", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun getFollowing(id: String, limit: Int = 50, cursor: String? = null): Page<FollowEdge> =
        getJson("/users/$id/following", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun getUserTopics(id: String, limit: Int = 20, cursor: String? = null): Page<Topic> =
        getJson("/users/$id/topics", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun getUserWorks(id: String, limit: Int = 20, cursor: String? = null): Page<Work> =
        getJson("/users/$id/works", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun getUserPosts(id: String, limit: Int = 20, cursor: String? = null): Page<Post> =
        getJson(
            "/users/$id/posts",
            query = mapOf("role" to "reply", "limit" to limit.toString(), "cursor" to cursor),
        )

    suspend fun getFeaturedTopics(id: String): Page<Topic> = getJson("/users/$id/featured-topics")

    suspend fun getFeaturedWorks(id: String): Page<Work> = getJson("/users/$id/featured-works")

    suspend fun setFeaturedTopics(id: String, topicIds: List<String>) {
        execute(
            "PUT",
            "/users/$id/featured-topics",
            jsonBody = buildJsonObject {
                put("topicIds", buildJsonArray { topicIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            },
        )
    }

    suspend fun setFeaturedWorks(id: String, workIds: List<String>) {
        execute(
            "PUT",
            "/users/$id/featured-works",
            jsonBody = buildJsonObject {
                put("workIds", buildJsonArray { workIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            },
        )
    }

    suspend fun getProfileActivities(id: String, year: Int, limit: Int = 20): Page<ProfileActivity> =
        getJson(
            "/users/$id/profile-activities",
            query = mapOf("year" to year.toString(), "limit" to limit.toString()),
        )

    suspend fun getLevelContributions(id: String, year: Int): LevelContributions =
        getJson("/users/$id/level-contributions/$year")

    suspend fun getUserBadges(id: String, limit: Int = 12): Page<BadgeGrant> =
        getJson("/users/$id/badges", query = mapOf("limit" to limit.toString()))

    suspend fun getBadgeDisplay(id: String): BadgeDisplay = getJson("/users/$id/badge-display")

    suspend fun getUserEmail(id: String): UserEmail = getJson("/users/$id/email")

    suspend fun getAccountSecurity(id: String): JsonObject = getJson("/users/$id/security")

    suspend fun getAccountSessions(id: String): JsonObject = getJson("/users/$id/sessions")

    suspend fun getManagementAuthorization(): JsonObject =
        getJson("/session/management-write-authorization")

    suspend fun getManagementAuthorizationHeader(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl("/session/management-write-authorization", emptyMap()))
            .header("Accept", "application/json")
            .header("Origin", PICKCAT_ORIGIN)
            .header("Referer", "$PICKCAT_ORIGIN/")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                response.header("Management-Write-Authorization")
            }
        }.getOrNull()
    }

    suspend fun revokeAccountSession(userId: String, sessionId: String, grant: String) {
        execute(
            "DELETE",
            "/users/$userId/sessions/$sessionId",
            headers = mapOf("Security-Operation-Grant" to grant),
        )
    }

    suspend fun revokeAllAccountSessions(userId: String, grant: String) {
        execute(
            "DELETE",
            "/users/$userId/sessions",
            headers = mapOf("Security-Operation-Grant" to grant),
        )
    }

    suspend fun getOfficialAccount(): OfficialAccount = getJson("/official-account")

    suspend fun updateBadgeDisplay(id: String, mode: String, badgeId: String? = null) {
        execute(
            "PUT",
            "/users/$id/badge-display",
            jsonBody = buildJsonObject {
                put("mode", mode)
                if (!badgeId.isNullOrBlank()) put("badgeId", badgeId)
            },
        )
    }

    suspend fun getTopicCollections(id: String, limit: Int = 20, cursor: String? = null): Page<TopicCollection> =
        getJson(
            "/users/$id/topic-collections",
            query = mapOf("limit" to limit.toString(), "cursor" to cursor),
        )

    suspend fun getTopicCollection(id: String): TopicCollection = getJson("/topic-collections/$id")

    suspend fun getTopicCollectionTopics(
        id: String,
        limit: Int = 20,
        cursor: String? = null,
    ): Page<Topic> = getJson(
        "/topic-collections/$id/topics",
        query = mapOf("limit" to limit.toString(), "cursor" to cursor),
    )

    suspend fun reportUser(id: String, reasonCode: String, detail: String? = null) {
        execute(
            "POST",
            "/users/$id/profile-reports",
            jsonBody = buildJsonObject {
                put("targetKind", "USER")
                put("reasonCode", reasonCode)
                if (!detail.isNullOrBlank()) put("detail", detail)
            },
        )
    }

    suspend fun getAvatarPresets(): Page<AvatarPreset> = getJson("/avatar-presets")

    suspend fun getLevelProgress(): LevelProgress = getJson("/level-progress")

    // -------------------------------------------------------------------- works

    suspend fun getWorkRecommendations(
        sort: String = "recommended",
        scope: String = "all",
        limit: Int = 12,
        cursor: String? = null,
    ): Page<Work> = getJson(
        "/work-recommendations",
        query = mapOf("sort" to sort, "scope" to scope, "limit" to limit.toString(), "cursor" to cursor),
    )

    suspend fun getFeaturedWorks(limit: Int = 7): Page<Work> =
        getJson("/featured-works", query = mapOf("limit" to limit.toString()))

    suspend fun getWork(id: String): Work = getJson("/works/$id")

    suspend fun getWorkStats(id: String): WorkStats = getJson("/works/$id/stats")

    suspend fun setWorkLike(id: String, liked: Boolean) {
        execute(if (liked) "PUT" else "DELETE", "/works/$id/like")
    }

    suspend fun setWorkFavorite(id: String, favorited: Boolean) {
        execute(if (favorited) "PUT" else "DELETE", "/works/$id/favorite")
    }

    suspend fun getWorkComments(
        id: String,
        limit: Int = 30,
        cursor: String? = null,
    ): Page<WorkComment> =
        getJson("/works/$id/comments", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun createWorkComment(id: String, markdown: String, replyToCommentNumber: Int? = null) {
        execute(
            "POST",
            "/works/$id/comments",
            jsonBody = buildJsonObject {
                put("markdown", markdown)
                if (replyToCommentNumber != null) put("replyToCommentNumber", replyToCommentNumber)
            },
        )
    }

    suspend fun deleteWorkComment(id: String, commentNumber: Int) {
        execute("DELETE", "/works/$id/comments/$commentNumber")
    }

    suspend fun likeWorkComment(id: String, commentNumber: Int, liked: Boolean) {
        execute(if (liked) "PUT" else "DELETE", "/works/$id/comments/$commentNumber/like")
    }

    // -------------------------------------------------------------- notifications

    suspend fun getNotifications(limit: Int = 20, cursor: String? = null): Page<NotificationItem> =
        getJson("/notifications", query = mapOf("limit" to limit.toString(), "cursor" to cursor))

    suspend fun getNotificationSummary(): NotificationSummary = getJson("/notification-summary")

    suspend fun markNotificationsRead(type: String? = null) {
        execute(
            "PATCH",
            "/notifications",
            query = if (type.isNullOrBlank()) emptyMap() else mapOf("type" to type),
            jsonBody = buildJsonObject { put("read", true) },
        )
    }

    suspend fun getLikeNotificationGroups(limit: Int = 20, cursor: String? = null): Page<LikeGroup> =
        getJson(
            "/like-notification-groups",
            query = mapOf("limit" to limit.toString(), "cursor" to cursor),
        )

    // --------------------------------------------------------------- search/tags

    suspend fun searchTopics(q: String, limit: Int = 20): Page<Topic> =
        getJson("/topics", query = mapOf("q" to q, "limit" to limit.toString()))

    suspend fun searchUsers(q: String, limit: Int = 20): Page<UserProfile> =
        getJson("/search/users", query = mapOf("q" to q, "limit" to limit.toString()))

    suspend fun searchWorks(q: String, limit: Int = 20): Page<Work> =
        getJson("/search/works", query = mapOf("q" to q, "limit" to limit.toString()))

    suspend fun getTags(assignable: Boolean? = null): Page<Tag> =
        getJson("/tags", query = if (assignable == true) mapOf("assignable" to "true") else emptyMap())

    // --------------------------------------------------------------------- files

    suspend fun getFiles(
        state: String,
        cursor: String? = null,
    ): Page<StoredFile> = getJson(
        "/files",
        query = mapOf("scope" to "OWN", "state" to state, "cursor" to cursor),
    )

    suspend fun getStorage(): FileStorage = getJson("/file-storage")

    suspend fun trashFile(id: String) {
        execute("PUT", "/files/$id/deletion", jsonBody = buildJsonObject { })
    }

    suspend fun restoreFile(id: String) {
        execute(
            "PATCH",
            "/files/$id/deletion",
            jsonBody = buildJsonObject { put("state", "RESTORED") },
        )
    }

    suspend fun purgeFile(id: String) {
        execute("PATCH", "/files/$id/deletion", jsonBody = buildJsonObject { })
    }

    suspend fun uploadFile(image: PickedImage): UploadedFile {
        require(image.bytes.size <= MAX_UPLOAD_BYTES) {
            "图片太大（${image.bytes.size / 1024 / 1024} MB），上限 20 MB"
        }
        val fileBody = image.bytes.toRequestBody(image.mimeType.toMediaTypeOrNull())
        val body = MultipartBody.Builder()
            .setType(FORM_MEDIA_TYPE)
            .addFormDataPart("ownership", "PERSONAL")
            .addFormDataPart("file", image.name, fileBody)
            .build()
        val text = execute("POST", "/files", requestBody = body)
        val record = findUploadRecord(runCatching { json.parseToJsonElement(text) }.getOrNull())
            ?: throw ApiException(200, "上传成功但无法识别返回数据")
        val id = record.stringOrNull("id")
            ?: record.stringOrNull("fileId")
            ?: record.stringOrNull("uuid")
            ?: throw ApiException(200, "上传成功但没有拿到文件 ID")
        return UploadedFile(
            id = id,
            url = record.stringOrNull("url") ?: record.stringOrNull("fileUrl") ?: record.stringOrNull("contentUrl"),
            name = record.stringOrNull("name") ?: record.stringOrNull("fileName") ?: image.name,
        )
    }

    // ----------------------------------------------------------------- internals

    private suspend inline fun <reified T> getJson(
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): T {
        val text = execute("GET", path, query)
        return decode(text, kotlinx.serialization.serializer())
    }

    private fun <T> decode(text: String, deserializer: DeserializationStrategy<T>): T {
        return try {
            json.decodeFromString(deserializer, text)
        } catch (error: Exception) {
            throw ApiException(200, "响应格式异常：${error.message?.take(120).orEmpty()}", cause = error)
        }
    }

    private suspend fun execute(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        jsonBody: JsonElement? = null,
        requestBody: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            try {
                return@withContext executeOnce(method, path, query, jsonBody, requestBody, headers)
            } catch (error: ApiException) {
                if (method == "GET" && attempt == 0 && error.status >= 500) {
                    attempt++
                    continue
                }
                throw error
            } catch (error: SocketTimeoutException) {
                if (method == "GET" && attempt == 0) {
                    attempt++
                    continue
                }
                throw ApiException(0, "请求超时，请稍后再试", cause = error)
            } catch (error: IOException) {
                if (method == "GET" && attempt == 0) {
                    attempt++
                    continue
                }
                throw ApiException(0, "无法连接 PickCat：${error.message ?: "网络错误"}", cause = error)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun executeOnce(
        method: String,
        path: String,
        query: Map<String, String?>,
        jsonBody: JsonElement?,
        requestBody: RequestBody?,
        headers: Map<String, String>,
    ): String {
        val url = buildUrl(path, query)
        val body: RequestBody? = when {
            requestBody != null -> requestBody
            jsonBody != null -> json.encodeToString(JsonElement.serializer(), jsonBody).toRequestBody(JSON_MEDIA_TYPE)
            method == "POST" || method == "PUT" || method == "PATCH" ->
                ByteArray(0).toRequestBody(null)
            else -> null
        }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Origin", PICKCAT_ORIGIN)
            .header("Referer", "$PICKCAT_ORIGIN/")
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (method != "GET") {
            builder.header("Idempotency-Key", UUID.randomUUID().toString())
        }
        val request = builder.method(method, body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                onMaintenanceChanged(false)
            } else {
                if (response.code == 503) {
                    onMaintenanceChanged(true)
                }
                if (response.code == 401 && !(method == "GET" && path == "/session")) {
                    onUnauthorized()
                }
                throw parseError(response.code, text)
            }
            return text
        }
    }

    private fun buildUrl(path: String, query: Map<String, String?>): HttpUrl {
        val builder = PICKCAT_API_BASE.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
        for ((key, value) in query) {
            if (value != null && value.isNotEmpty()) builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private fun parseError(status: Int, text: String): ApiException {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val message = sequenceOf(
            root?.stringOrNull("message"),
            root?.stringOrNull("error"),
            root?.stringOrNull("detail"),
        ).firstOrNull { !it.isNullOrBlank() }
            ?: when (status) {
                401 -> "登录状态已失效，请重新登录"
                403 -> "没有权限执行此操作"
                404 -> "内容不存在"
                else -> "请求失败（$status）"
            }
        val localizedMessage = when (status) {
            401 -> "登录状态已失效，请重新登录"
            403 -> permissionMessage(code = root?.stringOrNull("code"), serverMessage = message)
            404 -> "内容不存在"
            else -> message
        }
        val code = root?.stringOrNull("code")
        return ApiException(status, localizedMessage, code)
    }

    private fun permissionMessage(code: String?, serverMessage: String): String = when (code) {
        "USER_SILENCED" -> "账号处于禁言状态，暂时无法执行此操作"
        "USER_SUSPENDED" -> "账号已被停用，无法执行此操作"
        "PARTICIPATION_LEVEL_REQUIRED" -> "当前社区等级不足，暂时无法执行此操作"
        "QUOTA_EXCEEDED" -> "操作次数已达上限，请稍后再试"
        "PERMISSION_DENIED" -> serverMessage.ifBlank { "当前操作权限不足" }
        else -> serverMessage.ifBlank { "当前操作权限不足" }
    }

    private fun findUploadRecord(element: JsonElement?): JsonObject? {
        if (element == null) return null
        when {
            element is JsonObject -> {
                val root = element
                for (key in listOf("file", "data", "item", "result")) {
                    val nested = root[key]
                    if (nested is JsonObject) return nested
                }
                val items = root["items"]
                if (items is kotlinx.serialization.json.JsonArray && items.isNotEmpty()) {
                    return findUploadRecord(items[0])
                }
                return root
            }
            element is kotlinx.serialization.json.JsonArray && element.isNotEmpty() -> {
                return findUploadRecord(element[0])
            }
            else -> return null
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_UPLOAD_BYTES = 20 * 1024 * 1024
    }
}
