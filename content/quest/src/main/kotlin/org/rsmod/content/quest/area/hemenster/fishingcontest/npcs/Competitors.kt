package org.rsmod.content.quest.area.hemenster.fishingcontest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.BIG_DAVE
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.JOSHUA
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STRANGER_AT_SPOT
import org.rsmod.content.quest.area.hemenster.fishingcontest.FishingContestQuest.Companion.STRANGER_MOVED
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The other entrants: Big Dave the reigning champion, nervy Joshua, and the Sinister Stranger,
 * whose pallor, hood and horror of daylight are the clues to the garlic. Once the garlic has
 * driven him off for the player's round, the cache shows him as his second form and he complains
 * about the smell instead.
 */
class Competitors @Inject constructor(private val fc: FishingContestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BIG_DAVE) { startDialogue(it.npc) { bigDave() } }
        onOpNpc1(JOSHUA) { startDialogue(it.npc) { joshua() } }
        onOpNpc1(STRANGER_AT_SPOT) { startDialogue(it.npc) { stranger() } }
        onOpNpc1(STRANGER_MOVED) { startDialogue(it.npc) { strangerMoved() } }
    }

    private suspend fun Dialogue.bigDave() {
        chatPlayer(happy, "Hello. Catching much?")
        chatNpc(happy, "Big Dave's the name, fishing's the game. Three seasons champion, and that trophy's as good as mine again.")
        chatPlayer(quiz, "What's your secret?")
        chatNpc(laugh, "Turning up. Most years I'm the only one who does. Now hush, you're casting a shadow on my float.")
    }

    private suspend fun Dialogue.joshua() {
        chatPlayer(happy, "Hi there.")
        chatNpc(worried, "Shh! You'll scare the fish! I've been on the verge of a bite for an hour.")
        chatNpc(sad, "Everyone knows the big ones are over by the pipes. But that creepy fellow never leaves the spot. I asked him to swap once. He just... stared.")
    }

    private suspend fun Dialogue.stranger() {
        chatPlayer(happy, "Hello there.")
        chatNpc(shifty, "...Good evening.")
        chatPlayer(confused, "It's the middle of the afternoon.")
        chatNpc(neutral, "Is it? How... unfortunate. I so rarely get out in the day.")
        when (
            menu(
                "Are you any good at fishing?" to 1,
                "Would you swap spots with me?" to 2,
                "Where are you from?" to 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Are you any good at fishing?")
                chatNpc(shifty, "I have a talent for catching things. Fish, mostly. These days.")
            }
            2 -> {
                chatPlayer(quiz, "Would you swap spots with me?")
                chatNpc(angry, "No. This spot is mine. The building keeps the sun off, and nobody bothers me.")
                chatNpc(shifty, "Nothing on this earth could move me from it. Well. Almost nothing.")
            }
            else -> {
                chatPlayer(quiz, "Where are you from?")
                chatNpc(neutral, "Oh, a little place over the river Salve. Lovely in the dark. Terrible food, though. Everything's cooked with...")
                chatNpc(angry, "Never mind. I don't wish to talk about food.")
            }
        }
        if (fc.isContestStage(player) && fc.isGarlicPlaced(player)) {
            access.mes("You notice the Stranger keeps glancing nervously at the wall pipe above him.")
        }
    }

    private suspend fun Dialogue.strangerMoved() {
        chatNpc(angry, "Some barbarian has stuffed GARLIC into that pipe! I can taste it from here.")
        chatNpc(shifty, "You're welcome to that spot. I shan't be going back until the stench is gone.")
    }
}
