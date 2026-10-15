package org.rsmod.content.quest.area.karamja.junglepotion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.COINS
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.STAGE_ALL_HERBS
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.karamja.junglepotion.JunglePotionQuest.Companion.STAGE_GET_SNAKE_WEED

/**
 * Trufitus' side of Jungle Potion. His Talk-to and item-on-npc hooks live with the rest of his
 * dialogue in the Shilo Village plugin, which hands the conversation over here while this quest
 * owns it.
 */
@Singleton
class TrufitusJunglePotion @Inject constructor(private val junglePotion: JunglePotionQuest) {

    suspend fun Dialogue.talk() {
        val stage = junglePotion.stage(player)
        when {
            stage == 0 -> preQuest()
            stage == STAGE_ALL_HERBS -> finishQuest()
            stage >= STAGE_COMPLETE -> finalBlessing()
            else -> askForHerb(junglePotion.wantedHerb(player) ?: return)
        }
    }

    /** Herbs shown to Trufitus while he is collecting them; anything else is ignored. */
    suspend fun Dialogue.useItem(obj: String) {
        val wanted = junglePotion.wantedHerb(player) ?: return
        val clean = JungleHerb.byClean(obj)
        val grimy = JungleHerb.byGrimy(obj)
        when {
            clean == wanted -> acceptHerb(wanted)
            grimy == wanted -> declineGrimy()
            clean != null || grimy != null -> wrongHerb()
            else -> access.mes("Nothing interesting happens.")
        }
    }

    /** After the quest Trufitus pays a few coins for any clean herb added to his collection. */
    suspend fun Dialogue.buyHerb(herb: JungleHerb) {
        chatNpc(neutral, "Many thanks for the herb Bwana. I'll add it to my collection.")
        if (access.invDel(access.inv, herb.clean).failure) {
            return
        }
        val coins = access.random.of(1, 4)
        access.invAdd(access.inv, COINS, coins)
        val name = if (herb == JungleHerb.RoguesPurse) "rogues purse" else herb.displayName.lowercase()
        if (coins == 1) {
            access.mes("Trufitus gives you a coin for the $name.")
        } else {
            access.mes("Trufitus gives you $coins coins for the $name.")
        }
    }

    suspend fun Dialogue.declineGrimy() {
        chatNpc(
            confused,
            "Sorry, Bwana, that herb is so dirty that I can't even tell whether it is fresh. Please clean it first.",
        )
    }

    /* Starting the quest */

    private suspend fun Dialogue.preQuest() {
        chatNpc(happy, "Greetings Bwana! I am Trufitus Shakaya of the Tai Bwo Wannai village.")
        chatNpc(happy, "Welcome to our humble village.")
        when (
            choice3(
                "What does Bwana mean?", 1,
                "Tai Bwo Wannai? What does that mean?", 2,
                "It's a nice village, where is everyone?", 3,
            )
        ) {
            1 -> whatDoesBwanaMean()
            2 -> {
                chatPlayer(happy, "Tai Bwo Wannai? What does that mean?")
                chatNpc(happy, "It means 'small clearing in the jungle' but it is now the name of our village.")
                when (choice2("It's a nice village, where is everyone?", 1, "I am sorry, but I am very busy.", 2)) {
                    1 -> whereIsEveryone()
                    else -> busy()
                }
            }
            else -> whereIsEveryone()
        }
    }

    private suspend fun Dialogue.whatDoesBwanaMean() {
        chatPlayer(happy, "What does Bwana mean?")
        chatNpc(happy, "It means friend, and friends come in peace. I assume that you come in peace?")
        when (choice2("Yes, of course I do.", 1, "What does a warrior like me know about peace?", 2)) {
            1 -> {
                chatPlayer(happy, "Yes, of course I do.")
                chatNpc(happy, "Well, that is good news, as I may have a proposition for you.")
                when (choice2("A proposition eh? Sounds interesting!", 1, "I am sorry, but I am very busy.", 2)) {
                    1 -> {
                        chatPlayer(happy, "A proposition eh? Sounds interesting!")
                        chatNpc(neutral, "I hoped you would think so. My people are afraid to stay in the village.")
                        chatNpc(neutral, "They have returned to the jungle and I need to commune with the gods")
                        chatNpc(neutral, "to see what fate befalls us. You can help me by collecting some herbs that I need.")
                        when (choice2("Me? How can I help?", 1, "I am very sorry, but I don't have time for that.", 2)) {
                            1 -> howCanIHelp()
                            else -> {
                                chatPlayer(happy, "I am very sorry, but I don't have any time for that.")
                                farewell()
                            }
                        }
                    }
                    else -> busy()
                }
            }
            else -> {
                chatPlayer(happy, "What does a warrior like me know about peace?")
                chatNpc(sad, "When you grow weary of violence and seek a more enlightened path, please pay me a visit")
                chatNpc(
                    sad,
                    "as I may have a proposition for you. Now I need to attend to the plight of my people. " +
                        "Please excuse me...",
                )
            }
        }
    }

