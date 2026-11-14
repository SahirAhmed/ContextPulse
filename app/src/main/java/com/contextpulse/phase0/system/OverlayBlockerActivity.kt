package com.contextpulse.phase0.system

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.ui.MainActivity

class OverlayBlockerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PKG = "EXTRA_BLOCKED_PKG"
        const val EXTRA_ACTIVE_ROOM_NAME = "EXTRA_ACTIVE_ROOM_NAME"
        const val EXTRA_BLOCK_REASON = "EXTRA_BLOCK_REASON"
        const val REASON_BLOCK_LIST = "BLOCK_LIST"
        const val REASON_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PKG) ?: "Restricted Application"
        if (blockedPkg.substringBefore('/').trim().equals(packageName, ignoreCase = true)) {
            finish()
            return
        }
        val roomRepo = RoomProfileRepository.getInstance(this)
        val rawRoomName = intent.getStringExtra(EXTRA_ACTIVE_ROOM_NAME)
            ?: roomRepo.activeRoom.value?.name
            ?: "Active Profile"
        val roomName = rawRoomName.replace(Regex("\\s*\\(Budget Exhausted\\)", RegexOption.IGNORE_CASE), "").trim()

        val explicitReason = intent.getStringExtra(EXTRA_BLOCK_REASON)
        val isBudgetExhausted = explicitReason == REASON_BUDGET_EXHAUSTED ||
            (explicitReason == null && AppUsageBudgetManager.getActiveBudgetForPackage(blockedPkg) != null &&
             AppUsageBudgetManager.getRemainingSecondsForPackage(this, blockedPkg) <= 0)

        // Resolve friendly app label
        val appLabel = try {
            val info = packageManager.getApplicationInfo(blockedPkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            blockedPkg
        }

        setContent {
            // Intercept Android Back: Kick to home screen, never allow returning to the blocked app
            BackHandler {
                kickToHome()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0080B11))
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 520.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFF43F5E).copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141926))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Warning block icon
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(0x33F43F5E))
                                .border(1.5.dp, Color(0xFFF43F5E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Blocked",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (isBudgetExhausted) "Usage Budget Exhausted" else "App Access Blocked",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = Color(0x33F43F5E),
                            border = BorderStroke(1.dp, Color(0x66F43F5E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isBudgetExhausted) "Active Profile: '$roomName' • Daily Budget Exhausted" else "Active Profile: '$roomName' • App Blocked",
                                color = Color(0xFFFF6482),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Blocked App details pill
                        Surface(
                            color = Color(0xFF1E2638),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF2C3852))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = appLabel,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                if (appLabel != blockedPkg) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "($blockedPkg)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isBudgetExhausted) {
                                "Your daily usage budget for $appLabel has run out for '$roomName'. Access is restricted until the context changes or resets."
                            } else {
                                "$appLabel is on the blocked apps list for '$roomName'. Access is restricted during this mode."
                            },
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Single primary action: Exit to Home Screen
                        Button(
                            onClick = { kickToHome() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF43F5E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exit to Home Screen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    private fun kickToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        kickToHome()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
