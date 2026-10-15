package org.rsmod.content.quest.area.mortton.shades.catacombs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortton.shades.catacombs.ShadeCoffins.Companion.BROKEN_COFFIN
import org.rsmod.content.quest.area.mortton.shades.catacombs.ShadeCoffins.Companion.swapInPlace
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dampe, the gravedigger at the catacomb entrance. He hands out broken coffins, one at a time, and
 * fits a lock from the catacomb chests onto a broken coffin to make it hold shade remains.
 */
class Dampe : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DAMPE) { startDialogue(it.npc) { talk() } }
        onOpNpc3(DAMPE) { startDialogue(it.npc) { askAboutLocks() } }
        onOpNpcU(DAMPE, BROKEN_COFFIN) { startDialogue(it.npc) { fixCoffin() } }
        for (metal in ShadeMetal.entries) {
            onOpNpcU(DAMPE, ShadeCoffins.lock(metal)) { startDialogue(it.npc) { showLock() } }
        }
    }

    private suspend fun Dialogue.talk() {
        if (!ShadeCoffins.hasCoffin(player)) {
            chatPlayer(neutral, "Hello stranger.")
            chatNpc(neutral, "*grunts*")
            chatPlayer(quiz, "Ah very friendly I see. What's that coffin on your back?")
            chatNpc(neutral, "For carryin' the dead.")
            chatPlayer(shocked, "Woah, don't tell me you have a body in there?")
            chatNpc(neutral, "No, remains.")
            chatPlayer(quiz, "Oh, you carry the remains of shades in it? Could you give me one?")
            chatNpc(neutral, "*grunts*")
            if (!handOverCoffin()) return
            chatPlayer(confused, "It's broken? What am I suppose to do with a broken coffin?")
            chatNpc(neutral, "Bring me locks, find in the catacombs... I repair coffin with locks.")
            chatPlayer(
                neutral,
                "Right... Guess I should head into the catacombs then... see if I can find any of " +
                    "these 'locks'.",
            )
            chatNpc(neutral, "*grunts*")
            return
        }
        chatPlayer(neutral, "Hello again.")
        chatNpc(neutral, "*grunts*")
        chatPlayer(neutral, "Ah, as cheerful as ever I see.")
        chatNpc(neutral, "What d'ya want.")
        when (
            menu(
                "Ask about locks." to 1,
                "Ask for another coffin." to 2,
                "Ask about his robes." to 3,
                "Just wanted a chat." to 4,
            )
        ) {
            1 -> askAboutLocks()
            2 -> {
                chatPlayer(quiz, "Could I get another of those coffins?")
                if (ShadeCoffins.hasCoffin(player)) {
                    chatNpc(angry, "Get lost, you've already got a coffin, I ain't givin' you anotha.")
                    return
                }
                chatNpc(neutral, "*grunts*")
                if (!handOverCoffin()) return
                chatPlayer(confused, "It's broken?")
                chatNpc(neutral, "Bring me locks, find in the catacombs... I repair coffin with locks.")
            }
            3 -> {
                chatPlayer(quiz, "Where did you get those robes?")
                chatNpc(neutral, "Given to me.")
                chatPlayer(quiz, "By who?")
                chatNpc(neutral, "A dead friend of mine.")
                chatPlayer(sad, "Oh... how did they die?")
                chatNpc(neutral, "In the catacombs.")
                chatPlayer(sad, "Sorry to hear that...")
                chatNpc(neutral, "*grunts*")
            }
            else -> {
                chatPlayer(neutral, "Nothing, I just wanted to chat.")
                chatNpc(neutral, "*grunts*")
            }
        }
    }

    private suspend fun Dialogue.askAboutLocks() {
        if (heldLock() == null) {
            chatPlayer(confused, "You gave me a broken coffin, what am I suppose to do with it?")
            chatNpc(neutral, "Bring me locks, I fix coffin with locks...")
            chatPlayer(quiz, "Right... and where am I suppose to find such locks?")
            chatNpc(neutral, "In catacombs... in chests...")
            chatPlayer(neutral, "Great, thanks for the information.")
            return
        }
        chatPlayer(happy, "I found one of the locks you were talking about, I think.")
        mesbox("You show Dampe the lock you found and his cold eyes light up.")
        chatNpc(neutral, "That is it, give to me now and I repair the coffin.")
        offerLock()
    }

    private suspend fun Dialogue.fixCoffin() {
        chatPlayer(quiz, "Can you fix this for me?")
        chatNpc(neutral, "Depends, you got any locks for it?")
        if (heldLock() == null) {
            chatPlayer(sad, "No...")
            chatNpc(angry, "Yeah, well clear off them a go find one.")
            return
        }
        offerLock()
    }

    private suspend fun Dialogue.showLock() {
        chatPlayer(quiz, "Hey, I found this lock in the catacombs? Can you do anything with it?")
        chatNpc(neutral, "*grunts* Lets 'ava look.")
        attachLock()
    }

    private suspend fun Dialogue.offerLock() {
        val give = choice2("Yes", true, "No", false, title = "Give Dampe a lock?")
        if (!give) {
            chatPlayer(neutral, "No thanks, I'm going to hang on to it for now.")
            chatNpc(neutral, "*grunts*")
            return
        }
        attachLock()
    }

    private suspend fun Dialogue.attachLock() {
        val metal = heldLock() ?: return
        if (BROKEN_COFFIN !in access.inv) {
            chatNpc(neutral, "You ain't got no broken coffin for me to fix. If you need one, just ask.")
            return
        }
        mesbox("You hand over the coffin and lock to Dampe.")
        access.invDel(access.inv, ShadeCoffins.lock(metal))
        access.swapInPlace(access.inv, BROKEN_COFFIN, ShadeCoffins.coffin(metal, open = false))
        mesbox("You hear him clumsily attach the lock to the coffin.")
        chatNpc(neutral, "Finished, take it.")
    }

    private suspend fun Dialogue.handOverCoffin(): Boolean {
        if (access.inv.isFull()) {
            chatNpc(neutral, "*grunts* No room for coffin. Come back with space.")
            return false
        }
        ShadeCoffins.clear(player)
        access.invAdd(access.inv, BROKEN_COFFIN)
        objbox(BROKEN_COFFIN, "Dampe hands you a broken coffin.")
        return true
    }

    /** The best lock the player is carrying, which is the one Dampe fits. */
    private fun Dialogue.heldLock(): ShadeMetal? =
        ShadeMetal.entries.lastOrNull { ShadeCoffins.lock(it) in access.inv }

    private companion object {
        const val DAMPE = "npc.shades_coffin_keeper"
    }
}
