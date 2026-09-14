package org.rsmod.content.quest.area.mortmyre.naturespirit

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.BLESSED_SICKLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_ENTERED_SWAMP
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_STARTED
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The fence gate into Mort Myre south of Canifis, and the swamp decay it lets in.
 *
 * Going in through the gate warns the player, starts the decay timer and, for a player Drezel
 * sent to find Filliman, counts as entering the swamp; leaving through it cancels the timer. When
 * the timer runs out a player still in the decay area is hit for 1-3 and the timer restarts; one
 * who has left is told the effect is over. A blessed sickle, carried or wielded, keeps the timer
 * from starting at all.
 */
class MortMyreSwamp
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { gate(it.loc, it.type) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc, it.type) }
        onPlayerTimer(DECAY_TIMER) { decay() }
    }

    private suspend fun ProtectedAccess.gate(loc: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val entering = coords.z >= MortMyreCoords.GATE_Z
        if (!entering) {
            clearTimer(DECAY_TIMER)
            with(passages) { walkThrough(loc, type) }
            return
        }
        var enter = false
        startDialogue {
            enter =
                choice2(
                    "Enter the swamp.",
                    true,
                    "Stay out",
                    false,
                    title = "Warning! Mort Myre is a dangerous ghast-infested swamp. Do not enter if you value your life.",
                )
        }
        if (!enter) {
            return
        }
        mes("You walk into the gloomy atmosphere of Mort Myre.")
        if (natureSpirit.stage(player) == STAGE_STARTED) {
            natureSpirit.advanceTo(this, STAGE_ENTERED_SWAMP)
        }
        if (!player.carriesBlessedSickle()) {
            timer(DECAY_TIMER, DECAY_CYCLES)
        }
        with(passages) { walkThrough(loc, type) }
    }

    private fun ProtectedAccess.decay() {
        if (!MortMyreCoords.inDecayArea(coords) || MortMyreCoords.onGrottoIsland(coords)) {
            clearTimer(DECAY_TIMER)
            mes("The swamp decay effect is now over.")
            return
        }
        if (player.carriesBlessedSickle()) {
            clearTimer(DECAY_TIMER)
            return
        }
        mes("The swamp decays you!")
        spotanim(STENCH_SPOTANIM)
        soundSynth(GAS_SOUND)
        val damage = random.of(1, MAX_DECAY).coerceAtMost(player.hitpoints)
        if (damage > 0) {
            queueHit(delay = 0, type = HitType.Typeless, damage = damage)
        }
        timer(DECAY_TIMER, DECAY_CYCLES)
    }

    private companion object {
        const val GATE_LEFT = "loc.mortmyre_metalgateclosed_l"
        const val GATE_RIGHT = "loc.mortmyre_metalgateclosed_r"
        const val DECAY_TIMER = "timer.mortmyre_swamp_decay"

        /** Two minutes. */
        const val DECAY_CYCLES = 200
        const val MAX_DECAY = 3
        const val STENCH_SPOTANIM = "spotanim.mortmyre_swampstench"
        const val GAS_SOUND = "synth.swamp_gas"
    }
}

internal fun org.rsmod.game.entity.Player.carriesBlessedSickle(): Boolean =
    inv.contains(BLESSED_SICKLE) || worn.contains(BLESSED_SICKLE)
