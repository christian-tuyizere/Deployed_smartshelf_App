package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyStockReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = UserPreferencesRepository(context).prefsFlow.first()
                if (!prefs.onboardingCompleted || !prefs.stockAlertsEnabled) {
                    StockNotificationHelper.cancelDailyStockReport(context)
                    return@launch
                }
                if (action == ACTION_DAILY_STOCK_REPORT) {
                    StockNotificationHelper.notifyDailyStockSummaryNow(context, ignoreDisabledFlag = false)
                }
                // Always schedule the next 20:00 PM occurrence (including after reboot or package update)
                StockNotificationHelper.scheduleDailyStockReport(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_STOCK_REPORT = "com.example.notifications.ACTION_DAILY_STOCK_REPORT"
    }
}
