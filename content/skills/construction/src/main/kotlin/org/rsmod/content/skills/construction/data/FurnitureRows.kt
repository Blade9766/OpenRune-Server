package org.rsmod.content.skills.construction.data

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.FurnitureRow

/**
 * Finds the cache `furniture` row behind each [Buildable], which is what the furniture creation
 * interface draws an entry from.
 *
 * The cache names rows differently from the wiki labels used here ("Steel framed bench" for the
 * steel framed workbench), so a row is matched on its Construction level and bill of materials,
 * and the label only breaks ties, by the most words in common and the fewest extra: the cache
 * reorders and shortens names ("Saradomin symbol", "Mahogany eagle" for the lectern). Nails are
 * left out of the bill, because the cache costs them as the `any_nails` placeholder that stands
 * for every nail type.
 */
object FurnitureRows {
    private val construction by lazy { "stat.construction".asRSCM(RSCMType.STAT) }
    private val anyNails by lazy { "obj.any_nails".asRSCM(RSCMType.OBJ) }
    private val nails by lazy { "obj.nails".asRSCM(RSCMType.OBJ) }

    private val byKey: Map<Key, List<FurnitureRow>> by lazy {
        FurnitureRow.all().groupBy { row ->
            val level = row.levelRequirement.firstOrNull { it.t0.id == construction }?.t1 ?: -1
            val materials = row.materialCost.associate { it.t0.id to it.t1 }
            Key(level, materials.withoutNails())
        }
    }

    private val cache = HashMap<Buildable, FurnitureRow?>()

    fun of(option: Buildable): FurnitureRow? =
        cache.getOrPut(option) {
            val label = words(option.label)
            candidates(option).maxByOrNull { row ->
                val name = words(row.name)
                val shared = name.count { it in label }
                shared * 2 - (name.size - shared)
            }
        }

    /** True when every word of the row's name appears in [option]'s label. */
    fun namesMatch(option: Buildable, row: FurnitureRow): Boolean =
        words(option.label).containsAll(words(row.name))

    fun isAmbiguous(option: Buildable): Boolean = candidates(option).size > 1

    private fun candidates(option: Buildable): List<FurnitureRow> {
        val materials = option.materials.associate { it.obj.asRSCM(RSCMType.OBJ) to it.count }
        return byKey[Key(option.level, materials.withoutNails())].orEmpty()
    }

    private fun words(name: String): Set<String> =
        name.lowercase().split(' ', '-').filterTo(HashSet()) { it.isNotEmpty() && it != "of" }

    private fun Map<Int, Int>.withoutNails(): Map<Int, Int> =
        filterKeys { it != nails && it != anyNails }

    private data class Key(val level: Int, val materials: Map<Int, Int>)
}
