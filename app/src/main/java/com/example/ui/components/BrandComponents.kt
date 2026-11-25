package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.ChartPoint
import com.example.data.remote.onGoogleSignInClicked
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.stringsFor
import com.example.ui.ShopUiState
import com.example.ui.theme.LocalSmartShelfColors
import com.example.ui.theme.MonoLargeStyle
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.util.KigaliTime

@Composable
fun SmartShelfLogo(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true
) {
    val ext = LocalSmartShelfColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ext.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                // Stylized Bambu-inspired twin shelf & sprout bars in Spotify green
                drawLine(
                    color = ext.accent,
                    start = Offset(w * 0.30f, h * 0.90f),
                    end = Offset(w * 0.30f, h * 0.14f),
                    strokeWidth = w * 0.16f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ext.accentBright,
                    start = Offset(w * 0.30f, h * 0.56f),
                    end = Offset(w * 0.80f, h * 0.32f),
                    strokeWidth = w * 0.15f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ext.accent,
                    start = Offset(w * 0.30f, h * 0.88f),
                    end = Offset(w * 0.84f, h * 0.88f),
                    strokeWidth = w * 0.16f,
                    cap = StrokeCap.Round
                )
            }
        }
        if (showWordmark) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "SmartShelf",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = ext.textPrimary
            )
        }
    }
}

