package org.rsmod.content.skills.farming.data

import org.rsmod.map.CoordGrid

/**
 * A single farmable patch in the world.
 *
 * [loc] is the patch multiloc's gameval name and doubles as the patch's save key, because every
 * patch this plugin knows about has exactly one spawn cluster in the world. [varbit] is the
 * "transmit" varbit that multiloc reads: the handful of `farming_transmit_*` varbits are recycled
 * across the map, and the server sets whichever ones belong to the scene the player is standing in.
 */
class FarmingPatch(
    val loc: String,
    val kind: PatchKind,
    val varbit: String,
    val coords: CoordGrid,
    val region: String,
)

object FarmingPatches {
    val ALL: List<FarmingPatch> =
        listOf(
            // Falador
            patch("loc.farming_veg_patch_1", PatchKind.ALLOTMENT, A, 3052, 3309, "Falador"),
            patch("loc.farming_veg_patch_2", PatchKind.ALLOTMENT, B, 3057, 3305, "Falador"),
            patch("loc.farming_flower_patch_1", PatchKind.FLOWER, C, 3054, 3307, "Falador"),
            patch("loc.farming_herb_patch_1", PatchKind.HERB, D, 3058, 3311, "Falador"),

            // Catherby
            patch("loc.farming_veg_patch_3", PatchKind.ALLOTMENT, A, 2809, 3467, "Catherby"),
            patch("loc.farming_veg_patch_4", PatchKind.ALLOTMENT, B, 2809, 3460, "Catherby"),
            patch("loc.farming_flower_patch_2", PatchKind.FLOWER, C, 2809, 3463, "Catherby"),
            patch("loc.farming_herb_patch_2", PatchKind.HERB, D, 2813, 3463, "Catherby"),

            // Ardougne
            patch("loc.farming_veg_patch_5", PatchKind.ALLOTMENT, A, 2666, 3378, "Ardougne"),
            patch("loc.farming_veg_patch_6", PatchKind.ALLOTMENT, B, 2666, 3371, "Ardougne"),
            patch("loc.farming_flower_patch_3", PatchKind.FLOWER, C, 2666, 3374, "Ardougne"),
            patch("loc.farming_herb_patch_3", PatchKind.HERB, D, 2670, 3374, "Ardougne"),

            // Port Phasmatys
            patch("loc.farming_veg_patch_7", PatchKind.ALLOTMENT, A, 3599, 3527, "Port Phasmatys"),
            patch("loc.farming_veg_patch_8", PatchKind.ALLOTMENT, B, 3604, 3523, "Port Phasmatys"),
            patch("loc.farming_flower_patch_4", PatchKind.FLOWER, C, 3601, 3525, "Port Phasmatys"),
            patch("loc.farming_herb_patch_4", PatchKind.HERB, D, 3605, 3529, "Port Phasmatys"),

            // Harmony Island
            patch("loc.farming_veg_patch_9", PatchKind.ALLOTMENT, A, 3794, 2835, "Harmony Island"),
            patch("loc.farming_herb_patch_5", PatchKind.HERB, B, 3789, 2837, "Harmony Island"),

            // Hosidius
            patch("loc.farming_veg_patch_10", PatchKind.ALLOTMENT, A, 1736, 3556, "Hosidius"),
            patch("loc.farming_veg_patch_11", PatchKind.ALLOTMENT, B, 1732, 3552, "Hosidius"),
            patch("loc.farming_flower_patch_5", PatchKind.FLOWER, C, 1734, 3554, "Hosidius"),
            patch("loc.farming_herb_patch_6", PatchKind.HERB, D, 1738, 3550, "Hosidius"),

            // Farming Guild
            patch("loc.farming_veg_patch_12", PatchKind.ALLOTMENT, C, 1269, 3734, "Farming Guild"),
            patch("loc.farming_veg_patch_13", PatchKind.ALLOTMENT, D, 1269, 3725, "Farming Guild"),
            patch("loc.farming_flower_patch_6", PatchKind.FLOWER, F, 1260, 3725, "Farming Guild"),
            patch("loc.farming_herb_patch_7", PatchKind.HERB, E, 1238, 3726, "Farming Guild"),

            // Civitas illa Fortis
            patch("loc.farming_veg_patch_14", PatchKind.ALLOTMENT, A, 3290, 6102, "Civitas illa Fortis"),
            patch("loc.farming_veg_patch_15", PatchKind.ALLOTMENT, B, 3290, 6096, "Civitas illa Fortis"),
            patch("loc.farming_flower_patch_7", PatchKind.FLOWER, C, 3292, 6099, "Civitas illa Fortis"),

            // Aldarin
            patch("loc.farming_veg_patch_16", PatchKind.ALLOTMENT, A, 1583, 3100, "Aldarin"),
            patch("loc.farming_veg_patch_17", PatchKind.ALLOTMENT, B, 1587, 3096, "Aldarin"),
            patch("loc.farming_flower_patch_8", PatchKind.FLOWER, C, 1585, 3098, "Aldarin"),
            patch("loc.farming_herb_patch_8", PatchKind.HERB, D, 1581, 3094, "Aldarin"),

            // Sunset Coast
            patch("loc.farming_flower_patch_9", PatchKind.FLOWER, C, 1352, 3022, "Sunset Coast"),

            // Hops
            patch("loc.farming_hops_patch_1", PatchKind.HOPS, A, 2575, 3104, "Yanille"),
            patch("loc.farming_hops_patch_2", PatchKind.HOPS, A, 2810, 3336, "Entrana"),
            patch("loc.farming_hops_patch_3", PatchKind.HOPS, A, 3229, 3315, "Lumbridge"),
            patch("loc.farming_hops_patch_4", PatchKind.HOPS, A, 2666, 3525, "Seers' Village"),
            patch("loc.farming_hops_patch_5", PatchKind.HOPS, A, 1364, 2938, "Aldarin"),
        )

    private val byLoc: Map<String, FarmingPatch> = ALL.associateBy(FarmingPatch::loc)

    fun forLoc(loc: String): FarmingPatch? = byLoc[loc]

    /**
     * Patches close enough to [coords] for the client to be drawing them. The client scene is
     * 104x104 tiles, so nothing further than half of that can be on screen - which is also why
     * recycling a transmit varbit between two distant patches is safe.
     */
    fun visibleFrom(coords: CoordGrid): List<FarmingPatch> =
        ALL.filter {
            it.coords.level == coords.level &&
                kotlin.math.abs(it.coords.x - coords.x) <= SCENE_RADIUS &&
                kotlin.math.abs(it.coords.z - coords.z) <= SCENE_RADIUS
        }

    const val SCENE_RADIUS: Int = 52

    private const val A = "varbit.farming_transmit_a"
    private const val B = "varbit.farming_transmit_b"
    private const val C = "varbit.farming_transmit_c"
    private const val D = "varbit.farming_transmit_d"
    private const val E = "varbit.farming_transmit_e"
    private const val F = "varbit.farming_transmit_h"

    private fun patch(
        loc: String,
        kind: PatchKind,
        varbit: String,
        x: Int,
        z: Int,
        region: String,
    ) = FarmingPatch(loc, kind, varbit, CoordGrid(x, z), region)
}
