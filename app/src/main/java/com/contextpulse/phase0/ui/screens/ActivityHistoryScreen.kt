package com.contextpulse.phase0.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.ProfileActivationLog
import com.contextpulse.phase0.model.TriggerSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.contextpulse.phase0.ui.components.contextPulseLazyScrollbar

/**
 * Weeklong calendar view with 7-day retention policy and detailed profile activation event logs.
 */
@Composable
fun ActivityHistoryScreen(
    activationLogs: List<ProfileActivationLog>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    // Week navigation offset (0 = current week, -1 = previous week, etc.)
    var weekOffset by remember { mutableStateOf(0) }

    // Current time reference
    val todayCalendar = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    // Selected day in milliseconds (midnight of selected day)
    var selectedDayMillis by remember { mutableStateOf(todayCalendar.timeInMillis) }
    var expandedLogIds by remember { mutableStateOf(setOf<String>()) }

    // Calculate the 7 days of the currently viewed week
    val weekDays = remember(weekOffset) {
        val list = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayCalendar.timeInMillis
            // First day of current week
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0 until 7) {
            list.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        list
    }

    // Week header text (e.g. "Oct 26 – Nov 1, 2026")
    val weekHeaderFormat = SimpleDateFormat("MMM d", Locale.US)
    val yearFormat = SimpleDateFormat("yyyy", Locale.US)
    val weekHeaderText = remember(weekDays) {
        val start = weekDays.first()
        val end = weekDays.last()
        "${weekHeaderFormat.format(Date(start))} – ${weekHeaderFormat.format(Date(end))}, ${yearFormat.format(Date(end))}"
    }

    // 7-day policy cutoff: 7 days before today midnight
    val policyCutoffMillis = remember {
        Calendar.getInstance().apply {
            timeInMillis = todayCalendar.timeInMillis
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Check if the selected day is outside the 7-day retention policy
    val isDayOlderThanPolicy = selectedDayMillis < policyCutoffMillis

    // Filter events for the selected day
    val selectedDayEvents = remember(selectedDayMillis, activationLogs) {
        val startOfDay = selectedDayMillis
        val endOfDay = selectedDayMillis + 24 * 60 * 60 * 1000L - 1L
        activationLogs.filter { it.timestamp in startOfDay..endOfDay }
    }

    // Count events for each day of the visible week
    val eventCountsByDay = remember(weekDays, activationLogs) {
        weekDays.associateWith { dayMillis ->
            val startOfDay = dayMillis
            val endOfDay = dayMillis + 24 * 60 * 60 * 1000L - 1L
            activationLogs.count { it.timestamp in startOfDay..endOfDay }
        }
    }

    val dayNameFormat = SimpleDateFormat("EEE", Locale.US)
    val dayNumberFormat = SimpleDateFormat("d", Locale.US)
    val fullDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)

    val theme = com.contextpulse.phase0.ui.theme.LocalContextPulseTheme.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .contextPulseLazyScrollbar(listState)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Navigation Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Activity History",
                        color = theme.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Weeklong calendar & profile switch log",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        // Week Switcher Controls (< Week Date Range >)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            weekOffset -= 1
                            selectedDayMillis -= 7 * 24 * 60 * 60 * 1000L
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous week",
                            tint = theme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (weekOffset == 0) "Current Week" else if (weekOffset == -1) "Last Week" else "${-weekOffset} Weeks Ago",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = weekHeaderText,
                            color = theme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = {
                            if (weekOffset < 0) {
                                weekOffset += 1
                                selectedDayMillis += 7 * 24 * 60 * 60 * 1000L
                            }
                        },
                        enabled = weekOffset < 0,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next week",
                            tint = if (weekOffset < 0) theme.primary else theme.textSecondary.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // 7-Day Weeklong Calendar Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekDays.forEach { dayMillis ->
                    val isSelected = (dayMillis == selectedDayMillis)
                    val isToday = (dayMillis == todayCalendar.timeInMillis)
                    val count = eventCountsByDay[dayMillis] ?: 0
                    val isDayExpired = dayMillis < policyCutoffMillis

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isSelected -> theme.primary
                                    isDayExpired -> theme.surface.copy(alpha = 0.45f)
                                    else -> theme.surface
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isSelected -> theme.primary
                                    isToday -> theme.primary.copy(alpha = 0.5f)
                                    else -> theme.border
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedDayMillis = dayMillis
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dayNameFormat.format(Date(dayMillis)).uppercase(Locale.US),
                                color = when {
                                    isSelected -> theme.onPrimary
                                    isDayExpired -> theme.textSecondary.copy(alpha = 0.5f)
                                    else -> theme.textSecondary
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = dayNumberFormat.format(Date(dayMillis)),
                                color = when {
                                    isSelected -> theme.onPrimary
                                    isDayExpired -> theme.textSecondary.copy(alpha = 0.4f)
                                    else -> theme.textPrimary
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Event Indicator or Expiry Dot
                            if (isDayExpired) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(theme.textSecondary.copy(alpha = 0.3f), CircleShape)
                                )
                            } else if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isSelected) theme.onPrimary else theme.primary,
                                            CircleShape
                                        )
                                )
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section Title for Selected Day
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fullDateFormat.format(Date(selectedDayMillis)),
                    color = theme.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!isDayOlderThanPolicy) {
                    Text(
                        text = "${selectedDayEvents.size} event${if (selectedDayEvents.size != 1) "s" else ""}",
                        color = theme.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 7-DAY RETENTION POLICY ENFORCEMENT BANNER
        if (isDayOlderThanPolicy) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                        .padding(22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(theme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Policy set which prevents data kept longer than 7 days.",
                            color = theme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ContextPulse enforces a strict on-device 7-day retention policy for profile transitions and proximity beacon logs. Older telemetry is permanently purged for privacy protection.",
                            color = theme.textSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (selectedDayEvents.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = theme.textSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No profile switches recorded on this day.",
                            color = theme.textSecondary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Events list for the day
            item {
                Text(
                    text = "TAP ANY EVENT TO VIEW FULL PROXIMITY REASONING",
                    color = theme.primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            itemsIndexed(selectedDayEvents, key = { _, log -> log.id }) { index, log ->
                val isExpanded = expandedLogIds.contains(log.id)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.surface)
                        .border(1.dp, if (isExpanded) theme.primary.copy(alpha = 0.5f) else theme.border, RoundedCornerShape(18.dp))
                        .clickable {
                            expandedLogIds = if (isExpanded) expandedLogIds - log.id else expandedLogIds + log.id
                        }
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Trigger Source Icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(theme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (log.triggerSource) {
                                        TriggerSource.MANUAL -> Icons.Outlined.TouchApp
                                        TriggerSource.BLE_BEACON -> Icons.Default.Sensors
                                        TriggerSource.NFC_TAG -> Icons.Default.Nfc
                                        TriggerSource.QR_CODE -> Icons.Default.QrCode2
                                        TriggerSource.SYSTEM_RESTORE -> Icons.Outlined.Layers
                                    },
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.toProfileName,
                                    color = theme.textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val fromText = if (!log.fromProfileName.isNullOrBlank()) {
                                    "Changed from ${log.fromProfileName}"
                                } else {
                                    "Context activation"
                                }
                                Text(
                                    text = fromText,
                                    color = theme.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = log.getFormattedTime(),
                                    color = theme.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle reasoning",
                                    tint = if (isExpanded) theme.primary else theme.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Expandable reasoning card
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.surfaceVariant)
                                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "TRIGGER REASONING",
                                            color = theme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )

                                        Box(
                                            modifier = Modifier
                                                .background(theme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = log.triggerSource.getDisplayName(),
                                                color = theme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = log.reason,
                                        color = theme.textPrimary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
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