    private suspend fun Dialogue.whereIsEveryone() {
        chatPlayer(happy, "It's a nice village, where is everyone?")
        chatNpc(
            sad,
            "My people are afraid to stay in the village. They have returned to the jungle. I need to commune " +
                "with the gods to see what fate befalls us.",
        )
        chatNpc(sad, "You may be able to help with this.")
        when (choice2("Me? How can I help?", 1, "I am sorry, but I am very busy.", 2)) {
            1 -> howCanIHelp()
            else -> busy()
        }
    }

    private suspend fun Dialogue.busy() {
        chatPlayer(happy, "I am sorry, but I am very busy.")
        farewell()
    }

    private suspend fun Dialogue.farewell() {
        chatNpc(neutral, "Very well then, may your journeys bring you much joy.")
        chatNpc(neutral, "Maybe you will pass this way again and you then take up my proposal?")
        chatNpc(neutral, "But for now, fare thee well.")
    }

    private suspend fun Dialogue.howCanIHelp() {
        chatPlayer(happy, "Me? How can I help?")
        chatNpc(
            neutral,
            "I need to make a special brew! A potion that helps me to commune with the gods. For this potion, " +
                "I need very special herbs, that are only found in the deep jungle.",
        )
        chatNpc(
            neutral,
            "I can only guide you so far as the herbs are not easy to find. With some luck, you will find each " +
                "herb in turn and bring to me. I will give you details of where to find the next herb.",
        )
        chatNpc(neutral, "In return for this great favour I will give you training in Herblore.")
        if (!junglePotion.canStart(player)) {
            mesbox("You need to have completed the Druidic Ritual quest to start Jungle Potion.")
            return
        }
        if (!choice2("Yes.", true, "No.", false, title = "Start the Jungle Potion quest?")) {
            chatPlayer(happy, "Hmmm, sounds difficult, I don't know if I am ready for the challenge.")
            chatNpc(
                neutral,
                "Very well then Bwana, maybe you will return to me invigorated and ready to take up the challenge one day?",
            )
            return
        }
        chatPlayer(happy, "It sounds like just the challenge for me. And it would make a nice break from killing things!")
        junglePotion.advanceTo(access, STAGE_GET_SNAKE_WEED)
        chatNpc(neutral, "That is excellent Bwana! The first herb that you need to gather is called")
        chatNpc(neutral, "Snake Weed.")
        chatNpc(neutral, "It grows near the vines in an area to the south west where")
        chatNpc(neutral, "the ground turns soft and the water kisses your feet.")
    }

    /* Gathering the herbs */

    private suspend fun Dialogue.askForHerb(herb: JungleHerb) {
        when (herb) {
            JungleHerb.SnakeWeed -> chatNpc(neutral, "Hello Bwana, do you have the Snake Weed?")
            JungleHerb.Ardrigal -> chatNpc(neutral, "Hello Bwana, have you been able to get the Ardrigal?")
            JungleHerb.SitoFoil -> chatNpc(happy, "Greetings Bwana, have you been successful in getting the Sito Foil?")
            JungleHerb.VolenciaMoss -> chatNpc(happy, "Greetings Bwana, have you been successful in getting the Volencia Moss?")
            JungleHerb.RoguesPurse -> chatNpc(happy, "Greetings Bwana, have you been successful in getting the Rogue's Purse?")
        }
        if (choice2("Of course!", true, "Not yet, sorry, what's the clue again?", false)) {
            chatPlayer(happy, "Of course!")
            when {
                player.inv.contains(herb.clean) -> acceptHerb(herb)
                player.inv.contains(herb.grimy) -> declineGrimy()
                herb == JungleHerb.VolenciaMoss ->
                    chatNpc(neutral, "Please don't try to deceive me! I really need that Volencia Moss if I am to make this potion.")
                herb == JungleHerb.RoguesPurse ->
                    chatNpc(neutral, "Please don't try to deceive me, I really need that Rogue's Purse if I am to make this potion.")
                else -> {
                    chatNpc(neutral, "Please don't try to deceive me.")
                    reallyNeed(herb)
                }
            }
            return
        }
        chatPlayer(happy, "Not yet, sorry, what's the clue again?")
        repeatClue(herb)
        reallyNeed(herb)
    }

