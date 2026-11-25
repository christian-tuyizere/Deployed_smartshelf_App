package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CommodityReportRow
import com.example.data.local.SaleEntity
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.ReportPeriod
import com.example.ui.ShopUiState
import com.example.util.ReportPdfGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SmartShelf", appName)
  }

  @Test
  fun `daily earnings rows aggregate cash momo credit and profit per date`() {
    val sales = listOf(
      SaleEntity(
        id = "s1", productId = "p1", productName = "Sugar",
        quantity = 2, unitPrice = 1500L, buyingPriceAtSale = 1200L,
        totalAmount = 3000L, profitAmount = 600L,
        paymentMethod = "cash", status = "completed",
        dateKey = "2026-11-24", monthKey = "2026-11"
      ),
      SaleEntity(
        id = "s2", productId = "p2", productName = "Oil",
        quantity = 1, unitPrice = 4500L, buyingPriceAtSale = 4000L,
        totalAmount = 4500L, profitAmount = 500L,
        paymentMethod = "momo", status = "completed",
        dateKey = "2026-11-25", monthKey = "2026-11"
      ),
      SaleEntity(
        id = "s3", productId = "p1", productName = "Sugar",
        quantity = 1, unitPrice = 1500L, buyingPriceAtSale = 1200L,
        totalAmount = 1500L, profitAmount = 300L,
        paymentMethod = "credit", status = "completed",
        dateKey = "2026-11-25", monthKey = "2026-11"
      ),
      SaleEntity(
        id = "s4", productId = "p1", productName = "Sugar",
        quantity = 1, unitPrice = 1500L, buyingPriceAtSale = 1200L,
        totalAmount = 1500L, profitAmount = 300L,
        paymentMethod = "cash", status = "cancelled",
        dateKey = "2026-11-25", monthKey = "2026-11"
      )
    )

    val rows = ReportPdfGenerator.buildDailyEarningsRows(sales)
    assertEquals(2, rows.size)
    assertEquals("2026-11-25", rows[0].dateKey)
    assertEquals(2, rows[0].salesCount)
    assertEquals(6000L, rows[0].totalSalesRwf)
    assertEquals(800L, rows[0].profitRwf)
    assertEquals(4500L, rows[0].momoRwf)
    assertEquals(1500L, rows[0].creditRwf)

    assertEquals("2026-11-24", rows[1].dateKey)
    assertEquals(3000L, rows[1].cashRwf)
    assertEquals(600L, rows[1].profitRwf)
  }

  @Test
  fun `generateBusinessReportPdf creates non-empty valid PDF file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sales = (1..25).map { idx ->
      SaleEntity(
        id = "s$idx", productId = "p1", productName = "Paint 4L",
        quantity = 1, unitPrice = 5000L, buyingPriceAtSale = 4000L,
        totalAmount = 5000L, profitAmount = 1000L,
        paymentMethod = if (idx % 2 == 0) "momo" else "cash",
        status = "completed",
        dateKey = "2026-11-${(idx % 9) + 10}",
        monthKey = "2026-11"
      )
    }
    val state = ShopUiState(
      isLoading = false,
      strings = stringsFor(AppLanguage.EN),
      sales = sales,
      shelfStockValueRwf = 125_000L
    )
    val commodities = listOf(
      CommodityReportRow(
        productId = "p1", productName = "Paint 4L",
        openStock = 30, addedQty = 0, soldQty = 25,
        adjustedQty = 0, closeStock = 5, profitRwf = 25_000L
      )
    )

    val pdf = ReportPdfGenerator.generateBusinessReportPdf(
      context = context,
      state = state,
      period = ReportPeriod.ALL,
      commodityRows = commodities
    )

    assertTrue(pdf.exists())
    assertTrue(pdf.length() > 100L)
    val header = pdf.inputStream().use { stream ->
      ByteArray(4).also { stream.read(it) }.decodeToString()
    }
    assertEquals("%PDF", header)
  }
}
