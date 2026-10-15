package org.rsmod.content.other.special.attacks.magic

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.content.other.special.attacks.specialAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Pulsate - the Dawnbringer's special attack: a guaranteed blast for 75-150 damage.
 *
 * The damage is rolled flat and deliberately bypasses every combat formula, because the special
 * ignores magic bonuses, prayer and level boosts alike. It only works on Verzik, which is the one
 * thing the Dawnbringer was made to shoot.
 */
class DawnbringerSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMagic("obj.verzik_special_weapon", Pulsate(manager))
    }

    private class Pulsate(private val manager: SpecialAttackManager) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!VERZIK_NAME.containsMatchIn(npcVisType(target).name)) {
                return refuse()
            }
            return pulsate(target, attack)
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean = refuse()

        private fun ProtectedAccess.pulsate(target: Npc, attack: CombatAttack.Staff): Boolean {
            specialAnim(CAST_ANIM)
            spotanim(CAST_SPOT, height = 92, slot = constants.spotanim_slot_combat)

            val damage = random.of(MIN_DAMAGE..MAX_DAMAGE)
            val proj = manager.spawnProjectile(this, target, TRAVEL_SPOT, "projanim.magic_spell")
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, proj.clientCycles, proj.serverCycles)
            target.spotanim(IMPACT_SPOT, delay = proj.clientCycles, height = 124)

            manager.continueCombat(this, target)
            return true
        }

        /** Returning `false` leaves the player's special attack energy untouched. */
        private fun ProtectedAccess.refuse(): Boolean {
            manager.stopCombat(this)
            mes("The Dawnbringer's power only has an effect on Verzik Vitur.")
            return false
        }

        private companion object {
            const val MIN_DAMAGE = 75
            const val MAX_DAMAGE = 150
            const val CAST_ANIM = "seq.human_castwave_staff"
            const val CAST_SPOT = "spotanim.dawnbringer_casting_spec"
            const val TRAVEL_SPOT = "spotanim.dawnbringer_projectile_spec"
            const val IMPACT_SPOT = "spotanim.dawnbringer_impact_spec"

            val VERZIK_NAME = Regex("verzik", RegexOption.IGNORE_CASE)
        }
    }
}
