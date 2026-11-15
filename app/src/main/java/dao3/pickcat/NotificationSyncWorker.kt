package dao3.pickcat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dao3.pickcat.core.data.AppContainer
import dao3.pickcat.core.network.NotificationItem
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        ensureChannel(applicationContext)
        if (!container.preferences.preferences.first().backgroundNotifications) {
            return Result.success()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()

        val session = runCatching { container.api.getSession() }.getOrNull()
        if (session?.user == null) return Result.success()

        val page = runCatching { container.api.getNotifications(limit = 20) }.getOrNull()
            ?: return Result.retry()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val previous = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        if (!initialized) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putStringSet(KEY_SEEN, page.items.map { it.id }.toSet())
                .apply()
            return Result.success()
        }

        createChannel(applicationContext)
        createChannel(applicationContext)
        val unseen = page.items
            .filter { !it.read && it.id !in previous }
            .take(5)
        unseen.forEach { item -> postNotification(applicationContext, item) }

        val nextSeen = (page.items.map { it.id }.toSet() + previous).take(200).toSet()
        prefs.edit().putStringSet(KEY_SEEN, nextSeen).apply()
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, item: NotificationItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actor = item.actor?.username ?: "PICKCAT"
        val action = ACTION_LABELS[item.type] ?: "有新的互动"
        val target = item.topic?.title ?: item.work?.workName
        val body = if (!target.isNullOrBlank()) target else "打开 PICKCAT 查看详情"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pickcat)
            .setContentTitle("$actor $action")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification) }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "社区通知",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "回复、点赞、关注和系统通知"
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pickcat_notifications_v2"
        const val GROUP_KEY = "pickcat_notifications"
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        private const val PREFS = "pickcat_notification_sync"
        private const val KEY_SEEN = "seen_notification_ids"
        private const val KEY_INITIALIZED = "initialized"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "社区通知",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "回复、点赞、关注和系统通知"
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        private val ACTION_LABELS = mapOf(
            "REPLY_CREATED" to "回复了你的帖子",
            "WORK_COMMENT_REPLY_CREATED" to "回复了你的作品评论",
            "WORK_COMMENT_CREATED" to "评论了你的作品",
            "USER_MENTIONED" to "在帖子里提到了你",
            "WORK_COMMENT_MENTIONED" to "在作品评论里提到了你",
            "POST_LIKED" to "赞了你的帖子",
            "WORK_LIKED" to "赞了你的作品",
            "WORK_COMMENT_LIKED" to "赞了你的评论",
            "USER_FOLLOWED" to "关注了你",
            "MANAGEMENT_ACTION" to "有一条管理通知",
            "TOPIC_EVENT" to "帖子有新动态",
            "LEVEL_CERTIFICATION" to "等级认证更新",
        )
    }
}

object NotificationSyncScheduler {
    private const val WORK_NAME = "pickcat-notification-sync"

    fun schedule(context: Context) {
        NotificationSyncWorker.ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(context: Context) {
        NotificationSyncWorker.ensureChannel(context)
        val request = androidx.work.OneTimeWorkRequestBuilder<NotificationSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK_NAME}-now",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
