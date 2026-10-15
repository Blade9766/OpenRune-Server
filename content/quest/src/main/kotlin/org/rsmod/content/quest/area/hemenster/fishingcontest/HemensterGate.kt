package org.rsmod.content.quest.area.hemenster.fishingcontest

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.MORRIS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.PASS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_GROUNDS
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STAGE_STARTED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gate into the Hemenster competition grounds and Morris, who keeps it. The gate hangs on the
 * east edge of the grounds; leaving is always allowed, but coming in needs Morris to have seen a
 * fishing pass once. He remembers a face after that, so a pass lost later doesn't lock anyone out.
 */
class HemensterGate
@Inject
constructor(
    private val fc: FishingContestQuest,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { gate(it.loc) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc) }
        onOpNpc1(MORRIS) { startDialogue(it.npc) { morris() } }
    }

    private suspend fun ProtectedAccess.gate(gate: BoundLocInfo) {
        arriveDelay()
        faceLoc(gate)
        val inside = isInside(coords)
        if (!inside && !fc.hasShownPass(player)) {
            var admitted = false
            startDialogue { admitted = checkPass() }
            if (!admitted) {
                return
            }
        }
        passGate(gate, inside)
    }

    private suspend fun ProtectedAccess.passGate(gate: BoundLocInfo, inside: Boolean) {
        val dest = if (inside) CoordGrid(OUTSIDE_X, gate.coords.z, 0) else CoordGrid(INSIDE_X, gate.coords.z, 0)
        locRepo.del(gate, OPEN_CYCLES)
        soundSynth(OPEN_SOUND)
        playerWalk(dest)
    }

    private suspend fun Dialogue.morris() {
        if (fc.hasShownPass(player)) {
            chatNpcSpecific("Morris", MORRIS, neutral, "Back again? In you go. Bonzo's taking entries by the scales.")
            return
        }
        chatPlayer(quiz, "What are you guarding?")
        chatNpc(neutral, "The Hemenster fishing competition, the finest angling event in Kandarin. Club members and pass holders only.")
        checkPass()
    }

    /** Morris asks for the pass; true once he is satisfied and will open the gate. */
    private suspend fun Dialogue.checkPass(): Boolean {
        chatNpcSpecific("Morris", MORRIS, neutral, "Competition pass, please.")
        if (PASS !in access.inv) {
            chatPlayer(sad, "I don't seem to have one with me.")
            if (fc.stage(player) >= STAGE_STARTED) {
                chatNpcSpecific("Morris", MORRIS, angry, "No pass, no fishing. If somebody gave you one, go and ask them for another.")
            } else {
                chatNpcSpecific("Morris", MORRIS, angry, "Then you're not coming in. Rules are rules, and I'm the rules.")
            }
            return false
        }
        chatPlayer(happy, "Here you go.")
        objbox(PASS, zoom = 400, "You show Morris your fishing pass.")
        chatNpcSpecific("Morris", MORRIS, neutral, "A genuine pass, and the dwarven seal on it too. Right you are. Talk to Bonzo inside to enter.")
        if (fc.stage(player) >= STAGE_STARTED) {
            fc.markPassShown(player)
            fc.advanceTo(access, STAGE_GROUNDS)
        }
        return true
    }

    companion object {
        const val GATE_LEFT = "loc.fishinggateclosedl"
        const val GATE_RIGHT = "loc.fishinggateclosedr"
        const val OPEN_SOUND = "synth.picketgate_open"
        const val OPEN_CYCLES = 2

        /** The gate panels are walls on the east edge of x [INSIDE_X]. */
        const val INSIDE_X = 2642
        const val OUTSIDE_X = 2643

        fun isInside(coords: CoordGrid): Boolean = coords.x <= INSIDE_X
    }
}
