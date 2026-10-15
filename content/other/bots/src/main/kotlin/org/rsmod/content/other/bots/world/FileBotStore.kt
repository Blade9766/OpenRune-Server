package org.rsmod.content.other.bots.world

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
 * crash leaves either the old or the new file, never a partial one.
 */
class FileBotStore(private val path: Path) : BotStore {
    private val bots = LinkedHashMap<Long, BotRecord>()
    private var settings = BotGlobalSettings()

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

    override fun write(write: BotWrite) {
        write.bots.forEach { bots[it.id] = it }
        write.deleted.forEach { bots.remove(it) }
        write.settings?.let { settings = it }
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        MAPPER.writeValue(tmp.toFile(), BotFileData(settings, bots.values.toList()))
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        private val MAPPER =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}

internal data class BotFileData(
    val settings: BotGlobalSettings = BotGlobalSettings(),
    val bots: List<BotRecord> = emptyList(),
)
