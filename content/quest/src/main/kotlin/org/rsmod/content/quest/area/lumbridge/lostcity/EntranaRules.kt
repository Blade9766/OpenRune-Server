package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

private val FORBIDDEN_SLOTS =
    setOf(Wearpos.Hat, Wearpos.Torso, Wearpos.Legs, Wearpos.RightHand, Wearpos.LeftHand)

/** Robes the monks let through despite their small magic bonuses. */
private val TOLERATED =
    setOf(
        "obj.wizards_robe",
        "obj.wizards_robe_trim",
        "obj.wizards_robe_trim_gold",
        "obj.bluewizhat",
        "obj.black_robe",
        "obj.black_wizards_robe_trim",
        "obj.black_wizards_robe_gold",
        "obj.blackwizhat",
        "obj.blue_skirt",
        "obj.black_skirt",
    )

private val BONUS_PARAMS =
    listOf(
        params.attack_stab,
        params.attack_slash,
        params.attack_crush,
        params.attack_magic,
        params.attack_ranged,
        params.defence_stab,
        params.defence_slash,
        params.defence_crush,
        params.defence_magic,
        params.defence_ranged,
        params.melee_strength,
        params.ranged_strength,
    )

/** Saradomin's edict for Entrana, shared by the monks' ship and the Abyss's law rift. */
internal fun ItemServerType.isForbiddenOnEntrana(): Boolean {
    val slot = Wearpos[wearpos1] ?: return false
    if (slot !in FORBIDDEN_SLOTS || internalName in TOLERATED) {
        return false
    }
    return BONUS_PARAMS.any { (paramOrNull(it) ?: 0) != 0 }
}

internal fun Player.entranaForbiddenNames(): List<String> =
    (inv.filterNotNull { true } + worn.filterNotNull { true })
        .map { getInvObj(it) }
        .filter { it.isForbiddenOnEntrana() }
        .map { it.name }
        .distinct()

internal fun Player.carriesEntranaForbiddenItem(): Boolean {
    val carried = inv.filterNotNull { true } + worn.filterNotNull { true }
    return carried.any { getInvObj(it).isForbiddenOnEntrana() }
}
