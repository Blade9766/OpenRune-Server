package org.rsmod.content.quest.area.draynor.vampyreslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.draynor.vampyreslayer.harlowGivenBeer
import org.rsmod.content.quest.area.draynor.vampyreslayer.harlowGivenStake
import org.rsmod.content.quest.area.draynor.vampyreslayer.morganExtraDialogue
import org.rsmod.content.quest.area.draynor.vampyreslayer.morganPostquestDialogue
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Morgan starts the quest from his house north of Ned's. He is a multinpc on
 * `varbit.morgan_postquest_dialogue`, so the Talk-to is bound to the base type.
 */
class Morgan @Inject constructor(private val vampyreSlayer: VampyreSlayerQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.morgan") { startDialogue(it.npc) { morgan() } }
    }

    private suspend fun Dialogue.morgan() {
        when (vampyreSlayer.stage(player)) {
            0 -> notStarted()
            STAGE_COMPLETE -> afterQuest()
            else -> duringQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(shocked, "Could it be? A bold adventurer! Please, you must help us!")
        chatPlayer(quiz, "What is it? What's the problem?")
        chatNpc(
            worried,
            "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his manor " +
                "to the north. Someone finally needs to put a stop to him once and for all!",
        )
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT.",
            )
        }
        when (choice2("Yes.", 1, "No.", 2, title = "Start the Vampyre Slayer quest?")) {
            1 -> startQuest()
            2 -> {
                chatPlayer(
                    worried,
                    "An evil vampyre? I don't think this is the job for me. It sounds far too scary!",
                )
                chatNpc(
                    sad,
                    "I understand... Hopefully someone else will come along to finally save us " +
                        "from this evil.",
                )
            }
        }
    }

    private suspend fun Dialogue.startQuest() {
        chatPlayer(neutral, "Sounds like a job for me. Where should I start?")
        player.morganExtraDialogue = false
        player.harlowGivenBeer = false
        player.harlowGivenStake = false
        player.morganPostquestDialogue = false
        vampyreSlayer.quest.advanceQuestStage(access)
        chatNpc(
            happy,
            "Oh, thank goodness! I've been hoping this day would come for a long time, so I've " +
                "made sure to do my research. I've heard of a retired vampyre hunter called Dr " +
                "Harlow who lives in Varrock.",
        )
        chatNpc(
            neutral,
            "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old " +
                "soak these days, so he spends most of his time in the Blue Moon Inn.",
        )
        when (
            choice2(
                "What else can you tell me about this vampyre?",
                1,
                "Alright, I'll see about paying him a visit.",
                2,
            )
        ) {
            1 -> legends(subject = "this vampyre")
            2 -> payVisit()
        }
    }

    private suspend fun Dialogue.duringQuest() {
        chatNpc(quiz, "Adventurer! Has Count Draynor been dealt with yet?")
        if (player.harlowGivenStake) {
            chatPlayer(neutral, "I'm still working on it, but I could use some garlic to help me out.")
            chatNpc(
                happy,
                "Ah, yes! I heard vampyres didn't like garlic. I keep some in the cupboard " +
                    "upstairs, just in case Count Draynor comes and tries anything!",
            )
            player.morganExtraDialogue = true
            when (choice2("What else can you tell me about Count Draynor?", 1, "Perfect, thanks.", 2)) {
                1 -> legends(subject = "Count Draynor")
                2 -> {
                    chatPlayer(happy, "Perfect, thanks.")
                    chatNpc(neutral, "Good luck in Draynor Manor, adventurer.")
                }
            }
            return
        }
        chatPlayer(neutral, "I'm still working on it.")
        chatNpc(worried, "Please hurry! I'm sure Dr Harlow in Varrock's Blue Moon Inn can help.")
        when (
            choice2(
                "What else can you tell me about Count Draynor?",
                1,
                "Alright, I'll see about paying him a visit.",
                2,
            )
        ) {
            1 -> legends(subject = "Count Draynor")
            2 -> payVisit()
        }
    }

    private suspend fun Dialogue.payVisit() {
        chatPlayer(neutral, "Alright, I'll see about paying him a visit.")
        chatNpc(happy, "Thank you, brave adventurer.")
    }

    private suspend fun Dialogue.legends(subject: String) {
        chatPlayer(quiz, "What else can you tell me about $subject?")
        chatNpc(happy, "All sorts of things! I've studied the legends extensively!")
        chatPlayer(confused, "Wait... legends? What do you mean legends?")
        chatNpc(
            neutral,
            "The legends of Count Draynor! It's said that long ago, he travelled here from dark " +
                "lands in the east. He built Draynor Manor, and from there he began his campaign " +
                "of darkness against our peaceful lands!",
        )
        chatPlayer(
            quiz,
            "Long ago? If this has been going on for so long, why has no one tried to stop him?",
        )
        chatNpc(
            sad,
            "Many have tried. All have failed. Vampyres are not killed easily. Only those with " +
                "the right skills and equipment have any hope of slaying one.",
        )
        chatPlayer(quiz, "Like this Dr Harlow? How come he's never tried to kill this Count Draynor?")
        chatNpc(confused, "I'm... not sure. Perhaps he was always too busy killing other vampyres?")
        chatPlayer(shifty, "Hmm... Something seems amiss here. Are you sure you're telling me everything?")
        chatNpc(
            shifty,
            "Well... it is true that some claim Count Draynor hasn't left his manor for " +
                "generations now.",
        )
        chatPlayer(quiz, "So he's not actually attacked the village?")
        chatNpc(worried, "Er... no. At least, not for a long time! He used to though!")
        chatPlayer(quiz, "When?")
        chatNpc(shifty, "Well... not since I've moved here at least.")
        chatPlayer(
            confused,
            "So if there's no actual danger, why are you so keen for someone to kill this vampyre?",
        )
        chatNpc(
            angry,
            "Because what if he does start attacking again? Are we just meant to live our lives " +
                "constantly fearing the future?",
        )
        chatPlayer(neutral, "It doesn't really sound like anyone is living in fear apart from you.")
        chatNpc(
            angry,
            "More fool them! Count Draynor has cast a shadow over Draynor Village for " +
                "generations. They even named the place after him for Saradomin's sake!",
        )
        chatNpc(
            worried,
            "Even if the people here don't realise it, we will never be at peace until we know " +
                "he's dead!",
        )
        chatPlayer(neutral, "If you're sure... I guess I'll go and see this Dr Harlow.")
        chatNpc(happy, "Thank you, brave adventurer.")
    }

    private suspend fun Dialogue.afterQuest() {
        if (!player.morganPostquestDialogue) {
            chatPlayer(happy, "I have some good news. Count Draynor is no more!")
            chatNpc(
                happy,
                "He's really gone? Finally, we can live without fear! Thank you, thank you! " +
                    "You're a true hero!",
            )
            chatPlayer(happy, "I'm happy to have helped.")
            player.morganPostquestDialogue = true
            return
        }
        chatNpc(happy, "Once again, thank you for slaying that vampyre! You will always be a hero!")
        chatPlayer(happy, "Don't mention it.")
    }
}
