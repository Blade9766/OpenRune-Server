package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rank-and-file goblins of Goblin Village, half in red mail for Bentnoze and half in green
 * for Wartface. They bicker about it out loud all day, tell the player which colour is best
 * during the quest, and afterwards mostly want a fight.
 */
class VillageGoblins
@Inject
constructor(
    private val goblinDiplomacy: GoblinDiplomacyQuest,
    private val random: GameRandom,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val quest
        get() = goblinDiplomacy.quest

    override fun ScriptContext.startup() {
        for (type in RED_GOBLINS) {
            onOpNpc1(type) { startDialogue(it.npc) { goblin(it.npc, Side.RED) } }
            onAiTimer(type) { npc.shout(Side.RED) }
        }
        for (type in GREEN_GOBLINS) {
            onOpNpc1(type) { startDialogue(it.npc) { goblin(it.npc, Side.GREEN) } }
            onAiTimer(type) { npc.shout(Side.GREEN) }
        }
    }

    /** The in-fighting: every so often a goblin yells its side's colour at the others. */
    private fun Npc.shout(side: Side) {
        aiTimer(random.of(SHOUT_MIN_TICKS..SHOUT_MAX_TICKS))
        say(side.shouts.random())
    }

    private suspend fun Dialogue.goblin(npc: Npc, side: Side) {
        if (quest.isQuestCompleted(player)) {
            afterQuest(npc, side)
        } else {
            duringQuest(side)
        }
    }

    private suspend fun Dialogue.duringQuest(side: Side) {
        when (random.of(1..3)) {
            1 -> {
                chatNpc(angry, "${side.colour.replaceFirstChar { it.uppercase() }} armour best!")
                when (
                    choice2(
                        "Why is ${side.colour} best?", 1,
                        "Err, okay.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Why is ${side.colour} best?")
                        chatNpc(neutral, "Cos ${side.general} says so, and he bigger than me.")
                    }
                    2 -> chatPlayer(confused, "Err, okay.")
                }
            }
            2 -> {
                chatNpc(angry, "${side.colour.replaceFirstChar { it.uppercase() }} armour best!")
                chatNpcSpecific("Goblin", side.other.head, angry, "${side.other.colour.replaceFirstChar { it.uppercase() }} armour best!")
            }
            else -> {
                chatPlayer(quiz, "Why are you fighting?")
                chatNpc(angry, "He wearing ${side.other.colour} armour! ${side.general} tell us wear ${side.colour}!")
                chatNpcSpecific("Goblin", side.other.head, angry, "But ${side.other.general} say we must wear ${side.other.colour}!")
            }
        }
    }

    private suspend fun Dialogue.afterQuest(npc: Npc, side: Side) {
        when (random.of(1..5)) {
            1 -> {
                chatNpc(angry, "I kill you human!")
                attack(npc)
            }
            2 -> {
                chatNpc(angry, "Go away smelly human!")
                attack(npc)
            }
            3 -> {
                chatNpc(quiz, "What you doing here?")
                when (
                    choice2(
                        "I'm here to kill all you goblins!", 1,
                        "I'm just looking around.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(angry, "I'm here to kill all you goblins!")
                        chatNpc(angry, "I kill you!")
                        attack(npc)
                    }
                    2 -> {
                        chatPlayer(neutral, "I'm just looking around.")
                        chatNpc(neutral, "Me not sure that allowed. You have to check with generals.")
                    }
                }
            }
            4 -> newCentury(npc)
            else -> {
                chatNpc(happy, "Brown armour best!")
                when (
                    choice3(
                        "Why is brown best?", 1,
                        "But you're still wearing ${side.colour}!", 2,
                        "Err, okay.", 3,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Why is brown best?")
                        chatNpc(neutral, "General Wartface and General Bentnoze both say it is. And normally they never agree!")
                    }
                    2 -> {
                        chatPlayer(quiz, "But you're still wearing ${side.colour} armour!")
                        chatNpc(shifty, side.excuse)
                    }
                    3 -> chatPlayer(confused, "Err, okay.")
                }
            }
        }
    }

    private suspend fun Dialogue.newCentury(npc: Npc) {
        chatNpc(happy, "Happy goblin new century!")
        when (
            choice2(
                "Happy new century!", 1,
                "What is the goblin new century?", 2,
            )
        ) {
            1 -> chatPlayer(happy, "Happy new century!")
            2 -> {
                chatPlayer(quiz, "What is the goblin new century?")
                chatNpc(neutral, "Goblin century mark year of big battle on Plain of Mud. That when Big High War God give us commandments.")
                when (
                    choice4(
                        "Who is the Big High War God?", 1,
                        "What are the goblin commandments?", 2,
                        "Where is the Plain of Mud?", 3,
                        "I need to go.", 4,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Who is the Big High War God?")
                        chatNpc(happy, "Big High War God take goblins and make them strong! Without him, we small and weak and stupid. But thanks to Big High War God, we most powerful race in Gielinor!")
                        chatPlayer(confused, "Umm... you're clearly not the most powerful race in Gielinor.")
                        chatNpc(angry, "Not to doubt word of Big High War God! I kill you!")
                        attack(npc)
                    }
                    2 -> {
                        chatPlayer(quiz, "What are the goblin commandments?")
                        chatNpc(angry, "Slay enemies of Big High War God! Not show mercy! Not run from battle! Not doubt word of Big High War God!")
                        chatNpc(neutral, "Without commandments we not know right from wrong. We be like other races who not know war is good!")
                        attack(npc)
                    }
                    3 -> {
                        chatPlayer(quiz, "Where is the Plain of Mud?")
                        chatNpc(angry, "That goblin secret! No human ever find Plain of Mud!")
                        attack(npc)
                    }
                    4 -> chatPlayer(neutral, "I need to go.")
                }
            }
        }
    }

    private fun Dialogue.attack(npc: Npc) {
        npc.opPlayer2(player, aiInteractions)
    }

    private enum class Side(
        val colour: String,
        val general: String,
        val head: String,
        val excuse: String,
        val shouts: List<String>,
    ) {
        RED(
            colour = "red",
            general = "General Bentnoze",
            head = "npc.goblin_red_soldier_2",
            excuse = "It not red, it just red-ish brown!",
            shouts = listOf("Red armour best!", "Red not green!", "Red red red!", "Stupid greenie!", "Green armour stupid!"),
        ),
        GREEN(
            colour = "green",
            general = "General Wartface",
            head = "npc.goblin_green_soldier_2",
            excuse = "Um... my brown armour getting wash.",
            shouts = listOf("Green!", "Green armour best!", "Green not red!", "Stupid reddie!", "Red armour stupid!"),
        );

        val other: Side
            get() = if (this == RED) GREEN else RED
    }

    private companion object {
        val RED_GOBLINS =
            listOf("npc.goblin_red_soldier_1", "npc.goblin_redarmour") +
                (2..8).map { "npc.goblin_red_soldier_$it" }
        val GREEN_GOBLINS =
            listOf("npc.goblin_greenarmour") + (2..8).map { "npc.goblin_green_soldier_$it" }

        const val SHOUT_MIN_TICKS = 25
        const val SHOUT_MAX_TICKS = 80
    }
}
