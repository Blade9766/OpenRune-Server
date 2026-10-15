package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.BALLISTA_REPAIRED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.LOGS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.LOGS_NEEDED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_FINDING_TRACKERS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_GATHERING_LOGS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_LOGS_GIVEN
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Commander Montai, who runs the gnome side of the battlefield north of the maze. */
class CommanderMontai @Inject constructor(private val treeGnomeVillage: TreeGnomeVillageQuest) : PluginScript() {

    private val quest
        get() = treeGnomeVillage.quest

    override fun ScriptContext.startup() {
        onOpNpc1(MONTAI) { startDialogue(it.npc) { montai() } }
    }

    private suspend fun Dialogue.montai() {
        when (treeGnomeVillage.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "Hello traveller, are you here to help or just to watch? We're fighting a war here!")
                chatPlayer(neutral, "Just passing through.")
                chatNpc(neutral, "Then keep your head down. Khazard's men don't care who they cut down.")
            }
            STAGE_STARTED -> firstMeeting()
            STAGE_GATHERING_LOGS -> logs()
            STAGE_LOGS_GIVEN -> trackers()
            STAGE_FINDING_TRACKERS -> {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "Hello warrior. We need the coordinates for a direct hit from the ballista. Once you have a direct hit you will be able to enter the stronghold and retrieve the orb.")
            }
            STAGE_BREACHED -> {
                chatPlayer(happy, "I've breached the stronghold.")
                chatNpc(happy, "I saw, that was a beautiful sight. The Khazard troops didn't know what hit them.")
                chatNpc(neutral, "Now is the time to retrieve the orb. It's all in your hands. I'll be praying for you.")
            }
            STAGE_HAS_ORB -> {
                if (!player.inv.contains(ORB)) {
                    chatPlayer(happy, "Hello.")
                    chatNpc(worried, "Where is the orb, soldier? Take it back to the village before Khazard's men find it again.")
                    return
                }
                chatPlayer(happy, "I have the orb of protection.")
                chatNpc(happy, "Incredible, for a human you really are something.")
                chatPlayer(quiz, "Thanks... I think!")
                chatNpc(neutral, "I'll stay here with my troops and try and hold Khazard's men back. You return the orb to the gnome village. Go as quick as you can, the village is still unprotected.")
            }
            else -> {
                chatPlayer(happy, "Hello Montai, how are you?")
                chatNpc(neutral, "I'm ok, this battle is going to take longer to win than I expected. The Khazard troops won't give up even without the orb.")
                chatPlayer(neutral, "Hang in there.")
            }
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Hello traveller, are you here to help or just to watch?")
        chatPlayer(neutral, "I've been sent by King Bolren to retrieve the orb of protection.")
        chatNpc(happy, "Excellent we need all the help we can get.")
        chatNpc(neutral, "I'm commander Montai. The orb is in the Khazard stronghold to the north, but until we weaken their defences we can't get close.")
        chatPlayer(quiz, "What can I do?")
        chatNpc(neutral, "Firstly we need to strengthen our own defences. We desperately need wood to make more battlements, once the battlements are gone it's all over. Six loads of normal logs should do it.")
        when (
            choice2(
                "Sorry, I no longer want to be involved.", 1,
                "Ok, I'll gather some wood.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Sorry I no longer want to be involved.")
                chatNpc(sad, "That's a shame, we could have done with your help.")
            }
            2 -> {
                chatPlayer(happy, "Ok, I'll gather some wood.")
                quest.advanceQuestStage(access)
                chatNpc(worried, "Please be as quick as you can, I don't know how much longer we can hold out.")
            }
        }
    }

    private suspend fun Dialogue.logs() {
        chatPlayer(happy, "Hello.")
        if (access.invTotal(access.inv, LOGS) < LOGS_NEEDED) {
            chatNpc(worried, "Hello again, we're still desperate for wood soldier. We need six loads of normal logs.")
            chatPlayer(neutral, "I'll see what I can do.")
            chatNpc(neutral, "Thank you.")
            return
        }
        chatNpc(worried, "Hello again, we're still desperate for wood soldier.")
        if (access.invDel(access.inv, LOGS, LOGS_NEEDED).failure) {
            return
        }
        chatPlayer(happy, "I have some here. <col=ffffff>(You give six loads of logs to the commander.)</col>")
        treeGnomeVillage.ballistaState.set(player, BALLISTA_REPAIRED)
        treeGnomeVillage.syncVars(player)
        quest.advanceQuestStage(access)
        chatNpc(happy, "That's excellent, now we can make more defensive battlements. Give me a moment to organise the troops and then come speak to me. I'll inform you of our next phase of attack.")
    }

    private suspend fun Dialogue.trackers() {
        chatPlayer(quiz, "How are you doing Montai?")
        chatNpc(neutral, "We're hanging in there soldier. For the next phase of our attack we need to breach their stronghold.")
        chatNpc(neutral, "The ballista can break through the stronghold wall, and then we can advance and seize back the orb.")
        chatPlayer(quiz, "So what's the problem?")
        chatNpc(neutral, "From this distance we can't get an accurate enough shot. We need the correct coordinates of the stronghold for a direct hit. I've sent out three tracker gnomes to gather them.")
        chatPlayer(quiz, "Have they returned?")
        chatNpc(worried, "I'm afraid not, and we're running out of time. I need you to go into the heart of the battlefield, find the trackers, and bring back the coordinates.")
        chatNpc(quiz, "Do you think you can do it?")
        when (
            choice2(
                "No, I've had enough of your battle.", 1,
                "I'll try my best.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "No, I've had enough of your battle.")
                chatNpc(sad, "I understand, this isn't your fight.")
            }
            2 -> {
                chatPlayer(happy, "I'll try my best.")
                if (treeGnomeVillage.trackerX.get(player) == 0) {
                    treeGnomeVillage.trackerX.set(player, (1..MAX_X_COORDINATE).random())
                }
                treeGnomeVillage.advanceTo(access, STAGE_FINDING_TRACKERS)
                chatNpc(happy, "Thank you, you're braver than most.")
                chatNpc(neutral, "I don't know how long I will be able to hold out. Once you have the coordinates come back and fire the ballista right into those monsters.")
                chatNpc(neutral, "If you can retrieve the orb and bring safety back to my people, none of the blood spilled on this field will be in vain.")
            }
        }
    }

    private companion object {
        const val MONTAI = "npc.commander_montai"
        const val MAX_X_COORDINATE = 4
    }
}
