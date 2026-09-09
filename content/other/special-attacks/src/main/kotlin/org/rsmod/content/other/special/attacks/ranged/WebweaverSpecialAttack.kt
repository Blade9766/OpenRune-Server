package org.rsmod.content.other.special.attacks.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.content.other.poison.NpcPoison
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Webweaver bow - Swarm: four arrows in quick succession, each rolled separately at double
 * accuracy for up to 40% (rounded up) of the player's max hit, with a chance to poison the target
 * on every hit that lands. Costs 50% special energy (from the cache enum) and one revenant ether
 * for the whole volley.
 */
class WebweaverSpecialAttack @Inject constructor(private val charges: CombatChargeManager) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged("obj.wild_cave_webweaver_charged", Swarm(manager, charges))
    }

    private class Swarm(
        private val manager: SpecialAttackManager,
        private val charges: CombatChargeManager,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean =
            swarm(target, attack) {
                val immune = (target.visType.paramOrNull(params.poison_immunity) ?: 0) > 0
                if (!immune) {
                    NpcPoison.tryPoison(target, POISON_DAMAGE)
                }
            }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = swarm(target, attack) { PlayerPoison.tryPoison(target, initialDamage = POISON_DAMAGE) }

        private fun ProtectedAccess.swarm(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            poison: () -> Unit,
        ): Boolean {
            val ether = charges.getWeaponCharges(player, ETHER_VAROBJ)
            if (ether <= ACTIVATION_ETHER) {
                manager.stopCombat(this)
                mes("Your Webweaver bow has no revenant ether left to fire. Use more ether on it.")
                return false
            }

            anim("seq.human_special01_webweaver")
            soundSynth(ATTACK_SOUND)
            spotanim(LAUNCH, height = 96, slot = constants.spotanim_slot_combat)

            val maxHit =
                manager.calculateRangedMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = 1.0,
                    boltSpecDamage = 0,
                )
            // 40% of the max hit, rounded up.
            val swarmMaxHit = (maxHit * 2 + 4) / 5

            var totalDamage = 0
            for ((index, projanim) in PROJANIMS.withIndex()) {
                val proj = manager.spawnProjectile(this, target, TRAVEL, projanim)
                val landed =
                    manager.rollRangedAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = attack.type,
                        multiplier = 2.0,
                    )
                val damage = if (landed) random.of(swarmMaxHit + 1) else 0
                totalDamage += damage

                if (index == 0) {
                    manager.queueRangedHit(this, target, null, damage, proj.clientCycles, proj.serverCycles)
                } else {
                    manager.queueRangedDamage(this, target, null, damage, proj.serverCycles)
                }

                if (damage > 0) {
                    target.spotanim(IMPACT, delay = proj.clientCycles, height = 96)
                    if (random.of(POISON_ONE_IN) == 0) {
                        poison()
                    }
                }
            }

            manager.giveCombatXp(this, target, attack, totalDamage)
            charges.attemptDetractWeapon(player, ETHER_VAROBJ)
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        /** Shared with `RevenantBowWeapons` in the special-weapons plugin. */
        const val ETHER_VAROBJ = "varobj.wild_cave_bow_ether"
        const val ACTIVATION_ETHER = 1_000

        /** The bow's regular attack sound (`attack_sound_stance1`). */
        const val ATTACK_SOUND = 2693

        const val LAUNCH = "spotanim.fx_webweaver01_launch_spotanim"
        const val IMPACT = "spotanim.fx_webweaver01_impact_spotanim"
        const val TRAVEL = "spotanim.wild_cave_bow_arrow_travel02"

        /** One projectile per arrow, each landing roughly a tick after the previous one. */
        val PROJANIMS =
            listOf(
                "projanim.arrow",
                "projanim.doublearrow_two",
                "projanim.webweaver_swarm_three",
                "projanim.webweaver_swarm_four",
            )

        const val POISON_DAMAGE = 4

        /** Chance for a landed arrow to poison. The wiki gives no rate; one in four is assumed. */
        const val POISON_ONE_IN = 4
    }
}