    private suspend fun Dialogue.repeatClue(herb: JungleHerb) {
        when (herb) {
            JungleHerb.SnakeWeed ->
                chatNpc(
                    neutral,
                    "It grows near vines in an area to the south west where the ground turns soft and the water " +
                        "kisses your feet.",
                )
            JungleHerb.Ardrigal -> {
                chatNpc(neutral, "You are looking for Ardrigal. It is related to the palm and grows in its brother's shady profusion.")
                eastPeninsula()
            }
            JungleHerb.SitoFoil ->
                chatNpc(
                    neutral,
                    "You are looking for Sito Foil, and it grows best where the ground has been blackened by the " +
                        "living flame.",
                )
            JungleHerb.VolenciaMoss -> {
                chatNpc(
                    neutral,
                    "You are looking for Volencia Moss. It clings to rocks for its existence. It is difficult to " +
                        "see, so you must search for it well.",
                )
                metalRocks()
            }
            JungleHerb.RoguesPurse -> {
                chatNpc(neutral, "You are looking for Rogue's Purse.")
                chatNpc(
                    neutral,
                    "It inhabits the darkness of the underground, and grows in caverns to the north. A secret " +
                        "entrance to the caverns is set into the northern cliffs, be careful Bwana.",
                )
            }
        }
    }

    private suspend fun Dialogue.reallyNeed(herb: JungleHerb) {
        chatNpc(neutral, "I really need that ${herb.displayName} if I am to make this potion.")
    }

    private suspend fun Dialogue.eastPeninsula() {
        chatNpc(
            neutral,
            "To the east you will find a small peninsula, it is just after the cliffs come down to meet the " +
                "sands, here is where you should search for it.",
        )
    }

    private suspend fun Dialogue.metalRocks() {
        chatNpc(
            neutral,
            "It prefers rocks of high metal content and a frequently disturbed environment. There is some, I " +
                "believe to the south east of this village.",
        )
    }

    private suspend fun Dialogue.acceptHerb(herb: JungleHerb) {
        if (junglePotion.stage(player) != herb.pickedStage) {
            val name = if (herb == JungleHerb.RoguesPurse) "Rogue's Purse" else herb.displayName.lowercase()
            chatNpc(
                angry,
                "That's not fresh $name, did you pick it yourself? Go get me some fresh $name and remember to " +
                    "pick it yourself.",
            )
            return
        }
        if (access.invDel(access.inv, herb.clean).failure) {
            return
        }
        val next = JungleHerb.entries.getOrNull(herb.ordinal + 1)
        junglePotion.advanceTo(access, next?.askedStage ?: STAGE_ALL_HERBS)
        objbox(herb.clean, "You give the ${herb.displayName} to Trufitus.")
        when (herb) {
            JungleHerb.SnakeWeed -> {
                chatNpc(
                    happy,
                    "Great, you have the Snake Weed! Many thanks. Ok, the next herb is called Ardrigal. It is " +
                        "related to the palm and grows to the east in its brother's shady profusion.",
                )
                eastPeninsula()
            }
            JungleHerb.Ardrigal -> {
                chatNpc(happy, "Great, you have the Ardrigal! Many thanks.")
                chatNpc(
                    neutral,
                    "You are doing well Bwana. The next herb is called Sito Foil, and it grows best where the " +
                        "ground has been blackened by the living flame.",
                )
            }
            JungleHerb.SitoFoil -> {
                chatNpc(happy, "Well done Bwana, just two more herbs to collect.")
                chatNpc(
                    neutral,
                    "The next herb is called Volencia Moss. It clings to rocks for its existence. It is difficult " +
                        "to see, so you must search for it well.",
                )
                metalRocks()
            }
            JungleHerb.VolenciaMoss -> {
                chatNpc(
                    happy,
                    "Ah Volencia Moss, beautiful. One final herb and the potion will be complete. This is the most " +
                        "difficult to find as it inhabits the darkness of the underground. It is called Rogue's " +
                        "Purse, and is only to be found in",
                )
                chatNpc(
                    neutral,
                    "caverns in the northern part of this island. A secret entrance to the caverns is set into the " +
                        "northern cliffs of this land. Take care Bwana as it may be dangerous.",
                )
            }
            JungleHerb.RoguesPurse -> {
                chatNpc(
                    happy,
                    "Most excellent Bwana! You have returned all the herbs to me and, I can finish the preparations " +
                        "for the potion, and at last divine with the gods.",
                )
                chatNpc(
                    happy,
                    "Many blessings on you! I must now prepare, please excuse me while I make the arrangements.",
                )
                finishQuest()
            }
        }
    }

    private suspend fun Dialogue.wrongHerb() {
        chatNpc(
            neutral,
            "Many thanks Bwana, but I don't need that herb at the moment. Can you please get me the herb I asked for?",
        )
    }

    /* The ritual */

    private suspend fun Dialogue.finishQuest() {
        mesbox("Trufitus shows you some techniques in Herblore. You gain some experience in Herblore.")
        junglePotion.complete(access)
    }

    private suspend fun Dialogue.finalBlessing() {
        chatNpc(neutral, "My greatest respects Bwana, I have communed with my gods and the future")
        chatNpc(neutral, "looks good for my people. We are happy now that the gods are not angry with us.")
        chatNpc(happy, "With some blessings we will be safe here.")
        junglePotion.heardFinalBlessing.set(player, true)
    }
}
