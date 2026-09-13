package org.rsmod.content.quest.area.baxtorianfalls.waterfall

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.GOLRIE
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.GOLRIE_KEY
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.WallSide
import org.rsmod.content.quest.area.varrock.dragonslayer.sideOf
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The hobgoblin caves beneath the Tree Gnome Village: the odd crate in the east room hides the
 * key to the gate Golrie has locked himself behind in the west room (the gate sits on the north
 * edge of 2515,9575; Golrie's room is north of it).
 */
class GnomeVillageDungeon
@Inject
constructor(
    private val waterfall: WaterfallQuest,
    private val objRepo: ObjRepository,
    private val doors: DoorPassage,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CRATE) { searchCrate() }
        onOpLoc1(GATE) { openGate(it.loc) }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        anim(SEARCH_SEQ)
        delay(1)
        val keyHere = waterfall.stage(player) >= STAGE_READ_BOOK && GOLRIE_KEY !in player.inv
        if (!keyHere) {
            mes("You search the crate but find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, GOLRIE_KEY)
        objbox(GOLRIE_KEY, "Hidden inside the crate you find a key.")
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo) {
        if (gate.sideOf(player.coords) == WallSide.NORTH) {
            mes("You open the gate and step through.")
            doors.walkThrough(this, gate, GATE, GATE_SOUND)
            return
        }
        if (GOLRIE_KEY in player.inv) {
            mes("You unlock the gate with the key.")
            doors.walkThrough(this, gate, GATE, GATE_SOUND)
            return
        }
        soundSynth(LOCKED_SOUND)
        when {
            waterfall.stage(player) < STAGE_READ_BOOK -> {
                startDialogue {
                    chatNpcSpecific(GOLRIE_NAME, GOLRIE, angry, "What are you doing down here? Leave before you land yourself in trouble.")
                }
            }
            waterfall.isComplete(player) -> mesbox("Golrie has locked himself in.")
            else -> {
                startDialogue {
                    chatPlayer(worried, "Hello? Are you alright in there?")
                    chatNpcSpecific(GOLRIE_NAME, GOLRIE, happy, "Oh, I'm perfectly fine. I locked myself in to keep safe, but I've gone and lost the key somewhere.")
                    chatPlayer(confused, "Right... I'll keep an eye out for it.")
                }
            }
        }
    }

    private companion object {
        const val CRATE = "loc.golrie_crate_waterfall_quest"
        const val GATE = "loc.golrie_gate_waterfall_quest"
        const val GOLRIE_NAME = "Golrie"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val GATE_SOUND = "synth.door_open"
        const val LOCKED_SOUND = "synth.irondoor_locked"
    }
}
