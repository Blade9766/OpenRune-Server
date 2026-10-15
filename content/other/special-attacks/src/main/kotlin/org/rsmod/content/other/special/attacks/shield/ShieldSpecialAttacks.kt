package org.rsmod.content.other.special.attacks.shield

import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.ShieldSpecialAttack
import org.rsmod.content.other.special.attacks.specialAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * The three shield specials: the dragonfire shield's and dragonfire ward's blasts of dragonfire,
 * and the ancient wyvern shield's freeze blast.
 *
 * All three are armed from the shield's own worn option rather than the special attack orb, cost
 * no special attack energy, and are limited only by their shared two-minute cooldown.
 */
class ShieldSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val dragonfire =
            ShieldBlast(
                manager = manager,
                maxHit = DRAGONFIRE_MAX_HIT,
                seq = "seq.qip_dragon_slayer_player_releasing_charge",
                castSpot = "spotanim.qip_dragon_slayer_player_releasing_charge",
                travelSpot = "spotanim.qip_dragon_slayer_player_projanim",
                impactSpot = "spotanim.qip_dragon_slayer_player_impact",
                synth = "synth.firebreath",
            )
        registerShield("obj.dragonfire_shield", dragonfire)

        registerShield(
            "obj.dragonfire_ward",
            ShieldBlast(
                manager = manager,
                maxHit = DRAGONFIRE_MAX_HIT,
                seq = "seq.dragonfire_ward_releasing_charge",
                castSpot = "spotanim.qip_dragon_slayer_player_releasing_charge",
                travelSpot = "spotanim.qip_dragon_slayer_player_projanim",
                impactSpot = "spotanim.qip_dragon_slayer_player_impact",
                synth = "synth.firebreath",
            ),
        )

        registerShield(
            "obj.wyvern_shield",
            ShieldBlast(
                manager = manager,
                maxHit = FROST_MAX_HIT,
                seq = "seq.fossil_wyvern_shield_release_charge",
                castSpot = "spotanim.fossil_wyvern_player_releasing_charge",
                travelSpot = "spotanim.wyvern_skeleton_travel_breath_ancient",
                impactSpot = "spotanim.fossil_wyvern_shield_attack_spotanim",
                synth = "synth.ice_blitz_impact",
            ),
        )
    }

    /**
     * A blast released from the shield, dealing a flat roll of damage that the wielder's combat
     * stats do not feed into.
     */
    private class ShieldBlast(
        private val manager: SpecialAttackManager,
        private val maxHit: Int,
        private val seq: String,
        private val castSpot: String,
        private val travelSpot: String,
        private val impactSpot: String,
        private val synth: String,
    ) : ShieldSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, shield: InvObj) = blast(target)

        override suspend fun ProtectedAccess.attack(target: Player, shield: InvObj) = blast(target)

        private fun ProtectedAccess.blast(target: PathingEntity): Boolean {
            specialAnim(seq)
            spotanim(castSpot, height = 96, slot = constants.spotanim_slot_combat)
            manager.soundArea(player, synth, radius = SOUND_RADIUS)

            val damage = random.of(0..maxHit)
            val proj = manager.spawnProjectile(this, target, travelSpot, "projanim.magic_spell")
            manager.queueMagicHit(this, target, damage, proj.clientCycles, proj.serverCycles)

            if (damage > 0) {
                target.spotanim(impactSpot, delay = proj.clientCycles, height = 96)
            }

            player.shieldSpecialReadyAt = mapClock + COOLDOWN_TICKS
            manager.continueCombat(this, target)
            return true
        }
    }

    internal companion object {
        /** Two minutes, shared by all three shields. */
        internal const val COOLDOWN_TICKS = 200

        internal val SPEC_SHIELDS =
            listOf(
                "obj.dragonfire_shield",
                "obj.dragonfire_ward",
                "obj.wyvern_shield",
            )

        private const val DRAGONFIRE_MAX_HIT = 25
        private const val FROST_MAX_HIT = 15
        private const val SOUND_RADIUS = 10
    }
}
