package org.rsmod.content.quest.area.desert.princealirescue

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.ASHES
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BALL_OF_WOOL
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.JUG_OF_WATER
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.POT_OF_FLOUR
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.REDBERRIES
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.SKIN_PASTE
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.WIG
import org.rsmod.content.quest.area.desert.princealirescue.PrinceAliRescueQuest.Companion.WOOL_PER_WIG
import org.rsmod.game.entity.Player

/**
 * The disguise pieces made in Draynor Village while the rescue is being planned: Ned's wig and
 * Aggie's skin paste. Their own scripts offer these topics while [offers] holds.
 */
@Singleton
class DisguiseMakers @Inject constructor(private val princeAli: PrinceAliRescueQuest) {

    fun offers(player: Player): Boolean = princeAli.gatheringDisguise(player)

    suspend fun Dialogue.nedOtherThings() {
        chatPlayer(quiz, "Could you make other things apart from rope?")
        chatNpc(happy, "I'm sure I can. What are you thinking of?")
        when (
            choice4(
                "Could you knit me a sweater?", 1,
                "How about some sort of wig?", 2,
                "Could you repair the arrow holes in the back of my shirt?", 3,
                "Actually, I don't need anything.", 4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Could you knit me a sweater?")
                chatNpc(
                    angry,
                    "Do I look like a member of a sewing circle? Be off wi' you. I have fought " +
                        "monsters that would turn your hair blue.",
                )
                chatNpc(sad, "I don't need to be laughed at just 'cos I'm getting a bit old.")
            }
            2 -> wig()
            3 -> {
                chatPlayer(quiz, "Could you repair the arrow holes in the back of my shirt?")
                chatNpc(
                    neutral,
                    "Ah yes, it's a tough world these days. There's a few brave enough to attack " +
                        "from ten metres away.",
                )
                mesbox("Ned pulls out a needle and attacks your shirt.")
                chatNpc(happy, "There you go, good as new.")
                chatPlayer(happy, "Thanks Ned. Maybe next time they will attack me face to face.")
            }
            else -> chatPlayer(neutral, "Actually, I don't need anything.")
        }
    }

    private suspend fun Dialogue.wig() {
        chatPlayer(quiz, "How about some sort of wig?")
        chatNpc(
            neutral,
            "Well... that's an interesting thought. Yes, I think I could do something. Give me " +
                "three balls of wool and I might be able to do it.",
        )
        if (player.inv.count(BALL_OF_WOOL) < WOOL_PER_WIG) {
            chatPlayer(happy, "Great, I will get some. I think a wig would be useful.")
            return
        }
        when (
            choice2(
                "I have them here. Please make me a wig.", 1,
                "Actually, I don't need one right now.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I have them here. Please make me a wig.")
                chatNpc(happy, "Okay, I'll have a go.")
                if (access.invDel(access.inv, BALL_OF_WOOL, WOOL_PER_WIG).failure) {
                    return
                }
                access.invAdd(access.inv, WIG)
                objbox(WIG, "Ned gives you a pretty good wig.")
                chatNpc(happy, "Here you go. How's that for a quick effort? Not bad I think!")
                chatPlayer(happy, "Thanks Ned. There's more to you than meets the eye.")
            }
            else -> {
                chatPlayer(neutral, "Actually, I don't need one right now.")
                chatNpc(neutral, "Fair enough.")
            }
        }
    }

    suspend fun Dialogue.aggieSkinPaste() {
        chatPlayer(quiz, "Can you make skin paste?")
        val water = waterFor(player)
        val hasAll = water != null && ASHES in player.inv && POT_OF_FLOUR in player.inv && REDBERRIES in player.inv
        if (!hasAll) {
            chatNpc(
                happy,
                "Why, it's one of my most popular potions! Lots of people around here like to " +
                    "pretty their faces up a bit. I can make it for you if you get me what's needed.",
            )
            chatPlayer(quiz, "What do you need?")
            chatNpc(
                neutral,
                "Well dearie, you need a base for the paste. That's a mix of ash, flour and water. " +
                    "Then you need redberries to colour it as you want. Bring me those four items " +
                    "and I will make you some.",
            )
            return
        }
        chatNpc(
            happy,
            "Yes I can. I see you already have the ingredients. Would you like me to mix some for " +
                "you now?",
        )
        when (
            choice2(
                "Yes please. Mix me some skin paste.", 1,
                "No thank you. I don't need any skin paste right now.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes please. Mix me some skin paste.")
                chatNpc(happy, "That should be simple. Hand the things to Aggie then.")
                val inv = access.inv
                if (access.invDel(inv, ASHES, 1, POT_OF_FLOUR, 1).failure) return
                if (access.invDel(inv, checkNotNull(water)).failure) return
                if (access.invDel(inv, REDBERRIES).failure) return
                doubleobjbox(
                    REDBERRIES,
                    POT_OF_FLOUR,
                    "You hand the ash, flour, water and redberries to Aggie. She tips the " +
                        "ingredients into a cauldron and mutters some words.",
                )
                chatNpc(happy, "Tourniquet, Fenderbaum, Tottenham, Marshmallow, Marblearch.")
                access.invAdd(inv, SKIN_PASTE)
                objbox(SKIN_PASTE, "Aggie hands you the skin paste.")
                chatNpc(happy, "There you go dearie. That will make you look good at the Varrock dances.")
            }
            else -> {
                chatPlayer(neutral, "No thank you. I don't need any skin paste right now.")
                chatNpc(neutral, "Okay dearie, that's always your choice.")
            }
        }
    }

    private fun waterFor(player: Player): String? =
        when {
            BUCKET_OF_WATER in player.inv -> BUCKET_OF_WATER
            JUG_OF_WATER in player.inv -> JUG_OF_WATER
            else -> null
        }
}
