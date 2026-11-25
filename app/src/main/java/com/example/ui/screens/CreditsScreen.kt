package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DebtEntity
import com.example.ui.ShopUiState
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime
import java.net.URLEncoder

@Composable
fun CreditsScreen(
    state: ShopUiState,
    onAddDebt: (direction: String, name: String, phone: String, desc: String, amount: Long, dueDate: String) -> Unit,
    onRecordPayment: (DebtEntity, Long, String) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val context = LocalContext.current

    var selectedDirection by remember { mutableStateOf("owed_to_me") }
    var showSettled by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<DebtEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val openDebts = state.debts.filter { it.direction == selectedDirection && it.status == "open" }
    val settledDebts = state.debts.filter { it.direction == selectedDirection && it.status == "paid" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ext.canvas),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sub-tabs: Owed to me vs I owe
        item(key = "debt_tabs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DebtDirectionCard(
                    title = strings.owedToMe,
                    totalRwf = state.pendingOwedToMeRwf,
                    selected = selectedDirection == "owed_to_me",
                    onClick = { selectedDirection = "owed_to_me" },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_owed_to_me")
                )
                DebtDirectionCard(
                    title = strings.iOwe,
                    totalRwf = state.pendingIOweRwf,
                    selected = selectedDirection == "i_owe",
                    onClick = { selectedDirection = "i_owe" },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_i_owe")
                )
            }
        }

        item(key = "add_debt_btn") {
            OutlinedButton(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                border = BorderStroke(1.dp, ext.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_debt_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = ext.accent)
                Spacer(Modifier.width(8.dp))
                Text(strings.addCreditDebt, color = ext.accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (openDebts.isEmpty()) {
            item(key = "empty_debts") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ext.card,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(shape = CircleShape, color = ext.accentMuted, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ext.accent)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(strings.noDebtsTitle, style = MaterialTheme.typography.titleMedium, color = ext.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(strings.noDebtsSubtitle, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                    }
                }
            }
        } else {
            items(openDebts, key = { it.id }) { debt ->
                DebtCard(
                    debt = debt,
                    strings = strings,
                    onRecordPayment = { paymentTarget = debt },
                    onSendWhatsApp = {
                        val remaining = (debt.originalAmount - debt.paidAmount).coerceAtLeast(0L)
                        val shopName = state.profile?.shopName?.ifBlank { strings.appName } ?: strings.appName
                        val msg = strings.whatsappDebtMessageTemplate(
                            debt.partyName,
                            KigaliTime.formatRwf(remaining),
                            shopName
                        )
                        val digits = debt.partyPhone.filter { it.isDigit() }
                        val encoded = URLEncoder.encode(msg, "UTF-8")
                        val url = if (digits.isNotBlank()) "https://wa.me/$digits?text=$encoded"
                        else "https://wa.me/?text=$encoded"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }
        }

        if (settledDebts.isNotEmpty()) {
            item(key = "settled_header") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.card,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettled = !showSettled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${strings.paidSettledSection} (${settledDebts.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = ext.textSecondary
                        )
                        Icon(
                            if (showSettled) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = ext.textSecondary
                        )
                    }
                }
            }

            if (showSettled) {
                items(settledDebts, key = { "settled_${it.id}" }) { debt ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.elevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(debt.partyName, style = MaterialTheme.typography.titleSmall, color = ext.textPrimary)
                                if (debt.description.isNotBlank()) {
                                    Text(debt.description, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                                }
                            }
                            Text(KigaliTime.formatRwf(debt.originalAmount), style = MonoSmallStyle, color = ext.accent)
                        }
                    }
                }
            }
        }
    }

    paymentTarget?.let { target ->
        RecordPaymentDialog(
            debt = target,
            strings = strings,
            onDismiss = { paymentTarget = null },
            onConfirm = { amount, method ->
                onRecordPayment(target, amount, method)
                paymentTarget = null
            }
        )
    }

    if (showAddDialog) {
        AddDebtDialog(
            defaultDirection = selectedDirection,
            strings = strings,
            onDismiss = { showAddDialog = false },
            onConfirm = { dir, name, phone, desc, amt, due ->
                onAddDebt(dir, name, phone, desc, amt, due)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun DebtDirectionCard(
    title: String,
    totalRwf: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartShelfColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) ext.accentMuted else ext.card,
        border = BorderStroke(1.dp, if (selected) ext.accent else ext.border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = if (selected) ext.accent else ext.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(KigaliTime.formatRwf(totalRwf), style = MonoMediumStyle.copy(fontWeight = FontWeight.Bold), color = ext.textPrimary)
        }
    }
}

@Composable
private fun DebtCard(
    debt: DebtEntity,
    strings: com.example.i18n.AppStrings,
    onRecordPayment: () -> Unit,
    onSendWhatsApp: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val remaining = (debt.originalAmount - debt.paidAmount).coerceAtLeast(0L)
    val daysLeft = KigaliTime.daysUntilDateKey(debt.dueDateKey)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ext.card,
        border = BorderStroke(1.dp, ext.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(debt.partyName, style = MaterialTheme.typography.titleMedium, color = ext.textPrimary)
                        if (daysLeft != null) {
                            Spacer(Modifier.width(8.dp))
                            if (daysLeft < 0) {
                                Surface(shape = CircleShape, color = ext.criticalBg) {
                                    Text(
                                        strings.overdueBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = ext.critical,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (daysLeft <= 2) {
                                Surface(shape = CircleShape, color = ext.warningBg) {
                                    Text(
                                        strings.dueSoonBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = ext.warning,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (debt.partyPhone.isNotBlank()) {
                        Text(debt.partyPhone, style = MonoSmallStyle, color = ext.textSecondary)
                    }
                    if (debt.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(debt.description, style = MaterialTheme.typography.bodySmall, color = ext.textSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(KigaliTime.formatRwf(remaining), style = MonoMediumStyle.copy(fontWeight = FontWeight.Bold), color = ext.accent)
                    Text(
                        "${strings.originalLabel}: ${KigaliTime.formatRwf(debt.originalAmount)}",
                        style = MonoSmallStyle,
                        color = ext.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecordPayment,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accent,
                        contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.recordPayment, style = MaterialTheme.typography.labelMedium)
                }

                if (debt.direction == "owed_to_me") {
                    OutlinedButton(
                        onClick = onSendWhatsApp,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ext.border)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = strings.whatsappReminder, tint = ext.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.whatsappReminder, style = MaterialTheme.typography.labelMedium, color = ext.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordPaymentDialog(
    debt: DebtEntity,
    strings: com.example.i18n.AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val remaining = (debt.originalAmount - debt.paidAmount).coerceAtLeast(0L)
    var amountText by remember { mutableStateOf(remaining.toString()) }
    var method by remember { mutableStateOf("cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text("${strings.recordPayment} — ${debt.partyName}", color = ext.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text(strings.amountRwfLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to strings.cash, "momo" to strings.momo).forEach { (key, lbl) ->
                        val selected = method == key
                        Surface(
                            onClick = { method = key },
                            shape = CircleShape,
                            color = if (selected) ext.accent else ext.elevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    lbl,
                                    color = if (selected && ext.isDark) Color(0xFF06130A) else if (selected) Color.White else ext.textPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: remaining
                    if (amt > 0L) onConfirm(amt, method)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ext.accent, contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White)
            ) { Text(strings.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = ext.textSecondary) }
        }
    )
}

@Composable
private fun AddDebtDialog(
    defaultDirection: String,
    strings: com.example.i18n.AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long, String) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    var direction by remember { mutableStateOf(defaultDirection) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ext.card,
        title = { Text(strings.addCreditDebt, color = ext.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("owed_to_me" to strings.owedToMe, "i_owe" to strings.iOwe).forEach { (k, l) ->
                        val sel = direction == k
                        Surface(
                            onClick = { direction = k },
                            shape = CircleShape,
                            color = if (sel) ext.accent else ext.elevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(vertical = 8.dp, horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    l,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel && ext.isDark) Color(0xFF06130A) else if (sel) Color.White else ext.textPrimary
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.partyNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.customerPhoneOptional) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text(strings.amountRwfLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(strings.descriptionLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text(strings.dueDateOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toLongOrNull() ?: 0L
                    if (name.isNotBlank() && amt > 0L) {
                        onConfirm(direction, name, phone, desc, amt, dueDate)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ext.accent, contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White)
            ) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel, color = ext.textSecondary) }
        }
    )
}
