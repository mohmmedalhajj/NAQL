package com.example.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory(val titleAr: String, val titleEn: String) {
    ALL("الكل", "All"),
    IMAGE("الصور", "Images"),
    VIDEO("الفيديوهات", "Videos"),
    AUDIO("الصوتيات", "Audio"),
    DOCUMENT("المستندات", "Documents"),
    APP("التطبيقات", "Apps"),
    OTHER("أخرى", "Other")
}

enum class ItemTransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class FileViewMode(val titleAr: String) {
    LIST("قائمة"),
    DETAILED_LIST("قائمة تفصيلية"),
    GRID("شبكة")
}

enum class FileSortOption(val titleAr: String) {
    DATE_DESC("الأحدث أولاً"),
    DATE_ASC("الأقدم أولاً"),
    NAME_ASC("الاسم (أ - ي)"),
    NAME_DESC("الاسم (ي - أ)"),
    SIZE_DESC("الأكبر حجماً"),
    SIZE_ASC("الأصغر حجماً")
}

data class TransferItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory = FileCategory.OTHER,
    val uri: Uri? = null,
    val localPath: String? = null,
    val sha256Checksum: String = "",
    val verifiedChecksum: Boolean = false,
    val status: ItemTransferStatus = ItemTransferStatus.PENDING,
    val bytesTransferred: Long = 0L,
    val payloadId: Long = 0L,
    val dateModified: Long = 0L,
    val durationMs: Long = 0L,
    val appPackageName: String? = null
) {
    val progress: Float
        get() = if (size > 0) (bytesTransferred.toFloat() / size.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSize: String
        get() = formatBytes(size)

    val fileExtension: String
        get() {
            val dot = name.lastIndexOf('.')
            return if (dot != -1 && dot < name.length - 1) {
                name.substring(dot + 1).uppercase(Locale.ROOT)
            } else {
                when (category) {
                    FileCategory.IMAGE -> "IMG"
                    FileCategory.VIDEO -> "VID"
                    FileCategory.AUDIO -> "AUD"
                    FileCategory.DOCUMENT -> "DOC"
                    FileCategory.APP -> "APK"
                    else -> "FILE"
                }
            }
        }

    val formattedDate: String
        get() {
            if (dateModified <= 0L) return ""
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return sdf.format(Date(dateModified))
        }

    val formattedDuration: String
        get() {
            if (durationMs <= 0L) return ""
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = durationMs / (1000 * 60 * 60)
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 ب"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f جيجابايت", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f ميجابايت", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f كيلوبايت", kb)
                else -> "$bytes بايت"
            }
        }
    }
}
