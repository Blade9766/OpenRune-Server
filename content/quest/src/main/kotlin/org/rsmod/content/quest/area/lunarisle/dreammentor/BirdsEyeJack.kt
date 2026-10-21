package org.rsmod.content.quest.area.lunarisle.dreammentor

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpc3
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.SEAL_OF_PASSAGE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_ARMED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_GOT_CHEST
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_NEEDS_ARMOUR
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * 'Birds-Eye' Jack, the Lady Zay's old navigator, now a banker on Lunar Isle and Cyrisus' friend.
 * He hands out Cyrisus's chest and opens Cyrisus's account to fill it. Like the Moon Clan's own
 * bankers he serves only holders of a Seal of Passage, until Dream Mentor is complete.
 */
class BirdsEyeJack
@Inject
constructor(
    private val dreamMentor: DreamMentorQuest,
    private val bank: CyrisusBank,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(JACK) { startDialogue(it.npc) { talk() } }
        onApNpc1(JACK) { if (isWithinApRange(it.npc, BOOTH_REACH)) startDialogue(it.npc) { talk() } }
        onOpNpc3(JACK) { bank() }
        onApNpc3(JACK) { if (isWithinApRange(it.npc, BOOTH_REACH)) bank() }
        onOpNpc4(JACK) { collect() }
        onApNpc4(JACK) { if (isWithinApRange(it.npc, BOOTH_REACH)) collect() }
    }

    fun mayBank(player: org.rsmod.game.entity.Player): Boolean =
        dreamMentor.unlocked(player) || SEAL_OF_PASSAGE in player.inv || SEAL_OF_PASSAGE in player.worn

    private fun ProtectedAccess.mayBank(): Boolean = mayBank(player)

    private suspend fun ProtectedAccess.bank() {
        if (mayBank()) tryOpenBank() else refuse()
    }

    private suspend fun ProtectedAccess.collect() {
        if (mayBank()) ifOpenMainModal(COLLECTION_BOX) else refuse()
    }

    private suspend fun ProtectedAccess.refuse() {
        startDialogue {
            chatNpc(neutral, "No Seal of Passage, no service. Moon Clan rules, not mine.")
        }
    }

    private suspend fun Dialogue.talk() {
        val stage = dreamMentor.stage(player)
        if (stage !in STAGE_NEEDS_ARMOUR until DreamMentorQuest.STAGE_COMPLETE) {
            chatNpc(neutral, "Good day. What can I do for you?")
            if (choice2("I'd like to access my bank account, please.", true, "Nothing, thanks.", false)) {
                openOwnBank()
            }
            return
        }
        chatNpc(neutral, "What would you like to talk about?")
        val topic = choice2("Using the bank.", 1, "Cyrisus in the mine.", 2)
        if (topic == 1) {
            openOwnBank()
            return
        }
        when {
            stage >= STAGE_ARMED -> recovered()
            !player.dreamBankerIntro -> firstMeeting()
            else -> reopen()
        }
    }

    private suspend fun Dialogue.openOwnBank() {
        if (!access.mayBank()) {
            chatNpc(neutral, "No Seal of Passage, no service. Moon Clan rules, not mine.")
            return
        }
        access.tryOpenBank()
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(quiz, "This might sound odd, but do you know someone called Cyrisus?")
        chatNpc(quiz, "I do. Funny, you look familiar too...")
        chatNpc(angry, "You! You're the cheeky so-and-so who said I navigated the Lady Zay in circles!")
        chatPlayer(shocked, "And you're the navigator who did navigate the Lady Zay in circles!")
        chatNpc(angry, "It was a jinx!")
        chatPlayer(neutral, "Yes. Of course. And without me, you'd still be going round.")
        chatNpc(bored, "Oh, the mighty ${player.displayName}. Whatever would Gielinor do without you?")
        chatPlayer(quiz, "So why is a pirate working in a bank?")
        chatNpc(neutral, "I'd had my fill of looting and treasure maps. Banking's an honest living, and more exciting than you'd think.")
        chatPlayer(bored, "If you say so.")
        chatPlayer(neutral, "Anyway, Cyrisus is hurt, down in the caves north of here. He wants some armour out of his account.")
        chatNpc(neutral, "Does he now? I suppose I can let you at it.")
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "I'd give you his chest to carry it in, but you've no room for it.")
            return
        }
        access.invAdd(access.inv, CyrisusBank.CHEST)
        player.dreamBankerIntro = true
        dreamMentor.advanceTo(access, STAGE_GOT_CHEST)
        chatNpc(neutral, "Take his chest to carry it in. Don't you dare lose any of it.")
        objbox(CyrisusBank.CHEST, "Jack hands you Cyrisus's chest.")
        access.ifClose()
        with(bank) { access.openBank() }
    }

    private suspend fun Dialogue.reopen() {
        chatPlayer(neutral, "Could I get into Cyrisus's account again?")
        chatNpc(neutral, "Go on then. Help him quickly, or you'll have me to answer to.")
        if (CyrisusBank.CHEST in player.inv) {
            access.ifClose()
            with(bank) { access.openBank() }
            return
        }
        chatNpc(neutral, "Not without his chest, mind.")
        if (access.bank.contains(CyrisusBank.CHEST)) {
            chatPlayer(neutral, "Ah. I think I left it in my bank.")
            return
        }
        chatPlayer(sad, "I'm afraid I've lost it.")
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "I've a spare, but you've got no room for it.")
            return
        }
        access.invAdd(access.inv, CyrisusBank.CHEST)
        chatNpc(neutral, "I suppose he has a spare. Here you go.")
        objbox(CyrisusBank.CHEST, "Jack hands you another of Cyrisus's chests.")
    }

    private suspend fun Dialogue.recovered() {
        chatNpc(quiz, "How's Cyrisus doing?")
        chatPlayer(happy, "Much better. He's on his feet and fully kitted out.")
        chatNpc(neutral, "Good. Don't think this means I like you.")
        chatPlayer(neutral, "Does my face look like it cares?")
        chatNpc(bored, "Not really.")
    }

    companion object {
        const val JACK = "npc.dream_birds_eye_jack"
        const val COLLECTION_BOX = "interface.ge_collect"

        /** Jack stands behind his bank booth; he is reached across it, like the other bankers. */
        const val BOOTH_REACH = 2
    }
}
