package org.rsmod.content.other.special.weapons.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.constants
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The crystal bow and the Bow of Faerdhinen: elven bows that generate their own arrows, so they
 * fire without anything in the quiver.
 *
 * The active bows spend one charge per shot (hit or miss) and turn into their inactive form when
 * the last charge is used. Charges are added with crystal shards, see
 * [org.rsmod.content.other.special.weapons.scripts.charge.CrystalBowCharging]. The corrupted Bow
 * of Faerdhinen never degrades. The crystal armour accuracy and damage boosts are part of the
 * ranged combat formulas.
 */
class CrystalBowWeapons @Inject constructor(private val charges: CombatChargeManager) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val crystalBow =
            CrystalBow(
                manager = manager,
                charges = charges,
                name = "crystal bow",
                launch = "spotanim.sp_attack_glow_arrow_launch",
                travel = "spotanim.sp_attack_glow_arrow_travel",
                degradable = true,
            )
        register("obj.crystal_bow", crystalBow)
        register("obj.crystal_bow_2500", crystalBow)
        register("obj.crystal_bow_inactive", InactiveCrystalBow(manager, "crystal bow"))

        register(
            "obj.bow_of_faerdhinen",
            CrystalBow(
                manager = manager,
                charges = charges,
                name = "Bow of Faerdhinen",
                launch = "spotanim.sp_attack_arrow_launch_faerdhinen",
                travel = "spotanim.sp_attack_arrow_travel_faerdhinen",
                degradable = true,
            ),
        )
        register(
            "obj.bow_of_faerdhinen_inactive",
            InactiveCrystalBow(manager, "Bow of Faerdhinen"),
        )

        for ((obj, spotSuffix) in CORRUPTED_BOWS) {
            register(
                obj,
                CrystalBow(
                    manager = manager,
                    charges = charges,
                    name = "Bow of Faerdhinen",
                    launch = "spotanim.sp_attack_arrow_launch_faerdhinen_$spotSuffix",
                    travel = "spotanim.sp_attack_arrow_travel_faerdhinen_$spotSuffix",
                    degradable = false,
                ),
            )
        }
    }

    private class CrystalBow(
        private val manager: WeaponAttackManager,
        private val charges: CombatChargeManager,
        private val name: String,
        private val launch: String,
        private val travel: String,
        private val degradable: Boolean,
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
            if (!degradable) {
                fireArrow(target, attack)
                manager.continueCombat(this, target)
                return
            }

            val charge = charges.attemptDetractWeapon(player, CRYSTAL_CHARGES)
            if (charge.isFailure()) {
                manager.stopCombat(this)
                mes("Your $name has run out of charges.")
                return
            }

            fireArrow(target, attack)

            if (charge.fullyUncharged) {
                manager.stopCombat(this)
                mes("Your $name has run out of charges and become inactive.")
                return
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.fireArrow(target: PathingEntity, attack: CombatAttack.Ranged) {
            // Plays the bow's `attack_anim_stance1` and `attack_sound_stance1` params.
            manager.playWeaponFx(this, attack)
            spotanim(launch, height = 96, slot = constants.spotanim_slot_combat)

            val projanim = manager.spawnProjectile(this, target, travel, "projanim.arrow")
            val (serverDelay, clientDelay) = projanim.durations

            val damage = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)
        }
    }

    private class InactiveCrystalBow(
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
            mes("Your $name is inactive. Use crystal shards on it to charge it before it can fire.")
        }
    }

    private companion object {
        const val CRYSTAL_CHARGES = "varobj.crystal_weapon_charges"

        /**
         * The corrupted bows and the suffix of their arrow spotanims. The Meilyr recolour has no
         * arrow graphic of its own in the cache, so it fires the plain corrupted arrow.
         */
        val CORRUPTED_BOWS =
            listOf(
                "obj.bow_of_faerdhinen_infinite" to "infinite",
                "obj.bow_of_faerdhinen_infinite_ithell" to "ithell",
                "obj.bow_of_faerdhinen_infinite_iorwerth" to "iorwerth",
                "obj.bow_of_faerdhinen_infinite_trahaearn" to "trahaearn",
                "obj.bow_of_faerdhinen_infinite_cadarn" to "cadarn",
                "obj.bow_of_faerdhinen_infinite_crwys" to "crwys",
                "obj.bow_of_faerdhinen_infinite_meilyr" to "infinite",
                "obj.bow_of_faerdhinen_infinite_amlodd" to "amlodd",
            )
    }
}
