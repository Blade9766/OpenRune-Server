package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.CHARGE_MAX_HIT_BONUS
import org.rsmod.content.skills.magic.spell.attacks.standard.GodSpellHooks
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the arena does to the god spells: every cast of a god spell, anywhere, counts towards
 * mastering it (100 casts), which is what Kolodion asks for before Mage Arena II; and the Charge
 * spell adds 10 to its max hit while the caster wears the god's cape and wields the god's staff.
 */
class GodSpellTraining
@Inject
constructor(private val hooks: GodSpellHooks, private val mageArena: MageArenaQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        hooks.addListener { player, spell, _ ->
            val god = God.bySpell(spell.id) ?: return@addListener
            if (mageArena.recordCast(player, god)) {
                player.mes("You have mastered ${god.spellName}. Kolodion may have a new challenge for you.")
            }
        }

        hooks.addMaxHitBonus { player, spell ->
            val god = God.bySpell(spell.id)
            val charged =
                god != null &&
                    mageArena.isCharged(player) &&
                    player.wornCapeGod() == god &&
                    player.wieldedStaffGod() == god
            if (charged) CHARGE_MAX_HIT_BONUS else 0
        }
    }
}

/**
 * The Charge spell (level 80): seven minutes of stronger god spells for anyone wearing a god
 * cape with its matching staff.
 */
class ChargeSpell
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val mageArena: MageArenaQuest,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val spellObj = ServerCacheManager.getItem(CHARGE.asRSCM(RSCMType.OBJ)) ?: error("Missing spell obj: $CHARGE")
        val spell = spells.getObjSpell(spellObj) ?: error("Charge is not a registered spell.")
        onIfOverlayButton(spell.component) { cast(spell) }
    }

    private fun ProtectedAccess.cast(spell: org.rsmod.api.combat.commons.magic.MagicSpell) {
        if (actionDelay > mapClock) {
            return
        }
        if (mageArena.isCharged(player)) {
            mes("You already have a charge of magical power.")
            return
        }
        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }
        actionDelay = mapClock + CAST_DELAY
        statAdvance("stat.magic", spell.castXp)
        anim(CAST_ANIM)
        spotanim(CAST_SPOTANIM, height = CAST_SPOTANIM_HEIGHT)
        soundSynth(CAST_SOUND)
        mageArena.charge(player)
        mes("You feel charged with magic power.")
    }

    private companion object {
        const val CHARGE = "obj.80_charge"
        const val CAST_ANIM = "seq.human_casting"

        /** Spotanim 301, the blue swirl the client uses for Charge (shared with the crossbow special). */
        const val CAST_SPOTANIM = "spotanim.acb_specialattack"
        const val CAST_SPOTANIM_HEIGHT = 0
        const val CAST_SOUND = "synth.godspell_charge"
        const val CAST_DELAY = 3
    }
}
