package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.GodBook
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.CASKET
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.JOSSIK
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jossik, recovering upstairs in the Lighthouse after the quest. He runs the Lighthouse Store,
 * reads the inscription on the rusty casket to pick the player's first god book, and replaces
 * or sells damaged god books afterwards.
 */
class Jossik
@Inject
constructor(
    private val horror: HorrorFromTheDeepQuest,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(JOSSIK) { startDialogue(it.npc) { jossik(it.npc) } }
        onOpNpc3(JOSSIK) { trade(it.npc) }
        onOpNpc4(JOSSIK) { rewards(it.npc) }
    }

    private fun ProtectedAccess.trade(npc: Npc) {
        if (!horror.isComplete(player)) {
            mes("Jossik is in no state to trade right now.")
            return
        }
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private suspend fun ProtectedAccess.rewards(npc: Npc) {
        if (!horror.isComplete(player)) {
            mes("Jossik is in no state to talk right now.")
            return
        }
        startDialogue(npc) { prayerBooks() }
    }

    private suspend fun Dialogue.jossik(npc: Npc) {
        if (!horror.isComplete(player)) {
            chatNpc(worried, "Please, go and help Larrissa. I need to rest.")
            return
        }
        if (casketUnopened()) {
            if (!player.inv.contains(CASKET) && access.bank.contains(CASKET)) {
                chatNpc(neutral, "Hello again! Did you bring that old casket with you? Fetch it from wherever you left it and I'll take a look.")
                return
            }
            casket()
            return
        }
        chatNpc(happy, "Hello again, adventurer. What can I do for you?")
        when (choice3("Can I see your wares?", 1, "Have you found any prayer books?", 2, "Nothing, thanks.", 3)) {
            1 -> {
                chatPlayer(quiz, "Can I see your wares?")
                chatNpc(happy, "Certainly. I keep a few supplies up here for passing sailors.")
                shops.open(player, npc, SHOP_TITLE, SHOP_INV)
            }
            2 -> {
                chatPlayer(quiz, "Have you found any prayer books?")
                prayerBooks()
            }
            3 -> chatPlayer(neutral, "Nothing, thanks.")
        }
    }

    /**
     * The first book comes from the casket. Until it has been opened Jossik will take it from the
     * player, send them to fetch it from the bank, or (if it was dropped or lost at sea) produce it
     * himself.
     */
    private fun Dialogue.casketUnopened(): Boolean = horror.unlockedBooks.get(player) == 0

    private suspend fun Dialogue.casket() {
        if (player.inv.contains(CASKET)) {
            chatPlayer(happy, "Here's that casket from the caves.")
        } else {
            chatNpc(neutral, "Ah, there you are. That casket you brought out of the caves turned up down on the rocks, so I kept it safe for you.")
        }
        chatNpc(neutral, "Let me take a look... Yes, there's something scratched into the lid, very faint.")
        chatNpc(quiz, "It seems to be a riddle. It asks which power guides your hand. Well, adventurer?")
        val book =
            choice3(
                "Saradomin, bringer of order.",
                GodBook.Saradomin,
                "Zamorak, the force of change.",
                GodBook.Zamorak,
                "Guthix, keeper of balance.",
                GodBook.Guthix,
                title = "Which god do you follow?",
            )
        if (player.inv.freeSpace() == 0 && !player.inv.contains(CASKET)) {
            chatNpc(neutral, "You'll need a free space in your pack before I hand anything over.")
            return
        }
        if (player.inv.contains(CASKET)) {
            access.invDel(access.inv, CASKET)
        }
        chatNpc(happy, "The lid gives way... and inside is an old prayer book. Water has ruined most of the pages, I'm afraid.")
        chatNpc(neutral, "I've heard of loose pages washing up along the coast. Perhaps you can find the rest.")
        access.invAdd(access.inv, book.damaged)
        unlock(book)
        objbox(book.damaged, "Jossik hands you a damaged book of ${book.god}.")
    }

    private suspend fun Dialogue.prayerBooks() {
        chatNpc(neutral, "I keep any prayer books that wash up. Which would you like?")
        val options = GodBook.entries
        val first = options.subList(0, 3)
        val second = options.subList(3, 6)
        val picked =
            choice4(
                label(first[0]), first[0],
                label(first[1]), first[1],
                label(first[2]), first[2],
                "More...", null,
            ) ?: choice4(
                label(second[0]), second[0],
                label(second[1]), second[1],
                label(second[2]), second[2],
                "Never mind.", null,
            ) ?: return
        if (isUnlocked(picked)) {
            reclaim(picked)
        } else {
            buy(picked)
        }
    }

    private fun Dialogue.label(book: GodBook): String =
        if (isUnlocked(book)) "Book of ${book.god}" else "Book of ${book.god} ($BOOK_PRICE coins)"

    private suspend fun Dialogue.reclaim(book: GodBook) {
        if (ownsBook(book)) {
            chatNpc(confused, "You already have that one. I'm sure I saw it on you.")
            return
        }
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "Your pack is full. Come back when you have room.")
            return
        }
        chatNpc(happy, "Here you are. Try not to drop this one in the sea.")
        access.invAdd(access.inv, book.damaged)
        objbox(book.damaged, "Jossik hands you a damaged book of ${book.god}.")
    }

    private suspend fun Dialogue.buy(book: GodBook) {
        chatNpc(neutral, "I've a damaged book of ${book.god} here. I'll part with it for $BOOK_PRICE coins.")
        if (!choice2("Buy it for $BOOK_PRICE coins.", true, "No thanks.", false)) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        if (player.inv.count(COINS) < BOOK_PRICE) {
            chatPlayer(sad, "I don't have enough coins.")
            return
        }
        if (player.inv.freeSpace() == 0 && player.inv.count(COINS) != BOOK_PRICE) {
            chatNpc(neutral, "Your pack is full. Come back when you have room.")
            return
        }
        if (access.invDel(access.inv, COINS, BOOK_PRICE).failure) {
            return
        }
        access.invAdd(access.inv, book.damaged)
        unlock(book)
        objbox(book.damaged, "You buy a damaged book of ${book.god} from Jossik.")
    }

    private fun Dialogue.isUnlocked(book: GodBook): Boolean = (horror.unlockedBooks.get(player) and (1 shl book.ordinal)) != 0

    private fun Dialogue.unlock(book: GodBook) {
        horror.unlockedBooks.set(player, horror.unlockedBooks.get(player) or (1 shl book.ordinal))
    }

    private fun Dialogue.ownsBook(book: GodBook): Boolean =
        listOf(access.inv, player.worn, access.bank).any { it.contains(book.damaged) || it.contains(book.complete) }

    private companion object {
        const val SHOP_TITLE = "The Lighthouse Store"
        const val SHOP_INV = "inv.lighthouseshop"
        const val COINS = "obj.coins"
        const val BOOK_PRICE = 5000
    }
}
