package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.game.entity.Player

/**
 * Inside a house, players may fight each other in a combat room's ring - both standing in the same
 * one - or anywhere in its dungeon while the owner has it in PvP challenge mode. The balance beam is
 * fought with pugels, not weapons. Attacks anywhere else are left to the other hooks.
 */
class HousePvPHook @Inject constructor(private val registry: HouseRegistry) : PvPAttackValidateHook {
    override fun validate(attacker: Player, target: Player): PvPAttackValidateResult {
        val house = registry.houseAt(attacker.coords) ?: return PvPAttackValidateResult.Pass
        if (registry.houseAt(target.coords) !== house) {
            return PvPAttackValidateResult.Deny("You can't attack someone outside this house.")
        }
        val ring = registry.ringAt(house, attacker.coords)
        if (ring != null) {
            return when {
                registry.ringAt(house, target.coords) !== ring ->
                    PvPAttackValidateResult.Deny("You can only fight someone in the same ring as you.")
                ring.ring == Combat.Ring.BEAM ->
                    PvPAttackValidateResult.Deny("Use a pugel to knock your opponent off the beam.")
                else -> PvPAttackValidateResult.Pass
            }
        }
        val dungeon = Floor.DUNGEON.regionLevel
        val allowed =
            house.owner.pvpMode && attacker.coords.level == dungeon && target.coords.level == dungeon
        return if (allowed) {
            PvPAttackValidateResult.Pass
        } else {
            PvPAttackValidateResult.Deny("You can only fight other players in a combat ring, or the dungeon in PvP challenge mode.")
        }
    }
}
