package com.contextpulse.phase0.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.contextpulse.phase0.model.ContextItem
import com.contextpulse.phase0.model.TriggerItem
import java.io.ByteArrayOutputStream

/**
 * Utility for printing all configured QR Code triggers on a single sheet of paper
 * via Android's native PrintManager and system print spooler.
 *
 * Formats a ready-to-cut printable grid with scissors guidelines, trigger names,
 * linked context indicators, and sharp high-resolution QR codes.
 */
object QrSheetPrinter {

    fun printQrCodesSheet(
        context: Context,
        qrTriggers: List<TriggerItem>,
        contexts: List<ContextItem>
    ) {
        if (qrTriggers.isEmpty()) {
            Toast.makeText(context, "No QR Code triggers to print", Toast.LENGTH_SHORT).show()
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    Toast.makeText(context, "Printing is not supported on this device", Toast.LENGTH_SHORT).show()
                    return@post
                }

                Toast.makeText(context, "Preparing QR codes for printing...", Toast.LENGTH_SHORT).show()

                // Generate HTML content in background-friendly way
                val html = generatePrintableHtml(qrTriggers, contexts)

                val webView = WebView(context)
                webView.settings.javaScriptEnabled = false
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        try {
                            val printAdapter = webView.createPrintDocumentAdapter("ContextPulse_QR_Triggers_Sheet")
                            val printAttributes = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("res_qr", "High Resolution", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()

                            printManager.print(
                                "ContextPulse QR Triggers (${qrTriggers.size})",
                                printAdapter,
                                printAttributes
                            )
                        } catch (e: Throwable) {
                            android.util.Log.e("QrSheetPrinter", "PrintManager failed to print", e)
                            Toast.makeText(context, "Failed to start printing", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            } catch (e: Throwable) {
                android.util.Log.e("QrSheetPrinter", "Error preparing print sheet", e)
                Toast.makeText(context, "Unable to generate print document", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePrintableHtml(
        qrTriggers: List<TriggerItem>,
        contexts: List<ContextItem>
    ): String {
        val cardsHtml = StringBuilder()

        for (trigger in qrTriggers) {
            val payload = if (trigger.qrPayload.isNotBlank()) {
                trigger.qrPayload
            } else {
                "https://sahirahmed.github.io/ContextPulse/?trigger=${trigger.name.lowercase().replace(" ", "_")}"
            }

            val linkedContext = if (trigger.assignedRoomName.isNotBlank()) {
                trigger.assignedRoomName
            } else {
                contexts.find { it.id == trigger.assignedRoomId }?.name ?: "Unattached"
            }

            val qrBitmap = QrCodeUtil.generateQrBitmap(payload, 380)
            val base64Image = if (qrBitmap != null) {
                val stream = ByteArrayOutputStream()
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } else {
                ""
            }

            cardsHtml.append("""
                <div class="qr-card">
                    <div class="cut-indicator">✂ CUT ALONG DASHED LINE</div>
                    <div class="card-header">
                        <span class="badge">ContextPulse Sensor</span>
                    </div>
                    <h2 class="trigger-name">${escapeHtml(trigger.name)}</h2>
                    <div class="context-tag">Linked: <strong>${escapeHtml(linkedContext)}</strong></div>
                    <div class="qr-frame">
                        ${if (base64Image.isNotEmpty()) """<img class="qr-img" src="data:image/png;base64,$base64Image" alt="QR Code" />""" else """<div class="error-box">QR Unavailable</div>"""}
                    </div>
                    <p class="scan-instructions">Scan with Android Camera or ContextPulse to activate</p>
                </div>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>ContextPulse QR Codes Sheet</title>
                <style>
                    @page {
                        size: A4 portrait;
                        margin: 12mm 10mm;
                    }
                    * {
                        box-sizing: border-box;
                        -webkit-print-color-adjust: exact;
                        print-color-adjust: exact;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        margin: 0;
                        padding: 0;
                        color: #0F172A;
                        background: #FFFFFF;
                    }
                    .sheet-header {
                        text-align: center;
                        border-bottom: 2px solid #E2E8F0;
                        padding-bottom: 10px;
                        margin-bottom: 16px;
                    }
                    .sheet-title {
                        font-size: 20px;
                        font-weight: 800;
                        margin: 0 0 4px 0;
                        letter-spacing: -0.5px;
                        color: #1E293B;
                    }
                    .sheet-desc {
                        font-size: 11px;
                        color: #64748B;
                        margin: 0;
                    }
                    .grid-container {
                        display: flex;
                        flex-wrap: wrap;
                        justify-content: flex-start;
                        gap: 16px;
                    }
                    .qr-card {
                        width: 48%;
                        border: 2px dashed #94A3B8;
                        border-radius: 14px;
                        padding: 14px;
                        text-align: center;
                        position: relative;
                        page-break-inside: avoid;
                        background: #FAFAFA;
                    }
                    .cut-indicator {
                        font-size: 9px;
                        font-weight: 700;
                        color: #94A3B8;
                        letter-spacing: 1px;
                        margin-bottom: 6px;
                        text-transform: uppercase;
                    }
                    .card-header {
                        margin-bottom: 4px;
                    }
                    .badge {
                        background: #0284C7;
                        color: #FFFFFF;
                        font-size: 9px;
                        font-weight: 800;
                        padding: 3px 8px;
                        border-radius: 6px;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .trigger-name {
                        font-size: 16px;
                        font-weight: 800;
                        color: #0F172A;
                        margin: 8px 0 2px 0;
                        word-break: break-word;
                    }
                    .context-tag {
                        font-size: 11px;
                        color: #475569;
                        margin-bottom: 10px;
                    }
                    .context-tag strong {
                        color: #0284C7;
                    }
                    .qr-frame {
                        background: #FFFFFF;
                        border: 1px solid #E2E8F0;
                        border-radius: 10px;
                        padding: 8px;
                        display: inline-block;
                        margin: 0 auto;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    }
                    .qr-img {
                        width: 140px;
                        height: 140px;
                        display: block;
                    }
                    .scan-instructions {
                        font-size: 9.5px;
                        font-weight: 600;
                        color: #64748B;
                        margin: 8px 0 2px 0;
                        line-height: 1.3;
                    }
                    .sheet-footer {
                        text-align: center;
                        margin-top: 18px;
                        font-size: 10px;
                        color: #94A3B8;
                        border-top: 1px solid #E2E8F0;
                        padding-top: 8px;
                    }
                </style>
            </head>
            <body>
                <div class="sheet-header">
                    <h1 class="sheet-title">ContextPulse • Room QR Code Cut-Out Sheet</h1>
                    <p class="sheet-desc">Print, cut along dashed guidelines, and place these QR codes around your home or workplace to switch contexts instantly.</p>
                </div>
                <div class="grid-container">
                    $cardsHtml
                </div>
                <div class="sheet-footer">
                    ContextPulse Automation &copy; 2026 • https://sahirahmed.github.io/ContextPulse/
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
