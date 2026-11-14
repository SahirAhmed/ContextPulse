package com.contextpulse.phase0.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.ui.components.ContextPulseLogo
import com.contextpulse.phase0.ui.theme.AppThemeRepository
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

class ContextPulseWidgetConfigureActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MAX_SLOTS = "EXTRA_MAX_SLOTS"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        val maxSlots = resolveMaxSlots(extras?.getInt(EXTRA_MAX_SLOTS, -1) ?: -1)

        setContent {
            val themeRepo = remember { AppThemeRepository.getInstance(applicationContext) }
            val currentTheme by themeRepo.currentTheme.collectAsState()

            val roomRepo = remember { RoomProfileRepository.getInstance(applicationContext) }
            val rooms by roomRepo.rooms.collectAsState()
            val activeRoom by roomRepo.activeRoom.collectAsState()

            val initialSelected = remember(appWidgetId, rooms, maxSlots) {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetPreferences.getChosenProfileIds(applicationContext, appWidgetId, maxSlots)
                } else {
                    WidgetPreferences.getDefaultProfileIds(applicationContext, maxSlots)
                }
            }

            var selectedIds by remember { mutableStateOf(initialSelected) }

            CompositionLocalProvider(LocalContextPulseTheme provides currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = currentTheme.background
                ) {
                    WidgetConfigureScreen(
                        currentTheme = currentTheme,
                        maxSlots = maxSlots,
                        rooms = rooms,
                        activeRoomId = activeRoom?.id,
                        selectedIds = selectedIds,
                        onToggleProfile = { roomId ->
                            selectedIds = if (selectedIds.contains(roomId)) {
                                selectedIds.filter { it != roomId }
                            } else {
                                if (selectedIds.size >= maxSlots) {
                                    Toast.makeText(
                                        this,
                                        "Maximum $maxSlots contexts allowed in widget",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    selectedIds
                                } else {
                                    selectedIds + roomId
                                }
                            }
                        },
                        onMoveUp = { index ->
                            if (index > 0 && index < selectedIds.size) {
                                val list = selectedIds.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                selectedIds = list
                            }
                        },
                        onMoveDown = { index ->
                            if (index >= 0 && index < selectedIds.size - 1) {
                                val list = selectedIds.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                selectedIds = list
                            }
                        },
                        onSave = {
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                WidgetPreferences.saveChosenProfileIds(
                                    applicationContext,
                                    appWidgetId,
                                    selectedIds,
                                    maxSlots
                                )
                                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                                if (maxSlots > 3) {
                                    QuickSwitch6WidgetProvider.updateWidget(
                                        applicationContext,
                                        appWidgetManager,
                                        appWidgetId
                                    )
                                } else {
                                    ContextPulseWidgetProvider.updateWidget(
                                        applicationContext,
                                        appWidgetManager,
                                        appWidgetId
                                    )
                                }

                                val resultValue = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(Activity.RESULT_OK, resultValue)
                            } else {
                                WidgetPreferences.saveDefaultProfileIds(
                                    applicationContext,
                                    selectedIds,
                                    maxSlots
                                )
                                ContextPulseWidgetProvider.updateAllWidgets(applicationContext)
                                setResult(Activity.RESULT_OK)
                            }

                            Toast.makeText(this, "Widget contexts saved", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun resolveMaxSlots(explicitMax: Int): Int {
        if (explicitMax == 3 || explicitMax == 6) return explicitMax
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            if (info?.provider?.className == QuickSwitch6WidgetProvider::class.java.name) {
                return 6
            }
        }
        return 3
    }
}

@Composable
fun WidgetConfigureScreen(
    currentTheme: com.contextpulse.phase0.ui.theme.AppThemeKey,
    maxSlots: Int = 3,
    rooms: List<RoomItem>,
    activeRoomId: String?,
    selectedIds: List<String>,
    onToggleProfile: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val theme = LocalContextPulseTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.tertiary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Widgets,
                        contentDescription = null,
                        tint = theme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (maxSlots > 3) "Quick Switch Context (6)" else "Quick Switch Context (3)",
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Choose and arrange up to $maxSlots contexts (${selectedIds.size}/$maxSlots)",
                        color = theme.tertiary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Widget Interactive Preview
        Text(
            text = "LIVE WIDGET PREVIEW",
            color = theme.tertiary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            border = BorderStroke(1.dp, theme.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContextPulseLogo(
                    size = 32.dp,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rowsCount = if (maxSlots > 3) 2 else 1
                    for (row in 0 until rowsCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until 3) {
                                val slotIdx = row * 3 + col
                                val profileId = selectedIds.getOrNull(slotIdx)
                                val room = rooms.find { it.id == profileId }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                room == null -> theme.surfaceVariant.copy(alpha = 0.5f)
                                                room.id == activeRoomId -> theme.primary
                                                else -> theme.surfaceVariant
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (room?.id == activeRoomId) theme.primary else theme.border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = room?.name ?: "Empty Slot",
                                            color = if (room?.id == activeRoomId) theme.onPrimary else theme.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (room?.id == activeRoomId) "ACTIVE" else if (room != null) "Slot ${slotIdx + 1}" else "Not in use",
                                            color = if (room?.id == activeRoomId) theme.onPrimary.copy(alpha = 0.85f) else theme.tertiary,
                                            fontSize = 9.sp,
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

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Select and arrange your widget contexts (use arrows to reorder):",
            color = theme.textSecondary,
            fontSize = 12.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val orderedRooms = remember(rooms, selectedIds) {
            val selected = selectedIds.mapNotNull { id -> rooms.find { it.id == id } }
            val unselected = rooms.filter { !selectedIds.contains(it.id) }
            selected + unselected
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rooms.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No contexts created yet. Please create contexts inside ContextPulse first.",
                            color = theme.textSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                }
            } else {
                items(orderedRooms, key = { it.id }) { room ->
                    val isSelected = selectedIds.contains(room.id)
                    val orderIndex = if (isSelected) selectedIds.indexOf(room.id) else -1
                    val selectionNumber = if (orderIndex >= 0) orderIndex + 1 else null

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) theme.surfaceVariant else theme.surface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) theme.primary else theme.border
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleProfile(room.id) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) theme.primary else theme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected && selectionNumber != null) {
                                        Text(
                                            text = "#$selectionNumber",
                                            color = theme.onPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Layers,
                                            contentDescription = null,
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = room.name,
                                            color = theme.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (room.id == activeRoomId) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(theme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = theme.tertiary,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val modesCount = room.profile.normalizedModes().size
                                    Text(
                                        text = "$modesCount configured mode${if (modesCount == 1) "" else "s"}",
                                        color = theme.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected && orderIndex >= 0) {
                                    IconButton(
                                        onClick = { onMoveUp(orderIndex) },
                                        enabled = orderIndex > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (orderIndex > 0) theme.tertiary else theme.textSecondary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onMoveDown(orderIndex) },
                                        enabled = orderIndex < selectedIds.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (orderIndex < selectedIds.size - 1) theme.tertiary else theme.textSecondary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleProfile(room.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = theme.primary,
                                        checkmarkColor = theme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.primary,
                contentColor = theme.onPrimary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Apply to Widget (${selectedIds.size} selected)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
