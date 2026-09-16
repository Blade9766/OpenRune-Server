package org.rsmod.content.quest.area.draynor.porcineofinterest

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The Draynor Village notice board behind the wine stall, where the bounty is posted. */
class NoticeBoard @Inject constructor(private val porcine: PorcineOfInterestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(NOTICE_BOARD) { checkBoard() }
    }

    private suspend fun ProtectedAccess.checkBoard() {
        if (porcine.stage(player) != 0) {
            mesbox("You search through the notice board but find nothing more of interest.")
            return
        }
        startDialogue { readBounty() }
    }

    private suspend fun Dialogue.readBounty() {
        mesbox("You search through the notice board...")
        chatPlayer(
            neutral,
            "'Damaged roof tiles for sale'... 'Come on down to Bob's Axes'... 'Contributions " +
                "welcomed to Ned's New-Window Fund'...",
        )
        chatPlayer(quiz, "Wait a minute, what's this?")
        mesbox(
            "'Keen fighter sought to deal with troublesome monster. Reward offered. Speak to " +
                "Sarah at the South Falador Farm for details.'",
        )
        chatPlayer(neutral, "This could be worth looking into.")
        val start =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Start the A Porcine of Interest quest?",
            )
        if (!start) {
            return
        }
        chatPlayer(happy, "Right, let's go and see what Sarah has in mind.")
        porcine.advanceTo(access, STAGE_STARTED)
    }

    private companion object {
        const val NOTICE_BOARD = "loc.porcine_noticeboard"
    }
}
