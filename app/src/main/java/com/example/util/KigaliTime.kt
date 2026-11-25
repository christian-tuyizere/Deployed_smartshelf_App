package com.example.util

import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object KigaliTime {
    val kigaliTimeZone: TimeZone = TimeZone.getTimeZone("Africa/Kigali")

    private fun dateFormat(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = kigaliTimeZone
        }
    }

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String =
        dateFormat("yyyy-MM-dd").format(Date(nowMillis))

    fun currentMonthKey(nowMillis: Long = System.currentTimeMillis()): String =
        dateFormat("yyyy-MM").format(Date(nowMillis))

    fun dateKeyFromMillis(millis: Long): String =
        dateFormat("yyyy-MM-dd").format(Date(millis))

    fun monthKeyFromMillis(millis: Long): String =
        dateFormat("yyyy-MM").format(Date(millis))

    fun formatTimeHm(millis: Long): String =
        dateFormat("HH:mm").format(Date(millis))

    fun formatShortDay(dateKey: String): String {
        return try {
            val parsed = dateFormat("yyyy-MM-dd").parse(dateKey) ?: return dateKey.takeLast(5)
            dateFormat("dd MMM").format(parsed)
        } catch (_: Exception) {
            dateKey.takeLast(5)
        }
    }

    fun weekStartDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(kigaliTimeZone, Locale.US)
        cal.timeInMillis = nowMillis
        cal.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diffFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -diffFromMonday)
        return dateFormat("yyyy-MM-dd").format(cal.time)
    }

    fun lastNDaysKeys(days: Int, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val cal = Calendar.getInstance(kigaliTimeZone, Locale.US)
        cal.timeInMillis = nowMillis
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        val result = mutableListOf<String>()
        val fmt = dateFormat("yyyy-MM-dd")
        repeat(days) {
            result.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    fun daysUntilDateKey(dueDateKey: String, nowMillis: Long = System.currentTimeMillis()): Int? {
        if (dueDateKey.isBlank()) return null
        return try {
            val today = dateFormat("yyyy-MM-dd").parse(todayDateKey(nowMillis)) ?: return null
            val due = dateFormat("yyyy-MM-dd").parse(dueDateKey) ?: return null
            ((due.time - today.time) / 86_400_000L).toInt()
        } catch (_: Exception) {
            null
        }
    }

    fun formatRwf(amount: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.isGroupingUsed = true
        nf.maximumFractionDigits = 0
        return "${nf.format(amount)} RWF"
    }

    fun formatSignedRwf(amount: Long): String {
        val prefix = if (amount > 0) "+" else ""
        return "$prefix${formatRwf(amount)}"
    }

    fun formatCompactRwf(amount: Long): String {
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absVal >= 1_000_000 -> "${sign}${String.format(Locale.US, "%.1f", absVal / 1_000_000.0)}M"
            absVal >= 1_000 -> "${sign}${String.format(Locale.US, "%.0f", absVal / 1_000.0)}k"
            else -> "$amount"
        }
    }

    fun isCriticalLowStock(stock: Int, minStockLevel: Int): Boolean {
        if (stock <= 0) return true
        return minStockLevel > 0 && stock <= (minStockLevel / 2)
    }

    fun isLowStock(stock: Int, minStockLevel: Int): Boolean {
        return stock <= minStockLevel
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun fuzzyScore(query: String, target: String): Int {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return 100
        val t = target.trim().lowercase(Locale.ROOT)
        if (t == q) return 1000
        if (t.startsWith(q)) return 850
        if (t.contains(q)) return 700

        val qTokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (qTokens.isNotEmpty() && qTokens.all { t.contains(it) }) {
            return 600
        }

        // Subsequence match
        var qi = 0
        var ti = 0
        while (qi < q.length && ti < t.length) {
            if (q[qi] == t[ti]) qi++
            ti++
        }
        if (qi == q.length) {
            return 450 - (t.length - q.length).coerceAtMost(200)
        }

        // Token-level edit distance <= 1 for short typos
        val tTokens = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (qt in qTokens) {
            if (qt.length >= 3 && tTokens.any { levenshtein(qt, it) <= 1 || it.contains(qt) }) {
                return 300
            }
        }
        return 0
    }

    private fun levenshtein(a: String, b: String): Int {
        if (abs(a.length - b.length) > 2) return 99
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev
                else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = temp
            }
        }
        return dp[b.length]
    }
}
