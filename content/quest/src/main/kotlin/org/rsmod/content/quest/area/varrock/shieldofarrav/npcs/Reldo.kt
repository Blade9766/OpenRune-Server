package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BOOK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_READ_BOOK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_STARTED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TOLD_BY_RELDO
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.reldoMet
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Reldo, the palace librarian. He starts Shield of Arrav, and once the player has read the book
 * points them at both gangs. `npc.reldo` is the spawned multinpc (its A Tail of Two Cats form is
 * `reldo_normal` until that quest), so the op is bound to the base name.
 */
class Reldo @Inject constructor(private val arrav: ShieldOfArravQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(RELDO) { startDialogue(it.npc) { reldo() } }
    }

    private suspend fun Dialogue.reldo() {
        greet()
        val stage = player.phoenixGang
        val quest =
            when {
                !arrav.isStarted(player) -> Topic.Quest
                arrav.isComplete(player) -> null
                stage == PHOENIX_STARTED && access.inv.count(BOOK) == 0 -> Topic.WhereIsBook
                stage == PHOENIX_STARTED -> Topic.FoundBook
                stage == PHOENIX_READ_BOOK -> Topic.ReadBook
                heldShieldHalf() != null -> Topic.HalfShield
                else -> null
            }
        val picked =
            if (quest == null) {
                choice2("Do you have anything to trade?", Topic.Trade, "I'd better get going.", Topic.Leave)
            } else {
                choice3(
                    quest.option,
                    quest,
                    "Do you have anything to trade?",
                    Topic.Trade,
                    "I'd better get going.",
                    Topic.Leave,
                )
            }
        when (picked) {
            Topic.Quest -> offerQuest()
            Topic.WhereIsBook -> {
                chatPlayer(quiz, Topic.WhereIsBook.option)
                chatNpc(neutral, "I couldn't tell you exactly... but it's somewhere in this library, I'm sure of it.")
            }
            Topic.FoundBook -> {
                chatPlayer(happy, Topic.FoundBook.option)
                chatNpc(happy, "Splendid. Have a read of it, then.")
            }
            Topic.ReadBook -> readBook()
            Topic.HalfShield -> halfShield()
            Topic.Trade -> trade()
            Topic.Leave -> {
                chatPlayer(neutral, "I'd better get going.")
                chatNpc(neutral, "Until next time.")
            }
        }
    }

    private suspend fun Dialogue.greet() {
        if (player.reldoMet) {
            chatNpc(neutral, "Hello there, ${player.displayName}.")
            return
        }
        chatNpc(neutral, "Hello, stranger.")
        chatPlayer(happy, "Hello! I'm ${player.displayName}. And you are?")
        chatNpc(neutral, "Reldo. I look after the palace library.")
        chatPlayer(neutral, "Ah, so that's why you're in the library.")
        chatNpc(
            happy,
            "Between you and me, I'd be in here even if it weren't my job. I love to read, and " +
                "even more to share what I've read with anyone who'll listen.",
        )
        chatPlayer(happy, "That's a fine ambition!")
        player.reldoMet = true
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(neutral, Topic.Quest.option)
        chatNpc(neutral, "Hmm... I can't think of any around here.")
        chatNpc(confused, "Wait, let me think...")
        chatNpc(happy, "Ah, yes. There might be something after all, if you're really interested?")
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT.",
            )
        }
        if (!choice2("Yes.", true, "No.", false, title = "Start the Shield of Arrav quest?")) {
            chatPlayer(neutral, "On second thoughts, I don't think I am. Goodbye.")
            return
        }
        chatPlayer(happy, "Definitely.")
        arrav.start(access)
        chatNpc(
            neutral,
            "Then look around for a book called The Shield of Arrav. You'll find your quest " +
                "inside it.",
        )
        chatNpc(neutral, "Where it's got to I couldn't say... but it's in here somewhere.")
        chatPlayer(happy, "Great! Thanks.")
    }

    private suspend fun Dialogue.readBook() {
        chatPlayer(neutral, Topic.ReadBook.option)
        chatNpc(quiz, "So, have you found your quest?")
        chatPlayer(
            quiz,
            "I think so. Do you know where I could find the Phoenix Gang, or the Black Arm Gang?",
        )
        chatNpc(
            neutral,
            "Not personally. But I hear Baraek, the fur trader in the market, has had dealings " +
                "with the Phoenix Gang.",
        )
        chatPlayer(quiz, "And the Black Arm Gang?")
        player.phoenixGang = PHOENIX_TOLD_BY_RELDO
        chatNpc(
            neutral,
            "Rumour has it they're based in the south-west of the city. Charlie the Tramp knows " +
                "that part of Varrock better than anyone; he may be able to tell you more.",
        )
        chatPlayer(happy, "Thanks! I'll get on it.")
        chatNpc(happy, "Good luck.")
    }

    private suspend fun Dialogue.halfShield() {
        chatPlayer(happy, Topic.HalfShield.option)
        chatNpc(shocked, "Half?")
        chatPlayer(neutral, "Yes. It looks like it was broken in two.")
        chatNpc(quiz, "I see... and where did this half turn up?")
        if (heldShieldHalf() == PHOENIX_SHIELD) {
            chatPlayer(
                happy,
                "The Phoenix Gang had it. I got myself into their gang and took it from them.",
            )
            chatNpc(neutral, "A gang split in two and a shield split in two. I'd wager the other gang has the rest.")
            chatPlayer(
                worried,
                "I doubt the Black Arm Gang will have me now that I've joined their rivals, but " +
                    "maybe somebody else can get in.",
            )
        } else {
            chatPlayer(
                happy,
                "The Black Arm Gang had it. I got myself into their gang and took it from them.",
            )
            chatNpc(neutral, "A gang split in two and a shield split in two. I'd wager the other gang has the rest.")
            chatPlayer(
                neutral,
                "A member of the Phoenix Gang helped me get in. Maybe they can find the other " +
                    "half.",
            )
        }
        chatNpc(
            neutral,
            "Let's hope so. Meanwhile, you should take that half to the museum so they can check " +
                "it's genuine.",
        )
    }

    private suspend fun Dialogue.trade() {
        chatPlayer(quiz, "Do you have anything to trade?")
        chatNpc(neutral, "Only knowledge.")
        chatPlayer(quiz, "What do you want for that, then?")
        chatNpc(laugh, "Oh, nothing - that was just a little joke. I'm not really the trading sort.")
        chatPlayer(neutral, "Oh well.")
    }

    private fun Dialogue.heldShieldHalf(): String? =
        listOf(PHOENIX_SHIELD, BLACKARM_SHIELD).firstOrNull { access.inv.count(it) > 0 }

    private enum class Topic(val option: String) {
        Quest("I'm in search of a quest."),
        WhereIsBook("About that book... where is it again?"),
        FoundBook("I found that book."),
        ReadBook("I've read that book about the Shield of Arrav."),
        HalfShield("I've found half of the Shield of Arrav!"),
        Trade(""),
        Leave(""),
    }

    private companion object {
        const val RELDO = "npc.reldo"
    }
}
