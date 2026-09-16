package org.rsmod.content.quest.area.zanaris.fairytale1.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.FAIRY_QUEEN
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.FAT_ROCCO
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.FAT_ROCCO_MULTI
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.GODFATHER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.QUEENS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SLIM_LOUIE
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SLIM_LOUIE_MULTI
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_GODFATHER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SENT_TO_ZANARIS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Zanaris throne room. Until Martin sends the player to Zanaris the Fairy Queen sits on her
 * own throne; from then on `varbit.fairy_godfather_check` swaps her, her throne and her guards
 * for the Fairy Godfather, Slim Louie and Fat Rocco. The op hooks go on the multi npcs' base
 * names, because a hook on a transformed name never fires.
 */
class FairyGodfather @Inject constructor(private val fairytale: Fairytale1Quest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GODFATHER) { startDialogue(it.npc) { godfather() } }
        onOpNpc1(FAIRY_QUEEN) { startDialogue(it.npc) { fairyQueen() } }
        for (henchman in listOf(SLIM_LOUIE, SLIM_LOUIE_MULTI)) {
            onOpNpc1(henchman) { startDialogue(it.npc) { slimLouie() } }
        }
        for (henchman in listOf(FAT_ROCCO, FAT_ROCCO_MULTI)) {
            onOpNpc1(henchman) { startDialogue(it.npc) { fatRocco() } }
        }
    }

    private suspend fun Dialogue.godfather() {
        val stage = fairytale.stage(player)
        when {
            fairytale.isComplete(player) -> afterQuest()
            stage == STAGE_HAS_SECATEURS && QUEENS_SECATEURS in player.inv -> handOverSecateurs()
            stage == STAGE_HAS_SECATEURS -> stillWaiting()
            stage == STAGE_SENT_TO_ZANARIS -> firstAudience()
            stage >= STAGE_SEEN_GODFATHER -> brushOff()
            else -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(neutral, "Do I know you? No. Then we have nothing to discuss.")
            }
        }
    }

    private suspend fun Dialogue.firstAudience() {
        chatPlayer(neutral, "Hello. I was expecting to find the Fairy Queen here.")
        chatNpc(
            neutral,
            "The Queen. Everybody wants the Queen. The Queen is taking a little holiday, and " +
                "while she is away, I am looking after her affairs.",
        )
        when (
            choice3(
                "Where's the Fairy Queen?", 1,
                "The crops have stopped growing in my world.", 2,
                "Who are you, exactly?", 3,
            )
        ) {
            1 -> whereIsTheQueen()
            2 -> {
                theCrops()
                whereIsTheQueen()
            }
            3 -> {
                chatPlayer(quiz, "Who are you, exactly?")
                chatNpc(
                    neutral,
                    "I am the Fairy Godfather. My associates here are Slim Louie and Fat Rocco. " +
                        "We take care of things. It is what we do.",
                )
                whereIsTheQueen()
            }
        }
    }

    private suspend fun Dialogue.theCrops() {
        chatPlayer(neutral, "The crops have stopped growing in my world.")
        chatNpc(
            neutral,
            "So I am told. A great sadness. These things happen, and then they stop happening, " +
                "and nobody need ask why.",
        )
    }

    private suspend fun Dialogue.whereIsTheQueen() {
        chatPlayer(quiz, "Where's the Fairy Queen?")
        chatNpc(angry, "I have answered that question. She is on holiday. Do not make me answer it again.")
        chatPlayer(neutral, "Fine. I'll ask somebody else.")
        chatNpc(
            neutral,
            "Ask who you like, irrispettoso. You will hear the same thing from everyone, because " +
                "everyone knows what is good for them.",
        )
        fairytale.advanceTo(access, STAGE_SEEN_GODFATHER)
        access.mes("Neither of the Godfather's associates looks at you as you leave.")
    }

    private suspend fun Dialogue.brushOff() {
        chatPlayer(neutral, "Godfather, about the Fairy Queen...")
        chatNpc(angry, "The Queen is on holiday. This conversation is over.")
    }

    private suspend fun Dialogue.stillWaiting() {
        chatPlayer(neutral, "Godfather.")
        chatNpc(
            neutral,
            "You are still here. There is nothing here for you. Go and pick your flowers " +
                "somewhere else.",
        )
    }

    private suspend fun Dialogue.handOverSecateurs() {
        chatPlayer(happy, "I found these in the Tanglefoot's nest. They're the Queen's secateurs, aren't they?")
        chatNpc(
            sad,
            "...They are. You went into the tunnel. You found the creature. You cut it down with " +
                "a pair of garden shears.",
        )
        chatPlayer(neutral, "They were rather good shears.")
        chatNpc(
            neutral,
            "A misunderstanding, then. The secateurs were mislaid, the Queen fell ill, and while " +
                "she was ill somebody had to sit on the throne. You understand.",
        )
        chatPlayer(neutral, "I understand perfectly.")
        chatNpc(
            neutral,
            "Give them here. Fairy Nuff will have what she needs, the crops of your world will " +
                "grow again, and you and I will say no more about it.",
        )
        access.invDel(access.inv, QUEENS_SECATEURS)
        fairytale.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(neutral, "Godfather.")
        chatNpc(
            neutral,
            "The crops grow. The Queen rests. Everybody is happy. Do not spoil it by asking me " +
                "questions.",
        )
    }

    private suspend fun Dialogue.fairyQueen() {
        chatPlayer(neutral, "Hello, your Majesty.")
        chatNpc(happy, "Welcome to Zanaris, mortal. Everything that grows in your world grows because we tend it. Try to remember that.")
    }

    private suspend fun Dialogue.slimLouie() {
        chatPlayer(neutral, "Hello.")
        if (fairytale.stage(player) < STAGE_SEEN_GODFATHER) {
            chatNpc(neutral, "Keep moving.")
            return
        }
        chatPlayer(quiz, "Do you know where the Fairy Queen is?")
        chatNpc(neutral, "Me? I don't know nothing. I just stand here and look at people.")
        chatNpc(neutral, "You should try it. It's very restful, not knowing nothing.")
    }

    private suspend fun Dialogue.fatRocco() {
        chatPlayer(neutral, "Hello.")
        if (fairytale.stage(player) < STAGE_SEEN_GODFATHER) {
            chatNpc(neutral, "Do I know you? No? Then keep walking.")
            return
        }
        chatPlayer(quiz, "Does the Godfather always answer for the Queen?")
        chatNpc(
            neutral,
            "Somebody's got to. The Queen's not well, see, and when the Queen's not well, " +
                "somebody's got to. That's just how it works.",
        )
        chatNpc(angry, "And that's all I'm saying, so don't go looking at me like that.")
    }
}
