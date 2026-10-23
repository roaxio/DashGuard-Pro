package io.github.roaxio.dashguardpro

import androidx.test.core.app.ApplicationProvider
import io.github.roaxio.dashguardpro.data.settings.Sensitivity
import io.github.roaxio.dashguardpro.sensor.AcousticNoiseMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcousticNoiseMonitorTest {

    private lateinit var monitor: AcousticNoiseMonitor

    @Before
    fun setup() {
        val app: DashGuardApplication = ApplicationProvider.getApplicationContext()
        monitor = AcousticNoiseMonitor(app)
    }

    @Test
    fun testSensitivityThresholdMapping() {
        assertEquals(65f, AcousticNoiseMonitor.getThresholdForSensitivity(Sensitivity.HIGH), 0.01f)
        assertEquals(75f, AcousticNoiseMonitor.getThresholdForSensitivity(Sensitivity.MEDIUM), 0.01f)
        assertEquals(85f, AcousticNoiseMonitor.getThresholdForSensitivity(Sensitivity.LOW), 0.01f)
        assertEquals(999f, AcousticNoiseMonitor.getThresholdForSensitivity(Sensitivity.OFF), 0.01f)
    }

    @Test
    fun testCalculateDb_silentBufferReturnsZero() {
        val silentBuffer = ShortArray(100) { 0 }
        val db = AcousticNoiseMonitor.calculateDbFromSamples(silentBuffer, silentBuffer.size)
        assertEquals(0f, db, 0.01f)

        val emptyDb = AcousticNoiseMonitor.calculateDbFromSamples(silentBuffer, 0)
        assertEquals(0f, emptyDb, 0.01f)
    }

    @Test
    fun testCalculateDb_audibleSignalReturnsExpectedRange() {
        val loudBuffer = ShortArray(200) { 10000 }
        val db = AcousticNoiseMonitor.calculateDbFromSamples(loudBuffer, loudBuffer.size)
        // 20 * log10(10000) = 80 dB
        assertEquals(80.0f, db, 0.5f)

        val louderBuffer = ShortArray(200) { 32000 }
        val louderDb = AcousticNoiseMonitor.calculateDbFromSamples(louderBuffer, louderBuffer.size)
        assertTrue(louderDb > db)
        assertTrue(louderDb <= 100f)
    }

    @Test
    fun testStopAndRelease_resetsCurrentDbToZero() {
        monitor.stopAndRelease()
        assertEquals(0f, monitor.currentDb.value, 0.01f)
    }
}
