package org.rsmod.content.quest.area.ardougne.clocktower.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.KOJO
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BrotherKojo @Inject constructor(private val clockTower: ClockTowerQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KOJO) { startDialogue(it.npc) { kojo() } }
    }

    private suspend fun Dialogue.kojo() {
        when {
            clockTower.isComplete(player) -> finished()
            clockTower.isActive(player) -> progress()
            else -> offer()
        }
    }

    private suspend fun Dialogue.offer() {
        chatPlayer(neutral, "Hello monk.")
        chatNpc(neutral, "Hello adventurer. My name is Brother Kojo. Do you happen to know the time?")
        chatPlayer(neutral, "No, sorry, I don't.")
        chatNpc(
            worried,
            "Exactly! This clock tower has recently broken down, and without it nobody can tell " +
                "the correct time. I must fix it before the town people become too angry!",
        )
        chatNpc(quiz, "I don't suppose you could assist me in the repairs? I'll pay you for your help.")
        if (!startQuestPrompt(clockTower.quest)) {
            chatPlayer(neutral, "Not now old monk.")
            chatNpc(neutral, "OK then. Come back and let me know if you change your mind.")
            return
        }
        chatPlayer(neutral, "OK old monk, what can I do?")
        clockTower.quest.setQuestStage(access, STAGE_STARTED)
        chatNpc(
            happy,
            "Oh, thank you kindly! In the cellar below, you'll find four cogs. They're too heavy " +
                "for me, but you should be able to carry them one at a time.",
        )
        chatNpc(
            neutral,
            "I know one goes on each floor... but I can't exactly remember which goes where " +
                "specifically. Oh well, I'm sure you can figure it out fairly easily.",
        )
        chatPlayer(neutral, "Well, I'll do my best.")
        chatNpc(happy, "Thank you again! And remember to be careful, the cellar is full of strange beasts!")
    }

    private suspend fun Dialogue.progress() {
        when (clockTower.placedCount(player)) {
            0 -> {
                chatPlayer(neutral, "Hello again.")
                chatNpc(
                    quiz,
                    "Oh hello, are you having trouble? The cogs are in four rooms below us. Place " +
                        "one cog on a pole on each of the four tower levels.",
                )
                chatPlayer(neutral, "Right, gotcha. I'll do that then.")
            }
            1 -> {
                chatPlayer(happy, "I've placed a cog!")
                chatNpc(happy, "That's great. Come see me when you've done the other three.")
            }
            2 -> {
                chatPlayer(happy, "Two down!")
                chatNpc(happy, "Two to go.")
            }
            3 -> chatNpc(happy, "One left.")
            else -> reward()
        }
    }

    private suspend fun Dialogue.reward() {
        chatPlayer(happy, "I have replaced all the cogs!")
        chatNpc(
            happy,
            "Really..? Wait, listen! Well done, well done! Yes yes yes, you've done it! You ARE " +
                "clever!",
        )
        if (!hasRoomForCoins()) {
            chatNpc(neutral, "I'd like to pay you, but you've no room for the coins. Make a little space and come back.")
            return
        }
        chatNpc(
            happy,
            "The townsfolk will all be able to know the correct time now! Thank you so much for " +
                "all of your help! And as promised, here is your reward!",
        )
        if (!clockTower.isActive(player) || !clockTower.allPlaced(player)) {
            return
        }
        clockTower.quest.completeQuest(access)
    }

    private suspend fun Dialogue.finished() {
        chatPlayer(neutral, "Hello again Brother Kojo.")
        chatNpc(happy, "Oh hello there traveller. You've done a grand job with the clock. It's just like new.")
    }

    private fun Dialogue.hasRoomForCoins(): Boolean {
        val held = access.inv.count(COINS)
        if (held > 0) {
            return held <= Int.MAX_VALUE - REWARD_COINS
        }
        return access.inv.freeSpace() > 0
    }
}
