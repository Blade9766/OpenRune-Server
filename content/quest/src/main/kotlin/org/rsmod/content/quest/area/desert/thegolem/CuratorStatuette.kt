package org.rsmod.content.quest.area.desert.thegolem

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_READ_NOTES
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_ASKED_CURATOR
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_REPAIRED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STATUETTE
import org.rsmod.game.entity.Player

/** Whether Curator Haig Halen can be asked about the Uzer statuette. */
fun TheGolemQuest.canAskCuratorAboutStatuette(player: Player): Boolean =
    stage(player) in STAGE_REPAIRED..STAGE_ASKED_CURATOR && player.golemSide >= SIDE_READ_NOTES

const val CURATOR_STATUETTE_OPTION = "I'm looking for a statuette recovered from the city of Uzer."

/**
 * The curator's side of The Golem. If the player stole the statuette and has since lost it, it has
 * turned up back in its case, ready to be taken again with the key.
 */
suspend fun Dialogue.askCuratorAboutStatuette(golem: TheGolemQuest) {
    chatPlayer(quiz, CURATOR_STATUETTE_OPTION)
    chatNpc(
        happy,
        "Ah yes, a very impressive artefact. The people of that city were excellent sculptors.",
    )
    chatNpc(neutral, "It's in the display case upstairs.")
    if (player.golemStatuetteTaken && STATUETTE !in player.inv && STATUETTE !in access.bank) {
        player.golemStatuetteTaken = false
        chatNpc(neutral, "That statuette was stolen recently, but now it's been returned.")
    }
    chatPlayer(neutral, "No, I need to take it away with me.")
    chatNpc(quiz, "What do you want it for?")
    val honest =
        choice2(
            "I want to open a portal to the lair of an elder-demon.",
            true,
            "Well, I, er, just want it.",
            false,
        )
    golem.advanceTo(access, STAGE_ASKED_CURATOR)
    if (honest) {
        chatPlayer(neutral, "I want to open a portal to the lair of an elder-demon.")
        chatNpc(shocked, "Good heavens! I'd never let you do such a dangerous thing.")
    } else {
        chatPlayer(shifty, "Well, I, er, just want it.")
        chatNpc(angry, "Well, you can't have it! This museum never lets go of its treasures.")
    }
}
