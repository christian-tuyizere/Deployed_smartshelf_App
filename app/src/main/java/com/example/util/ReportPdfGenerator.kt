package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.CommodityReportRow
import com.example.data.local.SaleEntity
import com.example.i18n.AppStrings
import com.example.ui.ReportPeriod
import com.example.ui.ShopUiState
import java.io.File
import java.io.FileOutputStream

data class DailyEarningsRow(
    val dateKey: String,
    val salesCount: Int,
    val cashRwf: Long,
    val momoRwf: Long,
    val creditRwf: Long,
    val totalSalesRwf: Long,
    val profitRwf: Long
)

object ReportPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 34f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - 44f

    fun buildDailyEarningsRows(sales: List<SaleEntity>): List<DailyEarningsRow> {
        return sales
            .filter { it.status == "completed" }
            .groupBy { it.dateKey }
            .entries
            .sortedByDescending { it.key }
            .map { (dateKey, daySales) ->
                DailyEarningsRow(
                    dateKey = dateKey,
                    salesCount = daySales.size,
                    cashRwf = daySales.filter { it.paymentMethod == "cash" }.sumOf { it.totalAmount },
                    momoRwf = daySales.filter { it.paymentMethod == "momo" }.sumOf { it.totalAmount },
                    creditRwf = daySales.filter { it.paymentMethod == "credit" }.sumOf { it.totalAmount },
                    totalSalesRwf = daySales.sumOf { it.totalAmount },
                    profitRwf = daySales.sumOf { it.profitAmount }
                )
            }
    }

    fun generateBusinessReportPdf(
        context: Context,
        state: ShopUiState,
        period: ReportPeriod,
        commodityRows: List<CommodityReportRow>
    ): File {
        val strings = state.strings
        val today = KigaliTime.todayDateKey()
        val weekStart = KigaliTime.weekStartDateKey()
        val monthKey = KigaliTime.currentMonthKey()

        val periodSales = state.sales
            .filter { s ->
                s.status == "completed" && when (period) {
                    ReportPeriod.TODAY -> s.dateKey == today
                    ReportPeriod.WEEK -> s.dateKey >= weekStart
                    ReportPeriod.MONTH -> s.dateKey.startsWith(monthKey)
                    ReportPeriod.ALL -> true
                }
            }
            .sortedByDescending { it.createdAtMillis }

        val dailyRows = buildDailyEarningsRows(periodSales)
        val grossProfit = periodSales.sumOf { it.profitAmount }
        val totalSales = periodSales.sumOf { it.totalAmount }
        val cashSales = periodSales.filter { it.paymentMethod == "cash" }.sumOf { it.totalAmount }
        val momoSales = periodSales.filter { it.paymentMethod == "momo" }.sumOf { it.totalAmount }
        val creditSales = periodSales.filter { it.paymentMethod == "credit" }.sumOf { it.totalAmount }

        val periodLabel = when (period) {
            ReportPeriod.TODAY -> "${strings.periodToday} ($today)"
            ReportPeriod.WEEK -> "${strings.periodWeek} ($weekStart -> $today)"
            ReportPeriod.MONTH -> "${strings.periodMonth} ($monthKey)"
            ReportPeriod.ALL -> strings.periodAll
        }

        val shopName = state.profile?.shopName?.ifBlank { strings.appName } ?: strings.appName
        val shopPhone = state.profile?.phone.orEmpty()

        val doc = PdfDocument()
        val writer = PdfPageWriter(doc, shopName, periodLabel, strings)

        try {
            // Header Banner
            writer.drawHeaderBanner(shopName, shopPhone, periodLabel)

            // Financial Summary Cards
            writer.drawSummarySection(
                grossProfit = grossProfit,
                totalSales = totalSales,
                salesCount = periodSales.size,
                cashSales = cashSales,
                momoSales = momoSales,
                creditSales = creditSales,
                shelfValue = state.shelfStockValueRwf,
                owedToMe = state.pendingOwedToMeRwf,
                iOwe = state.pendingIOweRwf
            )

            // Section 1: Daily Breakdown (Documented on Each Date)
            writer.drawSectionHeading(strings.dailyBreakdownTitle)
            if (dailyRows.isEmpty()) {
                writer.drawEmptyNotice(strings.pdfNoSalesInPeriod)
            } else {
                writer.drawDailyTable(dailyRows, totalSales, grossProfit, periodSales.size, cashSales, momoSales, creditSales)
            }

            // Section 2: Product Movement & Profit
            if (commodityRows.isNotEmpty()) {
                writer.drawSectionHeading(strings.commodityTableTitle)
                writer.drawCommodityTable(commodityRows)
            }

            // Section 3: Full Itemized Sales Log (By Date & Time)
            writer.drawSectionHeading(strings.pdfItemizedLogTitle)
            if (periodSales.isEmpty()) {
                writer.drawEmptyNotice(strings.pdfNoSalesInPeriod)
            } else {
                writer.drawItemizedSalesTable(periodSales)
            }

            writer.finishCurrentPage()

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val periodTag = period.name.lowercase()
            val outFile = File(dir, "SmartShelf_Report_${periodTag}_$today.pdf")
            FileOutputStream(outFile).use { fos ->
                doc.writeTo(fos)
            }
            return outFile
        } finally {
            doc.close()
        }
    }

    fun savePdfToDownloads(context: Context, pdfFile: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, pdfFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SmartShelf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                pdfFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }

    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, pdfFile.name))
    }

    fun openPdf(context: Context, pdfFile: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(viewIntent)
            true
        }.getOrElse {
            sharePdf(context, pdfFile)
            false
        }
    }

    private class PdfPageWriter(
        private val doc: PdfDocument,
        private val shopName: String,
        private val periodLabel: String,
        private val strings: AppStrings
    ) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        private val bgHeaderPaint = Paint().apply { color = Color.parseColor("#0F1B14") }
        private val accentPaint = Paint().apply { color = Color.parseColor("#1DB954") }
        private val cardBgPaint = Paint().apply { color = Color.parseColor("#F5F7F5") }
        private val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#D5DDD7")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private val tableHeaderPaint = Paint().apply { color = Color.parseColor("#E6EFE9") }
        private val zebraPaint = Paint().apply { color = Color.parseColor("#FAFCFA") }
        private val dividerPaint = Paint().apply {
            color = Color.parseColor("#DDE3DF")
            strokeWidth = 0.8f
        }

        private val titleWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val subWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7E8D2")
            textSize = 9.5f
        }
        private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111914")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#546159")
            textSize = 8.5f
        }
        private val valueBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111914")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val accentBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0E8A3B")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A2B20")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2923")
            textSize = 8.5f
        }
        private val cellBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111914")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val cellGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0E8A3B")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val cellRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C62828")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        init {
            startNewPage()
        }

        private fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = doc.startPage(info)
            canvas = currentPage!!.canvas
            y = 32f
            if (pageNumber > 1) {
                val c = canvas!!
                c.drawText("$shopName • $periodLabel", MARGIN_X, y, labelPaint)
                y += 10f
                c.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
                y += 14f
            }
        }

        fun finishCurrentPage() {
            val page = currentPage ?: return
            val c = canvas ?: return
            val footerY = PAGE_HEIGHT - 22f
            c.drawLine(MARGIN_X, footerY - 12f, PAGE_WIDTH - MARGIN_X, footerY - 12f, dividerPaint)
            c.drawText("${strings.appName} • ${strings.pdfReportSubtitle}", MARGIN_X, footerY, labelPaint)
            val pageStr = "Page $pageNumber"
            val w = labelPaint.measureText(pageStr)
            c.drawText(pageStr, PAGE_WIDTH - MARGIN_X - w, footerY, labelPaint)
            doc.finishPage(page)
            currentPage = null
            canvas = null
        }

        private fun ensureSpace(neededHeight: Float, onPageBroken: (() -> Unit)? = null) {
            if (y + neededHeight > BOTTOM_LIMIT) {
                startNewPage()
                onPageBroken?.invoke()
            }
        }

        fun drawHeaderBanner(shopName: String, shopPhone: String, periodLabel: String) {
            val c = canvas!!
            val box = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 72f)
            c.drawRoundRect(box, 10f, 10f, bgHeaderPaint)
            // Left accent stripe
            c.drawRoundRect(RectF(MARGIN_X, y, MARGIN_X + 6f, y + 72f), 4f, 4f, accentPaint)

            c.drawText(shopName, MARGIN_X + 18f, y + 25f, titleWhitePaint)
            val subtitle = buildString {
                append(strings.pdfReportSubtitle)
                if (shopPhone.isNotBlank()) append("  •  $shopPhone")
            }
            c.drawText(subtitle, MARGIN_X + 18f, y + 42f, subWhitePaint)

            val generatedStr = "${strings.pdfGeneratedAt}: ${KigaliTime.todayDateKey()} ${KigaliTime.formatTimeHm(System.currentTimeMillis())}"
            c.drawText("$periodLabel  |  $generatedStr", MARGIN_X + 18f, y + 58f, subWhitePaint)
            y += 86f
        }

        fun drawSummarySection(
            grossProfit: Long,
            totalSales: Long,
            salesCount: Int,
            cashSales: Long,
            momoSales: Long,
            creditSales: Long,
            shelfValue: Long,
            owedToMe: Long,
            iOwe: Long
        ) {
            ensureSpace(112f)
            val c = canvas!!
            val halfW = (CONTENT_WIDTH - 10f) / 2f

            // Card 1: Gross Profit (Earned)
            val leftRect = RectF(MARGIN_X, y, MARGIN_X + halfW, y + 50f)
            c.drawRoundRect(leftRect, 8f, 8f, cardBgPaint)
            c.drawRoundRect(leftRect, 8f, 8f, cardBorderPaint)
            c.drawText(strings.grossProfitLabel.uppercase(), MARGIN_X + 12f, y + 17f, labelPaint)
            val profitPaint = if (grossProfit >= 0) accentBoldPaint else Paint(cellRedPaint).apply { textSize = 14f }
            c.drawText(KigaliTime.formatRwf(grossProfit), MARGIN_X + 12f, y + 38f, profitPaint)

            // Card 2: Total Sales & Count
            val rightX = MARGIN_X + halfW + 10f
            val rightRect = RectF(rightX, y, PAGE_WIDTH - MARGIN_X, y + 50f)
            c.drawRoundRect(rightRect, 8f, 8f, cardBgPaint)
            c.drawRoundRect(rightRect, 8f, 8f, cardBorderPaint)
            c.drawText("${strings.totalSalesLabel.uppercase()} ($salesCount)", rightX + 12f, y + 17f, labelPaint)
            c.drawText(KigaliTime.formatRwf(totalSales), rightX + 12f, y + 38f, valueBoldPaint)

            y += 58f

            // Payment & Balance strip
            val stripRect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 40f)
            c.drawRoundRect(stripRect, 8f, 8f, cardBgPaint)
            c.drawRoundRect(stripRect, 8f, 8f, cardBorderPaint)

            val colW = CONTENT_WIDTH / 5f
            val items = listOf(
                strings.cash to KigaliTime.formatCompactRwf(cashSales) + " RWF",
                strings.momo to KigaliTime.formatCompactRwf(momoSales) + " RWF",
                strings.credit to KigaliTime.formatCompactRwf(creditSales) + " RWF",
                strings.shelfStockValueLabel to KigaliTime.formatCompactRwf(shelfValue) + " RWF",
                strings.owedToMe to KigaliTime.formatCompactRwf(owedToMe - iOwe) + " RWF"
            )
            items.forEachIndexed { idx, (lbl, valStr) ->
                val cx = MARGIN_X + 10f + idx * colW
                c.drawText(truncate(lbl, labelPaint, colW - 10f), cx, y + 15f, labelPaint)
                c.drawText(truncate(valStr, cellBoldPaint, colW - 10f), cx, y + 30f, cellBoldPaint)
            }
            y += 54f
        }

        fun drawSectionHeading(title: String) {
            ensureSpace(34f)
            canvas!!.drawText(title, MARGIN_X, y + 12f, sectionPaint)
            y += 20f
        }

        fun drawEmptyNotice(message: String) {
            ensureSpace(30f)
            val c = canvas!!
            val box = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 26f)
            c.drawRoundRect(box, 6f, 6f, cardBgPaint)
            c.drawText(message, MARGIN_X + 10f, y + 17f, cellPaint)
            y += 36f
        }

        fun drawDailyTable(
            rows: List<DailyEarningsRow>,
            totalSales: Long,
            totalProfit: Long,
            totalOrders: Int,
            totalCash: Long,
            totalMomo: Long,
            totalCredit: Long
        ) {
            val cols = floatArrayOf(78f, 42f, 75f, 75f, 70f, 92f, 95f) // sum = 527 = CONTENT_WIDTH
            fun drawHeader() {
                val c = canvas!!
                c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, tableHeaderPaint)
                var x = MARGIN_X + 6f
                val headers = listOf(
                    strings.pdfDateCol,
                    strings.pdfTransactionsCol,
                    strings.cash,
                    strings.momo,
                    strings.credit,
                    strings.totalSalesLabel,
                    strings.grossProfitLabel
                )
                headers.forEachIndexed { i, h ->
                    drawAlignedCell(c, h, x, cols[i] - 8f, y + 13.5f, thPaint, rightAlign = i > 0)
                    x += cols[i]
                }
                y += 20f
            }

            ensureSpace(44f)
            drawHeader()

            rows.forEachIndexed { idx, row ->
                ensureSpace(19f) { drawHeader() }
                val c = canvas!!
                if (idx % 2 == 1) {
                    c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 18f, zebraPaint)
                }
                var x = MARGIN_X + 6f
                drawAlignedCell(c, row.dateKey, x, cols[0] - 8f, y + 12.5f, cellBoldPaint, false); x += cols[0]
                drawAlignedCell(c, "${row.salesCount}", x, cols[1] - 8f, y + 12.5f, cellPaint, true); x += cols[1]
                drawAlignedCell(c, KigaliTime.formatCompactRwf(row.cashRwf), x, cols[2] - 8f, y + 12.5f, cellPaint, true); x += cols[2]
                drawAlignedCell(c, KigaliTime.formatCompactRwf(row.momoRwf), x, cols[3] - 8f, y + 12.5f, cellPaint, true); x += cols[3]
                drawAlignedCell(c, KigaliTime.formatCompactRwf(row.creditRwf), x, cols[4] - 8f, y + 12.5f, cellPaint, true); x += cols[4]
                drawAlignedCell(c, KigaliTime.formatRwf(row.totalSalesRwf), x, cols[5] - 8f, y + 12.5f, cellBoldPaint, true); x += cols[5]
                val pPaint = if (row.profitRwf >= 0) cellGreenPaint else cellRedPaint
                drawAlignedCell(c, KigaliTime.formatRwf(row.profitRwf), x, cols[6] - 8f, y + 12.5f, pPaint, true)
                y += 18f
                c.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
            }

            // Total row
            ensureSpace(22f)
            val c = canvas!!
            c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, tableHeaderPaint)
            var x = MARGIN_X + 6f
            drawAlignedCell(c, strings.totalAmountLabel.uppercase(), x, cols[0] - 8f, y + 13.5f, thPaint, false); x += cols[0]
            drawAlignedCell(c, "$totalOrders", x, cols[1] - 8f, y + 13.5f, thPaint, true); x += cols[1]
            drawAlignedCell(c, KigaliTime.formatCompactRwf(totalCash), x, cols[2] - 8f, y + 13.5f, thPaint, true); x += cols[2]
            drawAlignedCell(c, KigaliTime.formatCompactRwf(totalMomo), x, cols[3] - 8f, y + 13.5f, thPaint, true); x += cols[3]
            drawAlignedCell(c, KigaliTime.formatCompactRwf(totalCredit), x, cols[4] - 8f, y + 13.5f, thPaint, true); x += cols[4]
            drawAlignedCell(c, KigaliTime.formatRwf(totalSales), x, cols[5] - 8f, y + 13.5f, thPaint, true); x += cols[5]
            val totProfitPaint = if (totalProfit >= 0) cellGreenPaint else cellRedPaint
            drawAlignedCell(c, KigaliTime.formatRwf(totalProfit), x, cols[6] - 8f, y + 13.5f, totProfitPaint, true)
            y += 30f
        }

        fun drawCommodityTable(rows: List<CommodityReportRow>) {
            val cols = floatArrayOf(175f, 55f, 55f, 55f, 62f, 125f) // sum = 527
            fun drawHeader() {
                val c = canvas!!
                c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, tableHeaderPaint)
                var x = MARGIN_X + 6f
                val headers = listOf(
                    strings.colProduct,
                    strings.colOpen,
                    strings.colAdded,
                    strings.colSold,
                    strings.colClose,
                    strings.colProfit
                )
                headers.forEachIndexed { i, h ->
                    drawAlignedCell(c, h, x, cols[i] - 8f, y + 13.5f, thPaint, rightAlign = i > 0)
                    x += cols[i]
                }
                y += 20f
            }

            ensureSpace(40f)
            drawHeader()

            rows.forEachIndexed { idx, r ->
                ensureSpace(19f) { drawHeader() }
                val c = canvas!!
                if (idx % 2 == 1) {
                    c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 18f, zebraPaint)
                }
                var x = MARGIN_X + 6f
                drawAlignedCell(c, r.productName, x, cols[0] - 8f, y + 12.5f, cellBoldPaint, false); x += cols[0]
                drawAlignedCell(c, "${r.openStock}", x, cols[1] - 8f, y + 12.5f, cellPaint, true); x += cols[1]
                drawAlignedCell(c, "${r.addedQty}", x, cols[2] - 8f, y + 12.5f, cellPaint, true); x += cols[2]
                drawAlignedCell(c, "${r.soldQty}", x, cols[3] - 8f, y + 12.5f, cellPaint, true); x += cols[3]
                drawAlignedCell(c, "${r.closeStock}", x, cols[4] - 8f, y + 12.5f, cellPaint, true); x += cols[4]
                val pPaint = if (r.profitRwf >= 0) cellGreenPaint else cellRedPaint
                drawAlignedCell(c, KigaliTime.formatRwf(r.profitRwf), x, cols[5] - 8f, y + 12.5f, pPaint, true)
                y += 18f
                c.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
            }
            y += 12f
        }

        fun drawItemizedSalesTable(sales: List<SaleEntity>) {
            val cols = floatArrayOf(92f, 165f, 36f, 54f, 90f, 90f) // sum = 527
            fun drawHeader() {
                val c = canvas!!
                c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, tableHeaderPaint)
                var x = MARGIN_X + 6f
                val headers = listOf(
                    strings.pdfDateCol,
                    strings.colProduct,
                    strings.colSold,
                    strings.paymentMethodLabel,
                    strings.totalAmountLabel,
                    strings.colProfit
                )
                headers.forEachIndexed { i, h ->
                    drawAlignedCell(c, h, x, cols[i] - 8f, y + 13.5f, thPaint, rightAlign = i >= 2 && i != 3)
                    x += cols[i]
                }
                y += 20f
            }

            ensureSpace(40f)
            drawHeader()

            sales.forEachIndexed { idx, s ->
                ensureSpace(19f) { drawHeader() }
                val c = canvas!!
                if (idx % 2 == 1) {
                    c.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 18f, zebraPaint)
                }
                var x = MARGIN_X + 6f
                val dateTime = "${s.dateKey} ${KigaliTime.formatTimeHm(s.createdAtMillis)}"
                drawAlignedCell(c, dateTime, x, cols[0] - 8f, y + 12.5f, cellPaint, false); x += cols[0]
                drawAlignedCell(c, s.productName, x, cols[1] - 8f, y + 12.5f, cellBoldPaint, false); x += cols[1]
                drawAlignedCell(c, "${s.quantity}", x, cols[2] - 8f, y + 12.5f, cellPaint, true); x += cols[2]
                drawAlignedCell(c, s.paymentMethod.uppercase(), x, cols[3] - 8f, y + 12.5f, cellPaint, false); x += cols[3]
                drawAlignedCell(c, KigaliTime.formatRwf(s.totalAmount), x, cols[4] - 8f, y + 12.5f, cellBoldPaint, true); x += cols[4]
                val pPaint = if (s.profitAmount >= 0) cellGreenPaint else cellRedPaint
                drawAlignedCell(c, KigaliTime.formatRwf(s.profitAmount), x, cols[5] - 8f, y + 12.5f, pPaint, true)
                y += 18f
                c.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, dividerPaint)
            }
        }

        private fun drawAlignedCell(
            c: Canvas,
            rawText: String,
            x: Float,
            maxWidth: Float,
            baselineY: Float,
            paint: Paint,
            rightAlign: Boolean
        ) {
            val text = truncate(rawText, paint, maxWidth)
            val drawX = if (rightAlign) {
                (x + maxWidth - paint.measureText(text)).coerceAtLeast(x)
            } else x
            c.drawText(text, drawX, baselineY, paint)
        }

        private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
            if (maxWidth <= 4f || paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "..") > maxWidth) {
                end--
            }
            return text.substring(0, end) + ".."
        }
    }
}
