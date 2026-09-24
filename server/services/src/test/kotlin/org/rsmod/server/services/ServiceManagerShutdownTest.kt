package org.rsmod.server.services

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceManagerShutdownTest {
    private class RecordingService(
        private val name: String,
        private val log: MutableList<String>,
        override val shutdownStage: Int = Service.DEFAULT_SHUTDOWN_STAGE,
        private val shutdownDelayMs: Long = 0,
    ) : Service {
        override suspend fun startup() {}

        override suspend fun shutdown() {
            log += "$name:start"
            delay(shutdownDelayMs)
            log += "$name:end"
        }
    }

    @Test
    fun `resource stage shuts down only after default stage services finish`() {
        val log = Collections.synchronizedList(mutableListOf<String>())
        val database =
            RecordingService("database", log, shutdownStage = Service.RESOURCE_SHUTDOWN_STAGE)
        val saver = RecordingService("saver", log, shutdownDelayMs = 200)
        val manager = ServiceManager.create(linkedSetOf(database, saver))

        runBlocking { manager.awaitStartup() }
        manager.shutdown()
        manager.awaitShutdown()

        assertEquals(listOf("saver:start", "saver:end", "database:start", "database:end"), log)
    }

    @Test
    fun `second awaitShutdown caller blocks until shutdown completes`() {
        val log = Collections.synchronizedList(mutableListOf<String>())
        val saver = RecordingService("saver", log, shutdownDelayMs = 300)
        val manager = ServiceManager.create(setOf(saver))
        runBlocking { manager.awaitStartup() }

        val done = CountDownLatch(2)
        val results = Collections.synchronizedList(mutableListOf<ServiceManager.ShutdownResult>())
        repeat(2) {
            thread {
                results += manager.awaitShutdown()
                assertTrue("saver:end" in log)
                done.countDown()
            }
        }
        manager.shutdown()

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(ServiceManager.ShutdownResult.AlreadyShutDown in results)
        assertTrue(ServiceManager.ShutdownResult.Clean in results)
    }
}
