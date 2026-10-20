package org.rsmod.content.other.bots.world

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.rsmod.content.other.bots.core.BotGlobalSettings
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotStore
import org.rsmod.content.other.bots.core.BotStoreSnapshot
import org.rsmod.content.other.bots.core.BotWrite

/**
 * Keeps every bot in one JSON file, rewritten through a temporary file and an atomic move so a
 * crash leaves either the old or the new file, never a partial one. Writes are serialized (the
 * shutdown hook writes off the game thread) and only change the in-memory copy once on disk.
 */
class FileBotStore(private val path: Path) : BotStore {
    private val bots = LinkedHashMap<Long, BotRecord>()
    private var settings = BotGlobalSettings()

    @Synchronized
    override fun load(): BotStoreSnapshot {
        bots.clear()
        settings = BotGlobalSettings()
        if (Files.exists(path)) {
            val data = MAPPER.readValue<BotFileData>(path.toFile())
            settings = data.settings
            data.bots.forEach { bots[it.id] = it }
        }
        return BotStoreSnapshot(bots.values.toList(), settings)
    }

    @Synchronized
    override fun write(write: BotWrite) {
        val next = LinkedHashMap(bots)
        write.bots.forEach { next[it.id] = it }
        write.deleted.forEach { next.remove(it) }
        val nextSettings = write.settings ?: settings
        val dir = path.toAbsolutePath().parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "${path.fileName}.", ".tmp")
        try {
            MAPPER.writeValue(tmp.toFile(), BotFileData(nextSettings, next.values.toList()))
            replace(tmp)
        } finally {
            Files.deleteIfExists(tmp)
        }
        bots.clear()
        bots.putAll(next)
        settings = nextSettings
    }

    /** Windows refuses the move while another process (a virus scanner, an editor) has the file open. */
    private fun replace(tmp: Path) {
        var attempt = 0
        while (true) {
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return
            } catch (e: AccessDeniedException) {
                if (++attempt >= MOVE_ATTEMPTS) {
                    throw e
                }
                Thread.sleep(MOVE_RETRY_MS)
            }
        }
    }

    private companion object {
        private const val MOVE_ATTEMPTS = 5
        private const val MOVE_RETRY_MS = 20L
        private val MAPPER =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}

internal data class BotFileData(
    val settings: BotGlobalSettings = BotGlobalSettings(),
    val bots: List<BotRecord> = emptyList(),
)
