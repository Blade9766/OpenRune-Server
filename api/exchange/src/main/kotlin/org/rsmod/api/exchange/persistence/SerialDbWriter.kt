package org.rsmod.api.exchange.persistence

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConnection

/**
 * Applies writes one at a time, in submission order, each in its own transaction. A failed write
 * is retried until it succeeds rather than skipped, because a later write may depend on it (an
 * offer row must exist before its collection is recorded). [healthy] drops to `false` while a
 * write keeps failing so callers can stop creating new economic state until the backlog clears.
 */
@Singleton
public class SerialDbWriter @Inject constructor(private val database: Database) {
    private val logger = InlineLogger()

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "serial-db-writer").apply { isDaemon = true }
        }

    private val queued = AtomicInteger(0)
    private val failing = AtomicBoolean(false)
    private val shuttingDown = AtomicBoolean(false)

    public val pending: Int
        get() = queued.get()

    public val healthy: Boolean
        get() = !failing.get()

    public fun submit(label: String, write: (DatabaseConnection) -> Unit) {
        queued.incrementAndGet()
        executor.execute {
            try {
                writeWithRetry(label, write)
            } finally {
                queued.decrementAndGet()
            }
        }
    }

    public fun <T> readBlocking(read: (DatabaseConnection) -> T): T {
        val future = CompletableFuture<T>()
        executor.execute {
            try {
                future.complete(runBlocking { database.withTransaction(read) })
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return future.get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Blocks until every write submitted so far has been committed, or [timeoutMillis] passes. */
    public fun flush(timeoutMillis: Long): Boolean {
        val barrier = CompletableFuture<Unit>()
        executor.execute { barrier.complete(Unit) }
        return try {
            barrier.get(timeoutMillis, TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Drains the queue before the process exits. Safe to call more than once. */
    public fun shutdown(timeoutMillis: Long) {
        shuttingDown.set(true)
        flush(timeoutMillis)
        if (queued.get() > 0) {
            logger.error { "Shut down with ${queued.get()} economy write(s) still unwritten." }
        }
    }

    private fun writeWithRetry(label: String, write: (DatabaseConnection) -> Unit) {
        var attempt = 0
        while (true) {
            try {
                runBlocking { database.withTransaction(write) }
                if (failing.compareAndSet(true, false)) {
                    logger.info { "Economy writes recovered after $attempt retries ($label)." }
                }
                return
            } catch (t: Throwable) {
                attempt++
                failing.set(true)
                logger.error(t) { "Economy write '$label' failed (attempt $attempt)." }
                if (shuttingDown.get() && attempt >= SHUTDOWN_ATTEMPTS) {
                    logger.error { "Dropping economy write '$label' during shutdown." }
                    return
                }
                Thread.sleep(backoffMillis(attempt))
            }
        }
    }

    private fun backoffMillis(attempt: Int): Long =
        minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl minOf(attempt, 6))

    private companion object {
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val BASE_BACKOFF_MS = 250L
        private const val MAX_BACKOFF_MS = 15_000L
        private const val SHUTDOWN_ATTEMPTS = 5
    }
}
