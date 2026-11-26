package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Classroom Sound Intelligence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Classroom Sound Intelligence", appName)
    assertTrue(appName.length <= 30)
  }

  @Test
  fun `bundled index html asset exists and contains core engine`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = context.assets.open("index.html").bufferedReader().use { it.readText() }
    assertTrue(html.contains("CLASSROOM SOUND INTELLIGENCE"))
    assertTrue(html.contains("MASTER CREATION"))
    assertTrue(html.contains("getFloatTimeDomainData"))
    assertTrue(html.contains("20 * Math.log10"))
    assertTrue(html.contains("Audio is processed locally on your device."))
  }

  @Test
  fun `manifest declares only microphone permissions without broad storage permissions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgInfo =
      context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS,
      )
    val perms = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertTrue(perms.contains(Manifest.permission.RECORD_AUDIO))
    assertFalse(perms.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
    assertFalse(perms.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    assertNotNull(MainActivity.VIRTUAL_APP_URL)
  }
}
