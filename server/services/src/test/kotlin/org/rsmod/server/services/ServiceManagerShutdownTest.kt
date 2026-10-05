package org.rsmod.server.services

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceManagerShutdownTest {
    private class RecordingService(
        private val name: String,
        private val log: MutableList<String>,
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
