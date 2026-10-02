package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepColour
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.FEED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.HALGRIVE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.REWARD_COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_DISPOSED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Councillor Halgrive, outside the East Ardougne church: starts Sheep Herder, hands out the
 * poisoned feed (again, if it is lost) and pays the 3,100 coins once all four sheep are burned.
 */
class CouncillorHalgrive @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HALGRIVE) { startDialogue(it.npc) { halgrive() } }
    }

    private suspend fun Dialogue.halgrive() {
        when (sheep.stage(player)) {
            0 -> offer()
            STAGE_STARTED -> progress()
            STAGE_DISPOSED -> payment()
            else -> chatNpc(happy, "Ah, our sheep herder! Not a single new case near the farm since you dealt with that flock.")
        }
    }

    private suspend fun Dialogue.offer() {
        chatNpc(worried, "You there! You look capable. The council has a small matter that needs seeing to, quietly.")
        val topic =
            menu(
                "What sort of matter?" to true,
                "I'm afraid I'm busy." to false,
            )
        if (!topic) {
            chatPlayer(neutral, "I'm afraid I'm busy.")
            chatNpc(sad, "Aren't we all. Ardougne won't protect itself, you know.")
            return
        }
        chatPlayer(quiz, "What sort of matter?")
        chatNpc(worried, "Four sheep at Farmer Brumty's farm, north-west of here, have turned strange colours: one red, one green, one blue and one yellow.")
        chatNpc(worried, "Our physicians believe they're carrying the plague. If it spreads through the flocks, it'll reach the city soon after.")
        chatNpc(neutral, "They must be herded into the farmer's enclosure, given this poisoned feed, and their remains burned in the incinerator there. Every last bone.")
        chatNpc(neutral, "The council will pay 3,000 coins for the job, and it'll cover your protective clothing too.")
        if (!startQuestPrompt(sheep.quest)) {
            chatPlayer(neutral, "Sorry, I'd rather not get mixed up with plague sheep.")
            chatNpc(sad, "I can hardly blame you. Come back if you change your mind.")
            return
        }
        chatPlayer(happy, "I'll take care of it.")
        if (access.inv.freeSpace() == 0) {
            chatNpc(neutral, "Splendid. You'll need to carry the feed, though, and your pack's full. Make some room and come back to me.")
            return
        }
        sheep.quest.setQuestStage(access, STAGE_STARTED)
        access.invAdd(access.inv, FEED, 1)
        objbox(FEED, zoom = 400, "Councillor Halgrive hands you some poisoned sheep feed.")
        chatNpc(neutral, "Don't go near those animals unprotected. Doctor Orbon, in the church behind me, sells plague clothing for 100 coins.")
        chatNpc(neutral, "Farmer Brumty keeps a cattleprod at the enclosure to move the sheep along. Mind you don't get too fond of them.")
    }

    private suspend fun Dialogue.progress() {
        chatNpc(quiz, "How goes the work with those sheep?")
        val left = sheep.remaining(player)
        val done = SheepColour.entries.size - left.size
        if (done == 0) {
            chatPlayer(neutral, "I haven't dealt with any of them yet.")
        } else {
            chatPlayer(neutral, "I've burned the remains of $done of them.")
        }
        chatNpc(neutral, "Then you've still to dispose of the ${left.joinToString(", ") { it.label }.replaceLast(", ", " and ")} sheep. Bring me word once all four are burned.")
        if (!sheep.hasFeed(player, access.bank)) {
            chatPlayer(sad, "I seem to have lost the sheep feed.")
            if (access.inv.freeSpace() == 0) {
                chatNpc(neutral, "I've more here, but you've nowhere to put it. Make some space in your pack.")
                return
            }
            access.invAdd(access.inv, FEED, 1)
            objbox(FEED, zoom = 400, "Councillor Halgrive hands you some more poisoned sheep feed.")
            chatNpc(neutral, "Try not to lose this lot. The council doesn't print money, you know.")
        }
    }

    private suspend fun Dialogue.payment() {
        chatPlayer(happy, "All four sheep have been dealt with, and their remains burned.")
        chatNpc(happy, "Excellent news! Ardougne owes you a debt.")
        if (!hasRoomForCoins()) {
            chatNpc(neutral, "I'd pay you now, but you've no room for the coins. Make a little space and come back.")
            return
        }
        chatNpc(neutral, "Here's 100 coins to pay you back for Doctor Orbon's clothing, and the 3,000 we agreed for the work.")
        if (sheep.stage(player) != STAGE_DISPOSED) {
            return
        }
        sheep.quest.completeQuest(access)
    }

    private fun Dialogue.hasRoomForCoins(): Boolean {
        val held = access.inv.count(COINS)
        if (held > 0) {
            return held <= Int.MAX_VALUE - REWARD_COINS
        }
        return access.inv.freeSpace() > 0
    }

    private fun String.replaceLast(old: String, new: String): String {
        val at = lastIndexOf(old)
        return if (at < 0) this else substring(0, at) + new + substring(at + old.length)
    }
}
