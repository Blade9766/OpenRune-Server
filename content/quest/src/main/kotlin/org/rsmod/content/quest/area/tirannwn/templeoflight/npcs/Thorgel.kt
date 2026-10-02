package org.rsmod.content.quest.area.tirannwn.templeoflight.npcs

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.DEATH_TALISMAN
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.ITEM_LIST
import org.rsmod.content.quest.area.tirannwn.templeoflight.ThorgelList
import org.rsmod.content.quest.area.tirannwn.templeoflight.hasAltarAccess
import org.rsmod.content.quest.area.tirannwn.templeoflight.metThorgel
import org.rsmod.content.quest.area.tirannwn.templeoflight.ownsAnywhere
import org.rsmod.content.quest.area.tirannwn.templeoflight.thorgelTask
import org.rsmod.content.quest.area.tirannwn.templeoflight.thorgelVisible
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Thorgel, the dwarf by the Death Altar who tunnelled in from the Underground Pass. He meets the
 * player the first time they step through the black door, and trades a death talisman for the
 * items on his list ([ThorgelList]) to anyone who has no other way into the altar. Items can be
 * handed over in as many trips as it takes, all at once or one at a time; only items still on
 * the list are taken, one each, and the talisman comes in the same transaction as the last item,
 * so a full pack keeps the last item instead of losing the talisman.
 */
