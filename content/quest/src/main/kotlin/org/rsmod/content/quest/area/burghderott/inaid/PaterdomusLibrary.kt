package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.HISTORIES
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.LIBRARY_KEY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.LIBRARY_TRAPDOOR
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.MODERN_MORYTANIA
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.ROD_FULL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.ROPE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL_ENCHANTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SLEEPING_SEVEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BOOK_READ
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_IVAN_DELIVERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_LIBRARY_KEY
import org.rsmod.content.quest.area.mortmyre.naturespirit.DrezelLine
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drezel, his secret library under the mausoleum and the well that draws on the Salve.
 *
 * Drezel's own script (Priest in Peril) hands him over to [talk] whenever [hasBusiness] says In
 * Aid of the Myreque has something for him. The key opens the trapdoor for good (`varbit
 * .burgh_temple_trapdoor`); using it again on the keyhole closes it, as in OSRS. Reading The
 * sleeping seven is what points the player to the tomb; nothing else does.
 */
@Singleton
class PaterdomusLibrary @Inject constructor(private val iaom: InAidOfTheMyrequeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(KEYHOLE) { inspectKeyhole() }
        onOpLocU(KEYHOLE, LIBRARY_KEY) { useKey() }
        onOpLoc1(TRAPDOOR_OPEN) {
            arriveDelay()
            anim(CLIMB_DOWN_SEQ)
            delay(CLIMB_TICKS)
            telejump(BurghCoords.LIBRARY_ARRIVAL, TeleportType.Exempt)
        }
        onOpLoc1(LADDER_UP) {
            arriveDelay()
            anim(CLIMB_UP_SEQ)
            delay(CLIMB_TICKS)
            telejump(BurghCoords.LIBRARY_TRAPDOOR_TOP, TeleportType.Exempt)
        }
        onOpLoc1(IVANDIS_BOOKCASE) { searchFor(listOf(SLEEPING_SEVEN)) }
        onOpLoc1(HISTORY_BOOKCASE) { searchFor(listOf(HISTORIES, MODERN_MORYTANIA)) }
        for (book in BOOKS.keys) {
            onOpHeld1(book) { read(book) }
        }
        onOpLocU(WELL, SILVTHRILL) { dip(enchanted = false) }
        onOpLocU(WELL, SILVTHRILL_ENCHANTED) { dip(enchanted = true) }
    }

    fun hasBusiness(player: Player): Boolean {
        val stage = iaom.stage(player)
        return stage in STAGE_IVAN_DELIVERED until InAidOfTheMyrequeQuest.STAGE_COMPLETE
    }

    suspend fun Dialogue.talk(drezel: DrezelLine) {
        if (iaom.stage(player) >= STAGE_LIBRARY_KEY) {
            chatPlayer(happy, "Thanks very much for your help. I really appreciate it.")
            drezel(sad, "I just hope all our sacrifices are worth it.")
            if (!player.holdsAnywhere(LIBRARY_KEY) && player.vars[LIBRARY_TRAPDOOR] == 0) {
                drezel(neutral, "You've lost the key? Here, take it, but guard it better this time.")
                if (!access.inv.isFull()) {
                    access.invAdd(access.inv, LIBRARY_KEY)
                }
            }
            return
        }
        drezel(neutral, "Greetings again, adventurer. How go your travels in Morytania? Is it as evil as I have heard?")
        drezel(happy, "Well done for bringing Ivan to the temple, ${player.displayName}. He will be much safer here.")
        chatPlayer(neutral, "Yes, it does seem more dangerous over there, doesn't it?")
        drezel(neutral, "Indeed it does, my friend. But thanks to you and the Myreque, we have at least the hope of salvation in that dark land.")
        if (!choice2("Veliaf told me about Ivandis.", true, "Okay, thanks.", false)) {
            chatPlayer(neutral, "Okay, thanks.")
            return
        }
        chatPlayer(neutral, "Veliaf told me about Ivandis.")
        drezel(neutral, "Hmmm, how can I put this diplomatically. Veliaf is a good leader, but perhaps he shouldn't air his ideas about religious history.")
        chatPlayer(confused, "But you don't even know what he said about Ivandis.")
        drezel(neutral, "I assume he suggested Ivandis isn't buried in this temple? If so, it's not something I'm prepared to discuss.")
        val asked = HashSet<Int>()
        while (true) {
            when (
                choice4(
                    "So, you're sure that Ivandis is buried here then?", 1,
                    "What proof do you have that Ivandis is buried here?", 2,
                    "What do you know about the history of this temple?", 3,
                    "Is there somewhere that I might get more information on Ivandis?", 4,
                )
            ) {
                1 -> {
                    asked += 1
                    chatPlayer(quiz, "So, you're sure that Ivandis is buried here then?")
                    drezel(neutral, "Forgive me, but that's a redundant question. All of the Seven Priestly Warriors are buried here, Ivandis included.")
                    chatPlayer(quiz, "So where did this rumour that he's buried elsewhere come from?")
                    drezel(angry, "Rumours come from all kinds of places, and they are rarely true. I'm sorry to be blunt, but I'm offended that you'd challenge me on this.")
                }
                2 -> {
                    asked += 2
                    chatPlayer(quiz, "What proof do you have that Ivandis is buried here?")
                    drezel(neutral, "Servants of Saradomin are not concerned with trifles such as proof. We have our faith, and our faith guides us.")
                    chatPlayer(quiz, "So you don't actually know for sure that he's buried here?")
                    drezel(angry, "Please, do not challenge me on matters of faith.")
                }
                3 -> {
                    asked += 3
                    chatPlayer(quiz, "What do you know about the history of this temple?")
                    drezel(neutral, "It stands where the Seven Priestly Warriors defended Misthalin against the vampyres, and their holy remains rest peacefully beneath us.")
                    chatPlayer(quiz, "Have you checked? Have you seen their remains with your own eyes?")
                    drezel(angry, "Why would I need to? You'd have me dig up their blessed remains? For what?")
                }
                else -> {
                    chatPlayer(quiz, "Is there somewhere that I might get more information on Ivandis?")
                    drezel(neutral, "What little there is to know, you probably know already.")
                    chatPlayer(neutral, "So you have nothing more that might help? Anything you know could help free the people of Morytania.")
                    drezel(neutral, "I have told you all I know. That is as much as I, or anyone, can do.")
                    if (asked.size >= 3 && pressDrezel(drezel)) {
                        return
                    }
                }
            }
        }
    }

    /** True once the conversation is over, with or without the key. */
    private suspend fun Dialogue.pressDrezel(drezel: DrezelLine): Boolean {
        while (true) {
            when (
                choice5(
                    "Will you let me check Ivandis' tomb?", 1,
                    "Why are you so resistant to talking about history?", 2,
                    "The lives of those pitiful few left in Morytania could rest on this!", 3,
                    "Very well then. Morytania is lost to ignorance.", 4,
                    "Okay, thanks.", 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Will you let me check Ivandis' tomb?")
                    drezel(angry, "Absolutely not! What a disgrace that you should even ask! He rests pure and undisturbed beneath this hallowed earth!")
                    chatPlayer(angry, "We need to consider everything that could help us!")
                    drezel(angry, "I don't see how that could help you! And if you carry on in this vein, you'll certainly not have my support!")
                }
                2 -> {
                    chatPlayer(quiz, "Why are you so resistant to talking about history?")
                    drezel(neutral, "I'm not resistant at all, my friend. I'd happily talk history with you for as long as you like. But you don't want history, you want rumours.")
                    chatPlayer(angry, "I think you're holding something back! We need all the help we can get!")
                    drezel(neutral, "I understand that, but digging up the remains of saintly heroes will not help us!")
                }
                3 -> {
                    chatPlayer(angry, "The lives of those pitiful few left in Morytania could rest on this!")
                    drezel(sad, "And you know I would do anything to help those people.")
                    chatPlayer(neutral, "I know. So please, help me as much as you can.")
                    drezel(sad, "Very well, but there is only so much I can do. Remember that I am still a priest of Saradomin.")
                    drezel(neutral, "I don't know if it will help, but take this key. It opens a secret library here in the mausoleum. You may find what you seek there, but I pray you never tell me of it.")
                    if (access.inv.isFull()) {
                        drezel(neutral, "...though you have no room to carry it. Come back when you do.")
                        return true
                    }
                    access.invAdd(access.inv, LIBRARY_KEY)
                    iaom.advanceTo(access, STAGE_LIBRARY_KEY)
                    objbox(LIBRARY_KEY, "Drezel gives you a key.")
                    chatPlayer(happy, "Thank you, Drezel.")
                    return true
                }
                4 -> {
                    chatPlayer(angry, "Very well then. Morytania is lost to ignorance.")
                    drezel(neutral, "Not ignorance, my friend. There are ways to save Morytania from the vampyres. This is not one of them.")
                    return true
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return true
                }
            }
        }
    }

    private suspend fun ProtectedAccess.inspectKeyhole() {
        arriveDelay()
        mesbox("You inspect the panel. It looks like there's some sort of keyhole in it.")
    }

    private suspend fun ProtectedAccess.useKey() {
        arriveDelay()
        if (!iaom.reached(player, STAGE_LIBRARY_KEY)) {
            return
        }
        val open = player.vars[LIBRARY_TRAPDOOR] == 1
        var yes = false
        startDialogue {
            mesbox("You inspect the panel. It looks like there's some sort of keyhole in it.")
            yes = choice2("Yes.", true, "No.", false, title = if (open) "Lock the library trapdoor?" else "Insert the library key?")
        }
        if (!yes) return
        VarPlayerIntMapSetter.set(player, LIBRARY_TRAPDOOR, if (open) 0 else 1)
        soundSynth(UNLOCK_SOUND)
        mesbox(if (open) "You turn the key and hear the trapdoor click shut." else "You use the library key in the keyhole and you hear a click to the left of you.")
    }

    private suspend fun ProtectedAccess.searchFor(books: List<String>) {
        arriveDelay()
        anim(SEARCH_SEQ)
        for (book in books) {
            if (book in inv) continue
            if (inv.isFull()) {
                mes("You don't have enough inventory space.")
                return
            }
            invAdd(inv, book)
            objbox(book, "You find a book called ${BOOKS.getValue(book).title}.")
        }
    }

    private suspend fun ProtectedAccess.read(book: String) {
        val text = BOOKS.getValue(book)
        if (book == SLEEPING_SEVEN && iaom.stage(player) == STAGE_LIBRARY_KEY) {
            iaom.advanceTo(this, STAGE_BOOK_READ)
        }
        var spread = 0
        while (true) {
            openSpread(text, spread)
            val input = pauseButton()
            spread =
                when (input.component) {
                    PAGE_LEFT -> (spread - 1).coerceAtLeast(0)
                    PAGE_RIGHT -> (spread + 1).coerceAtMost(text.spreads.lastIndex)
                    else -> spread
                }
        }
    }

    private fun ProtectedAccess.openSpread(book: Book, spread: Int) {
        ifOpenMainModal(BOOK_INTERFACE)
        player.runClientScript(
            BOOK_INIT_SCRIPT,
            RSCM.getRSCM("component.book:close_button"),
            RSCM.getRSCM("component.book:close_graphic"),
            RSCM.getRSCM(PAGE_LEFT),
            RSCM.getRSCM("component.book:page_left_graphic"),
            RSCM.getRSCM(PAGE_RIGHT),
            RSCM.getRSCM("component.book:page_right_graphic"),
        )
        ifSetText("component.book:title", book.title)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = book.spreads[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == book.spreads.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    /** Lowering a silvthrill rod into the Salve; only an enchanted one, on a rope, comes back blessed. */
    private suspend fun ProtectedAccess.dip(enchanted: Boolean) {
        arriveDelay()
        val rod = if (enchanted) SILVTHRILL_ENCHANTED else SILVTHRILL
        if (ROPE !in inv) {
            objbox(rod, "You'd lose the rod if you just dropped it down there.")
            return
        }
        anim(DIP_SEQ)
        delay(DIP_TICKS)
        if (!enchanted) {
            objbox(rod, "You lower the rod into the Salve and withdraw it shortly after. It seems to be wet, but otherwise nothing has changed.")
            return
        }
        if (invReplace(inv, SILVTHRILL_ENCHANTED, 1, ROD_FULL).failure) {
            return
        }
        objbox(ROD_FULL, "You lower the rod into the Salve and withdraw it shortly after. It seems to glow in a rather strange way.")
    }

    class Book(val title: String, pages: List<String>) {
        val spreads: List<Pair<List<String>, List<String>>> =
            pages.flatMap { wrap(it).chunked(LINES_PER_PAGE) }.chunked(2).map { it[0] to it.getOrElse(1) { emptyList() } }

        private fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > WRAP_WIDTH) {
                    lines += line.toString()
                    line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines
        }
    }

    internal companion object {
        const val KEYHOLE = "loc.burgh_library_keyhole"
        const val TRAPDOOR_OPEN = "loc.burgh_temple_library_trapdoor_open"
        const val LADDER_UP = "loc.burgh_temple_library_ladder_up"
        const val IVANDIS_BOOKCASE = "loc.burgh_library_bookcase_ivandis"
        const val HISTORY_BOOKCASE = "loc.burgh_library_bookcase_history"
        const val WELL = "loc.priestperil_well"

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val PAGE_SOUND = "synth.turn_book_page"

        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val CLIMB_UP_SEQ = "seq.human_reachforladdertop"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val DIP_SEQ = "seq.human_pickuptable"
        const val UNLOCK_SOUND = "synth.unlock"
        const val CLIMB_TICKS = 2
        const val DIP_TICKS = 2

        /** The books' contents are summarised in the server's own words. */
        val BOOKS: Map<String, Book> =
            mapOf(
                SLEEPING_SEVEN to
                    Book(
                        "The Sleeping Seven",
                        listOf(
                            "Of the seven priests who held the Salve against the vampyre host, six were laid to rest beneath the temple they died defending, and the brothers sing of them still.",
                            "The seventh, Ivandis Seergaze, would not wait for the enemy to come to him. Time and again he crossed the river and led the faithful into the cursed land beyond.",
                            "He carried a rod of his own devising, silver wedded to mithril and crowned with a sapphire, blessed in the waters of the Salve. With it he could hold a vampyre still, helpless as a babe.",
                            "On his last crossing Ivandis did not return. His followers carried him no further than they dared, and laid him in a cave in the hills of the Hollows, close to where the swamp begins.",
                            "They sealed the cave with timbers and left the rod upon his coffin, so that even in death he might stand guard. Let none disturb him who has not need as great as his.",
                        ),
                    ),
                HISTORIES to
                    Book(
                        "Histories of the Hallowland",
                        listOf(
                            "Before it was Morytania, the land east of the Salve was Hallowvale, a kingdom of light ruled by the Hallow family, among them Queen Efaritay.",
                            "When the vampyres came, Hallowvale fell. Its cities were renamed, its people penned and bled, and its name forgotten by all but a few.",
                        ),
                    ),
                MODERN_MORYTANIA to
                    Book(
                        "Modern day Morytania",
                        listOf(
                            "Morytania today is ruled from Castle Drakan by Lord Drakan and his kin. The few humans left pay a blood tithe, collected by servants of the vampyres.",
                            "Canifis is home to werewolves, Mort'ton to the afflicted, and Meiyerditch, beyond the walls to the east, to the penned folk whose blood feeds the vyres.",
                        ),
                    ),
            )
    }
}
