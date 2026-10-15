package org.rsmod.content.other.special.weapons.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Craw's bow and the Webweaver bow: revenant weapons that fire without arrows and instead burn one
 * revenant ether per shot, hit or miss.
 *
 * The bow's varobj holds the 1,000 activation ether plus up to 16,000 ether of ammunition (see
 * [org.rsmod.content.other.special.weapons.scripts.charge.RevenantBowCharging]). A charged bow
 * with only the activation ether left refuses to fire until more ether is added. The 50% accuracy
 * and damage boost against npcs in the Wilderness is applied by the ranged combat formulas.
 */
class RevenantBowWeapons @Inject constructor(private val charges: CombatChargeManager) :
    WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register(
            "obj.wild_cave_bow_charged",
            RevenantBow(
                manager = manager,
                charges = charges,
                name = "Craw's bow",
                launch = "spotanim.wild_cave_bow_arrow_launch",
                travel = "spotanim.wild_cave_bow_arrow_travel",
            ),
        )
        register("obj.wild_cave_bow_uncharged", UnchargedRevenantBow(manager, "Craw's bow"))

        register(
            "obj.wild_cave_webweaver_charged",
            RevenantBow(
                manager = manager,
                charges = charges,
                name = "Webweaver bow",
                launch = "spotanim.wild_cave_bow_arrow_launch02",
                travel = "spotanim.wild_cave_bow_arrow_travel02",
            ),
        )
        register(
            "obj.wild_cave_webweaver_uncharged",
            UnchargedRevenantBow(manager, "Webweaver bow"),
        )
    }

    private class RevenantBow(
        private val manager: WeaponAttackManager,
        private val charges: CombatChargeManager,
        private val name: String,
        private val launch: String,
        private val travel: String,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        private fun ProtectedAccess.shoot(target: PathingEntity, attack: CombatAttack.Ranged) {
            val ether = charges.getWeaponCharges(player, ETHER_VAROBJ)
            if (ether <= ACTIVATION_ETHER) {
                manager.stopCombat(this)
                mes("Your $name has no revenant ether left to fire. Use more ether on it.")
                return
            }

            // Plays the bow's `attack_anim_stance1` and `attack_sound_stance1` params.
            manager.playWeaponFx(this, attack)
            spotanim(launch, height = 96, slot = constants.spotanim_slot_combat)

            val projanim = manager.spawnProjectile(this, target, travel, "projanim.arrow")
            val (serverDelay, clientDelay) = projanim.durations

            val damage = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)

            // The activation ether is never spent, so the bow stays in its charged form.
            charges.attemptDetractWeapon(player, ETHER_VAROBJ)
            if (ether - 1 == ACTIVATION_ETHER) {
                mes("Your $name has run out of revenant ether.")
            }
            manager.continueCombat(this, target)
        }
    }

    private class UnchargedRevenantBow(
        private val manager: WeaponAttackManager,
        private val name: String,
    ) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            refuse()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            refuse()
            return true
        }

        private fun ProtectedAccess.refuse() {
            manager.stopCombat(this)
            mes("Your $name needs to be charged with revenant ether before it can fire.")
        }
    }

    companion object {
        const val ETHER_VAROBJ: String = "varobj.wild_cave_bow_ether"

        /** Ether spent to activate the bow. It is held in the varobj but never fired. */
        const val ACTIVATION_ETHER: Int = 1_000
    }
}
