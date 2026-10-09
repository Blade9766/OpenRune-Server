package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.player.mapMultiway
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class ChinchompaWeapons
@Inject
constructor(
    private val ammunition: RangedAmmoManager,
    private val npcRepo: NpcRepository,
    private val areaChecker: AreaChecker,
    private val attackValidateHooks: Set<NpcAttackValidateHook>,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val chinchompa = Chinchompa(manager)
        register("obj.chinchompa_captured", chinchompa)
        register("obj.chinchompa_big_captured", chinchompa)
        register("obj.chinchompa_black", chinchompa)
    }

    private inner class Chinchompa(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            throwChinchompa(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            throwChinchompa(target, attack)
            return true
        }

        private fun ProtectedAccess.throwChinchompa(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ) {
            val chinchompa = getInvObj(attack.weapon)
            val projectile = chinchompa.paramOrNull(params.proj_type)?.id
            val travelSpotanim = chinchompa.paramOrNull(params.proj_travel)
            if (projectile == null || travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to throw your chinchompa.")
                return
            }

            manager.playWeaponFx(this, attack)

            val launchSpotanim =
                chinchompa.paramOrNull(params.proj_launch)?.let {
                    RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id)
                }
            spotanim(launchSpotanim, height = 96, slot = constants.spotanim_slot_combat)

            val proj =
                manager.spawnProjectile(
                    this,
                    target,
                    RSCM.getReverseMapping(RSCMType.SPOTANIM, travelSpotanim.id),
                    RSCM.getReverseMapping(RSCMType.PROJANIM, projectile),
                )
            val (serverDelay, clientDelay) = proj.durations

            ammunition.consumeThrownWeapon(player, chinchompa)

            val accurate =
                manager.rollRangedAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = RangedAttackType.Heavy,
                    multiplier = 1.0,
                )
            for (victim in collectTargets(target)) {
                val damage =
                    if (accurate) {
                        manager.rollRangedMaxHit(
                            source = this,
                            target = victim,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            multiplier = 1.0,
                            boltSpecDamage = 0,
                        )
                    } else {
                        0
                    }
                manager.giveCombatXp(this, victim, attack, damage)
                manager.queueRangedHit(this, victim, chinchompa, damage, clientDelay, serverDelay)
            }

            if (player.righthand == null) {
                mes("That was your last one!")
                manager.stopCombat(this)
                return
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.collectTargets(primary: PathingEntity): List<PathingEntity> {
            val targets = mutableListOf(primary)
            if (primary !is Npc || !mapMultiway() || !primary.mapMultiway(areaChecker)) {
                return targets
            }
            val centre = primary.coords
            for (npc in npcRepo.findAll(ZoneKey.from(centre), zoneRadius = 1)) {
                if (targets.size >= MAX_TARGETS) {
                    break
                }
                if (npc !== primary && npc.overlapsAoe(centre) && canHitSecondary(player, npc)) {
                    targets += npc
                }
            }
            return targets
        }

        private fun canHitSecondary(player: Player, npc: Npc): Boolean {
            if (!npc.isValidTarget() || !npc.visType.hasOp(InteractionOp.Op2.slot)) {
                return false
            }
            if (!npc.mapMultiway(areaChecker)) {
                return false
            }
            return attackValidateHooks.none {
                it.validate(player, npc) is NpcAttackValidateResult.Deny
            }
        }

        private fun Npc.overlapsAoe(centre: CoordGrid): Boolean =
            coords.level == centre.level &&
                coords.x <= centre.x + AOE_RADIUS &&
                coords.x + size - 1 >= centre.x - AOE_RADIUS &&
                coords.z <= centre.z + AOE_RADIUS &&
                coords.z + size - 1 >= centre.z - AOE_RADIUS
    }

    private companion object {
        const val AOE_RADIUS = 1
        const val MAX_TARGETS = 9
    }
}
