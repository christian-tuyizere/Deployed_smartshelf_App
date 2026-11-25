package com.example.data.remote

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

enum class AssistantAction {
    SELL,
    VIEW_STOCK,
    ADD_STOCK_OPTIONS,
    SCAN_RECEIPT,
    NEW_PRODUCT,
    RESTOCK,
    SHOPPING_LIST,
    SAVED_RECEIPTS,
    CREDITS,
    REPORTS,
    SETTINGS;

    companion object {
        fun fromWireName(raw: String): AssistantAction? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

data class AssistantChatTurn(
    val isUser: Boolean,
    val text: String
)

data class AssistantReply(
    val text: String,
    val actions: List<AssistantAction>
)

class AssistantAiService {
    private val replySchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Friendly, concise answer in the user's language guiding them inside SmartShelf. Never mention Gemini, Google, or any AI provider/model/API."
            ),
            "actions" to Schema.array(
                items = Schema.enumeration(
                    values = AssistantAction.entries.map { it.name },
                    description = "Matching app screen or action shortcuts to open for the user"
                ),
                description = "0 to 3 matching shortcuts from the allowed list so the user can tap directly to go there"
            )
        )
    )

    private val systemPrompt = content {
        text(
            """
            You are SmartShelf Assistant, the built-in helper inside the SmartShelf shop management app (RWF currency, Kigali retail shops).
            Your job is to:
            1. Direct the user to whatever screen or task they want by giving clear short guidance AND picking the exact matching `actions` buttons (0 to 3).
            2. Explain what they can do in the app and step-by-step how to do it whenever they ask for help.

            STRICT IDENTITY & SECRECY RULE:
            - Never mention "Gemini", "Google AI", "API", "LLM", model names, or any third-party AI service. If asked what powers you, simply say you are SmartShelf's built-in shop assistant.

            AVAILABLE SMARTSHELF SCREENS & ACTIONS (use exact names in `actions`):
            - SELL: Quick Sell screen. Pick a product, set quantity, optionally set a custom/negotiated selling price, choose Cash, MoMo, or Credit (Ideni), and confirm. Includes a 7-second Undo toast after selling.
            - VIEW_STOCK: Full Stock table (Product | In Stock | Price). Users can search products, tap the pencil Edit icon on any row to rename or set/update prices, or tap a row to see Sell, See buying cost, Restock, Adjust, and Edit options.
            - ADD_STOCK_OPTIONS: Opens the 2-way Add Stock menu (Scan a wholesale receipt photo OR Enter manually).
            - SCAN_RECEIPT: Opens the AI receipt scanner. Users take or pick a wholesale receipt photo, review extracted items/quantities/buying prices, or enter items manually while keeping the photo visible. Receipts are saved automatically for future reference.
            - NEW_PRODUCT: Opens the form to manually add a brand-new product (name, unit, buying price, selling price, starting quantity, supplier).
            - RESTOCK: Opens the Restock sheet to add newly purchased quantity to an existing product (Cash, MoMo, or supplier Credit).
            - SHOPPING_LIST: Opens the editable Shopping List. Users can edit item names/quantities/units, remove items, add custom items or quick-add from stock, add or pick a supplier phone number, call the supplier directly, or send the order on WhatsApp.
            - SAVED_RECEIPTS: Opens Saved Receipts & Wholesale Purchase History, including per-product "Price / 1 qty" buying cost lookups ("Reba uko waranguye" / "See buying cost") and full saved receipt photos.
            - CREDITS: Opens the Credits & Debts tab ("Owed to me" vs "I owe"), where users record customer credits or supplier debts, log partial/full payments, and send WhatsApp reminders.
            - REPORTS: Opens Profit & Reports (Today / This week / This month / All time gross profit, total sales, Cash/MoMo/Credit split, shelf stock value, earnings breakdown by date, sales chart, per-product movement table, downloadable PDF report documented per date, and option to cancel recent sales).
            - SETTINGS: Opens Settings (language RW/EN/FR, dark/light theme, daily 20:00 evening stock summary notification, Google Cloud backup/sync, shop name & phone, 4-digit security PIN, and JSON/CSV export).

            RESPONSE STYLE:
            - Reply in the exact language the user writes in (Kinyarwanda, English, or French), defaulting to the app language provided in the context.
            - Keep answers clear, practical, and concise (2 to 5 short sentences or brief numbered steps).
            - Always include 1 to 3 relevant `actions` whenever the user asks to go somewhere, do something, or asks what they can do.
            """.trimIndent()
        )
    }

    private val primaryConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = replySchema
        thinkingConfig = thinkingConfig {
            thinkingLevel = ThinkingLevel.LOW
        }
    }

    private val fallbackConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = replySchema
    }

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = primaryConfig,
            systemInstruction = systemPrompt
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = fallbackConfig,
            systemInstruction = systemPrompt
        )
    }

    suspend fun askAssistant(
        userMessage: String,
        history: List<AssistantChatTurn>,
        shopContextSummary: String,
        languageCode: String
    ): Result<AssistantReply> = withContext(Dispatchers.IO) {
        try {
            val recentHistory = history.takeLast(8).joinToString("\n") { turn ->
                val role = if (turn.isUser) "User" else "Assistant"
                "$role: ${turn.text}"
            }
            val prompt = buildString {
                appendLine("Active app language: $languageCode")
                appendLine("Live shop summary: $shopContextSummary")
                if (recentHistory.isNotBlank()) {
                    appendLine("Recent conversation:")
                    appendLine(recentHistory)
                }
                appendLine("Current user message: $userMessage")
            }

            val response = withTimeout(CHAT_TIMEOUT_MS) {
                try {
                    withTimeout(PRIMARY_TIMEOUT_MS) {
                        primaryModel.generateContent(prompt)
                    }
                } catch (primaryErr: Exception) {
                    if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                        throw primaryErr
                    }
                    Log.w(TAG, "Primary assistant model fallback: ${primaryErr.message}")
                    fallbackModel.generateContent(prompt)
                }
            }

            val jsonString = response.text
                ?: return@withContext Result.failure(IllegalStateException("Empty assistant response"))
            Result.success(parseReply(jsonString))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Assistant request timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Assistant request failed", e)
            Result.failure(e)
        }
    }

    private fun parseReply(jsonString: String): AssistantReply {
        val obj = JSONObject(jsonString)
        val rawReply = obj.optString("reply", "").trim()
        val sanitized = sanitizeReply(rawReply)
        val arr = obj.optJSONArray("actions") ?: JSONArray()
        val actions = (0 until arr.length())
            .mapNotNull { idx -> AssistantAction.fromWireName(arr.optString(idx, "")) }
            .distinct()
            .take(3)
        return AssistantReply(text = sanitized, actions = actions)
    }

    private fun sanitizeReply(text: String): String {
        return text
            .replace(Regex("""(?i)\bgemini\s*(api|ai)?\b"""), "SmartShelf Assistant")
            .trim()
    }

    private companion object {
        const val TAG = "AssistantAiService"
        const val PRIMARY_TIMEOUT_MS = 20_000L
        const val CHAT_TIMEOUT_MS = 45_000L
    }
}
