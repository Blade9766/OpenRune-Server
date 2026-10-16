package org.rsmod.content.other.bots.meister.world

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.rsmod.content.other.bots.meister.MeisterSave
import org.rsmod.content.other.bots.meister.MeisterStore

/** Master Meister's ledger, written like the bot file: a temporary file and an atomic move. */
class FileMeisterStore(private val path: Path) : MeisterStore {
    override fun load(): MeisterSave? = if (Files.exists(path)) MAPPER.readValue<MeisterSave>(path.toFile()) else null

    override fun write(save: MeisterSave) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        MAPPER.writeValue(tmp.toFile(), save)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        private val MAPPER =
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
