package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.DreamChallenge
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.ASTRAL_REWARD
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.ASTRAL_RUNE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.LUNAR_STAFF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_AT_LUNAR_ISLE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_CHALLENGES
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_DEFEATED_SELF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ENTERED_TOWN
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ENTER_DREAM
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_EQUIPMENT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_FACE_SELF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_IN_DREAM
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_MET_ONEIROMANCER
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_POTION
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_STAFF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarPiece
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarTravel
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarWasInDream
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Oneiromancer, who oversees the Moon Clan's initiation beside the Astral altar. She guides
 * the player through the waking sleep potion, the Lunar staff and the ceremonial clothing, keeps
 * each piece safe as it is brought in, and hands the whole set back with the magic kindling when
 * it is complete. She has a spare of anything lost on the way to the dream.
 */
class Oneiromancer
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val travel: LunarTravel,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(ONEIROMANCER) {
            if (with(travel) { expelWithoutSeal(it.npc) }) {
                return@onOpNpc1
            }
            startDialogue(it.npc) { talk() }
        }
    }

    private suspend fun Dialogue.talk() {
        val stage = lunar.stage(player)
        when {
            lunar.isComplete(player) -> afterQuest()
            stage < STAGE_AT_LUNAR_ISLE -> {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "Hello, traveller. I sense you have a long way still to go.")
            }
            stage <= STAGE_ENTERED_TOWN -> introduction()
            stage == STAGE_MET_ONEIROMANCER -> {
                chatPlayer(quiz, "Hi, what was I supposed to be doing again?")
                chatNpc(
                    neutral,
                    "Seek out Baba Yaga in her chicken house in the north of our town. She can " +
                        "help you with the potion of waking sleep.",
                )
            }
            stage == STAGE_POTION -> potion()
            stage == STAGE_STAFF -> staff()
            stage == STAGE_EQUIPMENT -> equipment()
            stage == STAGE_DEFEATED_SELF -> finish()
            else -> dreaming(stage)
        }
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Why hello, how are you today?")
        chatPlayer(neutral, "I'm...")
        chatNpc(neutral, "Fine. Yes, I know.")
        chatPlayer(shocked, "How did you...")
        chatNpc(
            neutral,
            "I read your mind. But that isn't important; we have things to discuss. Tell me why " +
                "you have come to our island.",
        )
        chatPlayer(
            neutral,
            "I'm hoping to settle the feud between your clan and the Fremennik. As one of them, I " +
                "came to see if something could be sorted out.",
        )
        chatNpc(quiz, "A very honourable aim, but how do you suppose that is possible?")
        chatPlayer(quiz, "I hoped you could tell me. Why don't your people get on with the Fremennik?")
        chatNpc(
            sad,
            "We are deeply in tune with magic, but we have yet to meet a Fremennik who sees any " +
                "value in it. They pester us for our secrets while sneering at our ways.",
        )
        chatPlayer(
            neutral,
            "I think they'd be more flexible than you imagine. They just feel you look down on them " +
                "from a high pedestal.",
        )
        chatNpc(
            neutral,
            "Hmm. Well, you are one of them. If you can come to understand our ways, that would at " +
                "least be a start.",
        )
        chatPlayer(happy, "I'm always eager to learn. What did you have in mind?")
        chatNpc(
            neutral,
            "We have a ritual, the 'View of Self Dream', that each of us undertakes. The path to " +
                "magic is through understanding ourselves, and our dreams are the truest view of " +
                "who we are.",
        )
        chatNpc(neutral, "Most of us prepare for years before attempting it.")
        chatPlayer(worried, "I'd rather not wait years. Could I try it now?")
        chatNpc(
            neutral,
            "I doubt you'll achieve much, but you may try. You'll need a waking sleep potion, a " +
                "Lunar staff and our ceremonial clothing.",
        )
        chatNpc(
            neutral,
            "Start with the potion. Baba Yaga will help you make it; you'll find her chicken house " +
                "walking around the north of the town.",
        )
        chatPlayer(confused, "Her... chicken house?")
        lunar.advanceTo(access, STAGE_MET_ONEIROMANCER)
        chatNpc(happy, "You can't miss it.")
    }

    private suspend fun Dialogue.potion() {
        chatPlayer(happy, "Hello again.")
        chatNpc(quiz, "Have you got the potion for me?")
        if (!player.inv.contains(FULL_VIAL)) {
            chatPlayer(sad, "No, sorry.")
            chatNpc(neutral, "Baba Yaga should be able to help you with that.")
            return
        }
        chatPlayer(happy, "I have indeed.")
        access.invDel(access.inv, FULL_VIAL)
        lunar.advanceTo(access, STAGE_STAFF)
        chatNpc(happy, "Well done. I'll keep that safe for you.")
        chatPlayer(quiz, "So what next?")
        staffInstructions()
    }

    private suspend fun Dialogue.staffInstructions() {
        chatNpc(
            neutral,
            "Next, the staff. Bring a Dramen staff, such as grows beneath Entrana, to the air, " +
                "fire, water and earth altars, in that order, and use it on each to imbue it with " +
                "their power. It will become a Lunar staff.",
        )
        chatNpc(
            happy,
            "And don't worry, the enchanted staff will do everything the Dramen staff could, " +
                "including opening the way to the Lost City!",
        )
    }

    private suspend fun Dialogue.staff() {
        chatPlayer(happy, "Hi.")
        chatNpc(quiz, "Managed to get the Dramen staff enchanted?")
        if (!player.inv.contains(LUNAR_STAFF)) {
            chatPlayer(sad, "Not yet. What do I have to do again?")
            staffInstructions()
            return
        }
        chatPlayer(happy, "I have indeed!")
        access.invDel(access.inv, LUNAR_STAFF)
        lunar.staffHeld.set(player, true)
        lunar.advanceTo(access, STAGE_EQUIPMENT)
        chatNpc(happy, "Well done, pass it here. Now for the next stage: your suit. There are eight parts to it.")
        pieceMenu()
    }

    private suspend fun Dialogue.equipment() {
        chatPlayer(quiz, "Could you remind me what I'm supposed to be doing?")
        chatNpc(neutral, "You need to be making your Lunar clothes.")
        for (piece in LunarPiece.entries) {
            if (piece.given(player)) {
                continue
            }
            if (player.inv.contains(piece.obj)) {
                chatPlayer(happy, "I've got the Lunar ${piece.label}!")
                access.invDel(access.inv, piece.obj)
                piece.give(player)
                chatNpc(happy, "Well done, I'll take that for safe keeping.")
            } else if (access.bank.contains(piece.obj) || piece.obj in player.worn) {
                chatPlayer(neutral, "I've made the Lunar ${piece.label}, but I haven't got it with me.")
                chatNpc(neutral, "That's no good to me. Best you go and fetch it.")
            }
        }
        if (LunarPiece.entries.all { it.given(player) }) {
            handBack()
            return
        }
        pieceMenu()
    }

    private suspend fun Dialogue.handBack() {
        chatNpc(happy, "Ah, you have everything! Good work!")
        if (player.inv.freeSpace() < HAND_BACK_SLOTS) {
            chatNpc(
                neutral,
                "I can give you back all you need now, but you haven't the space. There are eleven " +
                    "items in all; come back when you have room.",
            )
            chatPlayer(confused, "How do you know how much space I have?")
            chatNpc(neutral, "You're forgetting I read minds.")
            return
        }
        for (piece in LunarPiece.entries) {
            access.invAdd(access.inv, piece.obj)
        }
        access.invAdd(access.inv, LUNAR_STAFF)
        access.invAdd(access.inv, KINDLING)
        access.invAdd(access.inv, FULL_VIAL)
        lunar.staffHeld.set(player, false)
        lunar.advanceTo(access, STAGE_ENTER_DREAM)
        dreamInstructions()
        chatPlayer(happy, "Thanks!")
    }

    private suspend fun Dialogue.dreamInstructions() {
        chatNpc(
            neutral,
            "Here is some magic kindling from the first magic tree that ever grew, and your " +
                "ceremonial clothes. Put them on, take hold of the Lunar staff and pour your potion " +
                "on the kindling.",
        )
        chatNpc(
            neutral,
            "Then burn it on the ceremonial brazier in the lodge on the west of the town. If you " +
                "have done it all correctly, you will be carried to the land of dreams. What happens " +
                "there depends on your understanding of yourself. Good luck!",
        )
    }

    private suspend fun Dialogue.pieceMenu() {
        var page = 0
        while (true) {
            val pieces = LunarPiece.entries.drop(page * PIECES_PER_PAGE).take(PIECES_PER_PAGE)
            val labels = pieces.map { label(it) }
            val last = page * PIECES_PER_PAGE + PIECES_PER_PAGE >= LunarPiece.entries.size
            val choice =
                if (last) {
                    choice4(labels[0], 0, labels[1], 1, "Previous", PREVIOUS, "Exit", EXIT)
                } else {
                    choice5(labels[0], 0, labels[1], 1, labels[2], 2, if (page == 0) "Exit" else "Previous", if (page == 0) EXIT else PREVIOUS, "Next", NEXT)
                }
            when (choice) {
                EXIT -> return
                NEXT -> page++
                PREVIOUS -> page--
                else -> describe(pieces[choice])
            }
        }
    }

    private fun Dialogue.label(piece: LunarPiece): String =
        if (piece.given(player)) "The ${piece.label} (complete!)" else "The ${piece.label}"

    private suspend fun Dialogue.describe(piece: LunarPiece) {
        chatPlayer(quiz, "The ${piece.label}.")
        if (piece.given(player)) {
            chatNpc(neutral, "Why do you want to know that? You've already given me the Lunar ${piece.label} to look after.")
            return
        }
        val text =
            when (piece) {
                LunarPiece.Helm ->
                    "The helm is smithed from an ore found only in the mine on this island. Mine " +
                        "one of the stalagmites down there."
                LunarPiece.Cape ->
                    "There's one of those floating about somewhere. Ask around the village; " +
                        "someone will be able to help."
                LunarPiece.Amulet ->
                    "Go and speak with Meteora. She always seems to wear one on her head, for " +
                        "some reason. She might part with it."
                LunarPiece.Ring ->
                    "Speak to Selene. She always has bits and pieces like that lying around."
                else ->
                    "The Suqah, the beasts native to this island, have hides we tan in this town. " +
                        "You'll need one to make your ${piece.label}."
            }
        chatNpc(neutral, text)
    }

    private suspend fun Dialogue.dreaming(stage: Int) {
        when {
            stage == STAGE_ENTER_DREAM && player.lunarWasInDream == 0 -> chatNpc(quiz, "You not been to the dreamland yet?")
            stage <= STAGE_IN_DREAM -> {
                chatPlayer(happy, "I've been to the dreamland!")
                chatNpc(quiz, "What happened there?")
                chatPlayer(sad, "Well, I didn't manage to do anything. I left straight away.")
                chatNpc(bored, "Well, that's no good.")
            }
            stage == STAGE_CHALLENGES && !DreamChallenge.allComplete(player) -> {
                chatPlayer(happy, "I'm making progress in the dreamland!")
                chatNpc(quiz, "Very good, but have you learnt anything yet?")
                chatPlayer(neutral, "We shall see.")
            }
            stage <= STAGE_FACE_SELF -> {
                chatPlayer(
                    happy,
                    "I've learned a variety of things in the dreamland, but there's one last thing " +
                        "I have to do.",
                )
                chatNpc(neutral, "Then go back and do it before you tell me what you've learnt.")
            }
        }
        replacements()
        chatPlayer(quiz, "What am I supposed to do again?")
        dreamInstructions()
    }

    private suspend fun Dialogue.replacements() {
        if (!owns(LUNAR_STAFF)) {
            chatPlayer(sad, "I've misplaced my staff.")
            chatNpc(neutral, "I knew you would, and used my magic to bring it back to me.")
            if (!giveReplacement(LUNAR_STAFF)) {
                return
            }
        }
        for (piece in LunarPiece.entries) {
            if (owns(piece.obj)) {
                continue
            }
            chatPlayer(sad, "I've lost my Lunar ${piece.label}.")
            chatNpc(neutral, SPARE_LINES.random())
            if (!giveReplacement(piece.obj)) {
                return
            }
        }
        if (lunar.stage(player) < STAGE_DEFEATED_SELF) {
            if (!owns(KINDLING) && !owns(SOAKED_KINDLING)) {
                chatPlayer(quiz, "Can I have some more kindling?")
                chatNpc(neutral, "Of course. I can't seem to get rid of the stuff!")
                if (!giveReplacement(KINDLING)) {
                    return
                }
            }
            if (!owns(FULL_VIAL) && !owns(SOAKED_KINDLING)) {
                chatPlayer(sad, "I've misplaced my potion of waking sleep.")
                chatNpc(happy, "Good job I have a spare, isn't it!")
                giveReplacement(FULL_VIAL)
            }
        }
    }

    private suspend fun Dialogue.giveReplacement(obj: String): Boolean {
        if (player.inv.freeSpace() == 0) {
            chatPlayer(sad, "Unfortunately I don't have the space to carry it.")
            return false
        }
        access.invAdd(access.inv, obj)
        objbox(obj, "The Oneiromancer hands you a replacement.")
        return true
    }

    private fun Dialogue.owns(obj: String): Boolean =
        player.inv.contains(obj) || obj in player.worn || access.bank.contains(obj)

    private suspend fun Dialogue.finish() {
        chatPlayer(happy, "I've done it! I understand now!")
        chatNpc(quiz, "Do you? I'm not sure I believe you. Tell me what you've learnt.")
        chatPlayer(
            neutral,
            "Make sense of others to understand yourself; be able to relate to others; use your " +
                "abilities to progress; know your past and present to plan your future; harness " +
                "your confidence; and appreciate the unknown.",
        )
        chatNpc(
            happy,
            "That's wonderful! I never imagined someone from the mainland could be so open-minded. " +
                "I shall spread the word among our people; I see no reason why we cannot begin to " +
                "get along.",
        )
        chatPlayer(
            happy,
            "If you reach out to the Fremennik and tell them you're willing to share, I think " +
                "you'll find plenty of eager ears.",
        )
        chatNpc(
            happy,
            "You must be rewarded for this. You may now use the altar behind me to craft the " +
                "astral runes we use here, which power a whole new set of spells. Pray at the altar " +
                "to gain the knowledge.",
        )
        chatPlayer(happy, "Thanks!")
        access.invAddOrDrop(objRepo, ASTRAL_RUNE, ASTRAL_REWARD)
        lunar.complete(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hi, how are things going?")
        chatNpc(
            happy,
            "Much better now that you've brought calm between the Moon Clan and the Fremennik. " +
                "Remember, pray at the altar beside me whenever you wish to use our Lunar spells.",
        )
        replacements()
        if (!choice2("Could I buy another Lunar staff?", true, "Goodbye.", false)) {
            return
        }
        chatNpc(neutral, "I can enchant another for you, but it will cost ${STAFF_PRICE_TEXT} coins.")
        if (!choice2("Yes, please.", true, "No, thanks.", false)) {
            return
        }
        if (player.inv.freeSpace() == 0 && player.inv.count(COINS) != STAFF_PRICE) {
            chatNpc(neutral, "You haven't the space to carry it.")
            return
        }
        if (access.invDel(access.inv, COINS, STAFF_PRICE).failure) {
            chatPlayer(sad, "I don't seem to have enough money.")
            return
        }
        access.invAdd(access.inv, LUNAR_STAFF)
        objbox(LUNAR_STAFF, "The Oneiromancer hands you a Lunar staff.")
    }

    private companion object {
        const val ONEIROMANCER = "npc.lunar_oneiromancer"

        const val FULL_VIAL = "obj.lunar_moonclan_liminal_vial_full"
        const val KINDLING = "obj.lunar_moonclan_kindling"
        const val SOAKED_KINDLING = "obj.lunar_moonclan_kindling_soaked"
        const val COINS = "obj.coins"

        const val HAND_BACK_SLOTS = 11
        const val PIECES_PER_PAGE = 3
        const val PREVIOUS = -1
        const val NEXT = -2
        const val EXIT = -3

        const val STAFF_PRICE = 30_000
        const val STAFF_PRICE_TEXT = "30,000"

        val SPARE_LINES =
            listOf(
                "I already knew that. I made a spare especially.",
                "Of course; I knew that already, so I kept a backup for you.",
                "Oh dear. Good job I saw that coming!",
                "I know. Good job I knew you would drop it!",
            )
    }
}
