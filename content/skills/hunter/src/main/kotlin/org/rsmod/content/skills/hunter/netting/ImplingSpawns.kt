package org.rsmod.content.skills.hunter.netting

import org.rsmod.map.CoordGrid

enum class ImplingTier(val precursor: String, val weights: List<Pair<Impling, Int>>) {
    Low(
        precursor = "npc.ii_common_impling_precursor",
        weights =
            listOf(
                Impling.Baby to 20,
                Impling.Young to 20,
                Impling.Gourmet to 20,
                Impling.Earth to 20,
                Impling.Essence to 10,
                Impling.Eclectic to 10,
            ),
    ),
    Mid(
        precursor = "npc.ii_uncommon_impling_precursor",
        weights =
            listOf(
                Impling.Gourmet to 10,
                Impling.Earth to 10,
                Impling.Essence to 20,
                Impling.Eclectic to 37,
                Impling.Nature to 20,
                Impling.Magpie to 2,
                Impling.Ninja to 1,
            ),
    ),
    High(
        precursor = "npc.ii_rare_impling_precursor",
        weights =
            listOf(
                Impling.Nature to 10,
                Impling.Magpie to 50,
                Impling.Ninja to 30,
                Impling.Dragon to 10,
                Impling.Lucky to 1,
            ),
    ),
    Crystal(
        precursor = "npc.ii_impling_type_12_precursor",
        weights = listOf(Impling.Crystal to 1),
    ),
}

object ImplingSpawns {
    val variable: List<CoordGrid> =
        coords(
            1254, 3559, 1275, 3749, 1705, 3479, 2203, 3236, 2331, 3639, 2394, 3512, 2459, 3418,
            2584, 2972, 2592, 3252, 2739, 3342, 2841, 2928, 2849, 3033, 3018, 3523, 3020, 3424,
            3143, 3231, 3239, 3283, 3292, 3265, 3356, 3013, 3408, 3125, 3441, 3351, 3454, 3486,
            3548, 3528, 3677, 3321, 2982, 3276, 3169, 3001, 2904, 3489, 2646, 3421, 2654, 3609,
            1616, 3701, 3135, 3378, 2727, 3768, 2814, 3512, 2470, 3217, 2740, 3535, 1600, 3837,
            2843, 3161, 1525, 3520, 3730, 3016, 3765, 3798, 2198, 2963, 2526, 3101, 2500, 2875,
            1328, 2971, 1453, 3130, 1721, 3001, 1617, 3182, 1288, 3168, 1455, 3319, 3160, 2488,
        )

    val lowTier: List<CoordGrid> =
        coords(
            1705, 3483, 2354, 3608, 2279, 3188, 2455, 3084, 2567, 3384, 2784, 3463, 2967, 3411,
            3093, 3238, 3283, 3428, 3278, 3155, 2481, 4442,
        )

    val crystal: List<CoordGrid> =
        coords(
            3290, 6117, 3307, 6041, 3210, 6110, 3213, 6049, 3237, 6102, 3308, 6117, 3240, 6046,
            3214, 6113, 3290, 6049, 3234, 6072, 3240, 6120, 3234, 6129,
        )

    private fun coords(vararg xz: Int): List<CoordGrid> =
        xz.toList().chunked(2).map { (x, z) -> CoordGrid(x, z, 0) }
}
