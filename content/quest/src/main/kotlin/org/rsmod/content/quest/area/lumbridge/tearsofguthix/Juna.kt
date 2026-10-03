package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.JUNA_CHATHEAD
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.QP_REQ
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STONE_BOWL
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixRules.Companion.returnText
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt

/** Juna, the serpent guarding the Tears of Guthix: the quest, her stories and the way in. */
@Singleton
class Juna
@Inject
constructor(
    private val quest: TearsOfGuthixQuest,
    private val rules: TearsOfGuthixRules,
    private val cave: TearsCave,
) {
    suspend fun Dialogue.talk() {
        when (quest.stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> waitingForBowl()
            else -> afterQuest()
        }
    }

    /** Juna's Story op: straight to the stories, once the quest is done. */
    suspend fun Dialogue.story() {
        if (quest.stage(player) < STAGE_COMPLETE) {
            talk()
            return
        }
        tellStory(viaStoryOp = true)
    }

    suspend fun Dialogue.stoneOnJuna() {
        junaLine(neutral, "Perhaps you should use it with a chisel, rather than with my face.")
    }

    private suspend fun Dialogue.beforeQuest() {
        junaLine(neutral, "Tell me... a story...")
        while (true) {
            val next =
                menu(
                    "Okay..." to Opening.Okay,
                    "A story?" to Opening.AStory,
                    "You tell me a story." to Opening.YouTell,
                    "Not now." to Opening.NotNow,
                )
            when (next) {
                Opening.Okay -> return offerStory()
                Opening.AStory -> {
                    whatStory()
                    when (menu("Okay..." to 1, "What are the Tears of Guthix?" to 2, "Not now" to 3)) {
                        1 -> return offerStory()
                        2 -> {
                            chatPlayer(quiz, "What are the Tears of Guthix?")
                            tearsLore()
                            junaLine(neutral, "Tell me... a story...")
                        }
                        else -> return chatPlayer(neutral, "Not now.")
                    }
                }
                Opening.YouTell -> {
                    chatPlayer(quiz, "You tell me a story.")
                    val lore = choice2("Tell me about the Tears of Guthix", true, "Tell me a new story.", false)
                    if (lore) {
                        chatPlayer(quiz, "Tell me about the Tears of Guthix.")
                        tearsLore()
                    } else {
                        chatPlayer(quiz, "Tell me a new story.")
                        junaLine(neutral, "I have already told you my story, and you have not told me yours!")
                    }
                    junaLine(neutral, "Tell me... a story...")
                }
                Opening.NotNow -> return chatPlayer(neutral, "Not now.")
            }
        }
    }

    private suspend fun Dialogue.offerStory() {
        chatPlayer(neutral, "Okay...")
        if (player.togQuestPoints < QP_REQ) {
            chatPlayer(sad, "Actually, I don't really have one.")
            junaLine(neutral, "Hmm. Maybe you should come back when you do.")
            mesbox("You need $QP_REQ quest points to start the Tears of Guthix quest.")
            return
        }
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(sad, "Actually, I don't really have one.")
            junaLine(neutral, "Hmm. Maybe you should come back when you do.")
            return
        }
        tellAdventures()
        quest.start(access)
        junaLine(neutral, "Your stories have entertained me. I will let you into the cave for a short time.")
        junaLine(neutral, "But first you will need to make a bowl in which to collect the tears.")
        junaLine(
            neutral,
            "There is a cave on the south side of the chasm that is similarly infused with the power " +
                "of Guthix. The stone in that cave is the only substance that can catch the Tears of Guthix.",
        )
        junaLine(
            neutral,
            "Mine some stone from that cave, make it into a bowl, and bring it to me, and then I will " +
                "let you catch the Tears.",
        )
    }

    private suspend fun Dialogue.waitingForBowl() {
        junaLine(
            neutral,
            "Before you can collect the Tears of Guthix you must make a bowl out of the stone in the " +
                "cave on the south of the chasm.",
        )
        if (STONE_BOWL in player.inv) {
            chatPlayer(happy, "I have a bowl.")
            if (access.invDel(access.inv, STONE_BOWL).failure) {
                return
            }
            junaLine(neutral, "I will keep your bowl for you, so that you may collect the tears many times in the future.")
            junaLine(neutral, "Now... tell me another story, and I will let you collect the tears for the first time.")
            quest.complete(access)
            return
        }
        when (menu("But I don't know how to reach the cave!" to 1, "What are the Tears of Guthix?" to 2, "Okay." to 3)) {
            1 -> {
                chatPlayer(worried, "But I don't know how to reach the cave!")
                lightCreatureStory()
            }
            2 -> {
                chatPlayer(quiz, "What are the Tears of Guthix?")
                tearsLore()
            }
            else -> chatPlayer(neutral, "Okay.")
        }
    }

    private suspend fun Dialogue.afterQuest() {
        junaLine(neutral, "Tell me... a story...")
        val reminder =
            if (player.togRemindersOff) {
                "I'd like to receive messages prompting me to return here."
            } else {
                "I don't want any messages reminding me to return here."
            }
        when (
            menu(
                "Okay..." to 1,
                "You tell me a story." to 2,
                reminder to 3,
                "Not now." to 4,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Okay...")
                tellStory(viaStoryOp = false)
            }
            2 -> {
                chatPlayer(quiz, "You tell me a story.")
                junaTellsStory()
            }
            3 -> toggleReminders(reminder)
            else -> chatPlayer(neutral, "Not now.")
        }
    }

    private suspend fun Dialogue.toggleReminders(option: String) {
        chatPlayer(neutral, option)
        if (player.togRemindersOff) {
            player.togRemindersOff = false
            junaLine(neutral, "Very well, when you are eligible to drink from the Tears, you shall be reminded daily.")
        } else {
            player.togRemindersOff = true
            junaLine(
                neutral,
                "Very well, it will be up to you to remember when you can return. It is good that you are " +
                    "willing to take responsibility for yourself in this way.",
            )
        }
    }

    private suspend fun Dialogue.junaTellsStory() {
        when (player.togStoriesHeard) {
            0 -> {
                tearsLore()
                player.togStoriesHeard = 1
            }
            1 -> {
                if (choice2("Tell me about the Tears of Guthix", true, "Tell me a new story.", false)) {
                    chatPlayer(quiz, "Tell me about the Tears of Guthix.")
                    tearsLore()
                } else {
                    lightCreatureStory()
                    player.togStoriesHeard = 2
                }
            }
            2 -> {
                when (
                    menu(
                        "Tell me about the Tears of Guthix" to 1,
                        "Tell me about the light-creatures" to 2,
                        "Tell me a new story." to 3,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Tell me about the Tears of Guthix.")
                        tearsLore()
                    }
                    2 -> {
                        chatPlayer(quiz, "Tell me about the light-creatures.")
                        lightCreatureStory()
                    }
                    else -> {
                        caveGoblinStory()
                        player.togStoriesHeard = 3
                    }
                }
            }
            else -> {
                when (
                    menu(
                        "Tell me about the Tears of Guthix" to 1,
                        "Tell me about the light-creatures" to 2,
                        "Tell me about the Cave Goblins" to 3,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Tell me about the Tears of Guthix.")
                        tearsLore()
                    }
                    2 -> {
                        chatPlayer(quiz, "Tell me about the light-creatures.")
                        lightCreatureStory()
                    }
                    else -> {
                        chatPlayer(quiz, "Tell me about the Cave Goblins.")
                        caveGoblinStory()
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.tellStory(viaStoryOp: Boolean) {
        if (!cave.handsFree(player)) {
            junaLine(neutral, "Perhaps you should empty your hands before you begin.")
            return
        }
        tellAdventures()
        val adventures = rules.hadAdventures(player)
        val days = rules.daysUntilReturn(player)
        if (!adventures) {
            when {
                !viaStoryOp ->
                    junaLine(
                        neutral,
                        "Your story has entertained me. But it is a poor sort of adventurer who only tells " +
                            "stories of the past and does not find new stories to tell. I will not let you " +
                            "into the cave again until you have had more adventures!",
                    )
                days == 0 ->
                    junaLine(
                        neutral,
                        "I fear you will have no new stories to tell me. Come again when you have had " +
                            "more adventures.",
                    )
                else ->
                    junaLine(
                        neutral,
                        "I fear you will have no new stories to tell me. Come again ${returnText(days)} if " +
                            "you have had more adventures by then.",
                    )
            }
            mesbox(
                "You cannot enter the cave again until you have gained either one quest point or " +
                    "${"%,d".format(rules.xpDue(player))} total XP.",
            )
            return
        }
        if (days > 0) {
            junaLine(neutral, "It has not been long since your last visit. Come back ${returnText(days)}.")
            return
        }
        junaLine(neutral, "Your stories have entertained me. I will let you into the cave for a short time.")
        junaLine(
            neutral,
            "Collect as much as you can from the blue streams. If you let in water from the green " +
                "streams, it will take away from the blue. For Guthix is god of balance, and balance lies " +
                "in the juxtaposition of opposites.",
        )
        cave.enter(access)
    }

    private suspend fun Dialogue.tellAdventures() {
        mesbox("You tell Juna some stories of your adventures.")
        val known = JUNA_STORIES.filter { QuestRequirements.hasCompleted(player, it.quest) }
        if (known.isEmpty()) {
            return
        }
        val story = known[access.random.of(maxExclusive = known.size)]
        chatPlayer(neutral, story.told)
        story.reply?.let { junaLine(neutral, it) }
    }

    private suspend fun Dialogue.whatStory() {
        chatPlayer(quiz, "A story?")
        junaLine(
            neutral,
            "I have been waiting here three thousand years, guarding the Tears of Guthix. I serve my " +
                "master faithfully, but I am bored.",
        )
        junaLine(
            neutral,
            "An adventurer such as yourself must have many tales to tell. If you can entertain me, I " +
                "will let you into the cave for a time.",
        )
        junaLine(neutral, "The more I enjoy your story, the more time I will give you in the cave.")
        junaLine(
            neutral,
            "Then you can drink of the power of balance, which will make you stronger in whatever area " +
                "you are weakest.",
        )
    }

    private suspend fun Dialogue.tearsLore() {
        for (line in TEARS_LORE) {
            junaLine(neutral, line)
        }
    }

    private suspend fun Dialogue.lightCreatureStory() {
        junaLine(neutral, "I will tell you the story of the light-creatures.")
        for (line in LIGHT_CREATURE_LORE) {
            junaLine(neutral, line)
        }
    }

    private suspend fun Dialogue.caveGoblinStory() {
        for (line in CAVE_GOBLIN_LORE) {
            junaLine(neutral, line)
        }
    }

    private suspend fun Dialogue.junaLine(mood: MesAnimType, text: String) =
        chatNpcSpecific("Juna", JUNA_CHATHEAD, mood, text)

    private enum class Opening {
        Okay,
        AStory,
        YouTell,
        NotNow,
    }

    private companion object {
        val TEARS_LORE =
            listOf(
                "The Third Age of the world was a time of great conflict, of destruction never seen " +
                    "before or since, when all the gods save Guthix warred for control.",
                "The colossal Wyrms, of whom today's dragons are a pale reflection, turned all the sky to " +
                    "fire, while on the ground armies of foot soldiers, goblins and trolls and humans, " +
                    "filled the valleys and plains with blood.",
                "In time the noise of the conflict woke Guthix from His deep slumber, and He rose and " +
                    "stood in the centre of the battlefield so that the splendour of His wrath filled the " +
                    "world, and He called for the conflict to cease!",
                "Silence fell, for the gods knew that none could challenge the power of the mighty " +
                    "Guthix -- for His power is that of nature itself, to which all other things are " +
                    "subject, in the end.",
                "Guthix reclaimed that which had been stolen from Him, and went back underground to " +
                    "return to His sleep and continue to draw the world's power into Himself.",
                "But on His way into the depths of the earth He sat and rested in this cave; and, " +
                    "thinking of the battle-scarred desert that now stretched from one side of His world " +
                    "to the other, He wept.",
                "And so great was His sorrow, and so great was His life- giving power, that the rocks " +
                    "themselves began to weep with Him.",
                "Later, Guthix noticed that the rocks continued to weep, and that their tears were " +
                    "infused with a small part of His power.",
                "So He set me, His servant, to guard the cave, and He entrusted to me the task of " +
                    "judging who was and was not worthy to access the tears.",
            )

        val LIGHT_CREATURE_LORE =
            listOf(
                "Myriad and beautiful were the creatures and civilizations of the early ages of the " +
                    "world. Gielinor was a work of art, shaped lovingly over the millennia by the creative " +
                    "mind of Guthix.",
                "Only the sturdiest races survived the Godwars, and even then only by abandoning their " +
                    "high culture and gearing their societies towards war. Of the more delicate races " +
                    "there is now no trace, and almost no memory.",
                "One such race had bodies as fragile as snowflakes, yet they built crystal cities that " +
                    "stood for a thousand years.",
                "The wind would whisper through the spires and fill them with sweet harmonies, and the " +
                    "rising sun would shine through the precious gems that studded the towers and create " +
                    "inter plays of light as if rainbows were dancing.",
                "Indeed, so marvellous was this light-show at its height that the patterns of light " +
                    "themselves became alive, and great flocks of luminous creatures rode along the gem- " +
                    "cast beams, each drawn to its own colour.",
                "The creatures you see floating in this chasm are the last sorry remnants of that age. I " +
                    "do not know how they made their way here and survived to this time, but I am grateful " +
                    "for their company.",
            )

        val CAVE_GOBLIN_LORE =
            listOf(
                "Not long after the start of my vigil a party of goblins happened on my cave. These were " +
                    "the ugly brutes of the gods' armies, but their armour bore a faded patch where the " +
                    "symbol of a god had been removed.",
                "They looked around hesitantly, squinting in the light of crude torches, but as soon as " +
                    "they saw me they raised their spears and charged.",
                "No one may access the Tears of Guthix by force. I sent them tumbling into the chasm.",
                "Two hundred years later another goblin found me. This one was unarmed, and although she " +
                    "was very wary I succeeded in engaging her in conversation.",
                "Since then I have had many more visits from the descendants of those lost warriors, and " +
                    "over the centuries I have seen them change.",
                "They have become timid rather than aggressive, and I have seen the light of intelligence " +
                    "grow in their bulging eyes.",
                "Their stories are repetitive and grim, of scraping a living out of the harsh rock and " +
                    "through ingenuity and toil shaping it into a home.",
                "I have followed the progress of their race, but their individual stories hold little " +
                    "interest for me.",
            )
    }
}
