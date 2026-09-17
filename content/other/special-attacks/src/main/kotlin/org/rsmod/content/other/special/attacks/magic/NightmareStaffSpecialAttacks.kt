package org.rsmod.content.other.special.attacks.magic

import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.SpellSpecialAttack
import org.rsmod.content.other.special.attacks.TargetStats
import org.rsmod.content.other.special.attacks.specialAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * The nightmare staff specials: Immolate (volatile) and Invocate (eldritch).
 *
 * Both cast a spell of the staff's own, costing no runes, and so are reachable whether or not the
 * player has a spell autocast.
 */
class NightmareStaffSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val immolate = Immolate(manager)
        registerSpell("obj.nightmare_staff_volatile", immolate)
        registerSpell("obj.br_nightmare_staff_volatile", immolate)
        registerSpell("obj.deadman_nightmare_staff_volatile", immolate)

        registerSpell("obj.nightmare_staff_eldritch", Invocate(manager))
    }

    /** Volatile nightmare staff: a spell at +50% accuracy that scales off the caster's Magic. */
    private class Immolate(private val manager: SpecialAttackManager) : SpellSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, weapon: InvObj) =
            immolate(target, weapon)

        override suspend fun ProtectedAccess.attack(target: Player, weapon: InvObj) =
            immolate(target, weapon)

        private fun ProtectedAccess.immolate(target: PathingEntity, weapon: InvObj): Boolean {
            val attack = CombatAttack.Staff(weapon, style = null)
            specialAnim("seq.nightmare_staff_special")
            spotanim(CAST_SPOT, height = 96, slot = constants.spotanim_slot_combat)
            manager.playWeaponSound(this, attack)

            val landed = manager.rollStaffAccuracy(this, target, null, ACCURACY)
            val damage =
                if (landed) manager.rollStaffMaxHit(this, target, baseMaxHit(), 1.0) else 0

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0, hitDelay = HIT_DELAY)
            if (damage > 0) {
                target.spotanim(IMPACT_SPOT, height = 96)
            }
            manager.continueCombat(this, target)
            return true
        }

        /** `min(floor(58 * magic / 99) + 1, 58)`, as the staff's own spell is defined. */
        private fun ProtectedAccess.baseMaxHit(): Int =
            min(58 * player.stat("stat.magic") / 99 + 1, 58)

        private companion object {
            const val ACCURACY = 1.5
            const val HIT_DELAY = 2
            const val CAST_SPOT = "spotanim.nightmare_staff_volatile_cast_spotanim"
            const val IMPACT_SPOT = "spotanim.nightmare_staff_volatile_hit_spotanim"
        }
    }

    /**
     * Eldritch nightmare staff: a spell that returns half its damage as prayer points, which may
     * carry the caster above their Prayer level.
     *
     * The prayer is granted from the damage the spell rolled rather than the damage the target
     * ends up taking, so an immune target still pays out.
     */
    private class Invocate(private val manager: SpecialAttackManager) : SpellSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, weapon: InvObj) =
            invocate(target, weapon)

        override suspend fun ProtectedAccess.attack(target: Player, weapon: InvObj) =
            invocate(target, weapon)

        private fun ProtectedAccess.invocate(target: PathingEntity, weapon: InvObj): Boolean {
            val attack = CombatAttack.Staff(weapon, style = null)
            specialAnim("seq.nightmare_staff_special")
            spotanim(CAST_SPOT, height = 96, slot = constants.spotanim_slot_combat)
            manager.playWeaponSound(this, attack)

            val landed = manager.rollStaffAccuracy(this, target, null, 1.0)
            val damage =
                if (landed) manager.rollStaffMaxHit(this, target, baseMaxHit(), 1.0) else 0

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0, hitDelay = HIT_DELAY)
            if (damage > 0) {
                target.spotanim(IMPACT_SPOT, height = 96)
            }
            restorePrayer(damage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.restorePrayer(damage: Int) {
            val current = player.stat(TargetStats.PRAYER)
            val restore = min(damage / 2, PRAYER_CEILING - current)
            if (restore > 0) {
                player.statAdd(TargetStats.PRAYER, constant = restore, percent = 0)
            }
        }

        /** `min(floor(44 * magic / 99) + 1, 44)`, as the staff's own spell is defined. */
        private fun ProtectedAccess.baseMaxHit(): Int =
            min(44 * player.stat("stat.magic") / 99 + 1, 44)

        private companion object {
            const val HIT_DELAY = 2
            const val PRAYER_CEILING = 120
            const val CAST_SPOT = "spotanim.nightmare_staff_eldritch_cast_spotanim"
            const val IMPACT_SPOT = "spotanim.nightmare_staff_eldritch_hit_spotanim"
        }
    }
}
