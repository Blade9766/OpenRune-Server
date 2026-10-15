package org.rsmod.content.other.special.attacks.magic

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.content.other.special.attacks.TargetStats
import org.rsmod.content.other.special.attacks.specialAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The powered staff specials: Condemn (accursed sceptre) and Soul Rend (Eye of Ayak).
 *
 * Both staves build their max hit from the wielder's Magic level the same way their ordinary
 * attack does, so the special reuses that base and only applies its own multipliers on top.
 */
class PoweredStaffSpecialAttacks @Inject constructor(private val charges: CombatChargeManager) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val condemn = Condemn(manager, charges)
        registerMagic("obj.wild_cave_accursed_charged", condemn)
        registerMagic("obj.wild_cave_accursed_charged_recol", condemn)

        registerMagic("obj.eye_of_ayak", SoulRend(manager, charges))
    }

    /**
     * Accursed sceptre: +50% accuracy and damage, and a landed hit drains the target's Magic and
     * Defence by 15% of their current levels.
     */
    private class Condemn(
        private val manager: SpecialAttackManager,
        private val charges: CombatChargeManager,
    ) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Staff) =
            condemn(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Staff) =
            condemn(target, attack)

        private fun ProtectedAccess.condemn(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!hasCharge(charges, manager, OUT_OF_CHARGES)) {
                return false
            }
            specialAnim("seq.human_special_accursed")
            manager.playWeaponSound(this, attack)

            val landed = manager.rollStaffAccuracy(this, target, attack.style, ACCURACY)
            val damage =
                if (landed) {
                    manager.rollStaffMaxHit(this, target, sceptreBaseMaxHit(), DAMAGE)
                } else {
                    0
                }

            val proj = manager.spawnProjectile(this, target, SCEPTRE_TRAVEL, "projanim.magic_spell")
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, proj.clientCycles, proj.serverCycles)

            if (damage > 0) {
                TargetStats.drainPercentOfCurrent(target, TargetStats.MAGIC, DRAIN_PERCENT)
                TargetStats.drainPercentOfCurrent(target, TargetStats.DEFENCE, DRAIN_PERCENT)
            }
            charges.attemptDetractWeapon(player, STAFF_CHARGES)
            manager.continueCombat(this, target)
            return true
        }

        /** Mirrors the accursed sceptre's ordinary built-in spell. */
        private fun ProtectedAccess.sceptreBaseMaxHit(): Int = player.stat("stat.magic") / 3 - 6

        private companion object {
            const val ACCURACY = 1.5
            const val DAMAGE = 1.5
            const val DRAIN_PERCENT = 15
            const val SCEPTRE_TRAVEL = "spotanim.spells_thammaron01_travel01"
            const val OUT_OF_CHARGES =
                "The sceptre has no charges! You need to charge it with revenant ether."
        }
    }

    /**
     * Eye of Ayak: double accuracy and +30% damage, draining the target's Magic by the damage
     * dealt. The volley is slower than the staff's ordinary attack.
     */
    private class SoulRend(
        private val manager: SpecialAttackManager,
        private val charges: CombatChargeManager,
    ) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Staff) =
            soulRend(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Staff) =
            soulRend(target, attack)

        private fun ProtectedAccess.soulRend(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!hasCharge(charges, manager, OUT_OF_CHARGES)) {
                return false
            }
            specialAnim("seq.human_eye_of_ayak_special")
            spotanim(
                "spotanim.vfx_ayak_player_special_spotanim",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            manager.playWeaponSound(this, attack)

            val landed = manager.rollStaffAccuracy(this, target, attack.style, ACCURACY)
            val damage =
                if (landed) {
                    manager.rollStaffMaxHit(this, target, ayakBaseMaxHit(), DAMAGE)
                } else {
                    0
                }

            val proj = manager.spawnProjectile(this, target, AYAK_TRAVEL, "projanim.magic_spell")
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, proj.clientCycles, proj.serverCycles)

            if (damage > 0) {
                target.spotanim(AYAK_IMPACT, delay = proj.clientCycles, height = 96)
                TargetStats.drain(target, TargetStats.MAGIC, damage)
            }
            charges.attemptDetractWeapon(player, STAFF_CHARGES)
            manager.setNextAttackDelay(this, SPECIAL_ATTACK_RATE)
            manager.continueCombat(this, target)
            return true
        }

        /** Mirrors the Eye of Ayak's ordinary built-in spell. */
        private fun ProtectedAccess.ayakBaseMaxHit(): Int = player.stat("stat.magic") / 3 - 6

        private companion object {
            const val ACCURACY = 2.0
            const val DAMAGE = 1.3
            const val SPECIAL_ATTACK_RATE = 5
            const val AYAK_TRAVEL = "spotanim.vfx_ayak_normal_projectile"
            const val AYAK_IMPACT = "spotanim.vfx_ayak_impact_special_spotanim"
            const val OUT_OF_CHARGES =
                "The Eye of Ayak has no charges! You need to charge it with demon tears, " +
                    "or death and chaos runes."
        }
    }
}

/**
 * Powered staves keep their charges in a shared varobj. A special is still an attack from the
 * staff, so an empty one cannot fire it - without this the special would slip past the charge
 * check that the staff's ordinary attack performs.
 */
private fun ProtectedAccess.hasCharge(
    charges: CombatChargeManager,
    manager: SpecialAttackManager,
    outOfCharges: String,
): Boolean {
    if (charges.getWeaponCharges(player, STAFF_CHARGES) > 0) {
        return true
    }
    manager.stopCombat(this)
    mes(outOfCharges)
    return false
}

/** Every powered staff stores its charges here. */
private const val STAFF_CHARGES = "varobj.powered_staff_charges"
