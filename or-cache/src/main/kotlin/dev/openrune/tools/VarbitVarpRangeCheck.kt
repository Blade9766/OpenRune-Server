package dev.openrune.tools

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.toml.util.InternalAPI
import java.io.File

/**
 * A varbit stores its base varp as an unsigned 16-bit id, so a varbit on a varp above
 * [MAX_VARBIT_VARP_ID] silently wraps onto varp `id - 65536` at runtime. Varps that carry no
 * varbits (e.g. the `kc_*` counters) are free to sit above the limit.
 */
object VarbitVarpRangeCheck {
    const val MAX_VARBIT_VARP_ID = 0xFFFF

    private val VARBIT_HEADER = Regex("""^\s*\[\[?varbit]]?\s*$""", RegexOption.MULTILINE)

    private val mapper = tomlMapper { rsconfig { enableConstantProvider() } }

    private data class Offender(
        val varbit: String,
        val varp: String,
        val varpId: Int,
        val file: File,
    )

    fun validate(directories: List<File>) {
        val offenders =
            directories
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "toml" } }
                .distinct()
                .filter { VARBIT_HEADER.containsMatchIn(it.readText()) }
                .flatMap(::offendersIn)

        check(offenders.isEmpty()) {
            buildString {
                append("Varbit base varp ids are unsigned 16-bit; a varbit on a varp above ")
                append("$MAX_VARBIT_VARP_ID wraps onto varp (id - 65536). ")
                append("Move these varps below 65536:")
                offenders.forEach {
                    append("\n  ${it.varbit} -> ${it.varp} (id ${it.varpId}) in ${it.file.path}")
                }
            }
        }
    }

    @OptIn(InternalAPI::class)
    private fun offendersIn(file: File): List<Offender> =
        mapper
            .decodeRuneScapeBlocks(file.toPath())
            .filter { it.name == "varbit" }
            .mapNotNull { block ->
                val properties = block.map.properties
                val varpId = (properties["varp"] as? TomlValue.Integer)?.value?.toInt()
                if (varpId == null || varpId <= MAX_VARBIT_VARP_ID) return@mapNotNull null
                val varbitId = (properties["id"] as? TomlValue.Integer)?.value?.toInt()
                Offender(
                    varbit = varbitId?.let { nameOf("varbit", it) } ?: "varbit <no id>",
                    varp = nameOf("varp", varpId),
                    varpId = varpId,
                    file = file,
                )
            }

    private fun nameOf(table: String, id: Int): String =
        ConstantProvider.mappings[table]?.entries?.firstOrNull { it.value == id }?.key
            ?: "$table $id"
}
