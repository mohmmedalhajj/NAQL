package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FileManager
import com.example.model.FileCategory
import com.example.model.FileSortOption
import com.example.model.FileViewMode
import com.example.model.TransferItem
import com.example.model.TransferProtocol
import com.example.model.TransferSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NaqlTransferTest {

    private lateinit var context: Context
    private lateinit var fileManager: FileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        fileManager = FileManager(context)
    }

    @Test
    fun testAppNameResource() {
        val appName = context.getString(R.string.app_name)
        assertEquals("NAQL | نقل", appName)
    }

    @Test
    fun testProtocolMetadataSerialization() {
        val files = listOf(
            TransferProtocol.FileMeta(
                payloadId = 1001L,
                name = "photo_desert.jpg",
                size = 2048000L,
                mimeType = "image/jpeg",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ),
            TransferProtocol.FileMeta(
                payloadId = 1002L,
                name = "video_trip.mp4",
                size = 150000000L,
                mimeType = "video/mp4",
                sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
            )
        )

        val bytes = TransferProtocol.createMetadataBytes(
            sessionId = "sess-test-99",
            senderName = "Pixel 8 Pro",
            files = files
        )

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val parsed = TransferProtocol.parseMetadataBytes(bytes)
        assertNotNull(parsed)
        assertEquals("sess-test-99", parsed?.sessionId)
        assertEquals("Pixel 8 Pro", parsed?.senderName)
        assertEquals(2, parsed?.files?.size)
        assertEquals("photo_desert.jpg", parsed?.files?.get(0)?.name)
        assertEquals(2048000L, parsed?.files?.get(0)?.size)
        assertEquals("video_trip.mp4", parsed?.files?.get(1)?.name)
    }

    @Test
    fun testProtocolCancelMessage() {
        val cancelBytes = TransferProtocol.createCancelMessageBytes("sess-test-99", "User cancelled")
        assertTrue(TransferProtocol.isCancelMessage(cancelBytes))

        val regularBytes = "{\"type\":\"RANDOM\"}".toByteArray()
        assertFalse(TransferProtocol.isCancelMessage(regularBytes))
    }

    @Test
    fun testProtocolAckMessage() {
        val ackBytes = TransferProtocol.createAckMessageBytes("sess-test-99")
        assertTrue(TransferProtocol.isAckMessage(ackBytes))
        assertFalse(TransferProtocol.isCancelMessage(ackBytes))
    }

    @Test
    fun testFileCategoryClassificationAllSeven() {
        // Images
        assertEquals(FileCategory.IMAGE, fileManager.determineCategory("sunset.jpg", "image/jpeg"))
        assertEquals(FileCategory.IMAGE, fileManager.determineCategory("banner.png", "image/png"))
        assertEquals(FileCategory.IMAGE, fileManager.determineCategory("photo.heic", "image/heic"))

        // Videos
        assertEquals(FileCategory.VIDEO, fileManager.determineCategory("clip.mp4", "video/mp4"))
        assertEquals(FileCategory.VIDEO, fileManager.determineCategory("movie.mkv", "video/x-matroska"))

        // Audio
        assertEquals(FileCategory.AUDIO, fileManager.determineCategory("song.mp3", "audio/mpeg"))
        assertEquals(FileCategory.AUDIO, fileManager.determineCategory("voice.m4a", "audio/mp4"))

        // Documents (Extensive)
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("report.pdf", "application/pdf"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("sheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("notes.txt", "text/plain"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("data.csv", "text/csv"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("book.epub", "application/epub+zip"))
        assertEquals(FileCategory.DOCUMENT, fileManager.determineCategory("letter.odt", "application/vnd.oasis.opendocument.text"))

        // Apps
        assertEquals(FileCategory.APP, fileManager.determineCategory("game.apk", "application/vnd.android.package-archive"))

        // Other (Extensive)
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("archive.zip", "application/zip"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("backup.rar", "application/x-rar-compressed"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("package.7z", "application/x-7z-compressed"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("image.iso", "application/x-iso9660-image"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("data.bin", "application/octet-stream"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("config.json", "application/json"))
        assertEquals(FileCategory.OTHER, fileManager.determineCategory("font.ttf", "font/ttf"))
    }

    @Test
    fun testViewModesAndSortOptions() {
        assertEquals(3, FileViewMode.values().size)
        assertEquals("قائمة", FileViewMode.LIST.titleAr)
        assertEquals("قائمة تفصيلية", FileViewMode.DETAILED_LIST.titleAr)
        assertEquals("شبكة", FileViewMode.GRID.titleAr)

        assertEquals(6, FileSortOption.values().size)
        assertEquals("الأحدث أولاً", FileSortOption.DATE_DESC.titleAr)
        assertEquals("الأكبر حجماً", FileSortOption.SIZE_DESC.titleAr)
    }

    @Test
    fun testItemFormattingAndExtensions() {
        val item = TransferItem(
            id = "test-1",
            name = "Presentation.pdf",
            size = 1048576L,
            mimeType = "application/pdf",
            category = FileCategory.DOCUMENT,
            durationMs = 125000L,
            dateModified = 1700000000000L
        )

        assertEquals("PDF", item.fileExtension)
        assertEquals("1.0 ميجابايت", item.formattedSize)
        assertEquals("2:05", item.formattedDuration)
        assertTrue(item.formattedDate.isNotEmpty())
    }

    @Test
    fun testFormatBytes() {
        val bytes = 500L
        assertEquals("500 بايت", TransferItem.formatBytes(bytes))

        val kb = 1500L
        assertTrue(TransferItem.formatBytes(kb).contains("كيلوبايت"))

        val mb = 15 * 1024 * 1024L
        assertTrue(TransferItem.formatBytes(mb).contains("ميجابايت"))

        val gb = 2L * 1024 * 1024 * 1024
        assertTrue(TransferItem.formatBytes(gb).contains("جيجابايت"))
    }

    @Test
    fun testSha256ChecksumCalculation() {
        runBlocking {
            val testFile = File(context.cacheDir, "test_checksum.txt")
            testFile.writeText("NAQL Secure Offline P2P Transfer")

            val sha256 = fileManager.calculateSha256(testFile)
            assertNotNull(sha256)
            assertEquals(64, sha256.length)

            testFile.delete()
        }
    }

    @Test
    fun testUniqueFileNaming() {
        val tempDir = File(context.cacheDir, "test_dir_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val file1 = File(tempDir, "sample.pdf")
        file1.writeText("first")

        val uniqueTarget = fileManager.getUniqueFile(tempDir, "sample.pdf")
        assertEquals("sample_1.pdf", uniqueTarget.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun testTransferSessionMetrics() {
        val session = TransferSession(
            totalBytes = 1000L,
            transferredBytes = 500L,
            transferSpeedBytesPerSec = 250L
        )

        assertEquals(0.5f, session.overallProgress, 0.001f)
        assertEquals(2L, session.estimatedTimeRemainingSec)
    }
}
