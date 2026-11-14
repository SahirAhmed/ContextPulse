package com.contextpulse.phase0.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import java.nio.charset.Charset
import java.util.Locale

sealed class NfcWriteResult {
    data class Success(
        val tagUid: String,
        val writtenUri: String,
        val bytesWritten: Int
    ) : NfcWriteResult()

    data object TagReadOnly : NfcWriteResult()
    data class InsufficientCapacity(val neededBytes: Int, val maxBytes: Int) : NfcWriteResult()
    data object NotNdefCompatible : NfcWriteResult()
    data class Error(val message: String) : NfcWriteResult()
}

data class NfcTagExtraction(
    val uriOrPayload: String?,
    val hardwareUid: String?
)

object NfcTagHelper {

    const val GITHUB_APPLINK_BASE = "https://sahirahmed.github.io/ContextPulse/"

    fun buildAppLinkUri(triggerId: String): String {
        val cleanId = triggerId.trim()
        return "${GITHUB_APPLINK_BASE}?trigger=$cleanId"
    }

    fun isNfcSupported(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    fun isNfcEnabled(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    }

    fun formatTagUid(tag: Tag?): String {
        val bytes = tag?.id ?: return ""
        return formatUidBytes(bytes)
    }

    fun formatUidBytes(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return bytes.joinToString(":") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
        }
    }

    fun writeAppLinkToTag(tag: Tag, appLinkUrl: String): NfcWriteResult {
        val tagUid = formatTagUid(tag)
        val uriRecord = NdefRecord.createUri(appLinkUrl)
        val ndefMessage = NdefMessage(arrayOf(uriRecord))
        val payloadBytes = ndefMessage.toByteArray()

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    NfcWriteResult.TagReadOnly
                } else if (ndef.maxSize < payloadBytes.size) {
                    NfcWriteResult.InsufficientCapacity(
                        neededBytes = payloadBytes.size,
                        maxBytes = ndef.maxSize
                    )
                } else {
                    ndef.writeNdefMessage(ndefMessage)
                    NfcWriteResult.Success(
                        tagUid = tagUid,
                        writtenUri = appLinkUrl,
                        bytesWritten = payloadBytes.size
                    )
                }
            } catch (e: Exception) {
                NfcWriteResult.Error(e.message ?: "Tag moved away before write completed. Hold steady and try again.")
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(ndefMessage)
                NfcWriteResult.Success(
                    tagUid = tagUid,
                    writtenUri = appLinkUrl,
                    bytesWritten = payloadBytes.size
                )
            } catch (e: Exception) {
                NfcWriteResult.Error(e.message ?: "Failed to format NFC tag as NDEF.")
            } finally {
                try {
                    formatable.close()
                } catch (_: Exception) {
                }
            }
        }

        return NfcWriteResult.NotNdefCompatible
    }

    fun extractFromIntent(intent: Intent?): NfcTagExtraction {
        if (intent == null) return NfcTagExtraction(null, null)

        val uidBytes = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
            ?: getTagFromIntent(intent)?.id
        val uid = formatUidBytes(uidBytes).takeIf { it.isNotBlank() }

        val dataUri = intent.dataString?.takeIf { it.isNotBlank() }
        if (dataUri != null) {
            return NfcTagExtraction(uriOrPayload = dataUri, hardwareUid = uid)
        }

        val rawMessages = getNdefMessagesFromIntent(intent)
        if (rawMessages != null) {
            for (msg in rawMessages) {
                for (record in msg.records) {
                    val decoded = decodeNdefRecord(record)
                    if (!decoded.isNullOrBlank()) {
                        return NfcTagExtraction(uriOrPayload = decoded, hardwareUid = uid)
                    }
                }
            }
        }

        return NfcTagExtraction(uriOrPayload = null, hardwareUid = uid)
    }

    fun readTagPayload(tag: Tag): NfcTagExtraction {
        val uid = formatTagUid(tag).takeIf { it.isNotBlank() }
        val ndef = Ndef.get(tag)
        val cached = ndef?.cachedNdefMessage
        if (cached != null) {
            for (record in cached.records) {
                val decoded = decodeNdefRecord(record)
                if (!decoded.isNullOrBlank()) {
                    return NfcTagExtraction(uriOrPayload = decoded, hardwareUid = uid)
                }
            }
        }
        return NfcTagExtraction(uriOrPayload = null, hardwareUid = uid)
    }

    private fun getTagFromIntent(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    private fun getNdefMessagesFromIntent(intent: Intent): List<NdefMessage>? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        } ?: return null

        return raw.mapNotNull { it as? NdefMessage }
    }

    private fun decodeNdefRecord(record: NdefRecord): String? {
        return try {
            val uri = record.toUri()
            if (uri != null) return uri.toString()

            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                if (payload.isEmpty()) return null
                val encoding = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
                val languageCodeLength = payload[0].toInt() and 0x3F
                if (1 + languageCodeLength <= payload.size) {
                    String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, encoding)
                } else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
