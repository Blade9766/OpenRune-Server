package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.HAND_IN_ITEMS
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.LAVA_EEL
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.RAW_LAVA_EEL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Achietties, the heroine who guards the door of the Heroes' Guild. She starts the quest, takes
 * the three items, and is who a player speaks to when the guild door will not open for them.
 */
@Singleton
class AchiettiesDialogue
@Inject
constructor(private val heroes: HeroesQuest, private val search: NpcSearch) {
    /** What the Heroes' Guild door does for a player who has not finished the quest. */
    suspend fun ProtectedAccess.atGuildDoor() {
        val npc = search.find(coords, ACHIETTIES, DOOR_RADIUS, HuntVis.Off)
        if (npc == null) {
            mes("The door is locked.")
            return
        }
        startDialogue(npc) { achietties(heroes) }
    }

    suspend fun Dialogue.talk() {
        achietties(heroes)
    }

    private suspend fun Dialogue.achietties(heroes: HeroesQuest) {
        chatNpc(neutral, "Greetings. Welcome to the Heroes' Guild.")
        when {
            heroes.isComplete(player) -> return
            heroes.isStarted(player) -> progress(heroes)
            else -> applicant(heroes)
        }
    }

    private suspend fun Dialogue.applicant(heroes: HeroesQuest) {
        chatNpc(neutral, "Only the greatest heroes of this land may gain entrance to this guild.")
        if (heroes.meetsStartRequirements(player) && !heroes.hasRequiredSkills(player)) {
            mesbox(
                "Before starting this quest, be aware that one or more of your skill levels are " +
                    "lower than what is required to fully complete it.",
            )
        }
        val apply =
            choice2(
                "I'm a hero. May I apply to join?",
                true,
                "Good for the foremost heroes of the land.",
                false,
                title = "Start the Heroes' Quest?",
            )
        if (!apply) {
            chatPlayer(neutral, "Good for the foremost heroes of the land.")
            chatNpc(neutral, "Yes. Yes it is.")
            return
        }
        chatPlayer(quiz, "I'm a hero. May I apply to join?")
        if (!heroes.hasQuestPoints(player)) {
            chatNpc(
                confused,
                "You're a hero? I've never heard of YOU. You are required to possess at least " +
                    "${HeroesQuest.REQUIRED_QUEST_POINTS} quest points to file an application.",
            )
            requiredQuests()
            return
        }
        if (!heroes.hasRequiredQuests(player)) {
            chatNpc(
                neutral,
                "Well, you have a lot of quest points, but to be deemed worthy you must also " +
                    "have proved yourself on some of the land's greater quests.",
            )
            requiredQuests()
            return
        }
        chatNpc(
            neutral,
            "Well, you seem to meet our initial requirements, so you may now begin the tasks to " +
                "earn membership in the Heroes' Guild.",
        )
        heroes.start(access)
        chatNpc(
            neutral,
            "The three items required for entrance are: An Entranan Firebird feather, a Master " +
                "Thieves' armband, and a cooked Lava Eel.",
        )
        hints()
    }

    private suspend fun Dialogue.requiredQuests() {
        chatNpc(
            neutral,
            "Additionally you must have completed the Shield of Arrav, Lost City, Merlin's " +
                "Crystal and Dragon Slayer quests.",
        )
    }

    private suspend fun Dialogue.progress(heroes: HeroesQuest) {
        chatNpc(quiz, "How goes thy quest adventurer?")
        if (HAND_IN_ITEMS.all { access.inv.count(it) > 0 }) {
            handIn(heroes)
            return
        }
        if (access.inv.count(RAW_LAVA_EEL) > 0 && access.inv.count(LAVA_EEL) == 0) {
            chatPlayer(happy, "I have a raw Lava Eel!")
            chatNpc(neutral, "That's nice. I require it to be cooked however.")
            chatPlayer(sad, "Oh. Okay then.")
            return
        }
        chatPlayer(sad, "It's tough. I've not done it yet.")
        chatNpc(neutral, "Remember, the items you need to enter are:")
        chatNpc(
            neutral,
            "An Entranan Firebirds' feather, A Master Thieves armband, and a cooked Lava Eel.",
        )
        hints()
    }

    private suspend fun Dialogue.handIn(heroes: HeroesQuest) {
        chatPlayer(happy, "I have all the required items.")
        chatNpc(
            neutral,
            "I see that you have. Well done; Now, to complete the quest, and gain entry to the " +
                "Heroes' Guild in your final task all that you have to do is...",
        )
        chatPlayer(shocked, "W-what? What do you mean? There's MORE???")
        chatNpc(
            laugh,
            "I'm sorry, I was just having a little fun with you. Just a little Heroes' Guild " +
                "humour there. What I really meant was",
        )
        chatNpc(
            happy,
            "Congratulations! You have completed the Heroes' Guild entry requirements! You will " +
                "find the door now open for you! Enter, Hero! And take this reward!",
        )
        if (!HAND_IN_ITEMS.all { access.inv.count(it) > 0 }) {
            return
        }
        for (item in HAND_IN_ITEMS) {
            access.invDel(access.inv, item)
        }
        heroes.complete(access)
    }

    private suspend fun Dialogue.hints() {
        val topic =
            choice4(
                "Any hints on getting the armband?",
                1,
                "Any hints on getting the feather?",
                2,
                "Any hints on getting the eel?",
                3,
                "I'll start looking for all those things then.",
                4,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "Any hints on getting the thieves armband?")
                chatNpc(neutral, "I'm sure you have the relevant contacts to find out about that.")
            }
            2 -> {
                chatPlayer(quiz, "Any hints on getting the feather?")
                chatNpc(
                    neutral,
                    "Not really - other than Entranan firebirds tend to live on Entrana.",
                )
            }
            3 -> {
                chatPlayer(quiz, "Any hints on getting the eel?")
                chatNpc(neutral, "Maybe go and find someone who knows a lot about fishing?")
            }
            else -> {
                chatPlayer(neutral, "I'll start looking for all those things then.")
                chatNpc(neutral, "Good luck with that.")
            }
        }
    }

    private companion object {
        const val ACHIETTIES = "npc.achietties"
        const val DOOR_RADIUS = 10
    }
}

class Achietties @Inject constructor(private val dialogue: AchiettiesDialogue) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.achietties") { startDialogue(it.npc) { with(dialogue) { talk() } } }
    }
}
