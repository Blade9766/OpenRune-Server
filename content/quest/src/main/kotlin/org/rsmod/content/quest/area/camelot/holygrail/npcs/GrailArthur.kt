package org.rsmod.content.quest.area.camelot.holygrail.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HOLY_GRAIL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.REQUIRED_ATTACK
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_FEATHER
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_GRAIL_TAKEN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_HEIR_NEEDED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_PERCIVAL_SENT
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_ENTERED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_RESTORED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.camelot.holygrail.percivalFound
import org.rsmod.game.entity.Player

/**
 * King Arthur once Merlin is free: he sets the Grail quest, recognises the Fisher King's son as
 * Sir Percival and hands over the feather that tracks Percival's boots, and takes the Grail at the
 * end. The feather is replaced for anyone who has lost it while Percival is still missing.
 */
@Singleton
class GrailArthur @Inject constructor(private val quest: HolyGrailQuest) {

    fun isStarted(player: Player): Boolean = quest.isStarted(player)

    suspend fun Dialogue.talk() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> afterwards()
            stage == 0 -> offer()
            stage == STAGE_HEIR_NEEDED -> nameTheSon()
            stage == STAGE_FEATHER -> searching()
            stage == STAGE_GRAIL_TAKEN -> delivery()
            else -> progress(stage)
        }
    }

    private suspend fun Dialogue.offer() {
        chatNpc(happy, "Welcome back, Sir Knight. Merlin is himself again, thanks to you.")
        val ask =
            choice2(
                "Now I am a knight, do you have any quests for me?",
                true,
                "Good day, Your Majesty.",
                false,
            )
        if (!ask) {
            chatPlayer(neutral, "Good day, Your Majesty.")
            return
        }
        chatPlayer(quiz, "Now I am a knight, do you have any quests for me?")
        if (access.statBase("stat.attack") < REQUIRED_ATTACK) {
            chatNpc(
                sad,
                "There is one, but it may end at the point of a sword. Come back when your " +
                    "sword arm is stronger: level $REQUIRED_ATTACK Attack at the least.",
            )
            return
        }
        chatNpc(
            neutral,
            "There is the greatest quest of all. The Holy Grail: the cup from which the " +
                "blessed drank, lost to us for longer than any of my knights have lived.",
        )
        chatNpc(
            neutral,
            "Every knight at my table has ridden out after it. Most came back with stories. " +
                "Some did not come back at all.",
        )
        val accept =
            choice2(
                "I shall find the Grail for Camelot.",
                true,
                "That sounds like more than I can manage just now.",
                false,
            )
        if (!accept) {
            chatPlayer(neutral, "That sounds like more than I can manage just now.")
            chatNpc(neutral, "The Grail has waited this long. It will wait for you.")
            return
        }
        chatPlayer(happy, "I shall find the Grail for Camelot.")
        quest.advanceTo(access, STAGE_STARTED)
        chatNpc(
            happy,
            "Spoken like a knight! Go to Merlin first. If any man living knows where the Grail " +
                "lies, it is that cantankerous old wizard.",
        )
        chatNpc(neutral, "His workshop is on the floor above, east of the throne room.")
    }

    private suspend fun Dialogue.progress(stage: Int) {
        chatNpc(quiz, "How goes the search for the Grail?")
        when {
            stage < STAGE_MERLIN -> {
                chatPlayer(neutral, "I haven't spoken to Merlin yet.")
                chatNpc(neutral, "Then go up to his workshop. He hates to be kept waiting, though he never hurries.")
            }
            stage < STAGE_REALM_ENTERED -> {
                chatPlayer(neutral, "I am following Merlin's leads.")
                chatNpc(neutral, "Entrana and Galahad. Good. Galahad was the best of us; listen to him.")
            }
            stage == STAGE_REALM_ENTERED -> {
                chatPlayer(neutral, "I have found a grey, dying land where the Grail is kept.")
                chatNpc(worried, "Dying? Then do not linger. Whoever rules it may not have long.")
            }
            stage == STAGE_PERCIVAL_SENT -> {
                chatPlayer(happy, "Percival has gone home to his father.")
                chatNpc(happy, "Then follow him, and bring back the Grail.")
            }
            stage == STAGE_REALM_RESTORED -> {
                chatPlayer(happy, "Percival is king, and his land is green again.")
                chatNpc(laugh, "King Percival! I shall have to bow to him now. Fetch the Grail, my friend.")
            }
            else -> chatNpc(neutral, "Carry on, Sir Knight.")
        }
        if (stage == STAGE_MERLIN) {
            chatNpc(neutral, "And remember, the monks of Entrana will not let a sword onto their island.")
        }
    }

    private suspend fun Dialogue.nameTheSon() {
        chatNpc(quiz, "How goes the search for the Grail?")
        chatPlayer(
            neutral,
            "I found the Grail's keeper, the Fisher King. He is dying, and his land is dying with " +
                "him. He says only his son coming home can save them.",
        )
        chatNpc(confused, "His son? Did he give you a name?")
        chatPlayer(neutral, "He lost the boy as a child. He only knows that he was raised a knight.")
        chatNpc(
            shocked,
            "A foundling raised to knighthood... that is Percival! We never knew where he came " +
                "from. By the gods, he is a king's son.",
        )
        chatNpc(
            sad,
            "But he is not here. He rode off weeks ago after the golden boots of Arkaneeses, " +
                "some trinket he heard of in a tavern.",
        )
        quest.advanceTo(access, STAGE_FEATHER)
        chatNpc(
            neutral,
            "Take this. It is a feather from a bird of gold. Blow on it and it turns towards the " +
                "boots, and if I know Percival, he will not be far from them.",
        )
        giveFeather()
    }

    private suspend fun Dialogue.searching() {
        if (player.percivalFound) {
            chatNpc(quiz, "Have you found Percival?")
            chatPlayer(
                neutral,
                "Yes, but he needs a magic whistle to get home, and I must keep one to follow him.",
            )
            chatNpc(neutral, "Then fetch another. Where you found the first, I should think.")
            return
        }
        if (FEATHER in player.inv || access.bank.contains(FEATHER)) {
            chatNpc(quiz, "Any sign of Percival?")
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Blow on the feather and follow where it points.")
            return
        }
        chatPlayer(sad, "I have lost the feather you gave me.")
        chatNpc(sad, "Merlin will grumble. Here, he made me a spare.")
        giveFeather()
    }

    private suspend fun Dialogue.giveFeather() {
        if (player.inv.isFull()) {
            chatNpc(neutral, "Your pack is full. Make some room and come back to me for it.")
            return
        }
        access.invAdd(player.inv, FEATHER)
        objbox(FEATHER, "King Arthur gives you a magic gold feather.")
    }

    private suspend fun Dialogue.delivery() {
        chatNpc(quiz, "Is it... do you have it?")
        if (HOLY_GRAIL !in player.inv && access.bank.contains(HOLY_GRAIL)) {
            chatPlayer(neutral, "It is safe in my bank.")
            chatNpc(laugh, "In a bank! Fetch it, my friend, before the bankers start charging it rent.")
            return
        }
        if (HOLY_GRAIL !in player.inv) {
            chatPlayer(neutral, "I held it, but I don't have it with me.")
            chatNpc(
                neutral,
                "Then it will have gone back to its tower. Fetch it again, and bring it straight " +
                    "here.",
            )
            return
        }
        chatPlayer(happy, "My liege, the Holy Grail.")
        access.invDel(player.inv, HOLY_GRAIL)
        objbox(HOLY_GRAIL, "You hand the Holy Grail to King Arthur.")
        chatNpc(
            happy,
            "After all these years, in my own hands. The court will speak of this long after we " +
                "are all gone. Thank you, Sir Knight.",
        )
        quest.complete(access)
    }

    private suspend fun Dialogue.afterwards() {
        chatNpc(happy, "Welcome, finder of the Grail! Camelot is honoured by your visits.")
        chatPlayer(quiz, "How is King Percival?")
        chatNpc(
            laugh,
            "He writes that his fields are full and his fish are fat. Use your whistle if you " +
                "wish to see for yourself: his realm is open to you.",
        )
    }
}
