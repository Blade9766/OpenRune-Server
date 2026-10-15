package org.rsmod.content.quest.area.rellekka.fremenniktrials.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.SEAL_OF_PASSAGE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_GOT_SEAL
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.BRUNDT
import org.rsmod.content.quest.area.rellekka.fremenniktrials.FremennikTrialsQuest.Companion.STAGE_ALL_VOTES
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantContact
import org.rsmod.content.quest.area.rellekka.fremenniktrials.MerchantTrial
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Brundt the Chieftain, who starts the trials in the longhall and welcomes the player at the end.
 * Once Lunar Diplomacy is under way he also hands out Seals of Passage.
 */
class Brundt
@Inject
constructor(
    private val quest: FremennikTrialsQuest,
    private val merchant: MerchantTrial,
    private val lunar: LunarDiplomacyQuest,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(BRUNDT) { startDialogue(it.npc) { talk() } }
    }

    private suspend fun Dialogue.talk() {
        when {
            quest.isComplete(player) && lunar.isStarted(player) -> {
                val seal = choice2("Ask about a Seal of Passage.", true, "Ask about anything else.", false)
                if (seal) sealOfPassage() else welcomed()
            }
            quest.isComplete(player) -> welcomed()
            quest.isStarted(player) -> {
                chatNpc(
                    happy,
                    "Greetings again outerlander! How goes your attempts to gain votes with the " +
                        "council of elders?",
                )
                with(merchant) { withMerchantOption(MerchantContact.Brundt) { progress() } }
            }
            else -> introduction()
        }
    }

    private suspend fun Dialogue.welcomed() {
        chatNpc(
            happy,
            "From this day onward, you are outerlander no more! In honour of your " +
                "acceptance into the Fremennik, you gain a new name to be known as. You will " +
                "now be called ${quest.fremennikName(player)}.",
        )
    }

    private suspend fun Dialogue.sealOfPassage() {
        val name = quest.fremennikName(player)
        val stage = lunar.stage(player)
        val holding = player.inv.contains(SEAL_OF_PASSAGE) || SEAL_OF_PASSAGE in player.worn
        when {
            stage == STAGE_STARTED -> {
                chatPlayer(quiz, "Brundt, could you help me out with a Seal of Passage?")
                chatNpc(confused, "A Seal of Passage? Whatever for?")
                chatPlayer(
                    neutral,
                    "I've heard of the troubles between your people and the Moon Clan. I'd like to " +
                        "help, and the seal would show that I come in peace.",
                )
                chatNpc(
                    sad,
                    "Ah, $name, many have tried. The Moon Clan do everything by magic, and guard " +
                        "their secrets jealously. If only they would share, we wouldn't have to " +
                        "keep fighting them!",
                )
                chatPlayer(neutral, "I'll see what I can do. Can I have that seal, then?")
                if (player.inv.freeSpace() == 0) {
                    chatNpc(neutral, "Yes, but you've no room to carry it. You should have thought of that first, $name!")
                    return
                }
                access.invAdd(access.inv, SEAL_OF_PASSAGE)
                lunar.advanceTo(access, STAGE_GOT_SEAL)
                chatNpc(neutral, "If you are to bring peace to our two clans, then perhaps it is best that you do...")
                objbox(SEAL_OF_PASSAGE, "Brundt hands you a Seal of Passage.")
            }
            lunar.isComplete(player) -> {
                chatNpc(happy, "Well, if it isn't $name! I've heard of your exploits on Lunar Isle!")
                chatPlayer(shocked, "Already? News really does travel fast!")
                chatNpc(happy, "Thank you. I think we can finally make some progress with them now.")
                if (!holding) {
                    chatPlayer(quiz, "Could you spare me another Seal of Passage?")
                    giveReplacement()
                }
            }
            holding -> {
                chatNpc(neutral, "Ah, $name. How go the peace talks with the accursed Moon Clan?")
                chatPlayer(shifty, "Oh, you know. Ongoing negotiations.")
                chatNpc(bored, "I see. Well, I don't hold out much hope!")
                chatPlayer(sad, "I'm glad you're so confident in me.")
            }
            else -> {
                chatPlayer(sad, "I've lost my Seal of Passage!")
                giveReplacement()
            }
        }
    }

    private suspend fun Dialogue.giveReplacement() {
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "I would give you another, but you've no room to carry it.")
            return
        }
        access.invAdd(access.inv, SEAL_OF_PASSAGE)
        chatNpc(neutral, "Then take this one, and be less careless in future.")
        objbox(SEAL_OF_PASSAGE, "Brundt hands you a Seal of Passage.")
    }

    private suspend fun Dialogue.progress() {
        val votes = quest.votes(player)
        if (quest.stage(player) >= STAGE_ALL_VOTES) {
            chatPlayer(happy, "I have seven members of the council prepared to vote in my favour now!")
            chatNpc(happy, "I know outerlander, for I have been closely monitoring your progress so far!")
            chatNpc(
                happy,
                "Then let us put the formality aside, and let me personally welcome you into the " +
                    "Fremennik! May you bring us honour!",
            )
            quest.complete(access)
            return
        }
        when (votes) {
            0 -> {
                chatPlayer(sad, "I don't have any votes yet.")
                chatNpc(
                    neutral,
                    "Then you had best get to it, outerlander. Seek out the members of the council, " +
                        "and see what they would have of you.",
                )
            }
            1 -> {
                chatPlayer(neutral, "I only have 1 vote so far.")
                chatNpc(
                    happy,
                    "Hmmm... well that is certainly a good start I would say. Keep up the good work!",
                )
            }
            else -> {
                chatPlayer(neutral, "I only have $votes votes so far.")
                chatNpc(
                    happy,
                    "Hmmm... you are doing very well so far, outerlander. Keep up the good work!",
                )
            }
        }
        chatNpc(
            neutral,
            "Remember: You need to get at least seven council votes to be accepted as a member of " +
                "the Fremennik.",
        )
        chatNpc(
            neutral,
            "If you need any help with your trials, I suggest you speak to Askeladden. He is " +
                "currently doing his own trials of manhood to become a true Fremennik.",
        )
    }

    private suspend fun Dialogue.introduction() {
        val option =
            choice3(
                "What is this place?",
                1,
                "Why will no-one talk to me?",
                2,
                "Do you have any quests?",
                3,
            )
        when (option) {
            1 -> whatIsThisPlace()
            2 -> whyWillNoOneTalk()
            else -> anyQuests()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            happy,
            "This place? Why, this is Rellekka! Homeland of all Fremennik! I do not recognise your " +
                "face outerlander; Where do you come from?",
        )
        chatPlayer(shifty, "That's not important...")
        chatNpc(
            neutral,
            "Hmmm... I will not press the issue then outerlander. How may my tribe and I help you?",
        )
        if (choice2("Do you have any quests?", true, "Why will no-one talk to me?", false)) {
            anyQuests()
        } else {
            whyWillNoOneTalk()
        }
    }

    private suspend fun Dialogue.whyWillNoOneTalk() {
        chatPlayer(quiz, "Why will no-one talk to me?")
        chatNpc(
            neutral,
            "Do not take it personally, outerlander! We are a simple people, and it is our " +
                "experience that keeping ourselves to ourselves is best. This is why speaking to " +
                "outerlanders is forbidden.",
        )
        chatNpc(
            neutral,
            "We do not wish to enter war with the outerlanders and their strange magics, so we " +
                "limit all unauthorised communication.",
        )
        chatPlayer(quiz, "Then how come you're talking to me?")
        chatNpc(
            neutral,
            "Ah, this is because I am the chieftain. I am the one who authorises contact. You will " +
                "not find many of my tribe so forthcoming with you, as I.",
        )
        chatPlayer(quiz, "Is there a way for you to authorise your tribe to talk to me then?")
        chatNpc(neutral, "Well, there is one way... but I doubt it is of any interest to you.")
        interested()
    }

    private suspend fun Dialogue.anyQuests() {
        chatPlayer(quiz, "Do you have any quests?")
        chatNpc(
            neutral,
            "Quests, you say outerlander? Well, I would not call it a quest as such, but if you are " +
                "brave of heart and strong of body, perhaps...",
        )
        chatNpc(neutral, "No, you would not be interested. Forget I said anything, outerlander.")
        val interested =
            choice2("Yes, I am interested.", true, "No, I'm not interested.", false)
        if (!interested) {
            chatPlayer(bored, "You're right. I really couldn't care less.")
            chatNpc(
                neutral,
                "It is as I thought outerlander. Your kind care nothing for the Fremennik, and we " +
                    "feel the same about you. This is the way of the world.",
            )
            return
        }
        interested()
    }

    private suspend fun Dialogue.interested() {
        chatPlayer(happy, "Actually, I would be very interested to hear what you have to offer.")
        chatNpc(
            happy,
            "You would? These are unusual sentiments to hear from an outerlander! My suggestion was " +
                "going to be that if you crave adventure and battle,",
        )
        chatNpc(
            happy,
            "and your heart sings for glory, then perhaps you would be interested in joining our " +
                "clan, and becoming a Fremennik yourself?",
        )
        chatPlayer(quiz, "What would that involve exactly?")
        chatNpc(
            neutral,
            "Well, there are two ways to become a member of our clan and call yourself a Fremennik: " +
                "be born a Fremennik, or be voted in by our council of elders.",
        )
        chatPlayer(
            quiz,
            "Well, I think I've missed the first way, but how can I get the council of elders to " +
                "vote to let me join your clan?",
        )
        chatNpc(
            neutral,
            "Well, that I cannot answer myself. You will need to speak to each of them and see what " +
                "they require of you as proof of your dedication.",
        )
        chatNpc(
            neutral,
            "There are twelve council members around this village; you will need to gain a " +
                "majority vote of at least seven councillors in your favour.",
        )
        chatNpc(
            neutral,
            "So what say you? Give me the word, and I will tell all of my tribe of your intentions, " +
                "be they yea or nay.",
        )
        val start = choice2("Yes.", true, "No.", false, title = "Start The Fremennik Trials quest?")
        if (!start) {
            chatPlayer(
                angry,
                "No way do I want to become like YOU! Forget you, and forget your whole stupid " +
                    "town! You're a bunch of stupid primitives, from what I've seen!",
            )
            chatNpc(
                sad,
                "I am sorry to say that I expected such a response from an outerlander. I thought " +
                    "perhaps you might have been different to all the others...",
            )
            return
        }
        chatPlayer(
            happy,
            "I think I would enjoy the challenge of becoming an honorary Fremennik. Where and how " +
                "do I start?",
        )
        quest.start(access)
        chatNpc(
            neutral,
            "As I say outerlander, you must find and speak to the twelve members of the council of " +
                "elders, and see what tasks they might set you.",
        )
        chatNpc(
            neutral,
            "If you can gain the support of seven of the twelve, then you will be accepted as one " +
                "of us without question.",
        )
    }
}
