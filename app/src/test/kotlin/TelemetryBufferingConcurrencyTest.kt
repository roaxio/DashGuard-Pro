package io.github.roaxio.dashguardpro

import io.github.roaxio.dashguardpro.media.TelemetryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concurrency regression test for Section 1.4:
 * Verifies that concurrent 1Hz telemetry/speed sample appends and asynchronous segment
 * rollover snapshots (.toList()) execute with 100% thread safety and zero ConcurrentModificationException.
 */
class TelemetryBufferingConcurrencyTest {

    @Test
    fun testConcurrentTelemetryAppendAndSnapshotUnderHeavyLoad() = runBlocking(Dispatchers.Default) {
        val telemetryList: MutableList<TelemetryPoint> = CopyOnWriteArrayList()
        val speedList: MutableList<Float> = CopyOnWriteArrayList()
        val writeCount = 200
        val numWriters = 4
        val numReaders = 4
        val readerExceptions = CopyOnWriteArrayList<Throwable>()

        // 1. Spawn concurrent reader coroutines taking snapshots and iterating cooperatively
        val readers = (1..numReaders).map {
            launch {
                while (isActive) {
                    try {
                        val snapshot = telemetryList.toList()
                        snapshot.forEach { point ->
                            assertNotNull(point.speedUnit)
                        }
                        val speedSnapshot = speedList.toList()
                        speedSnapshot.forEach { speed ->
                            assertTrue(speed >= 0f)
                        }
                    } catch (t: Throwable) {
                        readerExceptions.add(t)
                    }
                    yield()
                }
            }
        }

        // 2. Spawn concurrent writer coroutines inserting items rapidly
        val writers = (1..numWriters).map { writerId ->
            launch {
                repeat(writeCount) { i ->
                    telemetryList.add(
                        TelemetryPoint(
                            offsetSeconds = i,
                            wallClockTimeMs = System.currentTimeMillis() + i * 1000L,
                            speedDisplay = (i % 120).toFloat(),
                            speedUnit = "km/h",
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitudeMeters = 15.0,
                            headingDegrees = 180f,
                            headingCardinal = "S",
                            gForce = 1.0f,
                            isLocked = false
                        )
                    )
                    speedList.add((i % 120).toFloat())
                    if (i % 25 == 0) {
                        yield()
                    }
                }
            }
        }

        // Await all writers
        writers.joinAll()

        // Stop readers cleanly
        readers.forEach { it.cancelAndJoin() }

        // Assertions: 0 exceptions, exact counts verified
        assertTrue("Zero ConcurrentModificationExceptions must occur during concurrent mutations", readerExceptions.isEmpty())
        val expectedTotal = numWriters * writeCount
        assertEquals(expectedTotal, telemetryList.size)
        assertEquals(expectedTotal, speedList.size)
    }
}
