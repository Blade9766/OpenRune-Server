package org.rsmod.content.quest.area.lumbridge.losttribe

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_EMOTES_LEARNT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SHOWN_BROOCH
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.zone.ZoneKey

/**
 * What Reldo and the Goblin Village generals know about the Dorgeshuun. Their own scripts ask
 * [reldoOption] / [asksGenerals] whether to offer the topic and hand over to [reldoBrooch] / [dorgeshuunLegend].
 */
@Singleton
class LostTribeLore
@Inject
constructor(private val lostTribe: LostTribeQuest, private val npcRepo: NpcRepository) {

    fun reldoOption(player: Player): String? =
        if (lostTribe.stage(player) == STAGE_SHOWN_BROOCH && BROOCH in player.inv) {
            "What can you tell me about this brooch?"
        } else {
            null
        }

    suspend fun Dialogue.reldoBrooch() {
        chatPlayer(quiz, "What can you tell me about this brooch?")
        chatNpc(confused, "I've never seen that symbol before. Where did you find it?")
        chatPlayer(neutral, "In a cave beneath Lumbridge.")
        chatNpc(quiz, "Very odd. Have you any idea how it got there?")
        chatPlayer(neutral, "A goblin might have dropped it.")
        chatNpc(confused, "I've never heard of a goblin carrying a brooch like this. But just a minute...")
        chatNpc(
            happy,
            "The other day I filed a book about ancient goblin tribes. It's somewhere at the west end of the " +
                "library, I think. Maybe that will be of some use.",
        )
    }

    fun asksGenerals(player: Player): Boolean = lostTribe.stage(player) == STAGE_READ_BOOK

    /**
     * The generals' argument about the Dorgeshuun legend. Each menu keeps offering its side
     * questions until the player moves the story on; "previous" questions step back a menu.
     */
    suspend fun Dialogue.dorgeshuunLegend() {
        chatPlayer(quiz, "Have you ever heard of the Dorgeshuun?")
        bent(neutral, "Dorgeshuun? That old goblin legend. Lost tribe.")
        wart(
            neutral,
            "In time of much-war there many tribes. Big High War-God send Dorgesh tribe to fight " +
                "beardy-short-people in mountains.",
        )
        bent(angry, "No no, he send them to fight tall people with biting blades, that what I hear.")
        wart(angry, "It was beardy-short-people, that how legend go.")
        var step = 1
        val asked = mutableSetOf<Int>()
        while (true) {
            step =
                when (step) {
                    1 -> whoTheyFought(asked)
                    2 -> whyPunished(asked)
                    3 -> theirPunishment(asked)
                    4 -> greetings(asked)
                    else -> return
                }
        }
    }

    private suspend fun Dialogue.whoTheyFought(asked: MutableSet<Int>): Int {
        val options = buildList {
            if (1 !in asked) add("Make up your minds!" to 1)
            if (2 !in asked) add("Do you want me to decide for you again?" to 2)
            add("It doesn't really matter" to 3)
        }
        when (val picked = menu(options)) {
            1 -> {
                asked += picked
                chatPlayer(angry, "Make up your minds!")
                bent(angry, "It was tall people! That what old storyteller say!")
                wart(angry, "No, beardy-short-people. That how legend always go, stupid.")
                bent(angry, "You stupid!")
            }
            2 -> {
                asked += picked
                chatPlayer(quiz, "Do you want me to decide for you again?")
                bent(
                    angry,
                    "That most stupid idea yet. If it happen it either happen one way or the other. You " +
                        "can't just decide which.",
                )
                wart(neutral, "Yeah even goblins know that.")
            }
            3 -> {
                chatPlayer(neutral, "It doesn't really matter. What happened to them?")
                wart(neutral, "Well, they say, 'We not want to fight,' so god punish them.")
                bent(laugh, "That silly. No goblin ever say that.")
                wart(quiz, "What happen then?")
                bent(neutral, "They lose battle, that why god punish them.")
                wart(laugh, "Ha ha! If they lost battle they all be dead!")
                bent(angry, "No no, they losing so they run away, and Big High War-God punish them for running away.")
                return 2
            }
        }
        return 1
    }

    private suspend fun Dialogue.whyPunished(asked: MutableSet<Int>): Int {
        val options = buildList {
            add("Who were they fighting again?" to 10)
            if (11 !in asked) add("What kind of god would punish them for refusing to fight?" to 11)
            add("Well either way they refused to fight" to 12)
        }
        when (val picked = menu(options)) {
            10 -> {
                chatPlayer(quiz, "Who were they fighting again?")
                wart(neutral, "It was beardy-short-people in mountains.")
                bent(angry, "No no, it was tall people.")
                return 1
            }
            11 -> {
                asked += picked
                chatPlayer(quiz, "What kind of god would punish them for refusing to fight?")
                bent(shocked, "Shh! Must not question Big High War-God!")
                wart(worried, "No, because he much bigger than us.")
            }
            12 -> {
                chatPlayer(neutral, "Well either way they refused to fight.")
                wart(neutral, "Yeah I suppose so.")
                bent(neutral, "Anyway then Big High War-God punish them. He turn their insides to stone.")
                wart(laugh, "Ha ha ha! That silly!")
                bent(angry, "Not as silly as green armour! What happen then?")
                wart(neutral, "He put them inside stone! Big hole open in ground and they all go into cave.")
                bent(laugh, "That not punishment! Caves nice and cool.")
                wart(neutral, "But then he close cave so they not get out! You not want to stay in cave all the time.")
                return 3
            }
        }
        return 2
    }

    private suspend fun Dialogue.theirPunishment(asked: MutableSet<Int>): Int {
        val options = buildList {
            add("Why were they punished again?" to 20)
            if (21 !in asked) add("So do goblins make their own tunnels?" to 21)
            add("Well I found a brooch underground..." to 22)
        }
        when (val picked = menu(options)) {
            20 -> {
                chatPlayer(quiz, "Why were there punished again?")
                wart(neutral, "It because they run away from fight.")
                bent(angry, "No, it because they refuse to fight night before battle.")
                return 2
            }
            21 -> {
                asked += picked
                chatPlayer(quiz, "So do goblins make their own tunnels?")
                bent(confused, "What? Make our own tunnels?")
                wart(confused, "We hadn't thought of that.")
            }
            22 -> {
                chatPlayer(
                    neutral,
                    "Well I found a brooch underground, and I looked up the symbol and it was the symbol of " +
                        "the Dorgeshuun.",
                )
                bent(confused, "That not look like goblin brooch.")
                wart(neutral, "Goblins not wear jewellery.")
                bent(
                    happy,
                    "Well if it Dorgeshuun tribe they not know who won big wars. You should greet them with " +
                        "goblin victory dance!",
                )
                wart(angry, "No no, you greet them with goblin tribal bow.")
                bent(angry, "Doing bow make you look like a wimp.")
                wart(neutral, "It how tribes greet each other in old days.")
                bent(angry, "Only if they wimpy tribes! Goblin salute strong!")
                return 4
            }
        }
        return 3
    }

    private suspend fun Dialogue.greetings(asked: MutableSet<Int>): Int {
        val learnt = lostTribe.stage(player) >= STAGE_EMOTES_LEARNT
        val options = buildList {
            add("So why is the Dorgeshuun tribe underground?" to 30)
            add("Wait, you say YOU won the big wars?" to 31)
            if (learnt) {
                add("Thanks" to 33)
            } else {
                add("Well why not show me both greetings?" to 32)
            }
        }
        when (menu(options)) {
            30 -> {
                chatPlayer(quiz, "So why is the Dorgeshuun tribe underground?")
                bent(neutral, "They punished by Big High War-God.")
                return 3
            }
            31 -> {
                chatPlayer(quiz, "Wait, you say YOU won the big wars?")
                bent(happy, "Well we must have done! We goblins! Goblins always win!")
                wart(confused, "Actually we not know who won.")
                bent(angry, "It was goblins I say!")
            }
            32 -> {
                chatPlayer(quiz, "Well why not show me both greetings?")
                wart(happy, "That good idea. Watch.")
                showGreetings(access)
                mesbox("The goblins show you the Goblin Victory Salute and Goblin Bow.")
                lostTribe.advanceTo(access, STAGE_EMOTES_LEARNT)
            }
            33 -> {
                chatPlayer(happy, "Thanks.")
                bent(neutral, "Bye then.")
                wart(neutral, "Bye.")
                return 0
            }
        }
        return 4
    }

    private suspend fun showGreetings(access: ProtectedAccess) {
        val nearby = npcRepo.findAll(ZoneKey.from(access.coords), 1).toList()
        nearby.firstOrNull { it.isGeneral(BENTNOZE) }?.anim(GOBLIN_DANCE_SEQ)
        nearby.firstOrNull { it.isGeneral(WARTFACE) }?.anim(GOBLIN_BOW_SEQ)
        access.delay(GREETING_TICKS)
    }

    private fun Npc.isGeneral(name: String): Boolean = type.name == name

    private suspend fun Dialogue.wart(mood: MesAnimType, text: String) =
        chatNpcSpecific("General Wartface", WARTFACE_HEAD, mood, text)

    private suspend fun Dialogue.bent(mood: MesAnimType, text: String) =
        chatNpcSpecific("General Bentnoze", BENTNOZE_HEAD, mood, text)

    private companion object {
        const val WARTFACE = "General Wartface"
        const val BENTNOZE = "General Bentnoze"
        const val WARTFACE_HEAD = "npc.general_wartface_green"
        const val BENTNOZE_HEAD = "npc.general_bentnoze_red"
        const val GOBLIN_BOW_SEQ = "seq.surface_goblin_update_bow"
        const val GOBLIN_DANCE_SEQ = "seq.surface_goblin_update_dance"
        const val GREETING_TICKS = 3
    }
}
