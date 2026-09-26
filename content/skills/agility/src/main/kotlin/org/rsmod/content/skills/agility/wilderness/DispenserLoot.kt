package org.rsmod.content.skills.agility.wilderness

import org.rsmod.api.random.GameRandom

/** One weighted entry of a dispenser roll: [count] of [obj] (noted where the wiki says so). */
data class DispenserDrop(val obj: String, val weight: Int, val count: IntRange = 1..1)

/**
 * The Agility dispenser's loot, from the wiki's drop tables. A paid lap rolls once on the
 * bracket's resources and once on its armour; both come noted. The brackets follow the lap
 * streak: 1-15, 16-30, 31-60 and 61+.
 */
object DispenserLoot {
    class Bracket(val laps: IntRange, val resources: List<DispenserDrop>, val armour: List<DispenserDrop>)

    val brackets: List<Bracket> by lazy {
        listOf(
            Bracket(1..15, resources(3..6, 1..2), armour(ARMOUR_1_15)),
            Bracket(16..30, resources(6..11, 2..4), armour(ARMOUR_16_30)),
            Bracket(31..60, resources(10..16, 3..6), armour(ARMOUR_31_60)),
            Bracket(61..Int.MAX_VALUE, resources(14..20, 4..8), armour(ARMOUR_61)),
        )
    }

    /** The unnoted supply given when the player has a free inventory slot. */
    val extraSupply: List<DispenserDrop> by lazy {
        listOf(
            DispenserDrop(ANGLERFISH, 5),
            DispenserDrop(MANTA_RAY, 5),
            DispenserDrop(KARAMBWAN, 5),
            DispenserDrop(SUPER_RESTORE, 1),
        )
    }

    fun bracket(streak: Int): Bracket = brackets.first { streak.coerceAtLeast(1) in it.laps }

    fun roll(random: GameRandom, drops: List<DispenserDrop>): DispenserDrop {
        var pick = random.of(drops.sumOf { it.weight })
        for (drop in drops) {
            pick -= drop.weight
            if (pick < 0) {
                return drop
            }
        }
        return drops.last()
    }

    private fun resources(food: IntRange, restores: IntRange): List<DispenserDrop> =
        listOf(
            DispenserDrop(cert(ANGLERFISH), 8, food),
            DispenserDrop(cert(MANTA_RAY), 8, food),
            DispenserDrop(cert(KARAMBWAN), 8, food),
            DispenserDrop(cert(SUPER_RESTORE), 7, restores),
        )

    private fun armour(weights: List<Pair<String, Int>>): List<DispenserDrop> =
        weights.map { (obj, weight) -> DispenserDrop(cert(obj), weight) }

    private fun cert(obj: String): String = "obj.cert_" + obj.removePrefix("obj.")

    const val ANGLERFISH = "obj.blighted_anglerfish"
    const val MANTA_RAY = "obj.blighted_mantaray"
    const val KARAMBWAN = "obj.blighted_karambwan"
    const val SUPER_RESTORE = "obj.blighted_4dose2restore"

    private val ARMOUR_1_15 =
        listOf(
            "obj.adamant_full_helm" to 1,
            "obj.adamant_platebody" to 2,
            "obj.adamant_platelegs" to 1,
            "obj.mithril_chainbody" to 1,
            "obj.mithril_platelegs" to 1,
            "obj.mithril_plateskirt" to 1,
            "obj.rune_med_helm" to 2,
            "obj.steel_platebody" to 1,
        )

    private val ARMOUR_16_30 =
        listOf(
            "obj.adamant_full_helm" to 1,
            "obj.adamant_platebody" to 1,
            "obj.adamant_platelegs" to 1,
            "obj.mithril_chainbody" to 1,
            "obj.mithril_platelegs" to 1,
            "obj.mithril_plateskirt" to 1,
            "obj.rune_chainbody" to 1,
            "obj.rune_kiteshield" to 1,
            "obj.rune_med_helm" to 1,
        )

    private val ARMOUR_31_60 =
        listOf(
            "obj.adamant_full_helm" to 1,
            "obj.adamant_platebody" to 1,
            "obj.adamant_platelegs" to 1,
            "obj.mithril_platelegs" to 1,
            "obj.mithril_plateskirt" to 1,
            "obj.rune_chainbody" to 2,
            "obj.rune_kiteshield" to 2,
            "obj.rune_med_helm" to 1,
        )

    private val ARMOUR_61 =
        listOf(
            "obj.adamant_full_helm" to 1,
            "obj.adamant_platebody" to 2,
            "obj.adamant_platelegs" to 1,
            "obj.mithril_platelegs" to 1,
            "obj.mithril_plateskirt" to 1,
            "obj.rune_chainbody" to 6,
            "obj.rune_kiteshield" to 6,
            "obj.rune_med_helm" to 2,
        )
}
