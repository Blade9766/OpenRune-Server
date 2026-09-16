package org.rsmod.content.quest.area.zanaris.fairytale1

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.DRAYNOR_SKULL
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SPADE
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_MORTIFER
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gravestone in the yard behind Draynor Manor. Once Malignius Mortifer has asked for a skull,
 * digging beside the stone - or putting a spade to it - turns one up. Mortifer only ever needs
 * the one, so a player who still has theirs digs up nothing but soil.
 */
class DraynorSkullGrave
@Inject
constructor(private val fairytale: Fairytale1Quest, private val spadeDigging: SpadeDigging) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU(GRAVESTONE, SPADE) { digGrave() }
        spadeDigging.register(Fairytale1Coords.DRAYNOR_GRAVE, radius = 2) { digGrave() }
    }

    private suspend fun ProtectedAccess.digGrave() {
        arriveDelay()
        faceSquare(Fairytale1Coords.DRAYNOR_GRAVE)
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(DIG_TICKS)
        when {
            fairytale.stage(player) != STAGE_SEEN_MORTIFER ||
                fairytale.mortiferListGiven.get(player) -> {
                mes("You dig around the grave, but find nothing you would want to keep.")
            }
            carries(DRAYNOR_SKULL) -> {
                mes("You already have the skull Mortifer asked for.")
            }
            player.inv.isFull() -> {
                mesbox("Your hands are too full to carry anything you dig up.")
            }
            else -> {
                invAdd(inv, DRAYNOR_SKULL)
                objbox(
                    DRAYNOR_SKULL,
                    "You dig down beside the gravestone and turn up a yellowed skull. Its owner " +
                        "does not appear to mind.",
                )
            }
        }
    }

    private companion object {
        const val GRAVESTONE = "loc.fairy_draynor_gravestone"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"
        const val DIG_TICKS = 3
    }
}
