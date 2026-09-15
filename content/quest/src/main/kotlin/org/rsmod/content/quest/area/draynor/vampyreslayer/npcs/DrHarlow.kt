package org.rsmod.content.quest.area.draynor.vampyreslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.BEER
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAGE_SPOKE_TO_HARLOW
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAKE
import org.rsmod.content.quest.area.draynor.vampyreslayer.harlowGivenBeer
import org.rsmod.content.quest.area.draynor.vampyreslayer.harlowGivenStake
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dr Harlow, the retired vampyre hunter drinking in the Blue Moon Inn. He trades a beer for a
 * stake and the know-how to use it, and hands out spares if the stake is lost.
 */
class DrHarlow
@Inject
constructor(
    private val vampyreSlayer: VampyreSlayerQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.dr_harlow") { startDialogue(it.npc) { harlow() } }
    }

    private suspend fun Dialogue.harlow() {
        val stage = vampyreSlayer.stage(player)
        when {
            stage == STAGE_STARTED -> firstMeeting()
            stage == STAGE_SPOKE_TO_HARLOW && player.harlowGivenStake -> afterStake()
            stage == STAGE_SPOKE_TO_HARLOW -> wantsBeer()
            else -> {
                chatNpc(drunk, "Buy me a drink pleassh...")
                chatPlayer(neutral, "I think you've had enough.")
            }
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(drunk, "Buy me a drink pleassh...")
        when (choice2("I need your help dealing with a vampyre.", 1, "I think you've had enough.", 2)) {
            1 -> {
                chatPlayer(neutral, "I need your help dealing with a vampyre.")
                chatNpc(drunk, "A vampyre you shhay...?")
                chatPlayer(neutral, "Not just any vampyre. Count Draynor.")
                vampyreSlayer.quest.advanceQuestStage(access)
                chatNpc(drunk, "Draynor? Well, buy me a beer firsht...")
                chatPlayer(quiz, "Are you sure you've not had enough?")
                chatNpc(drunk, "Huh? No, I don't think ssho. Now, buy ush a beer.")
            }
            2 -> chatPlayer(neutral, "I think you've had enough.")
        }
    }

    private suspend fun Dialogue.wantsBeer() {
        chatNpc(drunk, "Have yoush got me a beer?")
        val hasBeer = player.inv.contains(BEER)
        val firstOption = if (hasBeer) "Yes, here you go." else "I'll go and get you one."
        when (choice2(firstOption, 1, "No. I think you've had enough.", 2)) {
            1 -> if (hasBeer) giveBeer() else {
                chatPlayer(neutral, "I'll go and get you one.")
                chatNpc(drunk, "Cheersh, matey...")
            }
            2 -> {
                chatPlayer(neutral, "No. I think you've had enough.")
                chatNpc(drunk, "No help for yoush then.")
            }
        }
    }

    private suspend fun Dialogue.giveBeer() {
        chatPlayer(happy, "Yes, here you go.")
        if (access.invDel(player.inv, BEER).failure) {
            return
        }
        player.harlowGivenBeer = true
        access.anim(GIVE_BEER_SEQ)
        objbox(BEER, "You give a beer to Dr Harlow.")
        chatNpc(drunk, "Cheersh, matey...")
        chatPlayer(neutral, "Now, about Count Draynor...")
        chatNpc(
            drunk,
            "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh " +
                "and then, some adventurer... theysh go and try to kill him, but none of 'em " +
                "ever comesh back.",
        )
        chatNpc(
            drunk,
            "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... Theysh " +
                "hard to kill. Some... maybe imposhible.",
        )
        chatPlayer(quiz, "So how do I make sure I'm prepared?")
        chatNpc(
            drunk,
            "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the " +
                "easiest ish a stake. Here, You can havesh thish one.",
        )
        handOverStake()
        chatNpc(
            drunk,
            "Takesh that to Draynor Manor. Find the vampyre inshide and show him what for! When " +
                "hesh weak, use a hammer to drive the stake in! Mosht general stores have them.",
        )
        garlicAdvice()
        topics(afterStake = false)
    }

    private suspend fun Dialogue.afterStake() {
        chatNpc(drunk, "Yoush back! Killed that vampyre yet?")
        if (!with(vampyreSlayer) { access.hasStakeAnywhere() }) {
            chatPlayer(sad, "No. I lost that stake you gave me.")
            chatNpc(drunk, "Oh, hangsh on then...")
            handOverStake()
            chatNpc(
                drunk,
                "Good job I've gotsh some spares. Now, takesh that to Draynor Manor. Find the " +
                    "vampyre inshide and show him what for! When hesh weak, use a hammer to drive " +
                    "the stake in! Mosht general stores have them.",
            )
            garlicAdvice()
        }
        topics(afterStake = true)
    }

    private suspend fun Dialogue.handOverStake() {
        player.invAddOrDrop(objRepo, STAKE)
        player.harlowGivenStake = true
        objbox(STAKE, "Dr Harlow hands you a stake.")
    }

    private suspend fun Dialogue.garlicAdvice() {
        chatNpc(
            drunk,
            "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.",
        )
        chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
    }

    /**
     * The questions the player can ask once they have the stake. Straight after the beer the
     * reminder only appears once a question has been asked; on later visits it is always there.
     */
    private suspend fun Dialogue.topics(afterStake: Boolean) {
        var askedSeen = false
        var askedHunter = false
        while (true) {
            val options = mutableListOf<Pair<String, Topic>>()
            if (afterStake || askedSeen || askedHunter) {
                options += "What do I need to do again?" to Topic.Reminder
            }
            if (afterStake || !askedSeen) {
                options += "You said no one's ever seen Count Draynor?" to Topic.Seen
            }
            if (afterStake || !askedHunter) {
                options += "So you were once a proper vampyre hunter?" to Topic.Hunter
            }
            val leave = if (afterStake) "Not yet. I'd best get going." else "I'd best get going."
            options += leave to Topic.Leave
            when (choose(options)) {
                Topic.Reminder -> reminder()
                Topic.Seen -> {
                    neverSeen()
                    askedSeen = true
                }
                Topic.Hunter -> {
                    formerHunter()
                    askedHunter = true
                }
                Topic.Leave -> {
                    chatPlayer(neutral, leave)
                    chatNpc(drunk, "Good luck, matey...")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second)
            3 ->
                choice3(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                )
            else ->
                choice4(
                    options[0].first, options[0].second,
                    options[1].first, options[1].second,
                    options[2].first, options[2].second,
                    options[3].first, options[3].second,
                )
        }

    private suspend fun Dialogue.reminder() {
        chatPlayer(quiz, "What do I need to do again?")
        chatNpc(
            drunk,
            "Go to Draynor Manor. Find the vampyre inshide and show him what for! When hesh weak, " +
                "use a hammer to drive that stake I gave you in! Mosht general stores have them.",
        )
        garlicAdvice()
    }

    private suspend fun Dialogue.neverSeen() {
        chatPlayer(quiz, "You said no one's ever seen Count Draynor?")
        chatNpc(drunk, "No, butsh every now and then someonesh tried to kill him.")
        chatPlayer(
            quiz,
            "But they've never come back? Meaning someone or something killed them most likely. " +
                "Presumably Count Draynor?",
        )
        chatNpc(drunk, "Yesh, I'd say so.")
        chatPlayer(quiz, "So why does he never leave his manor?")
        chatNpc(drunk, "Who knowsh. Most likely he's grown too weak to do ssho.")
        chatPlayer(quiz, "Weak? So he shouldn't be too hard to kill then?")
        chatNpc(
            drunk,
            "Even a weak vampyre ish very dangerous. Just go ashk those other adventurers thatsh " +
                "never came back.",
        )
    }

    private suspend fun Dialogue.formerHunter() {
        chatPlayer(quiz, "So you were once a proper vampyre hunter?")
        chatNpc(
            drunk,
            "Yesh! I travelled their homeland to the easht. Morytania! I killed lotsh of vampyres " +
                "over there!",
        )
        chatPlayer(quiz, "What was the most dangerous vampyre you killed?")
        chatNpc(drunk, "Dangerous? Oh, well... Ish never killed one of the really dangerous ones.")
        chatPlayer(quiz, "Why not?")
        chatNpc(drunk, "Can't. Theysh too strong. Nothing cansh hurt them.")
        chatPlayer(worried, "Oh... But what if Count Draynor's like that?")
        chatNpc(
            drunk,
            "Yoush don't get many vampyres around here. Pretty much none, actshually. There's " +
                "speshial magic that stops them coming over here. Any that are here... theysh " +
                "weak... and trapped.",
        )
        chatPlayer(quiz, "So how come you've never tried to kill him yourself?")
        chatNpc(
            drunk,
            "Why bother? Hesh not killed anyone in a long time, so no one really caresh about him " +
                "that much, apart from the odd adventurer thatsh looking to prove themshelves.",
        )
        chatPlayer(neutral, "I see. Fair enough.")
    }

    private enum class Topic {
        Reminder,
        Seen,
        Hunter,
        Leave,
    }

    private companion object {
        const val GIVE_BEER_SEQ = "seq.vampireslayer_give_beer"
    }
}
