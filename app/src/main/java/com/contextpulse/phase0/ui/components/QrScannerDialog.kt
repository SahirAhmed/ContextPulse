package com.contextpulse.phase0.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme
import com.contextpulse.phase0.util.QrTriggerHandler
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

private const val TAG = "QrScannerDialog"

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val theme = LocalContextPulseTheme.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0080B11)),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission && !showManualInput) {
                    // Camera Preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            val cameraExecutor = Executors.newSingleThreadExecutor()

                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()

                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val multiFormatReader = MultiFormatReader().apply {
                                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                                    }

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!isProcessing) {
                                            val scanned = decodeQr(imageProxy, multiFormatReader)
                                            if (scanned != null) {
                                                isProcessing = true
                                                previewView.post {
                                                    onQrScanned(scanned)
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }

                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraControl = camera.cameraControl
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error binding CameraX lifecycle", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Viewfinder overlay cutout & animated laser
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x77000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "Scan ContextPulse Room QR",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            // Viewfinder Reticle box
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(2.5.dp, theme.primary, RoundedCornerShape(22.dp))
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                // Animated scanning laser line
                                val infiniteTransition = rememberInfiniteTransition(label = "laser")
                                val laserOffset by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 240f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "laser_anim"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .offset(y = (laserOffset - 120f).dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    theme.primary,
                                                    Color.White,
                                                    theme.primary,
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            Text(
                                text = "Align QR code inside frame\nor normal camera app also works",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else if (!hasCameraPermission) {
                    // Camera Permission Request Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(theme.primary.copy(alpha = 0.16f))
                                .border(1.5.dp, theme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Camera Access Required",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ContextPulse needs camera access to scan room QR codes directly inside the app and switch your context instantly.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Grant Camera Permission", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showManualInput = true },
                            border = BorderStroke(1.dp, theme.border),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Code Manually", color = theme.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Manual Input Entry Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enter Room Code / Link",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Type or paste a Room name (e.g. Work, Sleep, Bedroom), Trigger ID, or ContextPulse deep link:",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it },
                            placeholder = { Text("e.g. Work or contextpulse://trigger/work", color = theme.textSecondary.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primary,
                                unfocusedBorderColor = theme.border,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (manualText.isNotBlank()) {
                                    onQrScanned(manualText.trim())
                                }
                            },
                            enabled = manualText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Activate Context", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = { showManualInput = false }) {
                            Text("Switch back to Camera Scanner", color = theme.primary)
                        }
                    }
                }

                // Top Bar with Close, Torch, and Manual switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flashlight toggle
                    if (hasCameraPermission && !showManualInput) {
                        IconButton(
                            onClick = {
                                isTorchOn = !isTorchOn
                                cameraControl?.enableTorch(isTorchOn)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Torch",
                                tint = if (isTorchOn) Color(0xFFFBBF24) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasCameraPermission && !showManualInput) {
                            IconButton(
                                onClick = { showManualInput = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Keyboard,
                                    contentDescription = "Manual Code",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts and decodes QR code string from CameraX ImageProxy using ZXing.
 */
private fun decodeQr(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    val plane = imageProxy.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = imageProxy.width
    val height = imageProxy.height

    val yData = ByteArray(width * height)
    if (rowStride == width) {
        buffer.get(yData, 0, width * height)
    } else {
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(yData, row * width, width)
        }
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    val (rotatedData, rotatedWidth, rotatedHeight) = when (rotation) {
        90 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[x * height + (height - y - 1)] = yData[x + y * width]
                }
            }
            Triple(rotated, height, width)
        }
        270 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[(width - x - 1) * height + y] = yData[x + y * width]
                }
            }
            Triple(rotated, height, width)
        }
        180 -> {
            val rotated = ByteArray(width * height)
            for (i in yData.indices) {
                rotated[yData.size - i - 1] = yData[i]
            }
            Triple(rotated, width, height)
        }
        else -> Triple(yData, width, height)
    }

    val source = PlanarYUVLuminanceSource(
        rotatedData, rotatedWidth, rotatedHeight,
        0, 0, rotatedWidth, rotatedHeight, false
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

    return try {
        reader.decodeWithState(binaryBitmap).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