@Singleton
class Thorgel @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(THORGEL, THORGEL_VIS)) {
            onOpNpc1(type) { startDialogue(it.npc) { talk() } }
        }
        onOpHeld1(ITEM_LIST) { readList() }
    }

    /** The first meeting, as the player steps through the black door. */
    suspend fun meet(access: ProtectedAccess) {
        val player = access.player
        player.metThorgel = 1
        player.thorgelVisible = 1
        access.startDialogue {
            thorgel(angry, "Oi! How did you get in here?")
            chatPlayer(neutral, "Funny, I was about to ask you the same thing.")
            thorgel(angry, "I asked first.")
            chatPlayer(neutral, "I fought my way past a horde of shadows while bouncing a beam of light round a giant maze. Your turn.")
            thorgel(neutral, "I came in through the hole in the back wall.")
            chatPlayer(shocked, "You mean there's an easy way in?")
            thorgel(neutral, "Not easy, no. Some of the lads and I have an outpost at the other end of the tunnel, at the bottom of a huge cavern, and the only way there is through caves full of traps.")
            chatPlayer(quiz, "That sounds familiar. Is one of your friends called Klank?")
            thorgel(confused, "He is. How do you know that?")
            chatPlayer(happy, "He's a friend of mine. He made me a fine pair of gauntlets.")
            thorgel(shocked, "Wait... you must be ${player.displayName}!")
            chatPlayer(quiz, "That's right. How did you know?")
            thorgel(happy, "Klank told me how you got rid of that Iban. Seeing as it's you, I'll trade you a death talisman. You'll need one to get into this altar.")
            if (player.hasAltarAccess()) {
                chatPlayer(neutral, "Thanks, but I've already got a way in.")
                thorgel(neutral, "Well, if you ever want a talisman, come back and I'll tell you what I need.")
                return@startDialogue
            }
            chatPlayer(quiz, "Great... wait, in return for what?")
            thorgel(neutral, "Just a few things. Here, I'll write you a list.")
            writeList()
            chatPlayer(neutral, "Right, I'd better get started. See you soon.")
        }
    }

    private suspend fun Dialogue.talk() {
        if (player.metThorgel == 0) {
            return
        }
        when (player.thorgelTask) {
            ThorgelList.TASK_OPEN -> openList()
            ThorgelList.TASK_BETWEEN -> {
                thorgel(happy, "Hello again. Feel like getting us some more supplies?")
                offerList()
            }
            else -> {
                thorgel(neutral, "Hey there, ${player.displayName}. Do you want that shopping list now?")
                chatPlayer(quiz, "That depends. What do I get out of it again?")
                thorgel(neutral, "A death talisman. You'll need one if you want to get into the altar.")
                offerList()
            }
        }
    }

    private suspend fun Dialogue.offerList() {
        val yes = menu("Okay, sure. What do you need?" to true, "No thanks, I'm not interested." to false)
        if (!yes) {
            chatPlayer(neutral, "No thanks, I'm not interested.")
            return
        }
        chatPlayer(neutral, "Okay, sure. What do you need?")
        thorgel(neutral, "Take this list, we need everything on it! Bring the things to me and I'll take them off your hands.")
        writeList()
    }

    private suspend fun Dialogue.writeList() {
        ThorgelList.write(player) { bound -> access.random.of(bound) }
        if (player.inv.freeSpace() < 1 || access.invAdd(player.inv, ITEM_LIST).failure) {
            mesbox("Thorgel writes out a list, but you have no room to take it. You can ask him for another copy.")
            return
        }
        objbox(ITEM_LIST, "Thorgel scribbles on a scrap of paper and hands it to you.")
    }

    private suspend fun Dialogue.openList() {
        thorgel(neutral, "Hi again. How are you doing with those items we need?")
        val choice =
            menu(
                "I've got some of them with me." to 1,
                "Do you have another copy of the list?" to 2,
                "What do you need all this stuff for?" to 3,
                "No, I'd better get looking for them." to 4,
            )
        when (choice) {
            1 -> handOver()
            2 -> copyOfList()
            3 -> whatFor()
            else -> chatPlayer(neutral, "No, I'd better get looking for them.")
        }
    }

    private suspend fun Dialogue.handOver() {
        chatPlayer(neutral, "I've got some of them with me.")
        thorgel(quiz, "Great, can I take everything I need?")
        val all = menu("Yeah, sure." to true, "I'd rather you didn't." to false)
        chatPlayer(neutral, if (all) "Yeah, sure." else "I'd rather you didn't.")
        val carried = ThorgelList.outstanding(player).filter { ThorgelList.carried(player, it) != null }
        if (carried.isEmpty()) {
            thorgel(neutral, "Doesn't look like you've got any of them to me.")
            return
        }
        if (all) {
            take(carried)
            return
        }
        for (entry in carried) {
            thorgel(quiz, "I see you've got the ${entry.label.lowercase()}. Can I have it, please?")
            val give = menu("Sure, here you go!" to true, "I think I'll keep hold of it for now." to false)
            chatPlayer(neutral, if (give) "Sure, here you go!" else "I think I'll keep hold of it for now.")
            if (give && !take(listOf(entry))) {
                return
            }
            if (player.thorgelTask != ThorgelList.TASK_OPEN) {
                return
            }
        }
    }

    /**
     * Takes one of each item in [entries] the player carries and still owes, and the death
     * talisman goes in with the last of the list. Returns whether anything changed hands.
     */
    private suspend fun Dialogue.take(entries: List<ThorgelList.Entry>): Boolean {
        val outstanding = ThorgelList.outstanding(player)
        val giving = entries.mapNotNull { entry -> ThorgelList.carried(player, entry)?.let { entry to it } }
            .filter { (entry, _) -> outstanding.any { it.index == entry.index } }
        if (giving.isEmpty()) {
            return false
        }
        val finished = giving.size == outstanding.size
        val inv = player.inv
        val moved =
            player.invTransaction(inv) {
                val pack = select(inv)
                for ((_, obj) in giving) {
                    delete {
                        from = pack
                        this.obj = obj.asRSCM()
                        strictCount = 1
                    }
                }
                if (finished) {
                    insert {
                        into = pack
                        this.obj = DEATH_TALISMAN.asRSCM()
                        strictCount = 1
                    }
                }
            }.success
        if (!moved) {
            thorgel(neutral, "You'll want a free space for the talisman first.")
            return false
        }
        ThorgelList.markDelivered(player, giving.map { it.first.index })
        if (!finished) {
            thorgel(happy, "Thanks, I've updated your list for you.")
            return true
        }
        player.thorgelTask = ThorgelList.TASK_BETWEEN
        thorgel(happy, "Great, thank you so much for getting all that for us! Take this death talisman as a reward.")
        objbox(DEATH_TALISMAN, "Thorgel gives you a death talisman.")
        thorgel(happy, "Any time you want another of those, come and see me and I'll sort you out with another list.")
        return true
    }

    private suspend fun Dialogue.copyOfList() {
        chatPlayer(quiz, "Do you have another copy of the list?")
        when {
            access.ownsAnywhere(ITEM_LIST) -> thorgel(neutral, "You've already got the list! Just bring the things to me and I'll take them off your hands.")
            player.inv.freeSpace() < 1 -> thorgel(neutral, "Looks like you're carrying enough stuff at the moment.")
            access.invAdd(player.inv, ITEM_LIST).failure -> return
            else -> {
                thorgel(neutral, "Try not to lose it this time, we need everything on it! Bring the things to me and I'll take them off your hands.")
            }
        }
    }

    private suspend fun Dialogue.whatFor() {
        chatPlayer(quiz, "What do you need all this stuff for?")
        thorgel(shifty, "Oh, well, we don't get many deliveries down here, so it's supplies mostly...")
        val outstanding = ThorgelList.outstanding(player).associateBy { it.index }
        val options = mutableListOf<Pair<String, Int>>()
        outstanding[ThorgelList.TICKET_INDEX]?.let { options += "What about the ${it.label.lowercase()}?" to 1 }
        outstanding[ThorgelList.BOOK_INDEX]?.let { options += "So why do you want the ${it.label}?" to 2 }
        outstanding[ThorgelList.KEY_INDEX]?.let { options += "How do you explain the ${it.label.lowercase()}?" to 3 }
        options += "Yeah, right. All of those are just 'supplies'." to 4
        val choice = menu(options)
        chatPlayer(quiz, options.first { it.second == choice }.first)
        when (choice) {
            1 -> {
                thorgel(shifty, "Well, you see... we don't really get much chance to go out, and we'd like some of the prizes one day.")
                chatPlayer(quiz, "So why not just ask for the prizes?")
                thorgel(sad, "Oh no, that'd just be sad.")
            }
            2 -> {
                thorgel(neutral, "A bit of light reading. It's nice to catch up on current events now and then.")
                chatPlayer(confused, "Current events? But it's... oh, never mind.")
            }
            3 -> {
                thorgel(shifty, "Ah... um... I'm not sure how that got on there. Can we have it anyway?")
                chatPlayer(quiz, "Why do I get the feeling I shouldn't?")
                thorgel(happy, "Pretty please, with a blood talisman on top?")
                chatPlayer(shocked, "A blood talisman?!")
                thorgel(shocked, "Oops! I meant a death talisman!")
            }
        }
    }

    private fun ProtectedAccess.readList() {
        val text = ThorgelList.text(player)
        if (ThorgelList.entries(player) == null || text.isEmpty()) {
            mes("There is nothing left on this list.")
            return
        }
        ifOpenMain(LIST_INTERFACE)
        ifSetText(LIST_TEXT, text)
    }

    private suspend fun Dialogue.thorgel(mesanim: dev.openrune.types.MesAnimType, text: String) {
        val speaking = npc
        if (speaking != null) chatNpc(mesanim, text) else chatNpcSpecific("Thorgel", THORGEL_VIS, mesanim, text)
    }

    companion object {
        const val THORGEL = "npc.mourning_deathalter_dwarf"
        const val THORGEL_VIS = "npc.mourning_deathalter_dwarf_vis"
        const val LIST_INTERFACE = "interface.mourning_deathaltar_list"
        const val LIST_TEXT = "component.mourning_deathaltar_list:text"
    }
}
