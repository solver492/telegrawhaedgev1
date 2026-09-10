package com.example

import com.example.domain.telegram.TelegramBridgeScript
import com.example.util.ProductMediaManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun telegramInstallCommand_hasNoParasiticPrefix() {
    val actual = TelegramBridgeScript.INSTALL_COMMAND.trim()

    // Assert no parasitic prefix like 'ps-ox' or leading space/newline
    assertFalse("Must not start with ps-ox", actual.startsWith("ps-ox"))
    assertTrue("Must contain pkg update", actual.contains("pkg update"))
    assertFalse("Must not contain unneeded carriage returns", actual.contains("\r"))
  }

  @Test
  fun videoDetection_identifiesFormatsCorrectly() {
    // Tests vidéos en ligne
    assertTrue(ProductMediaManager.isVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertTrue(ProductMediaManager.isVideoUrl("https://youtu.be/dQw4w9WgXcQ"))
    assertTrue(ProductMediaManager.isVideoUrl("https://vimeo.com/76979871"))
    
    // Tests formats fichiers vidéo
    assertTrue(ProductMediaManager.isVideoUrl("https://storage.supabase.co/v1/object/public/product-media/clip.mp4"))
    assertTrue(ProductMediaManager.isVideoUrl("/data/data/com.example/files/demo.webm"))
    assertTrue(ProductMediaManager.isVideoUrl("file:///storage/emulated/0/video.mov"))

    // Tests images standards (ne doivent pas être détectées comme vidéos)
    assertFalse(ProductMediaManager.isVideoUrl("https://storage.supabase.co/v1/object/public/product-media/photo.jpg"))
    assertFalse(ProductMediaManager.isVideoUrl("https://example.com/image.png"))
    assertFalse(ProductMediaManager.isVideoUrl("/storage/emulated/0/picture.webp"))
  }
}
