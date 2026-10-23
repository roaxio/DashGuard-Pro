package io.github.roaxio.dashguardpro.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testCreateBatteryOptimizationIntent() {
        val intent = IntentUtils.createBatteryOptimizationIntent(context)
        assertNotNull(intent.action)
        assertTrue(
            intent.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ||
            intent.action == Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        )
    }

    @Test
    fun testCreateDndSettingsIntent() {
        val intent = IntentUtils.createDndSettingsIntent()
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, intent.action)
    }

    @Test
    fun testCreateAppDetailsIntent() {
        val intent = IntentUtils.createAppDetailsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
    }

    @Test
    fun testCreateSecuritySettingsIntent() {
        val intent = IntentUtils.createSecuritySettingsIntent()
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, intent.action)
    }

    @Test
    fun testCreateShareIntentSingleFile() {
        val videoFile = tempFolder.newFile("test_clip.mp4")
        val mockUri = Uri.parse("content://test.authority/test_clip.mp4")

        val intent = IntentUtils.createShareIntent(
            context = context,
            videoFile = videoFile,
            srtFile = null,
            uriResolver = { mockUri }
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent!!.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(mockUri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun testCreateShareIntentWithSubtitles() {
        val videoFile = tempFolder.newFile("test_clip.mp4")
        val srtFile = tempFolder.newFile("test_clip.srt")

        val intent = IntentUtils.createShareIntent(
            context = context,
            videoFile = videoFile,
            srtFile = srtFile,
            uriResolver = { file -> Uri.parse("content://test.authority/${file.name}") }
        )

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent!!.action)
        assertEquals("*/*", intent.type)
        val streamList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(streamList)
        assertEquals(2, streamList!!.size)
        assertEquals(Uri.parse("content://test.authority/test_clip.mp4"), streamList[0])
        assertEquals(Uri.parse("content://test.authority/test_clip.srt"), streamList[1])
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun testCreateShareIntentMissingFileReturnsNull() {
        val nonExistentFile = File("/tmp/does_not_exist_12345.mp4")
        val intent = IntentUtils.createShareIntent(context, nonExistentFile)
        assertNull(intent)
    }

    @Test
    fun testLaunchMapCoordinates() {
        IntentUtils.launchMapCoordinates(context, 37.4220, -122.0840, "Test Location")
        val nextStartedActivity = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, nextStartedActivity.action)

        val targetIntent = nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_VIEW, targetIntent?.action)
        assertTrue(targetIntent?.dataString?.startsWith("geo:37.4220") == true)
    }

    @Test
    fun testLaunchMapRoute() {
        IntentUtils.launchMapRoute(context, 37.4220, -122.0840, 37.4320, -122.0940)
        val nextStartedActivity = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, nextStartedActivity.action)

        val targetIntent = nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_VIEW, targetIntent?.action)
        assertTrue(targetIntent?.dataString?.contains("google.com/maps/dir") == true)
    }

    @Test
    fun testLaunchDndRulesConfiguration() {
        IntentUtils.launchDndRulesConfiguration(context)
        val nextStartedActivity = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertTrue(
            nextStartedActivity.action == Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS ||
            nextStartedActivity.action == "android.settings.ZEN_MODE_SETTINGS" ||
            nextStartedActivity.action == Settings.ACTION_SOUND_SETTINGS ||
            nextStartedActivity.action == Settings.ACTION_SETTINGS
        )
    }
}

