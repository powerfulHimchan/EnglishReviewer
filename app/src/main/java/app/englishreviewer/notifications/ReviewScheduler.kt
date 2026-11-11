package app.englishreviewer.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.englishreviewer.MainActivity
import app.englishreviewer.R
import app.englishreviewer.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.LocalDate

object ReviewScheduler {
    private const val channelId = "daily-review"
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 100,
        Intent(context, ReviewReceiver::class.java).setAction("app.englishreviewer.REVIEW"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun schedule(context: Context, settings: AppSettings) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if (!settings.reminders) return
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.toInstant().toEpochMilli()
        try {
            if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context))
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context))
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context)) }
    }
    suspend fun notify(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val db = LearningDatabase.get(context)
        val date = LocalDate.now()
        db.ensureReview(date)
        val entries = db.dao().review(date.toString())
        if (entries.isEmpty() || entries.all { it.completed }) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "매일 영어 복습", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 101,
            Intent(context, MainActivity::class.java).putExtra("review", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = entries.joinToString("\n") { it.sentence.analysis()?.corrected ?: it.sentence.original }
        val notification = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_book)
            .setContentTitle("오늘의 영어 ${entries.size}문장")
            .setContentText(entries.first().sentence.original)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(100, notification)
    }
}
class ReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = Settings(context).flow.first()
                if (settings.reminders) {
                    // Restore the next alarm even if notification delivery fails.
                    ReviewScheduler.schedule(context, settings)
                    if (intent.action == "app.englishreviewer.REVIEW") ReviewScheduler.notify(context)
                }
            } finally { pending.finish() }
        }
    }
}
