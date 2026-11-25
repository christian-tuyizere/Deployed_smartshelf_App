package com.example.notifications

import android.Manifest
import android.app.AlarmManager
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
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import kotlinx.coroutines.flow.first
import java.util.Calendar

object StockNotificationHelper {
    private const val CHANNEL_ID = "smartshelf_daily_stock"
    private const val DAILY_STOCK_NOTIF_ID = 2001
    private const val DAILY_ALARM_REQUEST_CODE = 2020

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily 20:00 Stock Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily 20:00 PM notification listing products currently in stock."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun next20PmTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun scheduleDailyStockReport(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildDailyReportPendingIntent(context)
        val triggerAt = next20PmTriggerMillis()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancelDailyStockReport(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildDailyReportPendingIntent(context)
        alarmManager.cancel(pendingIntent)
    }

    private fun buildDailyReportPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyStockReportReceiver::class.java).apply {
            action = DailyStockReportReceiver.ACTION_DAILY_STOCK_REPORT
        }
        return PendingIntent.getBroadcast(
            context,
            DAILY_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun notifyDailyStockSummaryNow(context: Context, ignoreDisabledFlag: Boolean = false): Boolean {
        if (!hasNotificationPermission(context)) return false
        val prefs = UserPreferencesRepository(context).prefsFlow.first()
        if (!prefs.onboardingCompleted) return false
        if (!ignoreDisabledFlag && !prefs.stockAlertsEnabled) return false

        val dao = AppDatabase.getInstance(context).shopDao()
        val products = dao.getAllProductsOnce().filter { it.active }
        val movements = dao.getAllMovementsOnce()
        val profile = dao.getShopProfileOnce()
        val stockMap = movements.groupBy { it.productId }
            .mapValues { (_, list) -> list.sumOf { it.quantityDelta } }

        val strings = stringsFor(AppLanguage.fromCode(prefs.language))
        val inStockLines = products.mapNotNull { prod ->
            val liveStock = stockMap[prod.id] ?: prod.cachedStock
            if (liveStock > 0) {
                val unitLabel = strings.unitNames[prod.unit] ?: prod.unit
                "• ${prod.name}: $liveStock $unitLabel"
            } else {
                null
            }
        }

        val shopName = profile?.shopName?.ifBlank { strings.appName } ?: strings.appName
        val title = "$shopName • ${strings.dailyStockNotifTitle}"
        val shortText = if (inStockLines.isEmpty()) {
            strings.dailyStockNotifEmpty
        } else {
            "${strings.dailyStockNotifSummaryHeader} ${inStockLines.size} ${strings.productsCountSuffix}"
        }
        val expandedText = if (inStockLines.isEmpty()) {
            strings.dailyStockNotifEmpty
        } else {
            buildString {
                append(strings.dailyStockNotifSummaryHeader)
                append('\n')
                append(inStockLines.joinToString("\n"))
            }
        }

        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(DAILY_STOCK_NOTIF_ID, notification)
        return true
    }
}