@Composable
fun TopProfitBar(
    state: ShopUiState,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleLanguage: () -> Unit,
    onLinkGoogle: () -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val strings = state.strings
    val shopName = state.profile?.shopName?.ifBlank { strings.appName } ?: strings.appName
    var showTodayProfit by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ext.canvas)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Minimal Header Row: Shop Brand + Compact Profit Pill + Language + Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                SmartShelfLogo(showWordmark = false)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ext.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SyncStatusPill(
                        isOnline = state.isOnline,
                        isLinked = state.firebaseUserUid != null,
                        pendingCount = state.pendingSyncCount,
                        strings = strings
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Compact Profit Peek Pill (Profit stays hidden until tapped)
                Surface(
                    onClick = { showTodayProfit = !showTodayProfit },
                    shape = CircleShape,
                    color = if (showTodayProfit) ext.accent else ext.accentMuted,
                    border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profit_peek_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showTodayProfit) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = strings.todaysProfit,
                            tint = if (showTodayProfit) {
                                if (ext.isDark) Color(0xFF06130A) else Color.White
                            } else ext.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showTodayProfit) strings.hideProfitLabel else strings.profitPillLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            color = if (showTodayProfit) {
                                if (ext.isDark) Color(0xFF06130A) else Color.White
                            } else ext.accent
                        )
                    }
                }

                Surface(
                    onClick = onCycleLanguage,
                    shape = CircleShape,
                    color = ext.elevated,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("language_cycle_button")
                ) {
                    Text(
                        text = state.prefs.language.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = ext.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.settingsTitle,
                        tint = ext.textPrimary
                    )
                }
            }
        }

        // Expandable Profit Card — ONLY visible when user taps the Profit Pill
        AnimatedVisibility(
            visible = showTodayProfit,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenReports)
                    .testTag("top_profit_card"),
                shape = RoundedCornerShape(18.dp),
                color = ext.card,
                border = BorderStroke(1.dp, ext.accent.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ext.accent.copy(alpha = if (ext.isDark) 0.20f else 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(80f, 30f),
                                radius = 420f
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.todaysProfit.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.accent
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = KigaliTime.formatRwf(state.todayProfitRwf),
                            style = MonoLargeStyle.copy(fontSize = 24.sp),
                            color = if (state.todayProfitRwf >= 0) ext.textPrimary else ext.critical
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${strings.todaysSales}: ${KigaliTime.formatRwf(state.todaySalesRwf)}",
                            style = MonoSmallStyle,
                            color = ext.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onOpenReports,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.accent,
                                contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("open_reports_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Assessment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = strings.viewReportsCta,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = { showTodayProfit = false }) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = strings.hideProfitLabel,
                                tint = ext.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusPill(
    isOnline: Boolean,
    isLinked: Boolean,
    pendingCount: Int,
    strings: AppStrings
) {
    val ext = LocalSmartShelfColors.current
    val (icon, text, color) = when {
        !isOnline || !isLinked -> Triple(Icons.Default.CloudOff, strings.offlineMode, ext.textSecondary)
        pendingCount > 0 -> Triple(Icons.Default.CloudUpload, "$pendingCount ${strings.pendingSyncSuffix}", ext.warning)
        else -> Triple(Icons.Default.CloudDone, strings.allSaved, ext.accent)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SalesSplineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    val ext = LocalSmartShelfColors.current
    val maxVal = (points.maxOfOrNull { it.salesRwf } ?: 0L).coerceAtLeast(1000L)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            val padTop = 12f
            val padBottom = 12f
            val usableH = (h - padTop - padBottom).coerceAtLeast(10f)

            // Subtle dashed horizontal grid lines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (step in 0..2) {
                val y = padTop + (usableH * step / 2f)
                drawLine(
                    color = ext.border,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )
            }

            val coords = points.mapIndexed { idx, pt ->
                val x = if (points.size == 1) w / 2f else (idx.toFloat() / (points.size - 1)) * w
                val ratio = (pt.salesRwf.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val y = padTop + usableH * (1f - ratio)
                Offset(x, y)
            }

            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(coords.last().x, h)
                lineTo(coords.first().x, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ext.accent.copy(alpha = 0.28f),
                        Color.Transparent
                    )
                )
            )
            drawPath(
                path = linePath,
                color = ext.accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            coords.forEach { pt ->
                drawCircle(color = ext.accent, radius = 6f, center = pt)
                drawCircle(color = ext.card, radius = 2.5f, center = pt)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = points.firstOrNull()?.label.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = ext.textSecondary
            )
            if (points.size > 2) {
                Text(
                    text = points[points.size / 2].label,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
            Text(
                text = points.lastOrNull()?.label.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = ext.textSecondary
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    initialLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onCompleteLocal: (shopName: String, phone: String, langCode: String, pin: String) -> Unit
) {
    val ext = LocalSmartShelfColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var lang by remember(initialLanguage) { mutableStateOf(initialLanguage) }
    val strings = stringsFor(lang)

    var shopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ext.canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Header with Logo + Language pills (Screenshot 1 & 2 layout)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmartShelfLogo()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppLanguage.entries.forEach { option ->
                            val selected = lang == option
                            Surface(
                                onClick = {
                                    lang = option
                                    onSelectLanguage(option)
                                },
                                shape = CircleShape,
                                color = if (selected) ext.accent else ext.elevated,
                                border = BorderStroke(1.dp, if (selected) ext.accent else ext.border)
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        if (ext.isDark) Color(0xFF06130A) else Color.White
                                    } else ext.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Green micro-eyebrow matching Screenshot 1
                Text(
                    text = strings.retailTagline,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                    color = ext.accent
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = strings.onboardingHeadline,
                    style = MaterialTheme.typography.displaySmall,
                    color = ext.textPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = strings.onboardingSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ext.textSecondary
                )

                Spacer(Modifier.height(28.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ext.card,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text(strings.shopNameLabel) },
                            placeholder = { Text("Kigali Fresh Market") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ext.accent,
                                unfocusedBorderColor = ext.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_shop_name")
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(strings.shopPhoneLabel) },
                            placeholder = { Text("+250 788 ...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ext.accent,
                                unfocusedBorderColor = ext.border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { it.isDigit() }) pin = input
                            },
                            label = { Text(strings.optionalPinHint) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ext.accent,
                                unfocusedBorderColor = ext.border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (authError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.critical
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onCompleteLocal(
                            shopName.ifBlank { "SmartShelf Shop" },
                            phone,
                            lang.code,
                            pin
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.accent,
                        contentColor = if (ext.isDark) Color(0xFF06130A) else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_local_button")
                ) {
                    Text(
                        text = strings.startOnPhoneFirst,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = {
                        isGoogleLoading = true
                        authError = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isGoogleLoading = false
                                onCompleteLocal(
                                    shopName.ifBlank { "SmartShelf Shop" },
                                    phone,
                                    lang.code,
                                    pin
                                )
                            },
                            onAuthError = { msg ->
                                isGoogleLoading = false
                                authError = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isGoogleLoading = false }
                        )
                    },
                    enabled = !isGoogleLoading,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ext.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_google_button")
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ext.accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ArrowOutward,
                            contentDescription = null,
                            tint = ext.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.continueWithGoogle,
                            style = MaterialTheme.typography.titleSmall,
                            color = ext.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinLockScreen(
    strings: AppStrings,
    onVerifyPin: (String) -> Boolean
) {
    val ext = LocalSmartShelfColors.current
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ext.canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = ext.accentMuted,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ext.accent, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = strings.unlockShopTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = ext.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.enter4DigitPin,
                style = MaterialTheme.typography.bodyMedium,
                color = ext.textSecondary
            )
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val filled = idx < entered.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (filled) ext.accent else ext.elevated)
                            .border(1.dp, if (filled) ext.accent else ext.border, CircleShape)
                    )
                }
            }

            if (error) {
                Spacer(Modifier.height(12.dp))
                Text(strings.wrongPinError, color = ext.critical, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(28.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.size(72.dp))
                            } else {
                                Surface(
                                    onClick = {
                                        error = false
                                        if (key == "DEL") {
                                            if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                        } else if (entered.length < 4) {
                                            val next = entered + key
                                            entered = next
                                            if (next.length == 4) {
                                                if (!onVerifyPin(next)) {
                                                    error = true
                                                    entered = ""
                                                }
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    color = ext.card,
                                    border = BorderStroke(1.dp, ext.border),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete",
                                                tint = ext.textPrimary
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                style = MonoLargeStyle.copy(fontSize = 22.sp),
                                                color = ext.textPrimary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
