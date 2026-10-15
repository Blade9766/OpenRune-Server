package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.GnomeMaze
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_GATHERING_LOGS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_ORB_RETURNED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_WARLORD_SLAIN
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.elkoyGuides
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elkoy guides the player through the maze. He stands twice: `npc.elkoy` at the north-west maze
 * entrance and `npc.elkoy_village` just inside the loose railing. Both are varp-multi npcs that
 * gain a Follow option once the quest is under way; the ops are registered on the base types
 * because that is the id the event carries.
 */
class Elkoy @Inject constructor(private val treeGnomeVillage: TreeGnomeVillageQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ELKOY_ENTRANCE, ELKOY_VILLAGE)) {
            onOpNpc1(type) { startDialogue(it.npc) { elkoy(it.npc) } }
            onOpNpc3(type) { follow(it.npc) }
        }
    }

    private fun inside(npc: Npc): Boolean = GnomeMaze.insideVillage(npc.coords)

    /** The Follow option skips the chat and leads the player straight through. */
    private suspend fun ProtectedAccess.follow(npc: Npc) {
        val stage = treeGnomeVillage.stage(player)
        if (stage == 0) {
            startDialogue(npc) { elkoy(npc) }
            return
        }
        guide(npc, partingLine(npc, stage))
    }

    private suspend fun ProtectedAccess.guide(npc: Npc, line: String) {
        val dest = if (inside(npc)) GnomeMaze.ENTRANCE else GnomeMaze.VILLAGE_GATE
        elkoyGuides(dest, line, boxText = "Elkoy guides you through the maze.")
    }

    private fun partingLine(npc: Npc, stage: Int): String =
        when {
            stage < STAGE_HAS_ORB -> "Please help us get our orb back."
            stage == STAGE_HAS_ORB && inside(npc) -> "Please return with our orb soon."
            stage == STAGE_HAS_ORB -> "Here we are. Take the orb to King Bolren, I'm sure he'll be pleased to see you."
            stage < STAGE_COMPLETE && inside(npc) -> "Please help us find the orbs."
            stage < STAGE_COMPLETE -> "Here we are. Despite what has happened here, I hope you feel welcome."
            inside(npc) -> "Here we are. Have a safe journey."
            else -> "Here we are. Feel free to have a look around."
        }

    private suspend fun Dialogue.elkoy(npc: Npc) {
        when (val stage = treeGnomeVillage.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> {
                chatPlayer(happy, "Hello Elkoy.")
                chatNpc(worried, "Oh my! Oh my!")
                chatPlayer(quiz, "What's wrong?")
                chatNpc(worried, "The orb, they have the orb. We're doomed. Do you need me to show you ${direction(npc)}?")
                offerGuide(npc, stage)
            }
            in STAGE_GATHERING_LOGS until STAGE_BREACHED -> {
                chatPlayer(happy, "Hello.")
                chatNpc(worried, "You must retrieve the orb, or the gnome village is doomed. Do you need me to show you ${direction(npc)}?")
                offerGuide(npc, stage)
            }
            STAGE_BREACHED, STAGE_HAS_ORB -> {
                if (player.inv.contains(ORB)) {
                    hasOrb(npc, stage)
                    return
                }
                chatPlayer(happy, "Hello Elkoy.")
                chatNpc(quiz, "You're back! And the orb?")
                chatPlayer(sad, "No, I'm afraid not.")
                chatNpc(worried, "Please, we must have the orb if we are to survive. Do you need me to show you ${direction(npc)}?")
                offerGuide(npc, stage)
            }
            STAGE_ORB_RETURNED, STAGE_WARLORD_SLAIN -> pillaged(npc, stage)
            else -> completed(npc, stage)
        }
    }

    private fun direction(npc: Npc): String = if (inside(npc)) "through the maze" else "back to the village"

    private suspend fun Dialogue.offerGuide(npc: Npc, stage: Int) {
        when (choice2("Yes please.", 1, "Not now, thanks.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                access.guide(npc, partingLine(npc, stage))
            }
            2 -> chatPlayer(neutral, "Not now, thanks.")
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Hello there. I'm Elkoy, keeper of this maze. Not many strangers find their way to our village.")
        chatPlayer(quiz, "What is this place?")
        chatNpc(neutral, "The home of the tree gnomes. If you can find your way to the centre, King Bolren would be glad of a visitor.")
    }

    private suspend fun Dialogue.hasOrb(npc: Npc, stage: Int) {
        if (inside(npc)) {
            chatPlayer(happy, "Hello Elkoy. I have the orb.")
            chatNpc(happy, "Take it to King Bolren, I'm sure he'll be pleased to see you.")
            when (choice2("Can you show me out of the village?", 1, "Okay.", 2)) {
                1 -> {
                    chatPlayer(quiz, "Can you show me out of the village?")
                    access.guide(npc, partingLine(npc, stage))
                }
                2 -> chatPlayer(happy, "Okay.")
            }
            return
        }
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(quiz, "You're back! And the orb?")
        chatPlayer(happy, "I have it here.")
        chatNpc(happy, "You're our saviour. Please return it to the village and we are all saved. Would you like me to show you the way to the village?")
        when (choice2("Yes please.", 1, "No thanks Elkoy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                access.guide(npc, partingLine(npc, stage))
            }
            2 -> {
                chatPlayer(neutral, "No thanks Elkoy.")
                chatNpc(worried, "Please, we must have the orb if we are to survive.")
            }
        }
    }

    private suspend fun Dialogue.pillaged(npc: Npc, stage: Int) {
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(sad, "Did you hear?")
        chatNpc(sad, "Khazard's men have pillaged the village! They slaughtered many, and took the other orbs in an attempt to lead us out of the maze. When will the misery end?")
        if (inside(npc)) {
            when (choice2("Can you show me out of the village?", 1, "I'm very sorry.", 2)) {
                1 -> {
                    chatPlayer(quiz, "Can you show me out of the village?")
                    access.guide(npc, partingLine(npc, stage))
                }
                2 -> chatPlayer(sad, "I'm very sorry.")
            }
            return
        }
        chatNpc(neutral, "Would you like me to show you the way to the village?")
        when (choice2("Yes please.", 1, "No thanks Elkoy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                access.guide(npc, partingLine(npc, stage))
            }
            2 -> {
                chatPlayer(neutral, "No thanks Elkoy.")
                chatNpc(neutral, "Ok then, take care.")
            }
        }
    }

    private suspend fun Dialogue.completed(npc: Npc, stage: Int) {
        chatPlayer(happy, "Hello Elkoy.")
        chatNpc(happy, "You truly are a hero.")
        chatPlayer(happy, "Thanks.")
        if (inside(npc)) {
            chatNpc(happy, "You saved us by returning the orbs of protection. I'm humbled and wish you well. Would you like me to show you through the maze?")
            when (choice2("Yes please.", 1, "Not now, thanks.", 2)) {
                1 -> {
                    chatPlayer(happy, "Yes please.")
                    access.guide(npc, partingLine(npc, stage))
                }
                2 -> chatPlayer(neutral, "Not now, thanks.")
            }
            return
        }
        chatNpc(happy, "You saved us by returning the orbs of protection. I'm humbled and wish you well.")
        chatNpc(neutral, "Would you like me to show you the way to the village?")
        when (choice2("Yes please.", 1, "No thanks Elkoy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                access.guide(npc, partingLine(npc, stage))
            }
            2 -> {
                chatPlayer(neutral, "No thanks Elkoy.")
                chatNpc(neutral, "Ok then, take care.")
            }
        }
    }

    companion object {
        /** The multi-npc at the north-west maze entrance. */
        const val ELKOY_ENTRANCE = "npc.elkoy"

        /** The multi-npc just inside the loose railing. */
        const val ELKOY_VILLAGE = "npc.elkoy_village"
    }
}
