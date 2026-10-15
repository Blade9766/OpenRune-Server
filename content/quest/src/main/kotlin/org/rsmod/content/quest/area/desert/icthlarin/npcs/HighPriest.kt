package org.rsmod.content.quest.area.desert.icthlarin.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.HOLY_SYMBOL
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.SPHINX_TOKEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_CEREMONY_COMPLETE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FREED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_RETURNED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PREPARING_CEREMONY
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_DEFEATED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_RETURN_JAR
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_SPHINX_TOKEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_WOKE_IN_SOPHANEM
import org.rsmod.content.quest.area.desert.icthlarin.ilhGaveToken
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The new High Priest of Icthlarin, Klenter's successor. In the city he stands in the temple in
 * the south-west (`npc.ics_little_hipriest_town`, away for stages 16-24); in the pyramid he leads
 * the reconsecration ceremony (`npc.ics_little_hipriest_ceremony`, who can be spoken to from 23).
 */
class HighPriest @Inject constructor(private val quest: IcthlarinsLittleHelperQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TOWN_PRIEST) { startDialogue(it.npc) { townPriest() } }
        onOpNpcU(TOWN_PRIEST) {
            if (it.objType.id != SPHINX_TOKEN.asRSCM(RSCMType.OBJ)) {
                mes("Nothing interesting happens.")
                return@onOpNpcU
            }
            startDialogue(it.npc) { townPriest() }
        }
        onOpNpc1(CEREMONY_PRIEST) { startDialogue(it.npc) { ceremonyPriest() } }
    }

    private suspend fun Dialogue.townPriest() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> afterQuest()
            stage == STAGE_FREED -> finishQuest()
            stage == STAGE_PREPARING_CEREMONY -> preparations()
            stage == STAGE_JAR_RETURNED -> jarReturned()
            stage in STAGE_RETURN_JAR until STAGE_JAR_RETURNED -> returnTheJar()
            stage == STAGE_SPHINX_TOKEN -> sphinxSentMe()
            stage >= STAGE_WOKE_IN_SOPHANEM -> rebuff()
            else -> stranger()
        }
    }

    private suspend fun Dialogue.stranger() {
        chatPlayer(happy, "Hello.")
        chatNpc(
            neutral,
            "Greetings, traveller. Forgive me, but with these plagues upon the city I have no time to talk.",
        )
    }

    private suspend fun Dialogue.rebuff() {
        chatPlayer(happy, "Hello.")
        chatNpc(angry, "You! I will have no dealings with those who plunder the houses of the dead.")
        chatNpc(angry, "Get thee gone from my presence!")
    }

    private suspend fun Dialogue.sphinxSentMe() {
        chatPlayer(happy, "Hello.")
        chatNpc(angry, "You! I will have no dealings with those who plunder the houses of the dead.")
        chatPlayer(worried, "Wait! The Sphinx told me that I should talk to you.")
        chatNpc(
            quiz,
            "The Sphinx? Is it possible? That contrary old cat has never done anything to help us. Prove it!",
        )
        if (SPHINX_TOKEN !in player.inv) {
            chatPlayer(sad, "She gave me a token to show you, but I lost it.")
            chatNpc(angry, "Well then stop wasting my time! Can't you see I'm trying to put a stop to these plagues?")
            return
        }
        if (access.invDel(access.inv, SPHINX_TOKEN).failure) {
            return
        }
        player.ilhGaveToken = true
        objbox(SPHINX_TOKEN, "You hand the token to the High Priest.")
        chatNpc(shocked, "Well this is... unexpected...")
        chatNpc(neutral, "So what do you want from me, thief?")
        chatPlayer(neutral, "I appreciate it doesn't look great right now, but I'm not actually a bad person.")
        chatNpc(angry, "Well you could have fooled me! Good people don't rob from the dead!")
        chatPlayer(
            worried,
            "It wasn't me! Well... it was, I guess. I didn't do it on purpose though. I was hypnotised!",
        )
        chatNpc(quiz, "Hypnotised?")
        chatPlayer(
            worried,
            "Yes! There was this wanderer! She had me break into that pyramid to steal... a burial " +
                "jar... I think. It's all still a bit confusing.",
        )
        chatNpc(
            worried,
            "This is all a disaster! The plagues were bad enough, but now we've got hypnotised " +
                "adventurers plundering tombs! I've only just started this job as well!",
        )
        chatPlayer(quiz, "You do seem to be going through a rough patch. Do you know what caused the plagues?")
        chatNpc(
            sad,
            "We fear they are a punishment sent by the gods. Perhaps we have displeased Icthlarin in " +
                "some way. Maybe I am to blame. All this only started after I became High Priest.",
        )
        chatPlayer(
            neutral,
            "Well I'm not sure if I can help with your plagues, but I need to fix whatever mess I " +
                "caused in that pyramid. Can you help me?",
        )
        chatNpc(
            neutral,
            "Oh, er... I suppose the burial jar you took must be returned as soon as possible. That " +
                "will allow my predecessor to rest once more.",
        )
        quest.advanceTo(access, STAGE_RETURN_JAR)
        chatPlayer(quiz, "I can do that. Do you have any advice for me when it comes to the pyramid?")
        chatNpc(neutral, "I don't think so... No wait! There is something important.")
        chatNpc(
            neutral,
            "As with many of our tombs, the pyramid has magical seals to protect it from the " +
                "Devourer, the goddess of destruction. The fact that you were able to get in before " +
                "means that you must have a cat.",
        )
        chatPlayer(quiz, "Well... yes. But how did you know that?")
        chatNpc(
            neutral,
            "Cats act as guardians against the Devourer. I'm surprised the Sphinx didn't tell you " +
                "this. As such, only a cat can open the pyramid door.",
        )
        chatPlayer(neutral, "I see. I guess that makes sense.")
        topics(jarStillMissing = true)
    }

    private suspend fun Dialogue.returnTheJar() {
        chatPlayer(happy, "Hello.")
        val jar = quest.jar(player)
        if (jar != null && jar.obj !in player.inv && jar.obj !in access.bank) {
            chatNpc(
                neutral,
                "One of my priests found this lying outside the pyramid. Please try to be more " +
                    "respectful of our dead.",
            )
            access.invAdd(access.inv, jar.obj)
            objbox(jar.obj, "The High Priest gives you a burial jar.")
            chatPlayer(sad, "Oops. I'll make sure I look after it this time. Sorry.")
        } else {
            chatNpc(quiz, "Have you managed to return the jar yet?")
            chatPlayer(neutral, "No, not yet.")
            chatNpc(worried, "Please hurry. The people are beginning to... well...")
            chatPlayer(quiz, "What? Question your sanity?")
            chatNpc(sad, "No, just my authority. Don't forget that to enter the pyramid, you'll need a cat.")
        }
        topics(jarStillMissing = true)
    }

    private suspend fun Dialogue.jarReturned() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Have you managed to return the jar yet?")
        chatPlayer(happy, "It's done.")
        chatNpc(happy, "Wonderful! Now we can prepare for the ceremony.")
        chatPlayer(confused, "Er... ceremony?")
        chatNpc(neutral, "The ceremony to reconsecrate Klenter's tomb of course!")
        chatPlayer(angry, "It would have been nice for you to mention this earlier.")
        chatNpc(sad, "Sorry, with all this plague business, it must have slipped my mind.")
        chatPlayer(quiz, "You're the High Priest of Icthlarin, but you forgot a key ceremony?")
        chatNpc(angry, "Well if you want to try running a city with multiple plagues, you're welcome to take over.")
        chatPlayer(neutral, "That's not what I... Never mind.")
        chatNpc(neutral, "Good. Now, this ceremony is very dangerous, so we must ensure everything is done correctly.")
        chatPlayer(quiz, "Dangerous? How?")
        chatNpc(neutral, "Well the pyramid has these magical seals on it...")
        chatPlayer(neutral, "... to protect it from the Devourer. Yes, you told me earlier.")
        chatNpc(
            neutral,
            "Right! Of course I did. Well for us to properly reconsecrate the tomb, we must first " +
                "remove those seals. That means that once we begin, the pyramid will be vulnerable " +
                "until the ceremony is complete.",
        )
        chatPlayer(neutral, "Ah, I see.")
        chatNpc(
            neutral,
            "Now, we made many of the preparations while you were gone, but the carpenter and the " +
                "embalmer could both use a hand. Could you go and speak with them?",
        )
        chatPlayer(quiz, "Why do I have to help?")
        chatNpc(angry, "Did you forget that this is all your fault?")
        chatPlayer(neutral, "I guess I assumed that by returning the jar I stole, my job would be done.")
        chatNpc(neutral, "The job will be done when Klenter is at rest once more.")
        chatNpc(
            neutral,
            "Besides, thanks to the plagues, we're not allowing people to leave the city. However, as " +
                "you seem to be unaffected, I've told the guards to make an exception for you.",
        )
        chatPlayer(neutral, "And that means I'm best placed to help. Fine. The carpenter and the embalmer, did you say?")
        chatNpc(
            neutral,
            "That's right. We need the carpenter to create a new holy symbol for the ceremony. He " +
                "lives over to the east. The embalmer also needs help gathering some supplies. He " +
                "lives just south of here.",
        )
        quest.advanceTo(access, STAGE_PREPARING_CEREMONY)
        topics(jarStillMissing = false)
    }

    private suspend fun Dialogue.preparations() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Have you helped the embalmer and the carpenter yet?")
        chatPlayer(neutral, "Not yet.")
        chatNpc(
            worried,
            "The embalmer lives just south of here and the carpenter lives over to the east. Please " +
                "hurry. We need to get this ceremony performed before some other bad thing happens.",
        )
        topics(jarStillMissing = false)
    }

    private suspend fun Dialogue.finishQuest() {
        chatNpc(
            happy,
            "Ah, there you are. You were gone for quite a while. As a thank you for what you've done, " +
                "I have a little trinket for you. I'm sure you'll get a huge amount of enjoyment out of it.",
        )
        access.ifClose()
        quest.complete(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Hello again, adventurer. Thanks to you, Klenter can finally rest.")
        topics(jarStillMissing = false)
    }

    /** The High Priest's questions, asked in any order until the player takes their leave. */
    private suspend fun Dialogue.topics(jarStillMissing: Boolean) {
        while (true) {
            val topic =
                if (jarStillMissing) {
                    choice5(
                        "Why is it so important that the jar be returned?", TOPIC_JAR,
                        "Can you tell me more about the plagues.", TOPIC_PLAGUES,
                        "Can you tell me more about Icthlarin.", TOPIC_ICTHLARIN,
                        "Can you tell me more about the Devourer.", TOPIC_DEVOURER,
                        "I'd better get going.", TOPIC_LEAVE,
                    )
                } else {
                    choice4(
                        "Can you tell me more about the plagues.", TOPIC_PLAGUES,
                        "Can you tell me more about Icthlarin.", TOPIC_ICTHLARIN,
                        "Can you tell me more about the Devourer.", TOPIC_DEVOURER,
                        "I'd better get going.", TOPIC_LEAVE,
                    )
                }
            when (topic) {
                TOPIC_JAR -> whyTheJar()
                TOPIC_PLAGUES -> plagues()
                TOPIC_ICTHLARIN -> icthlarin()
                TOPIC_DEVOURER -> devourer()
                else -> {
                    chatPlayer(neutral, "I'd better get going.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whyTheJar() {
        chatPlayer(quiz, "Why is it so important that the jar be returned?")
        chatNpc(
            neutral,
            "Well those jars are a key part of our burial practices. To remove one is to directly " +
                "insult not just Icthlarin, but all those that have passed on to the next life.",
        )
        chatNpc(
            angry,
            "Not to mention, it wasn't just any jar you took. You stole a jar from the tomb of our " +
                "former High Priest! Why do you think his ghost is now wandering the city once more?",
        )
        chatPlayer(quiz, "But what will happen if I don't return the jar?")
        chatNpc(
            worried,
            "Have you not noticed the plagues already filling our city? How long before Klenter " +
                "calls upon Icthlarin to send a few more?",
        )
        chatNpc(angry, "For all we know, the plagues we already have could be punishment for your grave robbing!")
        chatPlayer(
            angry,
            "Hey, don't go blaming me for that. Those plagues were here before I even met that wanderer!",
        )
        chatNpc(quiz, "Perhaps Icthlarin foresaw what would happen and sent the plagues as a pre-emptive punishment?")
        chatPlayer(quiz, "You really think he'd do that?")
        chatNpc(
            sad,
            "I don't know... Icthlarin is a patient and forgiving god. It does not make sense to me " +
                "that he would do this to us.",
        )
        chatPlayer(neutral, "Maybe he's not the one responsible.")
        chatNpc(neutral, "Perhaps. Either way, that jar needs to be returned.")
    }

    private suspend fun Dialogue.plagues() {
        chatPlayer(quiz, "Can you tell me more about the plagues.")
        chatNpc(
            sad,
            "They started a few days ago. We have locusts in the east of the city and frogs in the " +
                "west. The cows have started to produce sour milk, and all of us have become infected " +
                "with some sort of pox.",
        )
        chatNpc(
            sad,
            "Once it became clear how bad it was, Menaphos, our neighbouring city, closed their gates " +
                "to us and everyone else. We closed our own gates soon after.",
        )
        chatPlayer(neutral, "That can't have been an easy choice to make.")
        chatNpc(
            neutral,
            "I didn't want to, but the other priests insisted. We had to stop the plagues from " +
                "spreading. It seems that our choice was the right one. So far, the plagues have " +
                "remained contained, but at a great cost.",
        )
        chatNpc(
            sad,
            "Many of our citizens are trapped outside the city. Some are stuck in Menaphos, with more " +
                "in other parts of the desert.",
        )
        chatNpc(
            sad,
            "However, that may well be for the best. Without trade from other settlements, we've been " +
                "struggling to even feed the few of us left in the city.",
        )
        chatPlayer(quiz, "How much longer do you think you can last?")
        chatNpc(worried, "Not long. We need to find a way to stop the plagues soon.")
    }

    private suspend fun Dialogue.icthlarin() {
        chatPlayer(quiz, "Can you tell me more about Icthlarin.")
        chatNpc(
            neutral,
            "Icthlarin is the son of Tumeken and Elidinis and brother to the Devourer. He is the " +
                "Menaphite god of the dead. He is responsible for ensuring the safe passage of all " +
                "souls from this life to the next.",
        )
        chatNpc(
            neutral,
            "Here in Sophanem, we support Icthlarin in his role by making sure the dead are suitably " +
                "prepared for their journey.",
        )
        chatPlayer(quiz, "So is Icthlarin only responsible for those that die in the desert?")
        chatNpc(
            neutral,
            "No. While Icthlarin is only worshipped here, his responsibilities stretch across all of " +
                "Gielinor. He cares not for an individual's beliefs. All who pass on, enter his care.",
        )
        chatPlayer(neutral, "I imagine there are plenty of people out there who have a different take on things.")
        chatNpc(neutral, "Which they are welcome to. It does not change the facts.")
        chatPlayer(quiz, "You seem very sure in these 'facts'.")
        chatNpc(
            neutral,
            "While most of the gods, Tumeken and Elidinis included, may be long gone from Gielinor, " +
                "Icthlarin remains. His important role in caring for the dead means that he is still " +
                "able to interact with us.",
        )
        chatPlayer(quiz, "So you've met him?")
        chatNpc(
            neutral,
            "Well... no. While he is able to walk among us if he desires, he chooses not to so as to " +
                "respect our way of life. Not to mention, his role requires his constant attention.",
        )
        chatPlayer(neutral, "I see. Fair enough.")
    }

    private suspend fun Dialogue.devourer() {
        chatPlayer(quiz, "Can you tell me more about the Devourer.")
        chatNpc(
            neutral,
            "Long ago, back when Tumeken and Elidinis still walked these lands, they had two " +
                "children. Icthlarin, god of the dead, was one. The other was Amascut, the goddess of rebirth.",
        )
        chatNpc(
            neutral,
            "For a time, Icthlarin and Amascut worked together in harmony. Icthlarin would ensure most " +
                "souls are safely guided to the next life, while Amascut would select a small number " +
                "to be reborn.",
        )
        chatPlayer(quiz, "But something happened?")
        chatNpc(
            sad,
            "Yes. During a great war over these lands, Amascut became corrupted. She was warped into " +
                "the Devourer, becoming the goddess of destruction.",
        )
        chatNpc(sad, "Since then, her only goal has been to devour every soul in existence, both living and dead.")
        chatPlayer(neutral, "The Sphinx told me something similar, but her interpretation was a bit different.")
        chatNpc(
            neutral,
            "We here have a lot of respect for the Sphinx. However, you should be careful of her " +
                "words. She does not know the gods like we priests do.",
        )
        chatPlayer(quiz, "So is the Devourer still around?")
        chatNpc(
            worried,
            "Unfortunately so. Most gods have long since left this world. However, the Devourer was " +
                "able to find a way to remain.",
        )
        chatNpc(
            worried,
            "Luckily for us, some of her former priestesses were able to weaken her, but she still " +
                "poses a great threat.",
        )
    }

    /** The ceremony High Priest, in the eastern chamber of the pyramid. */
    private suspend fun Dialogue.ceremonyPriest() {
        val stage = quest.stage(player)
        if (stage < STAGE_PRIEST_DEFEATED) {
            return
        }
        if (stage >= STAGE_CEREMONY_COMPLETE) {
            chatNpc(
                neutral,
                "We have a few things to finish here, but then we'll head back up into the city. " +
                    "Please join us there.",
            )
            return
        }
        chatNpc(happy, "Thank you so much, adventurer. Thanks to you, we were able to complete the ceremony.")
        chatPlayer(
            neutral,
            "Well it was the least I could do. After all, it was sort of my fault all this happened.",
        )
        chatNpc(shocked, "Well on that note, what exactly did happen there? That was the Devourer!")
        chatPlayer(neutral, "It seems the wanderer that hypnotised me was actually the Devourer all along.")
        chatPlayer(
            neutral,
            "I'm guessing the reason she had me steal that burial jar was because she knew you'd have " +
                "to gather here to reconsecrate the tomb again.",
        )
        chatNpc(shocked, "She wanted to kill us all in one go?")
        chatPlayer(neutral, "Yes, and she had me plant an unholy symbol here to allow her to teleport right in.")
        chatNpc(
            happy,
            "Well once again, thank you. Without you, the Devourer would have struck a devastating " +
                "blow against Icthlarin.",
        )
        if (HOLY_SYMBOL in player.inv) {
            chatPlayer(happy, "Here's that holy symbol, by the way.")
            access.invDel(access.inv, HOLY_SYMBOL)
            objbox(HOLY_SYMBOL, "You give the holy symbol to the High Priest.")
            chatNpc(happy, "Thank you. We wouldn't have been able to complete the ceremony without that in our presence.")
        }
        quest.advanceTo(access, STAGE_CEREMONY_COMPLETE)
        chatPlayer(quiz, "So what now?")
        chatNpc(
            neutral,
            "We have a few things to finish here, but then we'll head back up into the city. Please " +
                "join us there.",
        )
    }

    private companion object {
        const val TOWN_PRIEST = "npc.ics_little_hipriest_town"
        const val CEREMONY_PRIEST = "npc.ics_little_hipriest_ceremony"

        const val TOPIC_JAR = 1
        const val TOPIC_PLAGUES = 2
        const val TOPIC_ICTHLARIN = 3
        const val TOPIC_DEVOURER = 4
        const val TOPIC_LEAVE = 5
    }
}
