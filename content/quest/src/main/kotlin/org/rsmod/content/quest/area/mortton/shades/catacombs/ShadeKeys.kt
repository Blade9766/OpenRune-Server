package org.rsmod.content.quest.area.mortton.shades.catacombs

import org.rsmod.game.inv.Inventory

/**
 * The shade keys: five metals, each opening its own and every lower metal's doors, and five loop
 * colours that pick which chest of that metal the key unlocks.
 */
internal enum class ShadeMetal(val key: String, val door: String, val chest: String) {
    Bronze("bronze", "loc.shadelair_bronzedoor", "Bronze"),
    Steel("steel", "loc.shadelair_steeldoor", "Steel"),
    Black("black", "loc.shadelair_blackdoor", "Black"),
    Silver("silver", "loc.shadelair_silverdoor", "Silver"),
    Gold("gold", "loc.shadelair_golddoor", "Gold"),
}

internal enum class ShadeTrim(val key: String) {
    BloodRed("bloodred"),
    Brown("brown"),
    Crimson("crimson"),
    Black("black"),
    Purple("purple"),
}

internal object ShadeKeys {
    fun key(metal: ShadeMetal, trim: ShadeTrim): String = "obj.shadekey_${metal.key}_${trim.key}"

    val ALL: List<String> = ShadeMetal.entries.flatMap { metal -> ShadeTrim.entries.map { key(metal, it) } }

    /** The best metal among the keys in [inv], or null without any shade key. */
    fun bestMetal(inv: Inventory): ShadeMetal? =
        ShadeMetal.entries.lastOrNull { metal -> ShadeTrim.entries.any { key(metal, it) in inv } }
}
