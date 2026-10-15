package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.CHARGE_MAX_HIT_BONUS
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.CHARGE_TICKS
import org.rsmod.content.skills.magic.spell.attacks.standard.GodSpellHooks
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the arena does to the god spells: every cast of a god spell, anywhere, counts towards
 * mastering it (100 casts), which is what Kolodion asks for before Mage Arena II; and the Charge
 * spell adds 10 to its max hit while the caster wears the god's cape.
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
            val charged = god != null && mageArena.isCharged(player) && player.wornCapeGod() == god
            if (charged) CHARGE_MAX_HIT_BONUS else 0
        }
    }
}

/**
 * The Charge spell (level 80): seven minutes of stronger god spells for anyone wearing a god cape.
 * Only battle mages who have been given a god staff by the chamber guardian know it.
 */
class ChargeSpell
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val mageArena: MageArenaQuest,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val spellObj = ServerCacheManager.getItem(CHARGE.asRSCM(RSCMType.OBJ)) ?: error("Missing spell obj: $CHARGE")
        val spell = spells.getObjSpell(spellObj) ?: error("Charge is not a registered spell.")
        onIfOverlayButton(spell.component) { cast(spell) }
        // The charge does not survive a logout, so neither should the client's timer.
        onPlayerLogin { VarPlayerIntMapSetter.set(player, CHARGE_VARP, 0) }
    }

    private fun ProtectedAccess.cast(spell: org.rsmod.api.combat.commons.magic.MagicSpell) {
        if (actionDelay > mapClock) {
            return
        }
        if (!mageArena.quest.isQuestCompleted(player)) {
            mes("You need a god staff from the Mage Arena's chamber guardian to cast this spell.")
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
        VarPlayerIntMapSetter.set(player, CHARGE_VARP, CHARGE_TICKS / CHARGE_VARP_TICKS_PER_UNIT)
        mes("<col=ef1020>You feel charged with magic power.</col>")

        val uid = player.uid
        val expiry = mageArena.chargeExpiry.get(player)
        worldQueues.add(CHARGE_TICKS) {
            val caster = uid.resolve(playerList) ?: return@add
            // A logout clears the charge, so only the cast that is still running gets the notice.
            if (mageArena.chargeExpiry.get(caster) != expiry) {
                return@add
            }
            VarPlayerIntMapSetter.set(caster, CHARGE_VARP, 0)
            caster.mes("<col=ef1020>Your magical charge fades away.</col>")
        }
    }

    private companion object {
        const val CHARGE = "obj.80_charge"
        const val CAST_ANIM = "seq.human_casting"

        /** Spotanim 301, the blue swirl the client uses for Charge (shared with the crossbow special). */
        const val CAST_SPOTANIM = "spotanim.acb_specialattack"
        const val CAST_SPOTANIM_HEIGHT = 0
        const val CAST_SOUND = "synth.godspell_charge"
        const val CAST_DELAY = 3

        /** The client counts this one down itself, in units of two game cycles. */
        const val CHARGE_VARP = "varp.magearena_charge"
        const val CHARGE_VARP_TICKS_PER_UNIT = 2
    }
}
