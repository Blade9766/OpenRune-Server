package org.rsmod.content.quest.area.karamja.shilovillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloCoords
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloUndead
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.MOSOL_REI
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.WAMPUM_BELT
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mosol Rei, the jungle warrior holding the undead inside Shilo Village. He sends the player to
 * Trufitus with a Wampum belt, and once Rashiliyia is at rest he leads them into the village.
 * His cache npc switches to a post-quest form with Follow on op 1 and Talk-to on op 3, so both
 * ops are registered on the multi npc.
 */
class MosolRei
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val undead: ShiloUndead,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MOSOL_REI) {
            if (shilo.isComplete(player)) {
                leadIntoVillage(it.npc)
            } else {
                talk(it.npc)
            }
        }
        onOpNpc3(MOSOL_REI) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(mosol: Npc) {
        var ambush = false
        startDialogue(mosol) {
            when {
                !shilo.completedJunglePotion(player) ->
                    chatNpc(neutral, "Sorry Bwana, I cannot help you at this time. Go and talk to Trufitus at Tai Bwo Wannai, I believe he needs some help.")
                shilo.isComplete(player) -> postQuest()
                shilo.stage(player) > 0 || access.hasBelt() -> askedAboutBelt()
                else -> {
                    mesbox("Mosol seems to be looking around very cautiously. He jumps a little when you approach and talk to him.")
                    chatNpc(worried, "Run! Run for your life! Save yourself! I'll keep them back as long as I can...")
                    ambush = firstMenu()
                }
            }
        }
        if (ambush) {
            mosol.say("Arrggghhh, here are some now!")
            mes("Mosol Rei: Arrggghhh, here are some now!")
            with(undead) { raiseUndeadOnes(AMBUSH_DURATION) }
        }
    }

    private suspend fun Dialogue.firstMenu(): Boolean =
        when (
            choice4(
                "Why do I need to run?", 1,
                "Yeah... Ok, I'm running!", 2,
                "Who are you?", 3,
                "Ok. Thanks for your help.", 4,
            )
        ) {
            1 -> whyRun()
            2 -> {
                chatPlayer(happy, "Yeah... Ok, I'm running!")
                chatNpc(neutral, "God speed to you my friend.")
                false
            }
            3 -> whoAreYou()
            else -> thanks()
        }

    private suspend fun Dialogue.whyRun(): Boolean {
        chatPlayer(quiz, "Why do I need to run?")
        chatNpc(worried, "Your very life is in danger. Rashiliyia has returned and we are all doomed.")
        return when (
            choice3(
                "Rashiliyia? Who is she?", 1,
                "What danger is there around here?", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> whoIsShe()
            2 -> danger()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.whoIsShe(): Boolean {
        chatPlayer(quiz, "Rashiliyia? Who is she?")
        chatNpc(
            neutral,
            "Rashiliyia is the Queen of the dead. She has returned and has brought a plague of undead " +
                "with her. They now occupy our village and we have them trapped. I warn people like " +
                "yourself to stay away.",
        )
        return when (
            choice3(
                "What can we do?", 1,
                "Uh, it sounds nasty, just the kind of thing I want to avoid.", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> whatCanWeDo()
            2 -> {
                chatPlayer(worried, "Uh, it sounds nasty, just the kind of thing I want to avoid!")
                chatNpc(sad, "Quite right, Bwana, please make all haste! Before your spine turns to water as we speak.")
                false
            }
            else -> thanks()
        }
    }

    private suspend fun Dialogue.whatCanWeDo(): Boolean {
        chatPlayer(quiz, "What can we do?")
        chatNpc(
            neutral,
            "We're doing all we can to keep the undead at bay. The village is covered in a deadly " +
                "green mist. If you go into the village, a terrible sickness will befall you.",
        )
        chatNpc(
            neutral,
            "And the undead are even stronger beyond the gates. My guess is that it has something to " +
                "do with the Legend of Rashiliyia.",
        )
        chatNpc(
            neutral,
            "But you would need to talk to the Shaman in Tai Bwo Wannai village to get more details " +
                "about that. I really have to go now and fight these undead.",
        )
        return when (
            choice5(
                "Why are the undead here?", 1,
                "What can we do?", 2,
                "Can I help in any way?", 3,
                "I'll go to see the Shaman.", 4,
                "Ok. Thanks for your help.", 5,
            )
        ) {
            1 -> whyUndead()
            2 -> whatCanWeDo()
            3 -> canIHelp()
            4 -> goToShaman()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.canIHelp(): Boolean {
        chatPlayer(quiz, "Can I help in any way?")
        chatNpc(
            neutral,
            "I don't think so Bwana, but you may want to consult with Trufitus the Shaman in Tai Bwo " +
                "Wannai village, he may have some information which could help.",
        )
        return when (
            choice4(
                "Why are the undead here?", 1,
                "What can we do?", 2,
                "I'll go to see the Shaman.", 3,
                "Ok. Thanks for your help.", 4,
            )
        ) {
            1 -> whyUndead()
            2 -> whatCanWeDo()
            3 -> goToShaman()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.whyUndead(): Boolean {
        chatPlayer(quiz, "Why are the undead here?")
        chatNpc(
            neutral,
            "Rashiliyia! The Queen of the undead has risen! She is the mother of the undead creatures " +
                "that roam this land. But alas I know nothing of the legend that surrounds her.",
        )
        return when (
            choice3(
                "Legend you say?", 1,
                "I don't think this is something I can help with at the moment.", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> legend()
            2 -> {
                chatPlayer(neutral, "Sorry, but I don't think this is something that I can help with at the moment!")
                chatNpc(neutral, "Ok, I understand Bwana! You may as well be on your way then.")
                false
            }
            else -> thanks()
        }
    }

    private suspend fun Dialogue.legend(): Boolean {
        chatPlayer(quiz, "Legend you say?")
        chatNpc(angry, "Yes.... I said it is a legend that I know nothing about.")
        return when (
            choice4(
                "Oh, ok, sorry for bothering you.", 1,
                "Oh come on, you must know something.", 2,
                "Maybe you know someone who does know something?", 3,
                "Ok. Thanks for your help.", 4,
            )
        ) {
            1 -> sorryForBothering()
            2 -> mustKnowSomething()
            3 -> someoneWhoKnows()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.mustKnowSomething(): Boolean {
        chatPlayer(neutral, "Oh come on, you must know something?")
        mesbox("Mosol lowers his head in deep concentration.")
        chatNpc(confused, "Well, let me think now...")
        mesbox("He scratches his head...")
        chatNpc(confused, "Hmmm, there was something I think that might help...")
        mesbox("Mosol strains to remember something...")
        chatNpc(sad, "Nope, sorry. It's gone.")
        return when (
            choice3(
                "Maybe you know someone who does know something?", 1,
                "Oh, Ok, sorry for bothering you.", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> someoneWhoKnows()
            2 -> sorryForBothering()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.someoneWhoKnows(): Boolean {
        chatPlayer(quiz, "Maybe you know someone who does know something?")
        chatNpc(
            neutral,
            "My guess is that this has something to do with the legend of Rashiliyia. But you need " +
                "to speak to the Shaman in 'Tai Bwo Wannai' village to get more details about that.",
        )
        chatNpc(worried, "I really have to fight these undead now Bwana, before they take over the world!")
        return when (
            choice3(
                "Oh, ok, sorry for bothering you.", 1,
                "I'll go to see the Shaman.", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> sorryForBothering()
            2 -> goToShaman()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.sorryForBothering(): Boolean {
        chatPlayer(neutral, "Oh, Ok, sorry for bothering you.")
        chatNpc(neutral, "Ok, perhaps you'd like to be on your way now?")
        return false
    }

    private suspend fun Dialogue.goToShaman(): Boolean {
        chatPlayer(neutral, "I'll go to see the Shaman.")
        val alreadyHasBelt = access.hasBelt() || shilo.stage(player) > 0
        if (alreadyHasBelt) {
            chatNpc(
                neutral,
                "Well, if you go to see the Shaman, please give him that Wampum belt I gave to you, it " +
                    "will give him more details about our situation down here.",
            )
        } else {
            chatNpc(
                neutral,
                "Well, that would be helpful Bwana. If you're sure you want to go, you can take a Wampum " +
                    "belt to him for me. It will give the Shaman, Trufitus the story of our problems down " +
                    "here. Are you sure you want to go?",
            )
        }
        return when (
            choice3(
                "Errr, I'm having second thoughts now.", 1,
                "Yes, I'm sure and I'll take the Wampum belt to Trufitus.", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> {
                chatPlayer(worried, "Errr, I'm having second thoughts now.")
                chatNpc(neutral, "That's Ok Bwana, it's a big responsibility. Come back and see me if you change your mind.")
                false
            }
            2 -> takeBelt()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.takeBelt(): Boolean {
        chatPlayer(neutral, "Yes, I'm sure and I'll take the Wampum belt to Trufitus.")
        if (access.hasBelt() || shilo.stage(player) > 0) {
            chatNpc(
                happy,
                "That's great Bwana, if you give Trufitus the Wampum belt I gave you, it will give him " +
                    "more details of our current situation.",
            )
            return false
        }
        chatNpc(happy, "I would be very grateful if you did. Here take this...")
        access.invAddOrDrop(objRepo, WAMPUM_BELT)
        objbox(WAMPUM_BELT, "Mosol gives you a Wampum belt.")
        chatNpc(
            neutral,
            "Please can you give it to Trufitus. He may be able to give you extra details on the legend " +
                "surrounding Rashiliyia. Good luck!",
        )
        return false
    }

    private suspend fun Dialogue.whoAreYou(): Boolean {
        chatPlayer(quiz, "Who are you?")
        chatNpc(
            neutral,
            "I am Mosol Rei, I am a Jungle Warrior. I used to live in this village, but it is too " +
                "dangerous for you to stay around here.",
        )
        return when (
            choice3(
                "Mosol Rei, that's a nice name.", 1,
                "What danger is there around here?", 2,
                "Ok. Thanks for your help.", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Mosol Rei, that's a nice name.")
                mesbox("Mosol looks at you and shakes his head in bewilderment.")
                chatNpc(confused, "Thanks, but you really should leave.")
                false
            }
            2 -> danger()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.danger(): Boolean {
        chatPlayer(quiz, "What danger is there around here?")
        chatNpc(shocked, "Can you not see Bwana? This whole area is infested with the living dead.")
        return true
    }

    private suspend fun Dialogue.thanks(): Boolean {
        chatPlayer(neutral, "Ok. Thanks for your help.")
        chatNpc(neutral, "You're welcome Bwana, good luck.")
        return false
    }

    private suspend fun Dialogue.askedAboutBelt() {
        chatNpc(quiz, "Hey there Bwana, have you delivered that Wampum Belt to Trufitus yet?")
        when (choice2("Yes, of course!", 1, "Not yet, I've been busy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes, of course!")
                chatNpc(
                    neutral,
                    "Well hopefully Trufitus will come up with some good ideas on how to resolve this problem " +
                        "with Rashiliyia. You should talk to him if you really want to help.",
                )
            }
            else -> {
                chatPlayer(neutral, "Not yet, I've been busy.")
                chatNpc(
                    neutral,
                    "If you can take it to him quickly, it would help me out a lot. If not, please can you " +
                        "give it back to me so that I can send a scout to deliver it.",
                )
            }
        }
        when (choice2("Ok. Thanks for your help.", 1, "Why are the undead here?", 2)) {
            1 -> thanks()
            else -> whyUndead()
        }
    }

    private suspend fun Dialogue.postQuest() {
        chatPlayer(happy, "Greetings!")
        chatNpc(
            happy,
            "Greetings Bwana! We've removed the threat of Rashiliyia and though there are still some " +
                "random outbreaks of undead activity we are more than able to deal with it.",
        )
        chatNpc(happy, "You're welcome to enter the village now Bwana, shall I show you the way?")
        val lead =
            choice2(
                "Yes, I'll give it a go!", true,
                "I think I'll see it some other time.", false,
                title = "You can now enter Shilo Village.",
            )
        if (!lead) {
            access.mes("You decide not to visit the village.")
            return
        }
        mesbox("Mosol leads you into the village.")
        access.walkIntoVillage()
    }

    private suspend fun ProtectedAccess.leadIntoVillage(mosol: Npc) {
        faceSquare(mosol.coords)
        mesbox("Mosol leads you into the village.")
        walkIntoVillage()
    }

    private suspend fun ProtectedAccess.walkIntoVillage() {
        fadeToBlack()
        telejump(ShiloCoords.INSIDE_VILLAGE, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        mes("Mosol leaves you by the gate and walks back out into the jungle.")
    }

    private fun ProtectedAccess.hasBelt(): Boolean = player.inv.contains(WAMPUM_BELT) || bank.contains(WAMPUM_BELT)

    private companion object {
        const val AMBUSH_DURATION = 200
    }
}
