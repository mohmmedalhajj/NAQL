package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NAQL | نقل", appName)
  }

  @Test
  fun testTransferItemFormatBytes() {
    assertEquals("0 ب", com.example.model.TransferItem.formatBytes(0L))
    assertEquals("500 بايت", com.example.model.TransferItem.formatBytes(500L))
    assertEquals("1.0 كيلوبايت", com.example.model.TransferItem.formatBytes(1024L))
    assertEquals("1.5 ميجابايت", com.example.model.TransferItem.formatBytes((1.5 * 1024 * 1024).toLong()))
    assertEquals("2.00 جيجابايت", com.example.model.TransferItem.formatBytes(2L * 1024 * 1024 * 1024))
  }

  @Test
  fun testFileCategoryClassification() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fm = com.example.data.FileManager(context)

    assertEquals(com.example.model.FileCategory.IMAGE, fm.determineCategory("photo.jpg", "image/jpeg"))
    assertEquals(com.example.model.FileCategory.VIDEO, fm.determineCategory("clip.mp4", "video/mp4"))
    assertEquals(com.example.model.FileCategory.AUDIO, fm.determineCategory("song.mp3", "audio/mpeg"))
    assertEquals(com.example.model.FileCategory.DOCUMENT, fm.determineCategory("document.pdf", "application/pdf"))
    assertEquals(com.example.model.FileCategory.APP, fm.determineCategory("whatsapp.apk", "application/vnd.android.package-archive"))
    assertEquals(com.example.model.FileCategory.OTHER, fm.determineCategory("archive.zip", "application/zip"))
  }
}
