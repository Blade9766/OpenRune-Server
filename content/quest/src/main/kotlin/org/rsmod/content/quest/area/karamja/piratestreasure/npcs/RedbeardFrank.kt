package org.rsmod.content.quest.area.karamja.piratestreasure.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.BANANA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CASKET
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.KARAMJA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.PIRATE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.REDBEARD_FRANK
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.SLICED_BANANA_RUM
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Redbeard Frank, outside the Rusty Anchor, starts the quest and trades the rum for Hector's key. */
class RedbeardFrank
@Inject
constructor(private val treasure: PiratesTreasureQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(REDBEARD_FRANK) { startDialogue(it.npc) { frank() } }
        onOpNpcU(REDBEARD_FRANK, KARAMJA_RUM) { startDialogue(it.npc) { offerRum() } }
        onOpNpcU(REDBEARD_FRANK, BANANA_RUM) { startDialogue(it.npc) { bananaRum(BANANA_RUM) } }
        onOpNpcU(REDBEARD_FRANK, SLICED_BANANA_RUM) {
            startDialogue(it.npc) { bananaRum(SLICED_BANANA_RUM) }
        }
        onOpNpcU(REDBEARD_FRANK, CASKET) { startDialogue(it.npc) { offerShare() } }
    }

    private suspend fun Dialogue.frank() {
        chatNpc(happy, "Arr, Matey!")
        val stage = treasure.stage(player)
        when {
            stage == 0 -> notStarted()
            stage == STAGE_STARTED -> askForRum()
            needsSpareKey(stage) -> spareKey()
            else -> smallTalk()
        }
    }

    private suspend fun Dialogue.notStarted() {
        while (true) {
            val topic =
                choice3(
                    "I'm in search of treasure.",
                    Topic.Treasure,
                    "Arr!",
                    Topic.Arr,
                    "Do you have anything for trade?",
                    Topic.Trade,
                )
            when (topic) {
                Topic.Treasure -> return searchOfTreasure()
                Topic.Arr -> arr()
                Topic.Trade -> return trade()
            }
        }
    }

    private suspend fun Dialogue.searchOfTreasure() {
        chatPlayer(happy, "I'm in search of treasure.")
        chatNpc(
            happy,
            "Arr, treasure you be after eh? Well I might be able to tell you where to find " +
                "some... For a price...",
        )
        chatPlayer(quiz, "What sort of price?")
        chatNpc(
            neutral,
            "Well for example if you can get me a bottle of rum... Not just any rum mind...",
        )
        chatNpc(
            happy,
            "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!",
        )
        if (Quest.get(RUM_DEAL)?.isQuestCompleted(player) == true) {
            chatPlayer(quiz, "Would some Braindeath 'Rum' do?")
            chatNpc(angry, "Gadzooks, that swill! Not a chance!")
            chatNpc(neutral, "It's Karamja Rum or no deal.")
        }
        if (!choice2("Yes.", true, "No.", false, title = "Start the Pirate's Treasure quest?")) {
            chatPlayer(neutral, "Not right now.")
            chatNpc(
                neutral,
                "Fair enough. I'll still be here and thirsty whenever you feel like helpin' out.",
            )
            return
        }
        chatPlayer(happy, "Ok, I will bring you some rum.")
        treasure.quest.advanceQuestStage(access)
        chatNpc(happy, "Yer a saint, although it'll take a miracle to get it off Karamja.")
        chatPlayer(quiz, "What do you mean?")
        customsWarning()
        chatPlayer(neutral, "Well I'll give it a shot.")
        chatNpc(happy, "Arr, that's the spirit!")
    }

    private suspend fun Dialogue.askForRum() {
        chatNpc(quiz, "Have ye brought some rum for yer ol' mate Frank?")
        val inv = access.inv
        when {
            inv.contains(KARAMJA_RUM) -> {
                chatPlayer(happy, "Yes, I've got some.")
                handOverRum()
            }
            inv.contains(BANANA_RUM) -> {
                chatPlayer(happy, "Yes, I've got some.")
                chatNpc(angry, "Arr - this here rum's got a banana stuck in it!")
            }
            inv.contains(SLICED_BANANA_RUM) -> {
                chatPlayer(happy, "Yes, I've got some.")
                chatNpc(angry, "Arr - I don't likes banana in me rum!")
            }
            else -> {
                chatPlayer(sad, "No, not yet.")
                chatNpc(neutral, "Not surprising, tis no easy task to get it off Karamja.")
                chatPlayer(quiz, "What do you mean?")
                customsWarning()
                chatPlayer(neutral, "Well I'll give it another shot.")
            }
        }
    }

    private suspend fun Dialogue.offerRum() {
        when (treasure.stage(player)) {
            STAGE_STARTED -> {
                chatPlayer(happy, "Frank, I have some Karamja Rum.")
                handOverRum()
            }
            0 -> {
                chatNpc(happy, "Arr, Matey!")
                notStarted()
            }
            else ->
                chatNpc(
                    happy,
                    "Thanks fer the thought, but I still have to finish this bottle.",
                )
        }
    }

    private suspend fun Dialogue.handOverRum() {
        if (access.invDel(access.inv, KARAMJA_RUM, 1).failure) {
            return
        }
        access.invAddOrDrop(objRepo, CHEST_KEY)
        treasure.quest.setQuestStage(access, STAGE_KEY)
        chatNpc(
            happy,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a " +
                "pirate captain called One-Eyed Hector.",
        )
        chatNpc(
            neutral,
            "Hector were very successful and became very rich. But about a year ago we were " +
                "boarded by the Customs and Excise Agents.",
        )
        chatNpc(
            sad,
            "Hector were killed along with many of the crew, I were one of the few to escape " +
                "and I escaped with this.",
        )
        objbox(CHEST_KEY, "Frank happily takes the rum... and hands you a key.")
        chatNpc(
            neutral,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue Moon " +
                "Inn in Varrock.",
        )
        chatNpc(happy, "With any luck his treasure will be in there.")
        val why =
            choice2(
                "Ok thanks, I'll go and get it.",
                false,
                "So why didn't you ever get it?",
                true,
            )
        if (!why) {
            chatPlayer(happy, "Ok thanks, I'll go and get it.")
            return
        }
        chatPlayer(quiz, "So why didn't you ever get it?")
        chatNpc(
            sad,
            "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.",
        )
    }

    private fun Dialogue.needsSpareKey(stage: Int): Boolean {
        if (access.inv.contains(CHEST_KEY)) {
            return false
        }
        return stage == STAGE_KEY ||
            (stage == STAGE_MESSAGE && !access.inv.contains(PIRATE_MESSAGE))
    }

    private suspend fun Dialogue.spareKey() {
        chatPlayer(sad, "I seem to have lost my chest key...")
        chatNpc(laugh, "Arr, silly you. Fortunately I took the precaution to have another one made.")
        access.invAddOrDrop(objRepo, CHEST_KEY)
        objbox(CHEST_KEY, "Frank hands you a chest key.")
        smallTalk()
    }

    private suspend fun Dialogue.smallTalk() {
        while (true) {
            if (choice2("Arr!", true, "Do you have anything for trade?", false)) {
                arr()
                continue
            }
            return trade()
        }
    }

    private suspend fun Dialogue.arr() {
        chatPlayer(laugh, "Arr!")
        chatNpc(laugh, "Arr!")
    }

    private suspend fun Dialogue.trade() {
        chatPlayer(quiz, "Do you have anything for trade?")
        chatNpc(
            neutral,
            "Nothin' at the moment, but then again the Customs Agents are on the warpath right now.",
        )
    }

    private suspend fun Dialogue.customsWarning() {
        chatNpc(
            neutral,
            "The Customs office has been clampin' down on the export of spirits. You seem like a " +
                "resourceful young ${if (isLad()) "lad" else "lass"}, I'm sure ye'll be able to " +
                "find a way to slip the stuff past them.",
        )
    }

    private suspend fun Dialogue.bananaRum(rum: String) {
        if (treasure.stage(player) != STAGE_STARTED) {
            chatNpc(happy, "Thanks fer the thought, but I still have to finish this bottle.")
            return
        }
        chatPlayer(happy, "Frank, I have some Karamja Rum.")
        if (rum == BANANA_RUM) {
            chatNpc(angry, "Arr - this here rum's got a banana stuck in it!")
        } else {
            chatNpc(angry, "Arr - I don't likes banana in me rum!")
        }
    }

    private suspend fun Dialogue.offerShare() {
        chatPlayer(happy, "I have the treasure, would you like a share?")
        chatNpc(happy, "No ${if (isLad()) "lad" else "lass"}, you got it fair and square.")
        chatNpc(happy, "You enjoy it. It's what Hector would have wanted.")
    }

    private fun Dialogue.isLad(): Boolean = player.appearance.bodyType == Constants.bodytype_a

    private enum class Topic {
        Treasure,
        Arr,
        Trade,
    }

    private companion object {
        const val RUM_DEAL = "quest_rumdeal"
    }
}
