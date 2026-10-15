package org.rsmod.content.skills.construction.house

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.hook.PvPAttackRestrictionHook
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.game.entity.Player

/**
 * Holds a fight in a combat room ring to the ring's rules, as the wiki gives them: the boxing ring
 * allows boxing gloves or bare fists and nothing worn, the fencing ring a weapon and shield but no
 * other armour or jewellery, both melee only; the ranging pedestals only ranged and magic, and the
 * combat ring anything. A fighter breaking a rule is stopped before the attack.
 */
class HouseRingRules @Inject constructor(private val registry: HouseRegistry) : PvPAttackRestrictionHook {
    override fun restriction(
        attacker: Player,
        target: Player,
        attack: CombatAttack.PlayerAttack,
        special: Boolean,
    ): String? {
        val house = registry.houseAt(attacker.coords) ?: return null
        val ring = registry.ringAt(house, attacker.coords)?.ring ?: return null
        val allowed =
            when (ring.style) {
                Combat.Style.MELEE -> attack is CombatAttack.Melee
                Combat.Style.RANGED_OR_MAGIC -> attack !is CombatAttack.Melee
                Combat.Style.ANY -> true
            }
        if (!allowed) {
            return if (ring.style == Combat.Style.MELEE) {
                "Only melee is allowed in the ${ring.label}."
            } else {
                "Only ranged and magic are allowed on the ${ring.label}."
            }
        }
        if (!ring.armour && attacker.worn.indices.any { it !in HANDS && attacker.worn[it] != null }) {
            return "You can't wear armour or jewellery in the ${ring.label}."
        }
        val weapons = ring.weapons ?: return null
        val held = listOfNotNull(attacker.worn[Wearpos.RightHand.slot], attacker.worn[Wearpos.LeftHand.slot])
        if (held.any { obj -> weapons.none { it.asRSCM(RSCMType.OBJ) == obj.id } }) {
            return "You can only fight with boxing gloves or your fists in the ${ring.label}."
        }
        return null
    }

    private companion object {
        val HANDS = setOf(Wearpos.RightHand.slot, Wearpos.LeftHand.slot)
    }
}
