package org.rsmod.content.quest.area.karamja.shilovillage.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.junglepotion.JungleHerb
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest
import org.rsmod.content.quest.area.karamja.junglepotion.TrufitusJunglePotion
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BEADS_OF_THE_DEAD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BERVIRIUS_NOTES
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_BEADS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_SHARD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CRUMPLED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.LOCATING_CRYSTAL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_DUG_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_BERVIRIUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_FOUND_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_LEFT_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_LIT_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ROPED_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_SEARCHED_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STONE_PLAQUE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.SWORD_POMMEL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TATTERED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TRUFITUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.WAMPUM_BELT
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ZADIMUS_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.owns
import org.rsmod.content.quest.area.karamja.shilovillage.ownsSwordPommel
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Trufitus, the shaman of Tai Bwo Wannai, who deciphers everything the player brings back from
 * Ah Za Rhoon and the Tomb of Bervirius. While Jungle Potion owns the conversation it is handed to
 * [TrufitusJunglePotion].
 */
class Trufitus
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val junglePotion: JunglePotionQuest,
    private val junglePotionTalk: TrufitusJunglePotion,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TRUFITUS) { startDialogue(it.npc) { talk() } }
        onOpNpcU(TRUFITUS) { useOnTrufitus(it.npc, it.objType.internalName) }
    }

    /**
     * Jungle Potion talks first until its closing blessing has been heard. A player who never did it
     * on a server that assumes it complete still reaches the Shilo Village story once Mosol Rei has
     * involved them.
     */
    private fun junglePotionOwnsTalk(player: Player): Boolean =
        when {
            junglePotion.isInProgress(player) -> true
            junglePotion.isComplete(player) -> !junglePotion.heardFinalBlessing.get(player)
            else -> !shilo.completedJunglePotion(player) || !shiloUnderway(player)
        }

    private fun shiloUnderway(player: Player): Boolean =
        shilo.stage(player) > 0 || shilo.isComplete(player) || player.inv.contains(WAMPUM_BELT)

    private suspend fun Dialogue.talk() {
        if (junglePotionOwnsTalk(player)) {
            with(junglePotionTalk) { talk() }
            return
        }
        if (!shilo.completedJunglePotion(player)) {
            chatNpc(happy, "Greetings Bwana! I am Trufitus Shakaya of the Tai Bwo Wannai village.")
            chatNpc(happy, "Welcome to our humble village.")
            return
        }
        if (shilo.isComplete(player)) {
            postQuest()
            return
        }
        when (shilo.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED, STAGE_FOUND_MOUND -> askAboutSearch()
            STAGE_SEARCHED_MOUND -> {
                greetAway()
                chatPlayer(neutral, "I think I found something, but I am not sure what it is.")
                chatNpc(neutral, "Well, investigate it further, you may find something interesting!")
            }
            STAGE_DUG_MOUND -> {
                greetAway()
                chatPlayer(neutral, "I think I have found the temple of Ah Za Rhoon.")
                chatPlayer(quiz, "I excavated a hole and found a fissure. Should I go through it?")
                chatNpc(neutral, "Well, I would certainly investigate it..")
                fissureQuestions()
            }
            STAGE_LIT_MOUND, STAGE_ROPED_MOUND -> {
                greetAway()
                chatPlayer(
                    neutral,
                    "I think I have found the temple of Ah Za Rhoon. I excavated a hole and illuminated " +
                        "it, there is a fissure with a large drop.",
                )
                chatNpc(
                    neutral,
                    "Perhaps you should explore it? If it's the temple of Ah Za Rhoon, it may be our only " +
                        "chance against Rashiliyia. I implore you to investigate Bwana.",
                )
            }
            STAGE_ENTERED_TEMPLE, STAGE_LEFT_TEMPLE -> {
                greetAway()
                chatPlayer(happy, "I think I found the temple of Ah Za Rhoon.")
                chatNpc(happy, "Well that sounds great Bwana. Tell me, what did you find?")
                when (
                    choice4(
                        "I have some items that I need help with.", 1,
                        "I need help with Zadimus.", 2,
                        "I need help with Rashiliyia.", 3,
                        "I need some help with the Temple of Ah Za Rhoon.", 4,
                    )
                ) {
                    1 -> items()
                    2 -> zadimus()
                    3 -> helpRashiliyia()
                    else -> helpTemple()
                }
            }
            STAGE_ENTERED_BERVIRIUS -> {
                chatPlayer(neutral, "Greetings...")
                chatNpc(quiz, "Greetings Bwana, did you find Bervirius' Tomb?")
                when (
                    choice4(
                        "I think I found Bervirius' Tomb.", 1,
                        "I have some items that I need help with.", 2,
                        "I need some help with the Temple of Ah Za Rhoon.", 3,
                        "No, I didn't find a thing.", 4,
                    )
                ) {
                    1 -> berviriusTomb()
                    2 -> items()
                    3 -> helpTemple()
                    else -> didntFind()
                }
            }
            else -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(
                    neutral,
                    "Greetings again Bwana. I hope that you have managed to locate Rashiliyia's Tomb. " +
                        "Again, if you found anything interesting, please show it to me.",
                )
                if (choice2("What should I do now?", 1, "Thanks!", 2) == 2) {
                    thanks()
                    return
                }
                chatPlayer(quiz, "What should I do now?")
                mesbox("Trufitus scratches his head.")
                chatNpc(
                    neutral,
                    "Well Bwana, if you have Rashiliyia's remains, you need to find a way to put her " +
                        "spirit to rest. Perhaps there was a clue with one of the artefacts that you have?",
                )
                chatNpc(
                    neutral,
                    "Why not have a look through the artefacts that you have found and see if there is some " +
                        "clue that might help? If you do not have her remains, you will need to find them.",
                )
            }
        }
    }

    private suspend fun Dialogue.greetAway() {
        chatPlayer(neutral, "Greetings...")
        chatNpc(
            worried,
            "Greetings Bwana, you have been away! The situation with Rashiliyia is worsening! I pray " +
                "that you have some good news for me.",
        )
    }

    private suspend fun Dialogue.postQuest() {
        when (shilo.postQuestChats.get(player)) {
            0 -> {
                chatPlayer(neutral, "Greetings.")
                shilo.postQuestChats.set(player, 1)
                chatNpc(
                    happy,
                    "Hello Bwana. I conclude that you have been successful. Mosol sent word that the " +
                        "village is clearing of zombies. You have done us all a great deed!",
                )
            }
            1 -> {
                chatPlayer(neutral, "Hello!")
                shilo.postQuestChats.set(player, 2)
                chatNpc(
                    happy,
                    "Hello again Bwana! Well Done again for helping to defeat Rashiliyia. Hopefully things " +
                        "will return to normal around here now.",
                )
            }
            else -> {
                chatPlayer(neutral, "Hello Bwana!")
                chatNpc(
                    neutral,
                    "Greetings! I hope things are going well for you now. I have no new information since " +
                        "last we spoke. Needless to say, that if something does come up I will certainly get " +
                        "in touch directly.",
                )
            }
        }
    }

    /* Before the quest */

    private suspend fun Dialogue.notStarted() {
        if (!player.inv.contains(WAMPUM_BELT)) {
            chatNpc(neutral, "Greetings once again Bwana,")
            chatNpc(neutral, "I have no more news since we last spoke.")
            return
        }
        chatPlayer(neutral, "Greetings.")
        chatNpc(neutral, "Greetings Bwana! You look like you have some serious news.")
        chatPlayer(
            worried,
            "Well, I think I may have. I have just spoken to Mosol Rei and he says that Rashiliyia has " +
                "returned...",
        )
        chatNpc(worried, "Oh dear, it is more serious than I have imagined.")
        when (
            choice3(
                "What do you know about Rashiliyia?", 1,
                "What do you know about Mosol Rei?", 2,
                "Mosol gave me something to show you.", 3,
            )
        ) {
            1 -> rashiliyia()
            2 -> mosolRei()
            else -> {
                chatPlayer(neutral, "Mosol gave me something to show you.")
                chatNpc(neutral, "Oh yes, let me see it then.")
                objbox(WAMPUM_BELT, "You show Trufitus the Wampum belt, he studies it for a long time.")
                chatNpc(worried, "Yes, things do look very bad indeed.")
                rashiliyiaOrLegend()
            }
        }
    }

    private suspend fun Dialogue.rashiliyiaOrLegend() {
        when (choice2("What do you know about Rashiliyia?", 1, "Mosol Rei said something about a legend?", 2)) {
            1 -> rashiliyia()
            else -> legend()
        }
    }

    private suspend fun Dialogue.rashiliyia() {
        chatPlayer(quiz, "What do you know about Rashiliyia?")
        chatNpc(
            neutral,
            "Hmmm, it's been a long time since I heard that name. Rashiliyia is the Queen of the Undead. " +
                "And a more fearsome enemy you will be unlikely to find.",
        )
        chatNpc(
            worried,
            "I fear that you bring me news that she has returned to plague us once again? Alas I know of " +
                "no weakness that she has.",
        )
        when (
            choice3(
                "So there is nothing we can do?", 1,
                "Should I start to evacuate the island?", 2,
                "Mosol Rei said something about a legend?", 3,
            )
        ) {
            1 -> {
                chatPlayer(sad, "So there is nothing we can do?")
                chatNpc(sad, "Not that I can think of.")
                when (choice2("Oh, ok!", 1, "Should I start to evacuate the Island?", 2)) {
                    1 -> ohOk()
                    else -> evacuate()
                }
            }
            2 -> evacuate()
            else -> legend()
        }
    }

    private suspend fun Dialogue.ohOk() {
        chatPlayer(neutral, "Oh, ok!")
        chatNpc(sad, "Yes, it's a bit sad really, I liked that village.")
        mesbox("Trufitus seems deeply touched...")
        chatNpc(neutral, "Well, I hope you will excuse me, but I need to get back to my studies.")
    }

    private suspend fun Dialogue.evacuate() {
        chatPlayer(quiz, "Should I start to evacuate the island?")
        chatNpc(worried, "Yes, that may be a good idea. Many people could die! If only there was a way to defeat her!")
        when (choice2("Mosol Rei said something about a legend?", 1, "Will you pack your things now?", 2)) {
            1 -> legend()
            else -> {
                chatPlayer(quiz, "Will you pack your things now?")
                chatNpc(
                    neutral,
                    "I will wait and see what will happen. Maybe Rashiliyia does not have the power to strike " +
                        "too far from her resting place? But there are many things that I need to do now.",
                )
                when (choice2("Is her resting place important?", 1, "Oh, ok!", 2)) {
                    1 -> restingPlace()
                    else -> ohOk()
                }
            }
        }
    }

    private suspend fun Dialogue.restingPlace() {
        chatPlayer(quiz, "Is her resting place important?")
        chatNpc(
            neutral,
            "I believe it is! It might be that her physical remains are the focal point of her supernatural " +
                "powers. It is said that many years ago, a group of adventurers once infiltrated her tomb to " +
                "try to rid the world of Rashiliyia.",
        )
        chatNpc(neutral, "These adventurers reported seeing a wraith- like creature.")
        chatNpc(
            neutral,
            "Although the adventurers disturbed Rashiliyia's bones, they were not able to properly sanctify " +
                "them. And this is the most likely reason why she still plagues us today.",
        )
        chatNpc(
            neutral,
            "Of course, she only has to order one of her minions to move her bones and she can quite " +
                "quickly and easily set up a new headquarters anywhere and continue to launch her plague of " +
                "undead.",
        )
        when (
            choice3(
                "What are minions?", 1,
                "What are onions?", 2,
                "Does she have any weaknesses?", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What are minions?")
                chatNpc(
                    neutral,
                    "Minions are the fiendish undead creatures that she controls. She has very few living " +
                        "worshippers, but they need to be dealt with at some point.",
                )
                chatNpc(
                    neutral,
                    "Usually a strong creature of some sort will be guarding her remains. And of course, she " +
                        "is a very powerful spell caster herself. Not to be tackled lightly.",
                )
                when (choice2("Thanks for the information!", 1, "Does she have any weaknesses?", 2)) {
                    1 -> thanksForInformation()
                    else -> weaknesses()
                }
            }
            2 -> {
                chatPlayer(quiz, "What are onions?")
                mesbox("Trufitus looks at you blankly")
                chatNpc(confused, "Surely you mean Minions?")
                chatPlayer(neutral, "Yes of course, I mean Minions, what made you think I said Onions?")
                mesbox("Trufitus frowns at you but continues about...minions...")
                chatNpc(
                    neutral,
                    "Minions are the fiendish undead creatures that Rashiliyia controls. She has very few " +
                        "living worshippers, but they need to be dealt with at some point.",
                )
                chatNpc(
                    neutral,
                    "Usually a strong creature of some sort will be guarding the bones and it is not to be " +
                        "tackled lightly.",
                )
            }
            else -> weaknesses()
        }
    }

    private suspend fun Dialogue.weaknesses() {
        chatPlayer(quiz, "Does she have any weaknesses?")
        chatNpc(
            neutral,
            "I am not sure, but the legend about her certainly is long. It's a pity that the temple of Ah " +
                "Za Rhoon has crumbled as there may be some clues that could help us to defeat her.",
        )
        chatNpc(neutral, "I think the largest problem will be in locating her resting place.")
        when (choice2("Why was it called Ah Za Rhoon?", 1, "Is her resting place important?", 2)) {
            1 -> whyAhZaRhoon()
            else -> restingPlace()
        }
    }

    private suspend fun Dialogue.thanksForInformation() {
        chatPlayer(happy, "Thanks for the information!")
        chatNpc(confused, "What information?")
        chatPlayer(neutral, "About Ah Za Rhoon and where it is.")
        mesbox("Trufitus looks at you blankly...")
        chatNpc(neutral, "Hmmm, well, you are welcome bwana.")
    }

    private suspend fun Dialogue.legend() {
        chatPlayer(quiz, "Mosol Rei said something about a legend?")
        chatNpc(neutral, "Ah, yes, there is a legend, but it is lost in the midst of antiquity...")
        chatNpc(
            sad,
            "The last place to hold any details regarding this mystery was in the temple of Ah Za " +
                "Rhoon....and that has long since vanished... it crumbled into dust...",
        )
        when (choice2("Why was it called Ah Za Rhoon?", 1, "Do you know anything more about the temple?", 2)) {
            1 -> whyAhZaRhoon()
            else -> temple()
        }
    }

    private suspend fun Dialogue.whyAhZaRhoon() {
        chatPlayer(quiz, "Why was it called Ah Za Rhoon?")
        chatNpc(
            neutral,
            "It is from an ancient language. The direct translation is... 'Magnificence floating on " +
                "water'. But my research makes me believe that the temple was built on land.",
        )
        chatNpc(
            neutral,
            "And most likely between large bodies of water, for example large lakes. However, many people " +
                "have searched for the temple, and have failed. I would hate to see you waste your time on a " +
                "pointless search like that.",
        )
        templeMenu()
    }

    private suspend fun Dialogue.templeMenu() {
        when (
            choice4(
                "Thanks for the information!", 1,
                "Do you know anything more about the temple?", 2,
                "I am going to search for Ah Za Rhoon!", 3,
                "It's a pity that I can't search for Ah Za Rhoon now.", 4,
            )
        ) {
            1 -> thanksForInformation()
            2 -> temple()
            3 -> searchForTemple()
            else -> {
                chatPlayer(sad, "It's a pity that I can't search for Ah Za Rhoon now.")
                chatNpc(
                    neutral,
                    "Well, I understand. Perhaps you can search for it another time? Come back when you think " +
                        "you're ready. Excuse me now won't you while I return to my studies.",
                )
                access.mes("Trufitus goes back to his studies.")
            }
        }
    }

    private suspend fun Dialogue.temple() {
        chatPlayer(quiz, "Do you know anything more about the temple?")
        chatNpc(neutral, "Not much... I would say that is about it...")
        chatNpc(
            neutral,
            "Even the great priest Zadimus who built the temple did not survive. Some say that Rashiliyia " +
                "caused the temple to collapse.",
        )
        chatNpc(
            neutral,
            "She was angry at Zadimus for not returning her affections. She was a great sorceress even " +
                "before they met.",
        )
        when (
            choice3(
                "Tell me more.", 1,
                "Are there any traps there?", 2,
                "Thanks for the information!", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Tell me more.")
                chatNpc(angry, "I don't know anymore. You're very demanding aren't you!")
                templeMenu()
            }
            2 -> {
                chatPlayer(quiz, "Are there any traps there?")
                chatNpc(
                    angry,
                    "How am I supposed to know? A lot of what I know is most probably wrong but some of it " +
                        "seems right to me. Excuse me but I must get back to my studies.",
                )
            }
            else -> thanksForInformation()
        }
    }

    private suspend fun Dialogue.searchForTemple() {
        chatPlayer(happy, "I am going to search for Ah Za Rhoon!")
        chatNpc(
            shocked,
            "What?! You must be crazy! That place has passed into myth and legend, it has been buried under " +
                "rubble for years. It's most likely buried 20 men deep, and that's if you can actually find it.",
        )
        chatNpc(
            neutral,
            "Are you sure you're going to go and look for it? I may be able to do some research into this if " +
                "you agree. Only I don't want to waste my time if you're not serious about this!",
        )
        if (!choice2("Yes.", true, "No.", false, title = "Start the Shilo Village quest?")) {
            chatPlayer(worried, "Actually, now it comes to it, I'm having second thoughts.")
            chatNpc(
                neutral,
                "Well, I understand. Perhaps you can search for it another time? Come back when you think " +
                    "you're ready. Excuse me now won't you while I return to my studies.",
            )
            return
        }
        chatPlayer(neutral, "Yes, I will seriously look for Ah Za Rhoon and I'd appreciate your help.")
        if (!player.inv.contains(WAMPUM_BELT)) {
            chatNpc(neutral, "Then bring me the Wampum belt that Mosol Rei gave you, so that I can study his message.")
            return
        }
        chatNpc(
            happy,
            "Ok then Bwana, good luck with your quest, and remember to stock up well with adventuring " +
                "supplies before setting off. You never know how useful some fairly ordinary things might be " +
                "when you're adventuring.",
        )
        access.invDel(player.inv, WAMPUM_BELT)
        shilo.advanceTo(access, STAGE_STARTED)
        chatNpc(
            neutral,
            "I'll hold on to this Wampum belt for you for the time being. I'll give it back to you when we " +
                "have completed this quest.",
        )
    }

    private suspend fun Dialogue.mosolRei() {
        chatPlayer(quiz, "What do you know about Mosol Rei?")
        chatNpc(
            neutral,
            "I know he is a brave warrior, he lives in a village south of here. Your journeys have taken you far!",
        )
        when (choice2("What do you know about Rashiliyia?", 1, "Do you trust him?", 2)) {
            1 -> rashiliyia()
            else -> {
                chatPlayer(quiz, "Do you trust him?")
                chatNpc(neutral, "He is a little headstrong, but for the right reasons. I think he is generally to be trusted.")
                rashiliyiaOrLegend()
            }
        }
    }

    /* The search for Ah Za Rhoon */

    private suspend fun Dialogue.askAboutSearch() {
        chatNpc(quiz, "Hello Bwana, how goes your quest to find Ah Za Rhoon?")
        when (choice2("Well thanks...", 1, "Erm, I don't know where to look?", 2)) {
            1 -> {
                chatPlayer(neutral, "Well thanks...")
                chatNpc(
                    happy,
                    "Well that's very good news. Let me know if you find anything useful, I may be able to help out.",
                )
            }
            else -> {
                chatPlayer(confused, "Erm, I don't know where to look?")
                chatNpc(
                    neutral,
                    "Hmm, that doesn't surprise me. The only information I have refers to its name. Ah Za " +
                        "Rhoon, its name means 'Magnificence Floating on Water'.",
                )
                shilo.advanceTo(access, STAGE_FOUND_MOUND)
                when (choice2("Do you think it was floating on water?", 1, "Ok thanks for your help.", 2)) {
                    1 -> {
                        chatPlayer(quiz, "Do you think it was floating on water?")
                        chatNpc(
                            neutral,
                            "It's very doubtful. I suspect it was built to 'appear' as if it was floating on water. " +
                                "Perhaps on an island or between large bodies of water.",
                        )
                        chatNpc(neutral, "If you search for somewhere like this, you may find something worth investigating.")
                    }
                    else -> {
                        chatPlayer(neutral, "Ok thanks for your help.")
                        chatNpc(neutral, "You're welcome Bwana, I only hope it helped.")
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.fissureQuestions() {
        while (true) {
            when (choice2("What's a fissure?", 1, "Are fissures dangerous?", 2)) {
                1 -> {
                    chatPlayer(quiz, "What's a fissure?")
                    chatNpc(
                        neutral,
                        "A fissure is a long, narrow crack, usually in rock. Just the kind of thing that " +
                            "adventurers love to find and explore.",
                    )
                }
                else -> {
                    chatPlayer(quiz, "Are fissures dangerous?")
                    chatNpc(
                        neutral,
                        "It most likely is, but that isn't normally a barrier to most adventurers! And it may " +
                            "very well lead to something very interesting.",
                    )
                }
            }
            if (choice2("Ok, thanks!", true, "I have another question.", false)) {
                okThanks()
                return
            }
        }
    }

    private suspend fun Dialogue.okThanks() {
        chatPlayer(happy, "Ok, thanks!")
        chatNpc(neutral, "You're quite welcome Bwana.")
    }

    private suspend fun Dialogue.thanks() {
        chatPlayer(happy, "Thanks!")
        chatNpc(happy, "You're more than welcome Bwana! Good luck for the rest of your quest.")
    }

    /* Help with the artefacts */

    private suspend fun Dialogue.clues() {
        if (access.owns(STONE_PLAQUE)) {
            chatNpc(neutral, "We need to identify that the place you have found is indeed Ah Za Rhoon.")
        } else {
            chatNpc(neutral, "Look for something that can identify the place. Leave no stone unturned.")
        }
        if (shilo.stage(player) >= STAGE_ENTERED_BERVIRIUS || access.owns(TATTERED_SCROLL)) {
            chatNpc(neutral, "Any scrolls or information about Rashiliyia's kin would be helpful.")
        } else {
            chatNpc(neutral, "Look for details of Rashiliyia's kin, these may be well hidden.")
        }
        if (access.owns(CRUMPLED_SCROLL)) {
            chatNpc(neutral, "Have you got any items concerning Rashiliyia? If so, please show me them.")
        } else {
            chatNpc(neutral, "There is a legend about Rashiliyia, look for it in the temple.")
        }
        if (access.owns(ZADIMUS_CORPSE)) {
            chatNpc(
                neutral,
                "There must be something relating to Zadimus at the temple. Did you find anything? If so, let me see it.",
            )
        } else {
            chatNpc(
                neutral,
                "Look for something relating to Zadimus at the temple. He was the Priest who built the temple.",
            )
        }
        chatNpc(happy, "And best of luck!")
    }

    private suspend fun Dialogue.items() {
        chatPlayer(neutral, "I have some items that I need help with.")
        chatNpc(neutral, "Well, just let me see the item and I'll help as much as I can.")
        clues()
        helpMenu(includeZadimus = true)
    }

    private suspend fun Dialogue.helpTemple() {
        chatPlayer(quiz, "I need some help with the Temple of Ah Za Rhoon.")
        chatNpc(
            neutral,
            "If you have found the temple, you should search it thoroughly and see if there are any clues about Rashiliyia.",
        )
        clues()
        helpMenu(includeZadimus = true)
    }

    private suspend fun Dialogue.helpRashiliyia() {
        chatPlayer(quiz, "I need help with Rashiliyia.")
        chatNpc(
            neutral,
            "We need to find Rashiliyia's resting place and learn how to put her spirit to rest. You may find " +
                "some clues to her resting place in Ah Za Rhoon or Bervirius' Tomb.",
        )
        helpMenu(includeZadimus = true)
    }

    private suspend fun Dialogue.bervirius() {
        chatPlayer(quiz, "I need help with Bervirius.")
        chatNpc(
            neutral,
            "Bervirius is the son of Rashiliyia. His tomb may hold some clues as to how Rashiliyia may be defeated.",
        )
        helpMenu(includeZadimus = true)
    }

    private suspend fun Dialogue.zadimus() {
        chatPlayer(quiz, "I need help with Zadimus.")
        if (access.owns(ZADIMUS_CORPSE)) {
            chatNpc(neutral, "Zadimus is a spirit yearning for freedom. Bury him in a sacred place to release his spirit.")
            when (
                choice5(
                    "Is there any sacred ground around here?", 1,
                    "I need help with Bervirius.", 2,
                    "I need help with Rashiliyia.", 3,
                    "I need some help with the Temple of Ah Za Rhoon.", 4,
                    "Ok, thanks!", 5,
                )
            ) {
                1 -> sacredGround()
                2 -> bervirius()
                3 -> helpRashiliyia()
                4 -> helpTemple()
                else -> okThanks()
            }
            return
        }
        chatNpc(
            neutral,
            "All I know is that Zadimus was a high priest of Zamorak. Rashiliyia loved him, but he did not return her affections.",
        )
        chatNpc(
            neutral,
            "When she became a more powerful sorceress she attacked Ah Za Rhoon, reducing it to rubble. What " +
                "Zadimus' fate was, I do not know.",
        )
        chatNpc(neutral, "If you find anything relating to him at the temple of Ah Za Rhoon, please let me see it.")
        helpMenu(includeZadimus = false)
    }

    private suspend fun Dialogue.helpMenu(includeZadimus: Boolean) {
        val tomb = shilo.stage(player) >= STAGE_ENTERED_BERVIRIUS
        val shard = access.owns(BONE_SHARD)
        when {
            tomb && shard ->
                when (
                    choice5(
                        "I have just buried Zadimus' corpse.", 1,
                        "I need help with Bervirius.", 2,
                        "I have some items that I need help with.", 3,
                        "I need some help with the Temple of Ah Za Rhoon.", 4,
                        if (includeZadimus) "I need help with Zadimus." else "I need help with Rashiliyia.", 5,
                    )
                ) {
                    1 -> buried()
                    2 -> bervirius()
                    3 -> items()
                    4 -> helpTemple()
                    else -> if (includeZadimus) zadimus() else helpRashiliyia()
                }
            tomb ->
                when (
                    choice5(
                        "I need help with Bervirius.", 1,
                        "I have some items that I need help with.", 2,
                        "I need help with Zadimus.", 3,
                        "I need help with Rashiliyia.", 4,
                        "I need some help with the Temple of Ah Za Rhoon.", 5,
                    )
                ) {
                    1 -> bervirius()
                    2 -> items()
                    3 -> zadimus()
                    4 -> helpRashiliyia()
                    else -> helpTemple()
                }
            shard ->
                when (
                    choice5(
                        "I have just buried Zadimus' corpse.", 1,
                        "I have some items that I need help with.", 2,
                        "I need some help with the Temple of Ah Za Rhoon.", 3,
                        "I need help with Zadimus.", 4,
                        "I need help with Rashiliyia.", 5,
                    )
                ) {
                    1 -> buried()
                    2 -> items()
                    3 -> helpTemple()
                    4 -> zadimus()
                    else -> helpRashiliyia()
                }
            else ->
                when (
                    choice4(
                        "I have some items that I need help with.", 1,
                        "I need help with Zadimus.", 2,
                        "I need help with Rashiliyia.", 3,
                        "I need some help with the Temple of Ah Za Rhoon.", 4,
                    )
                ) {
                    1 -> items()
                    2 -> zadimus()
                    3 -> helpRashiliyia()
                    else -> helpTemple()
                }
        }
    }

    private suspend fun Dialogue.buried() {
        chatPlayer(neutral, "I have just buried Zadimus' corpse.")
        chatNpc(quiz, "Something seems different about you. You look like you have seen a ghost?")
        chatPlayer(shocked, "It just so happens that I have!")
        chatNpc(shocked, "Oh! So you managed to bury Zadimus's corpse?")
        chatPlayer(neutral, "Yes, it was pretty grisly!")
        spiritWords()
    }

    private suspend fun Dialogue.spiritWords() {
        when (choice2("The spirit said something about keys and kin?", 1, "The spirit rambled on about some nonsense.", 2)) {
            1 -> {
                chatPlayer(quiz, "The spirit said something about keys and kin?")
                chatNpc(confused, "Hmmm, maybe it's a clue of some kind?")
                chatNpc(
                    neutral,
                    "Rashiliyia's only kin was a son, 'Bervirius'. His remains were entombed on a small island " +
                        "which lies to the South West. I will do some research into this to see if I can find any " +
                        "other details.",
                )
                if (!access.ownsSwordPommel()) {
                    chatNpc(
                        neutral,
                        "But I think we must take Zadimus' clue literally and get some item that belonged to " +
                            "Bervirius as it may be the only way to approach Rashiliyia. Perhaps something like this " +
                            "exists in his tomb?",
                    )
                } else {
                    chatNpc(
                        neutral,
                        "I still think we need to take this clue quite literally. Perhaps you found something at " +
                            "Bervirius' Tomb that could help to protect you from Rashiliyia's attacks?",
                    )
                }
            }
            else -> {
                chatPlayer(neutral, "The spirit rambled on about some nonsense.")
                chatNpc(neutral, "Oh, so it most likely was not very important then.")
            }
        }
    }

    private suspend fun Dialogue.sacredGround() {
        chatPlayer(quiz, "Is there any sacred ground around here?")
        chatNpc(neutral, "The ground in the centre of the village is very sacred to us. Maybe you could try there?")
    }

    private suspend fun Dialogue.berviriusTomb() {
        chatPlayer(happy, "I think I found Bervirius' Tomb.")
        chatNpc(happy, "Congratulations Bwana. Show me any items you have found though. I may be able to help.")
        when (choice2("I actually need help with something else.", 1, "I didn't find anything in the tomb.", 2)) {
            1 -> somethingElse()
            else -> didntFindInTomb()
        }
    }

    private suspend fun Dialogue.somethingElse() {
        chatPlayer(neutral, "I actually need help with something else.")
        chatNpc(quiz, "What could I possibly help you with Bwana?")
        when (
            choice5(
                "I need help with Rashiliyia.", 1,
                "I need help with Zadimus.", 2,
                "I have some items that I need help with.", 3,
                "I need help with Bervirius.", 4,
                "Ok, thanks!", 5,
            )
        ) {
            1 -> helpRashiliyia()
            2 -> zadimus()
            3 -> items()
            4 -> bervirius()
            else -> okThanks()
        }
    }

    private suspend fun Dialogue.didntFind() {
        chatPlayer(sad, "No, I didn't find a thing.")
        chatNpc(
            sad,
            "That is a shame Bwana. We really do need to act against Rashiliyia soon if we are ever to stand a " +
                "chance of defeating her.",
        )
        when (
            choice3(
                "Actually I did find the tomb, I was just joking.", 1,
                "I actually need help with something else.", 2,
                "I didn't find anything in the tomb.", 3,
            )
        ) {
            1 -> {
                chatPlayer(laugh, "Actually I did find the tomb, I was just joking.")
                chatNpc(
                    angry,
                    "Well, Bwana, this is no laughing matter. We need to take this very seriously and act now! If " +
                        "you have found any items at the tomb that you need help with please let me see them and I " +
                        "will help as much as I can.",
                )
                when (choice2("I didn't find anything in the tomb.", 1, "I actually need help with something else.", 2)) {
                    1 -> didntFindInTomb()
                    else -> somethingElse()
                }
            }
            2 -> somethingElse()
            else -> didntFindInTomb()
        }
    }

    private suspend fun Dialogue.didntFindInTomb() {
        chatPlayer(sad, "I didn't find anything in the tomb.")
        chatNpc(
            neutral,
            "Maybe you need to look around a little more. There must be some small detail at least that can help us.",
        )
        when (choice2("I have some items that I need some help with.", 1, "I actually need help with something else.", 2)) {
            1 -> {
                chatPlayer(neutral, "I have some items that I need some help with.")
                chatNpc(neutral, "Well, just show me the items and I'll help as much as I can.")
                when (choice2("I actually need help with something else.", 1, "Thanks!", 2)) {
                    1 -> somethingElse()
                    else -> thanks()
                }
            }
            else -> somethingElse()
        }
    }

    /* Items shown to Trufitus */

    private suspend fun ProtectedAccess.useOnTrufitus(trufitus: Npc, obj: String) {
        if (junglePotion.isInProgress(player)) {
            startDialogue(trufitus) { with(junglePotionTalk) { useItem(obj) } }
            return
        }
        if (!shilo.completedJunglePotion(player)) {
            mes("Nothing interesting happens.")
            return
        }
        val cleanHerb = JungleHerb.byClean(obj)
        if (cleanHerb != null) {
            startDialogue(trufitus) { with(junglePotionTalk) { buyHerb(cleanHerb) } }
            return
        }
        if (JungleHerb.byGrimy(obj) != null) {
            startDialogue(trufitus) { with(junglePotionTalk) { declineGrimy() } }
            return
        }
        if (shilo.isComplete(player) && obj in POST_QUEST_SELLABLE) {
            startDialogue(trufitus) {
                chatPlayer(neutral, "Have a look at this.")
                chatNpc(
                    neutral,
                    "Hmmm, I'm not sure you will get much use out of this. Why not see if you can sell it in Shilo Village.",
                )
            }
            return
        }
        startDialogue(trufitus) {
            when (obj) {
                WAMPUM_BELT -> showBelt()
                RASHILIYIA_CORPSE -> showCorpse()
                BONE_KEY -> showKey()
                BEADS_OF_THE_DEAD -> {
                    mesbox("You show Trufitus the 'Beads of the Dead'.")
                    chatPlayer(neutral, "Take a look at this...")
                    chatNpc(
                        happy,
                        "This is very impressive Bwana, I'm quite surprised at your ingenuity. This should be a good " +
                            "protection against Rashiliyia if you ever find her Tomb.",
                    )
                }
                BERVIRIUS_NOTES -> {
                    mesbox("You hand the notes over to Trufitus.")
                    chatNpc(
                        neutral,
                        "Hmm, these notes are quite extraordinary Bwana. They give location details of Rashiliyia's " +
                            "tomb, and some information on how to use the crystal.",
                    )
                    chatNpc(happy, "The information is quite specific, North of Ah Za Rhoon! That's a great place to start looking!")
                }
                BONE_BEADS -> {
                    mesbox("You show Trufitus the beads that you crafted.")
                    chatPlayer(neutral, "Take a look at these.")
                    chatNpc(
                        neutral,
                        "Hmm, very interesting Bwana, your abilities are much more focused than I had initially " +
                            "thought. I presume these are to be part of the ward to protect you from Rashiliyia?",
                    )
                }
                BONE_SHARD -> showShard()
                ZADIMUS_CORPSE -> showZadimus()
                STONE_PLAQUE -> {
                    mesbox("You hand over the Stone Plaque to Trufitus.")
                    chatPlayer(quiz, "Can you decipher this please?")
                    chatNpc(shocked, "This is an ancient artefact!")
                    mesbox("Trufitus looks at the item in awe.")
                    chatNpc(
                        neutral,
                        "I can certainly try! Hmm, incredible, it seems very ancient and mentions something about " +
                            "Zadimus and Ah Za Rhoon. It says, 'Here lies the traitor Zadimus, let his spirit be " +
                            "forever tormented'.",
                    )
                    shilo.decipheredPlaque.set(player, true)
                    mesbox("Trufitus hands the Stone Plaque back.")
                    chatNpc(neutral, "If you have found anything else that you need help with, please just let me know.")
                }
                LOCATING_CRYSTAL -> {
                    mesbox("You show Trufitus the Locating Crystal.")
                    chatNpc(shocked, "This is incredible Bwana.")
                    chatPlayer(quiz, "It is?")
                    chatNpc(
                        happy,
                        "Absolutely! This will help you to locate the entrance to Rashiliyia's tomb. Simply activate " +
                            "it when you think you are near, and it should glow different colours to show how near you are.",
                    )
                }
                TATTERED_SCROLL -> {
                    mesbox("You hand the tattered scroll to Trufitus.")
                    chatPlayer(quiz, "What do you make of this?")
                    chatNpc(
                        neutral,
                        "Truly amazing Bwana, this scroll must be ancient. I'm not sure if I get more meaning from it " +
                            "than you though. Perhaps Bervirius' tomb is still accessible?",
                    )
                    mesbox("Trufitus hands the tattered scroll back to you.")
                }
                CRUMPLED_SCROLL -> showCrumpledScroll()
                SWORD_POMMEL -> showPommel()
                else -> {
                    access.mes("You hand over the item.")
                    delay(2)
                    chatNpc(neutral, "I'm sorry Bwana but I just don't have a use for that!")
                }
            }
        }
    }

    private suspend fun Dialogue.showBelt() {
        if (shilo.stage(player) > 0) {
            chatNpc(neutral, "You've already given me one of these Bwana. I'm aware of the situation with Rashiliyia.")
            return
        }
        objbox(WAMPUM_BELT, "You show Trufitus the Wampum belt, he studies it for a long time.")
        chatNpc(worried, "Hello Bwana, this message from Mosol Rei bears bad news... Yes, things do look very bad indeed.")
        rashiliyiaOrLegend()
    }

    private suspend fun Dialogue.showCorpse() {
        mesbox("You show Trufitus the remains...")
        chatPlayer(quiz, "Could you have a look at this..")
        chatNpc(shocked, "This is truly incredible bwana... So these are the remains of the dread Queen Rashiliyia?")
        chatPlayer(neutral, "Yes, I think so.")
        var askWhat = choice2("What should I do with them?", true, "Can you take them off my hands?", false)
        while (true) {
            if (askWhat) {
                chatPlayer(quiz, "What should I do with them?")
                chatNpc(confused, "Hmm, I'm not exactly sure... Perhaps there is a clue in one of the artefacts you have found?")
                if (choice2("Can you take them off my hands?", true, "Thanks!", false)) {
                    askWhat = false
                    continue
                }
            } else {
                chatPlayer(quiz, "Can you take them off my hands?")
                chatNpc(worried, "I dare not take them, I may be taken over by the evil spirit of Rashiliyia!")
                if (choice2("What should I do with them?", true, "Thanks!", false)) {
                    askWhat = true
                    continue
                }
            }
            thanks()
            return
        }
    }

    private suspend fun Dialogue.showKey() {
        chatPlayer(happy, "Have a look at this!")
        chatNpc(shocked, "This is amazing Bwana, the level of detail is incredible. Where did you find it?")
        when (choice2("I made it from the bone shard that Zadimus gave me.", 1, "Do you know what it opens?", 2)) {
            1 -> {
                chatPlayer(neutral, "I made it from the bone shard that Zadimus gave me.")
                chatNpc(
                    happy,
                    "How very inventive Bwana. You must have seen the lock to have crafted it so well. Does the key work?",
                )
                when (choice2("Yes and I explored inside some sort of cavern.", 1, "I don't know, I haven't tried it yet.", 2)) {
                    1 -> {
                        chatPlayer(neutral, "Yes and I explored inside some sort of cavern.")
                        chatNpc(quiz, "How interesting Bwana, did you find anything?")
                        when (choice2("Not really.", 1, "Yes, I found lots of things.", 2)) {
                            1 -> {
                                chatPlayer(sad, "Not really.")
                                chatNpc(neutral, "Maybe you should go back and try to find some more things.")
                                chatNpc(neutral, "Maybe there are more items to be found at Ah Za Rhoon?")
                            }
                            else -> {
                                chatPlayer(happy, "Yes, I found lots of things.")
                                chatNpc(neutral, "If you let me see them Bwana, perhaps I can offer you some extra information.")
                            }
                        }
                    }
                    else -> {
                        chatPlayer(neutral, "I don't know, I haven't tried it yet.")
                        chatNpc(
                            neutral,
                            "It may be an idea to try it and then scout out the area. If it relates to Rashiliyia, it " +
                                "might help us to defeat her.",
                        )
                    }
                }
            }
            else -> {
                chatPlayer(quiz, "Do you know what it opens?")
                chatNpc(
                    neutral,
                    "You must already know what it opens to have carved it so perfectly. Perhaps in your travels you " +
                        "have come across some unique doors with a unique lock? I hope this helps with your quest.",
                )
            }
        }
    }

    private suspend fun Dialogue.showShard() {
        mesbox("You show Trufitus the Bone Shard.")
        chatPlayer(quiz, "Could you have a look at this please?")
        mesbox("Trufitus looks at the object for a moment.")
        chatNpc(quiz, "It looks like a simple shard of bone. Why do you think it is significant?")
        var appeared = choice2("It appeared when I buried Zadimus' corpse.", true, "No reason really.", false)
        if (!appeared) {
            chatPlayer(neutral, "No reason really.")
            chatNpc(confused, "Well why are you showing it to me then?")
            appeared = choice2("It appeared when I buried Zadimus' corpse.", true, "I'm not sure.", false)
            if (!appeared) {
                notSure()
                return
            }
        }
        chatPlayer(neutral, "It appeared when I buried Zadimus' corpse.")
        chatNpc(quiz, "Ah, interesting, so you think that Zadimus gave you the bone? What makes you say that?")
        if (!choice2("He said something after he gave it to me.", true, "I'm not sure.", false)) {
            notSure()
            return
        }
        chatPlayer(neutral, "He said something after he gave it to me.")
        chatNpc(quiz, "What did he say?")
        spiritWords()
    }

    private suspend fun Dialogue.notSure() {
        chatPlayer(confused, "I'm not sure.")
        chatNpc(neutral, "Oh, right. Come back and talk with me if you get an idea.")
    }

    private suspend fun Dialogue.showZadimus() {
        mesbox("You show Trufitus the corpse.")
        chatPlayer(quiz, "What do you make of this?")
        chatNpc(shocked, "! GASP ! That's incredible, where did you find it?")
        chatPlayer(
            worried,
            "I found the corpse in a decomposing gallows. I get a very strange feeling every time I try to bury the body.",
        )
        chatNpc(neutral, "Hmmm, that sounds very strange. I sense a spirit in torment, you should try to bury the remains.")
        when (choice2("Is there any sacred ground around here?", 1, "Can you dispose of this for me?", 2)) {
            1 -> sacredGround()
            else -> {
                chatPlayer(quiz, "Can you dispose of this for me?")
                mesbox("Trufitus pulls away from you...")
                chatNpc(
                    worried,
                    "I dare not touch it. I am a spiritual man and the spirit of this being may possess me and turn " +
                        "me into a minion of Rashiliyia.",
                )
            }
        }
    }

    private suspend fun Dialogue.showCrumpledScroll() {
        mesbox("You hand the crumpled scroll to Trufitus.")
        chatPlayer(quiz, "Have a look at this, tell me what you think.")
        chatNpc(shocked, "I am speechless Bwana, this is truly ancient. Where did you find it?")
        chatPlayer(neutral, "In an underground building of some sort.")
        chatNpc(
            happy,
            "You must truly have found the temple of Ah Za Rhoon! The scroll gives some interesting details about " +
                "Rashiliyia, some things I didn't know before.",
        )
        mesbox("Trufitus gives back the scroll.")
        when (choice2("Anything that can help?", 1, "Ok, thanks!", 2)) {
            1 -> {
                chatPlayer(quiz, "Anything that can help?")
                chatNpc(neutral, "Hmmm, well just that part about the wards...")
                mesbox("Trufitus seems to drift off in thought.")
                chatNpc(
                    neutral,
                    "It may be possible to make a ward like that... But what is the best thing to make it from? " +
                        "Perhaps something close to Bervirius, an item of some significance to him.",
                )
            }
            else -> okThanks()
        }
    }

    private suspend fun Dialogue.showPommel() {
        mesbox("You show Trufitus the sword pommel.")
        chatNpc(
            neutral,
            "It is a very nice item Bwana. It may be just what you need to gain access to Rashiliyia's tomb. " +
                "While you were away, I did some research.",
        )
        chatNpc(
            neutral,
            "Rashiliyia would spare the lives of those who wore bronze necklaces. This pommel may have some " +
                "significance to Bervirius. Perhaps you can craft something from it that can help?",
        )
        chatNpc(worried, "My guess is that you will need some protection from Rashiliyia if you intend to enter her tomb!")
        var necklace = choice2("How do I make a bronze necklace?", true, "What should I put on the necklace?", false)
        while (true) {
            if (necklace) {
                chatPlayer(quiz, "How do I make a bronze necklace?")
                chatNpc(
                    neutral,
                    "Well, Bwana, I would guess that you would need to get some bronze metal and work it into " +
                        "something that could be turned into a necklace?",
                )
                if (choice2("What should I put on the necklace?", true, "Thanks!", false)) {
                    necklace = false
                    continue
                }
            } else {
                chatPlayer(quiz, "What should I put on the necklace?")
                chatNpc(
                    neutral,
                    "Perhaps Zadimus' clue has the answer? Now, what was it that he said again? Something about kin " +
                        "and keys? That sword pommel belonged to Bervirius didn't it?",
                )
                if (choice2("How do I make a bronze necklace?", true, "Thanks!", false)) {
                    necklace = true
                    continue
                }
            }
            thanks()
            return
        }
    }

    private companion object {
        val POST_QUEST_SELLABLE =
            setOf(BEADS_OF_THE_DEAD, BERVIRIUS_NOTES, STONE_PLAQUE, LOCATING_CRYSTAL, TATTERED_SCROLL, CRUMPLED_SCROLL)
    }
}
