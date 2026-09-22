package org.rsmod.content.other.special.attacks.magic

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.SpellSpecialAttack
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * Purging staff special, Scatter ashes: casts the best demonbane spell the caster has the Magic
 * level for, taking its runes as normal. Only usable on the Arceuus spellbook and only against
 * demons. A cast that kills its target costs no energy and shortens the next attack delay.
 */
class PurgingStaffSpecialAttack
@Inject
constructor(private val spells: MagicSpellRegistry, private val spellManager: SpellAttackManager) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerSpell("obj.purging_staff", ScatterAshes(manager))
    }

    private inner class ScatterAshes(private val manager: SpecialAttackManager) :
        SpellSpecialAttack {
        private val ProtectedAccess.spellbook by enumVarBit<Spellbook>("varbit.spellbook")

        override suspend fun ProtectedAccess.attack(target: Player, weapon: InvObj): Boolean =
            reject(DEMONS_ONLY)

        override suspend fun ProtectedAccess.attack(target: Npc, weapon: InvObj): Boolean {
            if (spellbook != Spellbook.Arceuus) {
                return reject("You need to be on the Arceuus spellbook to use this special attack.")
            }
            if (target.type.param(params.demon) == 0) {
                return reject(DEMONS_ONLY)
            }
            val demonbane =
                bestDemonbane(player.magicLvl)
                    ?: return reject("You need a Magic level of at least 44 to cast a demonbane.")

            val attack = CombatAttack.Spell(weapon, demonbane.spell, defensive = false)
            val cast = spellManager.attemptCast(this, attack)
            if (cast.isFailure()) {
                return false
            }
            player.anim("seq.human_spellcast_demonbane", priority = 6)
            spotanim("spotanim.${demonbane.tier}_demonbane_cast_spotanim", height = LAUNCH_HEIGHT)

            if (!spellManager.rollSpellAccuracy(this, target, attack, cast)) {
                spellManager.playSplashFx(this, target, CLIENT_DELAY, null, SOUND_RADIUS)
                spellManager.queueSplashHit(this, target, demonbane.spell.obj, CLIENT_DELAY, HIT_DELAY)
                manager.continueCombat(this, target)
                return true
            }

            val damage = spellManager.rollMaxHit(this, target, attack, cast, demonbane.maxHit)
            spellManager.playHitFx(
                source = this,
                target = target,
                clientDelay = CLIENT_DELAY,
                castSound = null,
                soundRadius = SOUND_RADIUS,
                hitSpot = "spotanim.${demonbane.tier}_demonbane_hit_spotanim",
                hitSpotHeight = 0,
                hitSound = null,
            )
            spellManager.giveCombatXp(this, target, attack, damage)
            spellManager.queueMagicHit(this, target, demonbane.spell.obj, damage, CLIENT_DELAY, HIT_DELAY)
            manager.continueCombat(this, target)

            if (damage >= target.hitpoints) {
                // The 3-tick reduction lands a tick late, so a kill leaves an effective 3-tick delay.
                manager.setNextAttackDelay(this, KILL_ATTACK_DELAY)
                return false
            }
            return true
        }

        private fun ProtectedAccess.reject(message: String): Boolean {
            mes(message)
            manager.stopCombat(this)
            return false
        }
    }

    private data class Demonbane(val spell: MagicSpell, val tier: String, val maxHit: Int)

    private fun bestDemonbane(magicLvl: Int): Demonbane? =
        spells
            .combatSpells()
            .filter { it.spellbook == Spellbook.Arceuus && it.levelReq <= magicLvl }
            .mapNotNull { spell ->
                val maxHit = DEMONBANE_MAX_HITS[spell.name] ?: return@mapNotNull null
                Demonbane(spell, spell.name.substringBefore(' ').lowercase(), maxHit)
            }
            .maxByOrNull { it.spell.levelReq }

    private companion object {
        const val DEMONS_ONLY = "This spell only affects demons."
        const val LAUNCH_HEIGHT = 92
        const val SOUND_RADIUS = 8
        const val CLIENT_DELAY = 30
        const val HIT_DELAY = 2
        const val KILL_ATTACK_DELAY = 3

        val DEMONBANE_MAX_HITS =
            mapOf("Inferior Demonbane" to 16, "Superior Demonbane" to 23, "Dark Demonbane" to 30)
    }
}
