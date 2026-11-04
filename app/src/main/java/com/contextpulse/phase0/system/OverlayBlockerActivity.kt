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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PKG) ?: "Restricted Application"
        val roomRepo = RoomProfileRepository.getInstance(this)
        val roomName = intent.getStringExtra(EXTRA_ACTIVE_ROOM_NAME)
            ?: roomRepo.activeRoom.value?.name
            ?: "Active Room"

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
                    .background(Color(0xF0080B11)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFF43F5E).copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141926))
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
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
                            text = "App Access Blocked",
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
                                text = "Active Profile: '$roomName'",
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
                            text = "$roomName has blocked access to this app. Please change location or change profiles in the ContextPulse app.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Only action allowed: Exit to Home Screen (strict lock)
                        Button(
                            onClick = { kickToHome() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF43F5E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exit to Home Screen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
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
