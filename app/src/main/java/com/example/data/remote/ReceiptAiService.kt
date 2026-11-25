package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

data class ParsedReceiptLine(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val rawName: String,
    val quantity: Int,
    val unit: String,
    val unitBuyingPrice: Long,
    val lineTotal: Long,
    val confidence: String
)

data class ParsedReceiptResult(
    val readable: Boolean,
    val supplierName: String,
    val guidanceMessage: String,
    val items: List<ParsedReceiptLine>,
    val rawJson: String
)

class ReceiptScanTimeoutException(message: String) : IllegalStateException(message)

class ReceiptAiService {
    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "readable" to Schema.boolean(description = "True if the wholesale receipt items and prices can be read"),
            "supplierName" to Schema.string(description = "Wholesale supplier or shop name if visible, empty string otherwise"),
            "guidanceMessage" to Schema.string(description = "Short helpful guidance if unreadable or partial"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "rawName" to Schema.string(description = "Full product name including pack/container size like 4L, 1L, 500ml, 25kg"),
                        "quantity" to Schema.integer(description = "Integer count purchased from QTY column (>= 1)"),
                        "unit" to Schema.enumeration(
                            values = listOf("piece", "kg", "carton", "sack", "bottle", "liter", "pack"),
                            description = "Unit of sale: use 'piece' (or bottle/pack/sack/carton) for pre-packaged containers like 4L tins or 1L cans; only use 'liter' or 'kg' for loose bulk items"
                        ),
                        "unitBuyingPrice" to Schema.integer(description = "Wholesale unit price in RWF (integer)"),
                        "lineTotal" to Schema.integer(description = "Total line cost in RWF (integer)"),
                        "confidence" to Schema.enumeration(
                            values = listOf("high", "medium", "low"),
                            description = "Extraction confidence"
                        )
                    )
                ),
                description = "Extracted line items from the wholesale receipt"
            )
        )
    )

    private val primaryGenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = receiptSchema
        // Use LOW thinking level (MINIMAL is rejected by this model) for fast responses
        thinkingConfig = thinkingConfig {
            thinkingLevel = ThinkingLevel.LOW
        }
    }

    private val fallbackGenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = receiptSchema
    }

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = primaryGenerationConfig
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = fallbackGenerationConfig
        )
    }

    suspend fun compressAndSaveReceiptImage(context: Context, uri: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            val compressStartMs = SystemClock.elapsedRealtime()
            val tempRaw = File(context.cacheDir, "raw_receipt_${System.currentTimeMillis()}.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(IllegalStateException("Could not open selected photo."))
                input.use { src ->
                    FileOutputStream(tempRaw).use { dst ->
                        src.copyTo(dst)
                    }
                }

                val orientationDegrees = readExifRotationDegrees(tempRaw.absolutePath)
                val decoded = BitmapFactory.decodeFile(tempRaw.absolutePath)
                    ?: return@withContext Result.failure(IllegalStateException("Invalid image file."))

                // Rotate upright per EXIF before resizing so both AI and preview see upright text
                val upright = if (orientationDegrees != 0f) {
                    val matrix = Matrix().apply { postRotate(orientationDegrees) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }

                // Aggressively resize to max width 800px (and cap extreme heights) for <2s mobile uploads
                val widthScale = if (upright.width > MAX_UPLOAD_WIDTH_PX) {
                    MAX_UPLOAD_WIDTH_PX.toFloat() / upright.width.toFloat()
                } else 1f
                val heightScale = if (upright.height > MAX_UPLOAD_HEIGHT_PX) {
                    MAX_UPLOAD_HEIGHT_PX.toFloat() / upright.height.toFloat()
                } else 1f
                val scale = min(widthScale, heightScale)

                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        upright,
                        (upright.width * scale).toInt().coerceAtLeast(1),
                        (upright.height * scale).toInt().coerceAtLeast(1),
                        true
                    ).also {
                        if (it !== upright) upright.recycle()
                    }
                } else {
                    upright
                }

                // Compress to 0.6 JPEG quality and ensure target stays under 250KB
                val baos = ByteArrayOutputStream()
                var quality = JPEG_QUALITY_INITIAL
                resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                while (baos.size() > TARGET_MAX_BYTES && quality > 35) {
                    baos.reset()
                    quality -= 15
                    resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                }

                val dir = File(context.filesDir, "queued_receipts").apply { mkdirs() }
                val outFile = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    fos.write(baos.toByteArray())
                }

                val elapsed = SystemClock.elapsedRealtime() - compressStartMs
                Log.d(
                    TAG,
                    "gemini-scan:compress rot=${orientationDegrees.toInt()}deg ${resized.width}x${resized.height}, quality=$quality, size=${outFile.length() / 1024}KB in ${elapsed}ms"
                )

                resized.recycle()
                Result.success(outFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compress receipt image", e)
                Result.failure(e)
            } finally {
                tempRaw.delete()
            }
        }

    suspend fun parseReceiptFile(file: File): Result<ParsedReceiptResult> =
        withContext(Dispatchers.IO) {
            val totalStartMs = SystemClock.elapsedRealtime()
            var bitmap: Bitmap? = null
            try {
                val decodeStartMs = SystemClock.elapsedRealtime()
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: return@withContext Result.failure(IllegalStateException("Receipt image could not be decoded."))
                val decodeMs = SystemClock.elapsedRealtime() - decodeStartMs

                val prompt = """
                    Extract items from this wholesale receipt / RRA EBM invoice (RWF, Kinyarwanda/French/English).
                    Return supplierName, items (rawName, quantity, unit, unitBuyingPrice, lineTotal, confidence), and readable.
                    IMPORTANT unit & quantity rules:
                    - Container or pack sizes inside the description (e.g., "4L", "1L", "5L", "20L", "500ML", "25KG", "500G" in "LA001/4L EG LATEX WHITE PAINT" or "1L AVTOMOTY") describe ONE packaged container — keep the size inside rawName and set unit to "piece" (or "bottle"/"pack"/"sack"/"carton"), NEVER "liter" or "kg".
                    - Set quantity strictly to the invoice QTY count of containers/items, and unitBuyingPrice strictly to the invoice Unit Price (price per 1 packaged item).
                    - Parse formatted amounts like 4,200.00 as integer 4200 (round to nearest whole RWF; ignore .00 decimals). Verify quantity * unitBuyingPrice matches lineTotal.
                    - Only use "liter" or "kg" if the item has no packaged size and is clearly sold loose by bulk liter/kg.
                    - If unitBuyingPrice is missing, compute round(lineTotal / quantity).
                    - If blurry or not a receipt, set readable = false with brief guidanceMessage.
                """.trimIndent()

                val payloadKb = file.length() / 1024L
                Log.d(TAG, "gemini-scan: start (payload=${payloadKb}KB, decode=${decodeMs}ms)")
                val apiStartMs = SystemClock.elapsedRealtime()

                val response = withTimeout(SCAN_TIMEOUT_MS) {
                    val reqContent = content {
                        image(bitmap)
                        text(prompt)
                    }
                    try {
                        withTimeout(PRIMARY_TIMEOUT_MS) {
                            primaryModel.generateContent(reqContent)
                        }
                    } catch (primaryErr: Exception) {
                        if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                            throw primaryErr
                        }
                        Log.w(
                            TAG,
                            "gemini-scan: primary model failed (${primaryErr.message}), falling back to gemini-3.5-flash-lite"
                        )
                        fallbackModel.generateContent(reqContent)
                    }
                }

                val apiElapsedMs = SystemClock.elapsedRealtime() - apiStartMs
                val totalElapsedMs = SystemClock.elapsedRealtime() - totalStartMs
                Log.d(TAG, "gemini-scan: end (api=${apiElapsedMs}ms, total=${totalElapsedMs}ms, payload=${payloadKb}KB)")

                val jsonString = response.text
                    ?: return@withContext Result.failure(IllegalStateException("AI returned an empty response."))

                Result.success(parseJson(jsonString))
            } catch (e: TimeoutCancellationException) {
                val elapsedMs = SystemClock.elapsedRealtime() - totalStartMs
                Log.e(TAG, "gemini-scan: timeout after ${elapsedMs}ms (limit=${SCAN_TIMEOUT_MS}ms)", e)
                Result.failure(
                    ReceiptScanTimeoutException("Receipt scan timed out after 20 seconds.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsedMs = SystemClock.elapsedRealtime() - totalStartMs
                Log.e(TAG, "gemini-scan: failed after ${elapsedMs}ms", e)
                Result.failure(e)
            } finally {
                bitmap?.recycle()
            }
        }

    private fun readExifRotationDegrees(path: String): Float {
        return runCatching {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
    }

    fun parseJson(jsonString: String): ParsedReceiptResult {
        val json = JSONObject(jsonString)
        val readable = json.optBoolean("readable", false)
        val supplierName = json.optString("supplierName", "").trim()
        val guidanceMessage = json.optString("guidanceMessage", "").trim()
        val arr = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val rawName = obj.optString("rawName", "").trim()
            if (rawName.isBlank()) return@mapNotNull null
            val qty = obj.optInt("quantity", 1).coerceAtLeast(1)
            val rawUnit = obj.optString("unit", "piece").lowercase().let {
                if (it in VALID_UNITS) it else "piece"
            }
            val unit = normalizePackagedUnit(rawName, rawUnit)
            var unitPrice = obj.optLong("unitBuyingPrice", 0L).coerceAtLeast(0L)
            var lineTotal = obj.optLong("lineTotal", 0L).coerceAtLeast(0L)
            if (unitPrice == 0L && lineTotal > 0L) unitPrice = lineTotal / qty
            if (lineTotal == 0L && unitPrice > 0L) lineTotal = unitPrice * qty
            val confidence = obj.optString("confidence", "medium")
            ParsedReceiptLine(
                rawName = rawName,
                quantity = qty,
                unit = unit,
                unitBuyingPrice = unitPrice,
                lineTotal = lineTotal,
                confidence = confidence
            )
        }
        return ParsedReceiptResult(
            readable = readable && items.isNotEmpty(),
            supplierName = supplierName,
            guidanceMessage = guidanceMessage,
            items = items,
            rawJson = jsonString
        )
    }

    private companion object {
        const val TAG = "ReceiptAiService"
        const val MAX_UPLOAD_WIDTH_PX = 800
        const val MAX_UPLOAD_HEIGHT_PX = 1600
        const val JPEG_QUALITY_INITIAL = 60
        const val TARGET_MAX_BYTES = 250 * 1024
        const val PRIMARY_TIMEOUT_MS = 10_000L
        const val SCAN_TIMEOUT_MS = 20_000L
        val VALID_UNITS = setOf("piece", "kg", "carton", "sack", "bottle", "liter", "pack")
        val PACKAGED_VOLUME_REGEX = Regex("""(?i)(?:^|[\s/\-])\d+(?:[.,]\d+)?\s*(?:L|LT|LTR|ML)(?:\b|$)""")
        val PACKAGED_WEIGHT_REGEX = Regex("""(?i)(?:^|[\s/\-])\d+(?:[.,]\d+)?\s*(?:KG|G|GR)(?:\b|$)""")

        fun normalizePackagedUnit(rawName: String, extractedUnit: String): String {
            return when {
                extractedUnit == "liter" && PACKAGED_VOLUME_REGEX.containsMatchIn(rawName) -> "piece"
                extractedUnit == "kg" && PACKAGED_WEIGHT_REGEX.containsMatchIn(rawName) -> "piece"
                else -> extractedUnit
            }
        }
    }
}
