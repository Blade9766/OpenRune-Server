package org.rsmod.content.skills.construction.data

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.FurnitureRow

/**
 * Which furniture can be packed at a workbench, and the flatpack obj it packs into.
 *
 * The cache keeps that link in a server-only column, so it is rebuilt here from names: a flatpack
 * is `poh_flatpack_<x>` where the furniture row's icon obj is `poh_<x>`, give or take underscores,
 * and the few that are named differently share the row's display name instead.
 */
object Flatpacks {
    /** The highest furniture level each workbench can pack. */
    val WORKBENCHES: Map<String, Int> =
        mapOf(
            "loc.poh_workbench_1" to 20,
            "loc.poh_workbench_2" to 40,
            "loc.poh_workbench_3" to 60,
            "loc.poh_workbench_4" to 80,
            "loc.poh_workbench_5" to 99,
        )

    private const val PREFIX = "obj.poh_flatpack_"

    private class Flatpack(val obj: String, val key: String, val name: String)

    private val flatpacks: List<Flatpack> by lazy {
        ServerCacheManager.getItems().values.mapNotNull { item ->
            val obj = runCatching { RSCM.getReverseMapping(RSCMType.OBJ, item.id) }.getOrNull()
            if (obj == null || !obj.startsWith(PREFIX)) {
                return@mapNotNull null
            }
            Flatpack(obj, normalise(obj.removePrefix(PREFIX)), item.name.lowercase())
        }
    }

    /**
     * Flatpacks some furniture row's icon already names. The name fallback skips these, or the
     * costume room's "Oak wardrobe" would pack into the bedroom oak wardrobe's flatpack.
     */
    private val claimed: Set<String> by lazy {
        FurnitureRow.all().mapTo(HashSet()) {
            normalise(RSCM.getReverseMapping(RSCMType.OBJ, it.modelObj.id).removePrefix("obj.poh_"))
        }
    }

    private val cache = HashMap<Buildable, String?>()

    /** The flatpack [option] packs into, or null when it cannot be packed. */
    fun of(option: Buildable): String? =
        cache.getOrPut(option) {
            if (option.upgrade) {
                return@getOrPut null
            }
            val row = FurnitureRows.of(option) ?: return@getOrPut null
            val icon = RSCM.getReverseMapping(RSCMType.OBJ, row.modelObj.id)
            val key = normalise(icon.removePrefix("obj.poh_"))
            val name = row.name.lowercase()
            (flatpacks.firstOrNull { it.key == key } ?: flatpacks.firstOrNull { it.name == name && it.key !in claimed })
                ?.obj
        }

    private fun normalise(name: String): String = name.replace("_", "")
}
