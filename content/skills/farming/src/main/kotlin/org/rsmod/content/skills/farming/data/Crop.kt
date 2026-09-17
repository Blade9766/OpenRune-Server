package org.rsmod.content.skills.farming.data

/**
 * One growable crop.
 *
 * [growth], [watered], [diseased] and [dead] hold the raw values the patch's transmit varbit takes
 * for each visual state; they come straight out of the patch multiloc's transform list in the
 * cache, so the client renders exactly what live Old School does. [growth] runs from "just planted"
 * to "fully grown"; the other three are parallel to it:
 * - [watered] covers `growth[0]` up to but excluding the fully grown state.
 * - [diseased] and [dead] cover `growth[1]` up to but excluding the fully grown state, because a
 *   crop can neither catch a disease on the cycle it was planted nor after it has finished growing.
 */
class Crop(
    val key: String,
    val kind: PatchKind,
    val seed: String,
    val produce: String,
    val level: Int,
    val plantXp: Double,
    val harvestXp: Double,
    val growthMinutes: Int,
    val seedsPerPlant: Int,
    val growth: IntArray,
    val watered: IntArray,
    val diseased: IntArray,
    val dead: IntArray,
    val protection: Protection?,
    val lives: Int,
    val diseaseChance: Int,
    val saveLifeLow: Int = DEFAULT_SAVE_LOW,
    val saveLifeHigh: Int = DEFAULT_SAVE_HIGH,
) {
    val displayName: String = key.replace('_', ' ')

    /** Number of growth cycles between planting and a full crop. */
    val cycles: Int = growth.size - 1

    val minutesPerCycle: Int = growthMinutes / cycles

    val fullyGrownState: Int = growth.last()

    /** Single-harvest crops (the flowers) empty their patch on the one and only pick. */
    val singleHarvest: Boolean = lives == 1

    fun growthState(stage: Int, watered: Boolean): Int =
        if (watered && stage < this.watered.size) this.watered[stage] else growth[stage]

    fun diseasedState(stage: Int): Int = diseased[stage - 1]

    fun deadState(stage: Int): Int = dead[stage - 1]

    /** A crop is vulnerable on every cycle except the one it was planted on and the last one. */
    fun canCatchDisease(stage: Int): Boolean = stage in 1 until cycles

    companion object {
        /**
         * Old School only publishes the chance-to-save constants for herbs (25 at level 1, 80 at
         * level 99). Allotments and hops sit close enough to the same curve that sharing them keeps
         * yields in the right range without inventing per-crop numbers we cannot source.
         */
        const val DEFAULT_SAVE_LOW: Int = 25
        const val DEFAULT_SAVE_HIGH: Int = 80

        fun states(first: Int, count: Int): IntArray = IntArray(count) { first + it }
    }
}

/** What a gardener will accept to watch over a patch. */
class Protection(val obj: String, val count: Int, val label: String)
