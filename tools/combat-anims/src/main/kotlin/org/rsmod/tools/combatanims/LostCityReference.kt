package org.rsmod.tools.combatanims

/**
 * One npc of LostCity's 2004 content (`lostcity-npc-combat.tsv`, written by
 * `scripts/lostcity_reference.py`). Anims are bare sequence names and sounds are synth names, both
 * as Jagex named them, so they resolve against the OSRS cache directly.
 */
data class LostCityNpc(
    val key: String,
    val name: String,
    val osrsIds: List<Int>,
    val models: List<Int>,
    val combat: CombatNames,
)

/** An npc's combat set by name: sequence names for the anims, synth names for the sounds. */
data class CombatNames(
    val attackAnim: String? = null,
    val defendAnim: String? = null,
    val deathAnim: String? = null,
    val attackSound: String? = null,
    val defendSound: String? = null,
    val deathSound: String? = null,
)

object LostCityReference {
    const val NPC_RESOURCE: String = "lostcity-npc-combat.tsv"
    const val BODY_MODEL_RESOURCE: String = "lostcity-body-models.tsv"

    fun npcs(): List<LostCityNpc> = rows(NPC_RESOURCE).map(::parseNpc)

    /** Human body part models that are female (`woman_*`), as opposed to male (`man_*`). */
    fun femaleBodyModels(): Set<Int> =
        rows(BODY_MODEL_RESOURCE).filter { it[1] == "f" }.mapTo(HashSet()) { it[0].toInt() }

    fun maleBodyModels(): Set<Int> =
        rows(BODY_MODEL_RESOURCE).filter { it[1] == "m" }.mapTo(HashSet()) { it[0].toInt() }

    /**
     * Pairs each OSRS npc with its LostCity entry: first by the OSRS ids LostCity records against
     * the entry, then by display name and model list for the entries that record none.
     */
    fun match(
        npcs: List<LostCityNpc>,
        osrsNames: Map<Int, String>,
        osrsModels: Map<Int, List<Int>>,
    ): Map<Int, LostCityNpc> {
        val out = HashMap<Int, LostCityNpc>()
        for (npc in npcs) {
            for (id in npc.osrsIds) {
                if (id in osrsNames) {
                    out.putIfAbsent(id, npc)
                }
            }
        }
        val byLook = npcs.groupBy { it.name.lowercase() to it.models.sorted() }
        for ((id, name) in osrsNames) {
            if (id in out) {
                continue
            }
            val look = name.lowercase() to osrsModels[id].orEmpty().sorted()
            byLook[look]?.firstOrNull()?.let { out[id] = it }
        }
        return out
    }

    internal fun parseNpc(cells: List<String>): LostCityNpc {
        fun cell(index: Int): String? = cells.getOrNull(index)?.takeIf { it.isNotEmpty() && it != "null" }
        fun ints(index: Int): List<Int> =
            cell(index)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return LostCityNpc(
            key = cells[0],
            name = cells.getOrElse(1) { "" },
            osrsIds = ints(2),
            models = ints(3),
            combat =
                CombatNames(
                    attackAnim = cell(4),
                    defendAnim = cell(5),
                    deathAnim = cell(6),
                    attackSound = cell(7),
                    defendSound = cell(8),
                    deathSound = cell(9),
                ),
        )
    }

    private fun rows(resource: String): List<List<String>> {
        val stream =
            LostCityReference::class.java.classLoader.getResourceAsStream(resource)
                ?: error("Missing resource: $resource")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()
        }
    }
}
