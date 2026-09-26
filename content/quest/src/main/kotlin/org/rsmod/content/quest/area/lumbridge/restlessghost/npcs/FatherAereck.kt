package org.rsmod.content.quest.area.lumbridge.restlessghost.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Witness
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.GHOST_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_AMULET
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_SPOKE_TO_GHOST
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Father Aereck, priest of the Lumbridge church. Starts The Restless Ghost. */
class FatherAereck
@Inject
constructor(private val restlessGhost: RestlessGhostQuest, private val lostTribe: LostTribeQuest) :
    PluginScript() {

    private val quest
        get() = restlessGhost.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.father_aereck") { startDialogue(it.npc) { aereck() } }
    }

    private suspend fun Dialogue.aereck() {
        val stage = quest.getQuestStage(player)
        val midQuest = stage != 0 && !quest.isQuestCompleted(player)
        if (midQuest && with(lostTribe) { offerCellarQuestion(Witness.Aereck) }) {
            return
        }
        when (stage) {
            0 -> beforeQuest()
            STAGE_STARTED -> {
                chatNpc(quiz, "Have you got rid of the ghost yet?")
                chatPlayer(sad, "I can't find Father Urhney at the moment.")
                chatNpc(neutral, "Well, you can get to the swamp he lives in by going south through the cemetery.")
                chatNpc(neutral, "You'll have to go right into the far western depths of the swamp, near the coastline. That is where his house is.")
            }
            STAGE_GOT_AMULET -> {
                chatNpc(quiz, "Have you got rid of the ghost yet?")
                chatPlayer(neutral, "I had a talk with Father Urhney. He has given me this funny amulet to talk to the ghost with.")
                chatNpc(happy, "I always wondered what that amulet was... Well, I hope it's useful. Tell me when you get rid of the ghost!")
            }
            STAGE_SPOKE_TO_GHOST -> {
                chatNpc(quiz, "Have you got rid of the ghost yet?")
                chatPlayer(neutral, "I've found out that the ghost's corpse has lost its skull. If I can find the skull, the ghost should leave.")
                chatNpc(neutral, "That WOULD explain it. Hmmmm. Well, I haven't seen any skulls.")
                chatPlayer(neutral, "Yes, I think a warlock has stolen it.")
                chatNpc(angry, "I hate warlocks. Ah well, good luck!")
            }
            STAGE_GOT_SKULL -> {
                chatNpc(quiz, "Have you got rid of the ghost yet?")
                if (player.inv.count(GHOST_SKULL) > 0) {
                    chatPlayer(happy, "I've finally found the ghost's skull!")
                    chatNpc(happy, "Great! Put it in the ghost's coffin and see what happens!")
                } else {
                    chatPlayer(worried, "I found the ghost's skull, but I seem to have mislaid it.")
                    chatNpc(neutral, "Then you'd best go back to wherever you found it. The ghost isn't going anywhere.")
                }
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(happy, "Welcome to the church of holy Saradomin.")
        val options = buildList {
            add("Who's Saradomin?" to 1)
            add("Nice place you've got here." to 2)
            add("I'm looking for a quest!" to 3)
            lostTribe.cellarQuestion(player, Witness.Aereck)?.let { add(it to 4) }
        }
        when (menu(options)) {
            1 -> whoIsSaradomin()
            2 -> nicePlace()
            3 -> offerQuest()
            4 -> with(lostTribe) { askAboutCellar(Witness.Aereck) }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(happy, "I'm looking for a quest.")
        chatNpc(happy, "That's lucky. I need someone to do a quest for me.")
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start The Restless Ghost quest?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "Okay, let me help then.")
                chatNpc(happy, "Thank you. The problem is, there is a ghost in the church graveyard. I would like you to get rid of it.")
                chatNpc(neutral, "If you need any help, my friend Father Urhney is an expert on ghosts.")
                chatNpc(neutral, "I believe he is currently living as a hermit in Lumbridge swamp. He has a little shack in the far west of the swamps.")
                chatNpc(neutral, "Exit the graveyard through the south gate to reach the swamp. I'm sure if you told him that I sent you he'd be willing to help.")
                chatNpc(happy, "My name is Father Aereck, by the way. Pleased to meet you.")
                chatPlayer(happy, "Likewise.")
                chatNpc(worried, "Take care travelling through the swamps. I have heard they can be quite dangerous.")
                chatPlayer(neutral, "I will, thanks.")
                quest.advanceQuestStage(access)
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I don't have time right now.")
                chatNpc(neutral, "Oh well. If you do have some spare time on your hands, come back and talk to me.")
            }
        }
    }

    private suspend fun Dialogue.whoIsSaradomin() {
        chatPlayer(quiz, "Who's Saradomin?")
        chatNpc(shocked, "Well, if you don't know, I'm not sure I can do him justice in a few words. Saradomin is the god of order and wisdom, and the protector of this church.")
        chatNpc(happy, "Take a look at the altar and the pews. Everything here is built in his honour.")
        chatPlayer(neutral, "I'll keep that in mind.")
    }

    private suspend fun Dialogue.nicePlace() {
        chatPlayer(happy, "Nice place you've got here.")
        chatNpc(happy, "It is, isn't it? It was built over two centuries ago, and we've kept it just as it was.")
        chatNpc(neutral, "Feel free to stay and pray a while. The altar is open to all.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Welcome to the church of holy Saradomin. Thank you again for laying that poor ghost to rest.")
        val options = buildList {
            add("Who's Saradomin?" to 1)
            add("Nice place you've got here." to 2)
            add("Any more ghosts need dealing with?" to 3)
            lostTribe.cellarQuestion(player, Witness.Aereck)?.let { add(it to 4) }
        }
        when (menu(options)) {
            1 -> whoIsSaradomin()
            2 -> nicePlace()
            3 -> {
                chatPlayer(quiz, "Any more ghosts need dealing with?")
                chatNpc(happy, "Thankfully not. The graveyard has been quite peaceful since you found that skull. I hope it stays that way!")
            }
            4 -> with(lostTribe) { askAboutCellar(Witness.Aereck) }
        }
    }
}
