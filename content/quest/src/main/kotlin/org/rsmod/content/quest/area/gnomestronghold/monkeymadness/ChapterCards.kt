package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess

/**
 * The chapter cards Monkey Madness shows between its acts, drawn on the cache's `mm_message`
 * interface: a white title over grey body lines, shown for a few seconds with the quest's jingle.
 */
@Singleton
class ChapterCards @Inject constructor() {

    class Card(val title: String, val lines: List<String>)

    suspend fun ProtectedAccess.show(card: Card) {
        ifOpenMainModal(INTERFACE)
        ifSetText(TITLE, card.title)
        for (i in 0 until LINE_COUNT) {
            ifSetText("$LINE_PREFIX${i + 1}", card.lines.getOrNull(i) ?: "")
        }
        player.midiJingle(NEW_CHAPTER_JINGLE)
        delay(DISPLAY_TICKS)
        ifClose()
        player.mes(card.title)
    }

    companion object {
        private const val INTERFACE = "interface.mm_message"
        private const val TITLE = "component.mm_message:mm_mstitle"
        private const val LINE_PREFIX = "component.mm_message:mm_ms"
        private const val LINE_COUNT = 11
        private const val DISPLAY_TICKS = 8

        /** Js5 archive 11 groups of the quest's jingles (the "Cache ID" on their wiki pages). */
        const val NEW_CHAPTER_JINGLE = 136
        const val MEANWHILE_JINGLE = 135
        const val MISSION_OVER_JINGLE = 134

        val CHAPTER_ONE =
            Card(
                "Chapter 1: In which our hero is sent south",
                listOf(
                    "",
                    "King Narnode has lost his 10th squad. Caranock blames",
                    "the winds. Daero has other ideas, and a secret hangar",
                    "full of gliders that will not fly.",
                    "",
                    "Somewhere south of Karamja, monkeys are waiting.",
                ),
            )

        val CHAPTER_TWO =
            Card(
                "Chapter 2: In which our hero learns to speak monkey",
                listOf(
                    "",
                    "Ape Atoll does not welcome humans. To find the squad",
                    "and free them, our hero must become something the",
                    "monkeys will not shoot on sight.",
                    "",
                    "Zooknock has a plan. It involves teeth.",
                ),
            )

        val CHAPTER_THREE =
            Card(
                "Chapter 3: In which our hero engages in monkey business",
                listOf(
                    "",
                    "Disguised as a monkey, our hero must win the ear of",
                    "King Awowogei and learn what the monkeys intend.",
                    "",
                    "Diplomacy, it turns out, means a trip to the zoo.",
                ),
            )

        val CHAPTER_FOUR =
            Card(
                "Chapter 4: In which our hero fights a demon",
                listOf(
                    "",
                    "Glough and Awowogei have raised a Jungle Demon.",
                    "The 10th squad are ready. Zooknock has the sigil.",
                    "",
                    "There is nothing left to do but fight.",
                ),
            )
    }
}
