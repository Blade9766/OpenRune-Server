package org.rsmod.content.skills.construction

/** Shared identifiers for the construction plugin. */
object Construction {
    const val STAT = "stat.construction"

    const val COINS = "obj.coins"
    const val HAMMER = "obj.hammer"
    const val SAW = "obj.poh_saw"

    const val BUILD_ANIM = "seq.human_poh_build"

    const val BUILD_WOOD_SOUND = "synth.poh_build_wood"
    const val BUILD_STONE_SOUND = "synth.poh_build_stone"
    const val BUILD_METAL_SOUND = "synth.poh_build_metal"
    const val TELEPORT_SOUND = "synth.poh_teleport"

    /** Ticks a single build or removal takes. */
    const val BUILD_CYCLE = 3

    const val HOUSE_COST = 1_000

    /** Rooms per axis on each floor, matching the grid the house options map shows. */
    const val GRID = 13

    const val STARTER_CELL = GRID / 2
}
