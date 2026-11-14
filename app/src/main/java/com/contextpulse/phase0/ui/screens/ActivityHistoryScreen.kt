package com.contextpulse.phase0.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.ActivationLogEntry
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ActivityHistoryScreen(
    logs: List<ActivationLogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit
) {
    val theme = LocalContextPulseTheme.current
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val dayNameFormatter = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dayNumFormatter = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val dayKeyFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val weekRangeFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    // Week offset from current week (0 = current week / last 7 days, -1 = previous week)
    var weekOffset by remember { mutableIntStateOf(0) }
    // Only one expanded box at a time
    var expandedLogId by remember { mutableStateOf<String?>(null) }

    val now = remember { System.currentTimeMillis() }
    val validLogs = logs

    // Days in selected week view (ordered Sunday to Saturday or Monday to Sunday / past 7 days)
    val dayBuckets = remember(weekOffset, validLogs) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.add(Calendar.DAY_OF_YEAR, weekOffset * 7)

        val days = mutableListOf<DayDiaryBucket>()
        // 7 days ending with calendar's date, ordered earliest to latest for left-to-right reading
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance()
            dayCal.timeInMillis = calendar.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, -i)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            dayCal.set(Calendar.MINUTE, 0)
            dayCal.set(Calendar.SECOND, 0)
            dayCal.set(Calendar.MILLISECOND, 0)

            val startOfDay = dayCal.timeInMillis
            val endOfDay = startOfDay + (24 * 3600 * 1000L) - 1

            val dayLogs = validLogs.filter { it.timestampMillis in startOfDay..endOfDay }

            // Check if today
            val todayCal = Calendar.getInstance()
            val isToday = dayCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                dayCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

            days.add(
                DayDiaryBucket(
                    dateKey = dayKeyFormatter.format(Date(startOfDay)),
                    dayAbbrev = dayNameFormatter.format(Date(startOfDay)),
                    dayNum = dayNumFormatter.format(Date(startOfDay)),
                    fullTitle = fullDateFormatter.format(Date(startOfDay)),
                    isToday = isToday,
                    entries = dayLogs
                )
            )
        }
        days
    }

    // Currently selected day index in the days of week row (default to today / last day in bucket)
    var selectedDayKey by remember(weekOffset) {
        val initialKey = dayBuckets.find { it.isToday }?.dateKey ?: dayBuckets.lastOrNull()?.dateKey ?: ""
        mutableStateOf(initialKey)
    }

    val selectedBucket = remember(selectedDayKey, dayBuckets) {
        dayBuckets.find { it.dateKey == selectedDayKey } ?: dayBuckets.lastOrNull()
    }

    val weekRangeTitle = remember(weekOffset) {
        val calEnd = Calendar.getInstance()
        calEnd.timeInMillis = now
        calEnd.add(Calendar.DAY_OF_YEAR, weekOffset * 7)

        val calStart = Calendar.getInstance()
        calStart.timeInMillis = calEnd.timeInMillis
        calStart.add(Calendar.DAY_OF_YEAR, -6)

        "${weekRangeFormatter.format(calStart.time)} – ${weekRangeFormatter.format(calEnd.time)}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header - pinned cleanly below status bar
        Row(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AUDIT LOG",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Context Switch Diary",
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearLogs,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 640.dp

            val calendarNavigator: @Composable () -> Unit = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        border = BorderStroke(1.dp, theme.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    weekOffset -= 1
                                    expandedLogId = null
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Week",
                                    tint = theme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val weeksBack = -weekOffset
                                Text(
                                    text = when (weeksBack) {
                                        0 -> "CURRENT WEEK"
                                        1 -> "1 WEEK AGO"
                                        else -> "$weeksBack WEEKS AGO"
                                    },
                                    color = theme.accent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = weekRangeTitle,
                                    color = theme.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (weekOffset < 0) {
                                        weekOffset += 1
                                        expandedLogId = null
                                    }
                                },
                                enabled = weekOffset < 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Week",
                                    tint = if (weekOffset < 0) theme.primary else theme.border
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayBuckets.forEach { bucket ->
                            val isSelected = bucket.dateKey == selectedBucket?.dateKey
                            val switchCount = bucket.entries.size

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedDayKey = bucket.dateKey
                                        expandedLogId = null
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) theme.primary else theme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = when {
                                        isSelected -> theme.primary
                                        bucket.isToday -> theme.accent
                                        else -> theme.border
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = bucket.dayAbbrev.uppercase(),
                                        color = if (isSelected) theme.onPrimary.copy(alpha = 0.85f) else theme.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = bucket.dayNum,
                                        color = if (isSelected) theme.onPrimary else theme.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))

                                    if (switchCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) theme.onPrimary.copy(alpha = 0.25f) else theme.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$switchCount",
                                                color = if (isSelected) theme.onPrimary else theme.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) theme.onPrimary.copy(alpha = 0.4f) else Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    selectedBucket?.let { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (bucket.isToday) theme.primary else theme.accent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (bucket.isToday) "${bucket.fullTitle} (Today)" else bucket.fullTitle,
                                    color = theme.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = if (bucket.entries.size == 1) "1 switch" else "${bucket.entries.size} switches",
                                color = theme.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val entriesPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Box(modifier = paneModifier) {
                    val bucket = selectedBucket
                    if (bucket == null || bucket.entries.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            border = BorderStroke(1.dp, theme.border)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.EventBusy,
                                        contentDescription = null,
                                        tint = theme.accent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Switches on This Day",
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Select another day above to inspect recorded context activities.",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            items(bucket.entries, key = { it.id }) { entry ->
                                val isExpanded = expandedLogId == entry.id
                                val timeStr = remember(entry.timestampMillis) {
                                    timeFormatter.format(Date(entry.timestampMillis))
                                }
                                val sourceIcon = when (entry.triggerSource) {
                                    TriggerSource.BLE_BEACON -> Icons.Outlined.BluetoothSearching
                                    TriggerSource.NFC, TriggerSource.NFC_TAG -> Icons.Outlined.Nfc
                                    TriggerSource.QR_CODE -> Icons.Outlined.QrCode2
                                    TriggerSource.MANUAL -> Icons.Outlined.TouchApp
                                    TriggerSource.SYSTEM, TriggerSource.SYSTEM_RESTORE -> Icons.Outlined.SettingsSuggest
                                }

                                val transitionTitle = when {
                                    entry.fromRoomName != null && entry.toRoomName != null ->
                                        "${entry.fromRoomName} → ${entry.toRoomName}"
                                    entry.fromRoomName == null && entry.toRoomName != null ->
                                        "Activated ${entry.toRoomName}"
                                    entry.fromRoomName != null && entry.toRoomName == null ->
                                        "Deactivated ${entry.fromRoomName}"
                                    else -> "Baseline Restored"
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            expandedLogId = if (isExpanded) null else entry.id
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isExpanded) theme.surfaceVariant else theme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isExpanded) 1.5.dp else 1.dp,
                                        color = if (isExpanded) theme.primary else theme.border
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(theme.primary.copy(alpha = 0.14f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = sourceIcon,
                                                        contentDescription = null,
                                                        tint = theme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = transitionTitle,
                                                        color = theme.textPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                                                    )
                                                    if (!isExpanded) {
                                                        Text(
                                                            text = "${entry.triggerSource.label} • ${entry.reason}",
                                                            color = theme.textSecondary,
                                                            fontSize = 11.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = timeStr,
                                                    color = theme.accent,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand to read full description",
                                                    tint = theme.textSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(
                                                color = theme.border.copy(alpha = 0.5f),
                                                thickness = 0.8.dp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Text(
                                                text = "FULL EVENT DETAILS",
                                                color = theme.accent,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = entry.reason.ifBlank { "Triggered via ${entry.triggerSource.label}" },
                                                color = theme.textPrimary,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Source: ${entry.triggerSource.label}",
                                                    color = theme.tertiary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Logged at $timeStr",
                                                    color = theme.textSecondary,
                                                    fontSize = 11.sp
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

            if (isWide) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        calendarNavigator()
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    entriesPane(Modifier.weight(0.55f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                ) {
                    calendarNavigator()
                    Spacer(modifier = Modifier.height(10.dp))
                    entriesPane(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

private data class DayDiaryBucket(
    val dateKey: String,
    val dayAbbrev: String,
    val dayNum: String,
    val fullTitle: String,
    val isToday: Boolean,
    val entries: List<ActivationLogEntry>
)
