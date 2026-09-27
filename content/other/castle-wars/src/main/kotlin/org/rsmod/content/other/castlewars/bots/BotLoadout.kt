package org.rsmod.content.other.castlewars.bots

import kotlin.random.Random
import org.rsmod.api.player.stat.statBase
import org.rsmod.game.entity.Player

internal enum class BotStyle {
    Melee,
    Ranged,
    Magic,
}

/** Combat levels a bot plays at, copied from the real players it is balanced against. */
internal data class BotLevels(
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val hitpoints: Int,
    val ranged: Int,
    val magic: Int,
    val prayer: Int,
) {
    fun jittered(random: Random): BotLevels {
        fun vary(level: Int, floor: Int = 1) = (level + random.nextInt(-3, 3)).coerceIn(floor, MAX_LEVEL)
        return BotLevels(
            attack = vary(attack),
            strength = vary(strength),
            defence = vary(defence),
            hitpoints = vary(hitpoints, floor = MIN_HITPOINTS),
            ranged = vary(ranged),
            magic = vary(magic),
            prayer = vary(prayer),
        )
    }

    companion object {
        const val MAX_LEVEL = 99
        const val MIN_HITPOINTS = 10

        /** The rounded average of each combat stat across [players]. */
        fun averageOf(players: Collection<Player>): BotLevels {
            fun avg(stat: String) = players.sumOf { it.statBase(stat) } / players.size
            return BotLevels(
                attack = avg("stat.attack"),
                strength = avg("stat.strength"),
                defence = avg("stat.defence"),
                hitpoints = avg("stat.hitpoints").coerceAtLeast(MIN_HITPOINTS),
                ranged = avg("stat.ranged"),
                magic = avg("stat.magic"),
                prayer = avg("stat.prayer"),
            )
        }
    }
}

/** What a bot wears and carries into the arena, picked from the levels it plays at. */
internal object BotLoadout {
    fun worn(style: BotStyle, levels: BotLevels): List<String> =
        when (style) {
            BotStyle.Melee -> melee(levels)
            BotStyle.Ranged -> ranged(levels)
            BotStyle.Magic -> magic(levels)
        }

    private fun melee(levels: BotLevels): List<String> {
        val metal = metalFor(levels.defence)
        val weapon =
            when {
                levels.attack >= 70 -> "obj.abyssal_whip"
                levels.attack >= 60 -> "obj.dragon_scimitar"
                else -> "obj.${metalFor(levels.attack)}_scimitar"
            }
        val amulet = if (levels.attack >= 60) "obj.amulet_of_glory" else "obj.amulet_of_strength"
        return listOf(
            weapon,
            "obj.${metal}_full_helm",
            "obj.${metal}_platebody",
            "obj.${metal}_platelegs",
            "obj.${metal}_kiteshield",
            "obj.${metal}_armoured_boots",
            amulet,
        )
    }

    private fun metalFor(level: Int): String =
        when {
            level >= 40 -> "rune"
            level >= 30 -> "adamant"
            level >= 20 -> "mithril"
            level >= 5 -> "steel"
            else -> "iron"
        }

    private fun ranged(levels: BotLevels): List<String> {
        val (bow, arrows) =
            when {
                levels.ranged >= 50 -> "obj.magic_shortbow" to "obj.rune_arrow"
                levels.ranged >= 40 -> "obj.yew_shortbow" to "obj.adamant_arrow"
                levels.ranged >= 30 -> "obj.maple_shortbow" to "obj.mithril_arrow"
                levels.ranged >= 20 -> "obj.willow_shortbow" to "obj.steel_arrow"
                else -> "obj.oak_shortbow" to "obj.iron_arrow"
            }
        val hide =
            when {
                levels.ranged >= 70 && levels.defence >= 40 -> "black"
                levels.ranged >= 60 && levels.defence >= 40 -> "red"
                levels.ranged >= 50 && levels.defence >= 40 -> "blue"
                levels.ranged >= 40 && levels.defence >= 40 -> "green"
                else -> null
            }
        val armour =
            when (hide) {
                null ->
                    if (levels.ranged >= 30 && levels.defence >= 30) {
                        listOf("obj.snakeskin_body", "obj.snakeskin_chaps", "obj.leather_vambraces", "obj.snakeskin_boots")
                    } else {
                        listOf("obj.leather_armour", "obj.leather_chaps", "obj.leather_vambraces", "obj.leather_boots")
                    }
                "green" -> listOf("obj.dragonhide_body", "obj.dragonhide_chaps", "obj.dragon_vambraces", "obj.leather_boots")
                else ->
                    listOf(
                        "obj.${hide}_dragonhide_body",
                        "obj.${hide}_dragonhide_chaps",
                        "obj.${hide}_dragon_vambraces",
                        "obj.leather_boots",
                    )
            }
        return listOf(bow, arrows, "obj.coif", "obj.amulet_of_glory") + armour
    }

    private fun magic(levels: BotLevels): List<String> {
        val staff =
            when {
                levels.magic >= 40 && levels.attack >= 40 -> "obj.mystic_fire_staff"
                levels.magic >= 30 && levels.attack >= 30 -> "obj.fire_battlestaff"
                else -> "obj.staff_of_fire"
            }
        val robes =
            if (levels.magic >= 40 && levels.defence >= 20) {
                listOf("obj.mystic_hat", "obj.mystic_robe_top", "obj.mystic_robe_bottom", "obj.mystic_gloves", "obj.mystic_boots")
            } else {
                listOf("obj.bluewizhat", "obj.wizards_robe", "obj.blue_skirt", "obj.boots_wizard")
            }
        return listOf(staff, "obj.amulet_of_magic") + robes
    }

    fun arrowCount(): Int = ARROWS

    fun food(levels: BotLevels): String =
        when {
            levels.hitpoints >= 60 -> "obj.shark"
            levels.hitpoints >= 40 -> "obj.swordfish"
            else -> "obj.lobster"
        }

    const val FOOD_COUNT: Int = 10
    const val PRAYER_POTIONS: Int = 2
    const val PRAYER_POTION: String = "obj.4doseprayerrestore"
    private const val ARROWS = 500
}
