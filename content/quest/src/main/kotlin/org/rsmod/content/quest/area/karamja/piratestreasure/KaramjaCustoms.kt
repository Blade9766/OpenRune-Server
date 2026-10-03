package org.rsmod.content.quest.area.karamja.piratestreasure

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CHARMED_RING
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.KARAMJA_RUM
import org.rsmod.content.quest.manager.menu

/**
 * The search the Musa Point customs officer makes before anyone boards for Port Sarim: Asgarnia
 * has banned the import of spirits, so any Karamjan rum found is confiscated. [board] runs once
 * the player has been searched, declared clean and agreed to pay the fare.
 */
suspend fun Dialogue.customsBoarding(board: suspend Dialogue.() -> Unit) {
    chatPlayer(quiz, "Can I journey on this ship?")
    chatNpc(neutral, "You need to be searched before you can board.")
    while (true) {
        val charmed = access.worn.contains(CHARMED_RING)
        val topic =
            menu(
                buildList {
                    add("Why?" to CustomsTopic.Why)
                    add("Search away. I have nothing to hide." to CustomsTopic.Search)
                    add("You're not putting your hands on my things!" to CustomsTopic.Refuse)
                    if (charmed) {
                        add("(Charm) You don't need to look through my things." to CustomsTopic.Charm)
                    }
                },
            )
        when (topic) {
            CustomsTopic.Why -> {
                chatPlayer(quiz, "Why?")
                chatNpc(neutral, "Because Asgarnia has banned the import of intoxicating spirits.")
            }
            CustomsTopic.Search -> return search(board)
            CustomsTopic.Refuse -> {
                chatPlayer(angry, "You're not putting your hands on my things!")
                chatNpc(neutral, "You're not getting on this ship then.")
                return
            }
            CustomsTopic.Charm -> {
                chatPlayer(neutral, "You don't need to look through my things.")
                chatNpc(
                    neutral,
                    "Oh, I think I do. If you don't want to let me search your possessions, then " +
                        "you're not coming aboard this ship.",
                )
                return
            }
        }
    }
}

private suspend fun Dialogue.search(board: suspend Dialogue.() -> Unit) {
    chatPlayer(neutral, "Search away. I have nothing to hide.")
    if (access.inv.contains(KARAMJA_RUM)) {
        chatNpc(angry, "Aha, trying to smuggle rum are we?")
        val charm =
            access.worn.contains(CHARMED_RING) &&
                choice2(
                    "Umm... it's for personal use?",
                    false,
                    "(Charm) This is not the Karamja rum you are looking for.",
                    true,
                )
        if (charm) {
            return charmedSearch()
        }
        chatPlayer(shifty, "Umm... it's for personal use?")
        access.confiscateRum()
        return
    }
    chatNpc(
        neutral,
        "Well you've got some odd stuff, but it's all legal. Now you need to pay a boarding " +
            "charge of 30 coins.",
    )
    if (!choice2("Okay.", true, "Oh, I'll not bother then.", false)) {
        chatPlayer(neutral, "Oh, I'll not bother then.")
        return
    }
    chatPlayer(neutral, "Okay.")
    board()
}

private suspend fun Dialogue.charmedSearch() {
    chatPlayer(neutral, "This is not the Karamja rum you are looking for.")
    chatNpc(neutral, "This is not the Karamja rum we are looking for.")
    chatPlayer(happy, "Well that was easy!")
    access.anim(DANCE_SEQ)
    access.mes("You dance for joy...")
    access.invDel(access.inv, KARAMJA_RUM, access.inv.count(KARAMJA_RUM))
    access.mes("...and accidentally drop the rum.")
    chatPlayer(sad, "Drat...")
    access.mes("You will need to try again.")
}

/**
 * Takes every bottle of Karamjan rum the player carries; returns whether there was any. Used by
 * the customs officer's search and by her quick "Travel" option, which skips the conversation but
 * not the search.
 */
fun ProtectedAccess.confiscateRum(): Boolean {
    val bottles = inv.count(KARAMJA_RUM)
    if (bottles == 0) {
        return false
    }
    invDel(inv, KARAMJA_RUM, bottles)
    mes("The customs officer confiscates your rum.")
    mes("You will need to find some way to smuggle it off the island...")
    return true
}

private enum class CustomsTopic {
    Why,
    Search,
    Refuse,
    Charm,
}

private const val DANCE_SEQ = "seq.emote_dance"
