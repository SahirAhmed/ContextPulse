package com.contextpulse.phase0.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

object QrCodeUtil {

    /**
     * Generates a sharp high-resolution QR code Bitmap using ZXing.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Throwable) {
            android.util.Log.e("QrCodeUtil", "Failed to generate QR code bitmap", e)
            null
        }
    }

    /**
     * Saves the QR bitmap into app cache so it can be safely shared with external apps or printers.
     */
    fun saveQrToCache(context: Context, bitmap: Bitmap, fileName: String = "room_sensor_qr.png"): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "qr")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Throwable) {
            android.util.Log.e("QrCodeUtil", "Failed to write QR to cache for sharing", e)
            null
        }
    }

    /**
     * Launches the system share / print sheet for the user to print out or save the room QR code.
     */
    fun shareQrCode(context: Context, bitmap: Bitmap, roomName: String) {
        try {
            val uri = saveQrToCache(context, bitmap, "contextpulse_${roomName.replace(" ", "_")}_qr.png")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ContextPulse QR Code: $roomName")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "ContextPulse Sensor QR Code for $roomName.\nPrint and affix this near the entryway or desk to automate your profile switch."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Print or Share $roomName QR Code").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Throwable) {
            android.util.Log.e("QrCodeUtil", "Failed to share QR code", e)
            Toast.makeText(context, "Could not open share/print dialog", Toast.LENGTH_SHORT).show()
        }
    }
}
