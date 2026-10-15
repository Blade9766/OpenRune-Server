package org.rsmod.content.skills.hunter.pitfall

/**
 * Pitfall prey. Jagex has not published pitfall catch or undamaged-fur rates, so [low]/[high]
 * (catch) and [furLow]/[furHigh] (undamaged fur) are estimates on the usual /256 skilling curve.
 */
enum class PitfallCreature(
    val npc: String,
    val displayName: String,
    val level: Int,
    val xp: Double,
    val pits: IntRange,
    val guaranteed: List<String>,
    val fur: String?,
    val tattyFur: String?,
    val low: Int = 48,
    val high: Int = 256,
    val furLow: Int = 96,
    val furHigh: Int = 320,
) {
    SabreToothedKyatt(
        npc = "npc.hunting_snow_tiger",
        displayName = "sabre-toothed kyatt",
        level = 55,
        xp = 300.0,
        pits = 1..6,
        guaranteed = listOf("obj.big_bones", "obj.hunting_kyatt_meat"),
        fur = "obj.hunting_fur_tiger_perfect",
        tattyFur = "obj.hunting_fur_tiger_shabby",
    ),
    SpinedLarupia(
        npc = "npc.hunting_jaguar",
        displayName = "spined larupia",
        level = 31,
        xp = 180.0,
        pits = 7..11,
        guaranteed = listOf("obj.big_bones", "obj.hunting_larupia_meat"),
        fur = "obj.hunting_fur_jaguar_perfect",
        tattyFur = "obj.hunting_fur_jaguar_shabby",
    ),
    HornedGraahk(
        npc = "npc.hunting_leopard",
        displayName = "horned graahk",
        level = 41,
        xp = 240.0,
        pits = 12..16,
        guaranteed = listOf("obj.big_bones", "obj.hunting_graahk_meat"),
        fur = "obj.hunting_fur_leopard_perfect",
        tattyFur = "obj.hunting_fur_leopard_shabby",
    ),
    SunlightAntelope(
        npc = "npc.sunlight_antelope",
        displayName = "sunlight antelope",
        level = 72,
        xp = 380.0,
        pits = 17..21,
        guaranteed =
            listOf(
                "obj.big_bones",
                "obj.hunting_antelopesun_horn",
                "obj.hunting_antelopesun_fur",
                "obj.hunting_antelopesun_meat",
            ),
        fur = null,
        tattyFur = null,
    ),
    MoonlightAntelope(
        npc = "npc.moonlight_antelope",
        displayName = "moonlight antelope",
        level = 91,
        xp = 450.0,
        pits = 22..25,
        guaranteed =
            listOf(
                "obj.big_bones",
                "obj.hunting_antelopemoon_horn",
                "obj.hunting_antelopemoon_fur",
                "obj.hunting_antelopemoon_meat",
            ),
        fur = null,
        tattyFur = null,
    ),
    ;

    companion object {
        fun forPit(pit: Int): PitfallCreature? = entries.firstOrNull { pit in it.pits }

        val byNpc: Map<String, PitfallCreature> = entries.associateBy { it.npc }
    }
}

object Pits {
    const val COUNT = 25

    const val EMPTY = 0
    const val SET = 1
    const val CATCHING = 2
    const val FULL = 3
    const val FULL_TURNED = 4

    fun baseLoc(pit: Int): String = "loc.hunting_pitfall_$pit"

    fun varbit(pit: Int): String = "varbit.hunt_pitfall_state$pit"
}
