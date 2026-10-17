package org.rsmod.content.other.bots.meister.world

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.logging.InlineLogger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.rsmod.content.other.bots.meister.MeisterFill
import org.rsmod.content.other.bots.meister.MeisterHistorySink

/**
 * Master Meister's trade history: one JSON fill per line, only ever appended and forced to disk
 * before [record] returns. The first line records when tracking began. A torn last line from a
 * crash mid-write is skipped on load (its fill is recorded again on the next count).
 */
class FileHistorySink(private val path: Path, now: Long) : MeisterHistorySink {
    private val logger = InlineLogger()
    private val fills = LinkedHashMap<String, MeisterFill>()

    override val trackingSince: Long

    init {
        var since = now
        if (Files.exists(path)) {
            val bytes = Files.readAllBytes(path)
            if (bytes.isNotEmpty() && bytes.last() != NEWLINE) {
                append("\n")
            }
            for (line in Files.readAllLines(path, StandardCharsets.UTF_8)) {
                when {
                    line.startsWith(SINCE) -> since = line.removePrefix(SINCE).trim().toLongOrNull() ?: since
                    line.isBlank() -> {}
                    else ->
                        runCatching { MAPPER.readValue<MeisterFill>(line) }
                            .onSuccess { fills.putIfAbsent(it.id, it) }
                            .onFailure { logger.warn { "Skipping an unreadable trade history line in $path." } }
                }
            }
        } else {
            Files.createDirectories(path.toAbsolutePath().parent)
            append("$SINCE$now\n")
        }
        trackingSince = since
    }

    override fun record(fill: MeisterFill): Boolean {
        if (fill.id in fills) {
            return false
        }
        append(MAPPER.writeValueAsString(fill) + "\n")
        fills[fill.id] = fill
        return true
    }

    override fun all(): List<MeisterFill> = fills.values.toList()

    private fun append(text: String) {
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
            channel.write(ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)))
            channel.force(true)
        }
    }

    private companion object {
        private const val SINCE = "#tracking-since "
        private const val NEWLINE: Byte = 10
        private val MAPPER =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
