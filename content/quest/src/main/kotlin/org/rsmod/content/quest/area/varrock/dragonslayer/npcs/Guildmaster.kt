package org.rsmod.content.quest.area.varrock.dragonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.dragonslayer.DoorPassage
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAZE_KEY
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.QP_REQUIREMENT
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.RECOMMENDED_MAGIC
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_OZIACH
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.dragonslayer.WallSide
import org.rsmod.content.quest.area.varrock.dragonslayer.sideOf
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Guildmaster of the Champions' Guild, who starts the quest and, once Oziach has set the
 * task, explains how to reach Crandor. The guild's front door only opens for adventurers with
 * enough quest points.
 */
class Guildmaster
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val doors: DoorPassage,
) : PluginScript() {

    private val quest
        get() = dragonSlayer.quest

    private val Player.questPoints by intVarp("varp.qp")

    override fun ScriptContext.startup() {
        onOpNpc1(GUILDMASTER) { startDialogue(it.npc) { guildmaster() } }
        onOpLoc1(GUILD_DOOR) { guildDoor(it.loc) }
    }

    /* The door */

    private suspend fun ProtectedAccess.guildDoor(door: BoundLocInfo) {
        val outside = door.sideOf(player.coords) == WallSide.SOUTH
        if (outside && player.questPoints < QP_REQUIREMENT) {
            mesbox(
                "You have not proved yourself worthy to enter here yet. Only adventurers with " +
                    "at least $QP_REQUIREMENT Quest Points are admitted to the Champions' Guild.",
            )
            return
        }
        doors.walkThrough(this, door, GUILD_DOOR)
    }

    /* Talk-to */

    private suspend fun Dialogue.guildmaster() {
        chatNpc(happy, "Greetings!")
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            dragonSlayer.stage(player) == 0 -> notStarted()
            dragonSlayer.stage(player) == STAGE_STARTED -> beforeOziach()
            dragonSlayer.stage(player) == STAGE_OZIACH -> afterOziach()
            else -> duringQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        when (
            choice2(
                "What is this place?", 1,
                "Can I have a quest?", 2,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> offerQuest()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(happy, "This is the Champions' Guild. Only adventurers who have proved themselves worthy, by gaining influence from quests, are allowed in here.")
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(quiz, "Can I have a quest?")
        chatNpc(laugh, "Aha!")
        chatNpc(happy, "Yes! A mighty and perilous quest, fit only for the most powerful champions! And at the end of it you will earn the right to wear the legendary rune platebody!")
        if (player.combatLevel < RECOMMENDED_COMBAT || player.magicLvl < RECOMMENDED_MAGIC) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT, or one of your skill levels is " +
                    "lower than recommended ($RECOMMENDED_MAGIC Magic).",
            )
        }
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start the Dragon Slayer I quest?",
            )
        ) {
            1 -> {
                chatPlayer(quiz, "So, what is this quest?")
                dragonSlayer.setStage(access, STAGE_STARTED)
                chatNpc(neutral, "You'll have to speak to Oziach, the maker of rune armour. He sets the quests that champions must complete in order to wear it.")
                chatNpc(neutral, "Oziach lives in a hut by the cliffs to the west of Edgeville. He can be a little... odd... sometimes, though.")
            }
            2 -> chatPlayer(neutral, "Actually, I'll give it a miss.")
        }
    }

    private suspend fun Dialogue.beforeOziach() {
        when (
            choice2(
                "What is this place?", 1,
                "Can I have a quest?", 2,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(quiz, "Can I have a quest?")
                chatNpc(quiz, "You're already on a quest for me, if I recall correctly. Have you talked to Oziach yet?")
                chatPlayer(neutral, "No, not yet.")
                chatNpc(neutral, "Well, he's the only one who can grant you the right to wear rune platemail. He lives in a hut by the cliffs west of Edgeville.")
                chatPlayer(happy, "Okay, I'll go and talk to him.")
            }
        }
    }

    private suspend fun Dialogue.afterOziach() {
        when (
            choice2(
                "What is this place?", 1,
                "I talked to Oziach...", 2,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(neutral, "I talked to Oziach and he gave me a quest.")
                chatNpc(quiz, "Oh? What did he tell you to do?")
                chatPlayer(neutral, "Defeat the dragon of Crandor.")
                chatNpc(shocked, "The dragon of Crandor?")
                chatPlayer(worried, "Um, yes...")
                chatNpc(worried, "Goodness, he hasn't given you an easy job, has he?")
                chatPlayer(quiz, "What's so special about this dragon?")
                dragonHistory()
                dragonSlayer.setStage(access, STAGE_BRIEFED)
                journeyBriefing()
            }
        }
    }

    private suspend fun Dialogue.duringQuest() {
        when (
            choice2(
                "What is this place?", 1,
                "About my quest to kill the dragon...", 2,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(quiz, "About my quest to kill the dragon...")
                journeyBriefing()
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        when (
            choice2(
                "What is this place?", 1,
                "Do you have any more quests for me?", 2,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> {
                chatPlayer(quiz, "Do you have any more quests for me?")
                chatNpc(happy, "Not from me. Slaying Elvarg was the greatest deed any champion of this guild has managed. Wear your rune platebody with pride!")
            }
        }
    }

    /* The briefing */

    private suspend fun Dialogue.dragonHistory() {
        chatNpc(neutral, "Thirty years ago, Crandor was a thriving community with a great tradition of mages and adventurers. Many Crandorians even earned the right to join the Champions' Guild!")
        chatNpc(sad, "One of their adventurers went too far, however. He descended into the volcano at the centre of Crandor and woke the dragon Elvarg.")
        chatNpc(neutral, "He must have fought valiantly, because they say that to this day she has a scar down her side, but the dragon still won.")
        chatNpc(sad, "She emerged and laid waste to the whole of Crandor with her fire breath. Some refugees escaped in fishing boats and made camp on the coast north of Rimmington, but the dragon followed and burned the camp to the ground.")
        chatNpc(sad, "Out of all the people of Crandor there were only three survivors: a trio of wizards who used magic to escape. Their names were Thalzar, Lozar and Melzar.")
        dragonSlayer.explainedElvarg.set(player, true)
        dragonSlayer.syncVars(player)
    }

    private suspend fun Dialogue.journeyBriefing() {
        chatNpc(neutral, "If you're serious about taking on Elvarg, first you'll need to get to Crandor, and to my knowledge the only way to do that is by sea.")
        chatPlayer(happy, "So I just sail over there? Seems easy enough.")
        chatNpc(neutral, "If only that were true. The island is surrounded by dangerous reefs. You'll need a map to guide you through them and a ship capable of doing so.")
        chatNpc(neutral, "And then, of course, there's the dragon herself. Once you reach Crandor you'll need some kind of protection against her breath.")
        briefingOptions()
    }

    private suspend fun Dialogue.briefingOptions() {
        while (true) {
            when (
                choice5(
                    "Where can I find a map to Crandor?", 1,
                    "What kind of ship do I need?", 2,
                    "How can I protect myself from the dragon's breath?", 3,
                    "What's so special about this dragon?", 4,
                    "Okay, I'll get going!", 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where can I find a map to Crandor?")
                    chatNpc(neutral, "After what happened, people were afraid of enraging the dragon. Most maps to the island were destroyed, but I have heard that one survived. It was held by Melzar, Thalzar and Lozar.")
                    chatNpc(neutral, "However, the three of them split the map into three pieces, each taking one piece.")
                    if (mapOptions()) {
                        return
                    }
                }
                2 -> {
                    chatPlayer(quiz, "What kind of ship do I need?")
                    chatNpc(neutral, "Quite a specific one, I'm afraid. Even with a map, very few ships could navigate the reefs around the island. You're going to need one of Crandorian design.")
                    chatNpc(neutral, "If there's still one in existence, it's probably in Port Sarim. A ship also needs a captain, though, and I'm not sure where you'd find one willing to sail to Crandor!")
                    dragonSlayer.instructionsShip.set(player, true)
                    dragonSlayer.syncVars(player)
                }
                3 -> {
                    chatPlayer(quiz, "How can I protect myself from the dragon's breath?")
                    chatNpc(happy, "That part shouldn't be too difficult, actually. I believe the Duke of Lumbridge has a special shield in his armoury that is enchanted against dragon's breath.")
                    dragonSlayer.instructionsShield.set(player, true)
                    dragonSlayer.syncVars(player)
                }
                4 -> {
                    chatPlayer(quiz, "What's so special about this dragon?")
                    dragonHistory()
                }
                5 -> {
                    chatPlayer(happy, "Okay, I'll get going!")
                    return
                }
            }
        }
    }

    /** Returns true when the player chose to leave the conversation. */
    private suspend fun Dialogue.mapOptions(): Boolean {
        while (true) {
            when (
                choice4(
                    "Where is Melzar's map piece?", 1,
                    "Where is Thalzar's map piece?", 2,
                    "Where is Lozar's map piece?", 3,
                    "Okay, I'll get going!", 4,
                )
            ) {
                1 -> melzarsPiece()
                2 -> {
                    chatPlayer(quiz, "Where is Thalzar's map piece?")
                    chatNpc(neutral, "Thalzar was the most paranoid of the three wizards. He hid his map piece and took the secret of its location to his grave.")
                    chatNpc(neutral, "I don't think you'd be able to find out where it is by ordinary means. You'll need to talk to the Oracle on Ice Mountain.")
                    dragonSlayer.instructionsOracle.set(player, true)
                    dragonSlayer.syncVars(player)
                }
                3 -> {
                    chatPlayer(quiz, "Where is Lozar's map piece?")
                    chatNpc(neutral, "A few weeks ago I'd have told you to speak to Lozar herself, in her house across the river from Lumbridge.")
                    chatNpc(sad, "Unfortunately, goblin raiders killed her and stole everything. One of the goblins from the Goblin Village probably has the map piece now.")
                    dragonSlayer.instructionsWormbrain.set(player, true)
                    dragonSlayer.syncVars(player)
                }
                4 -> {
                    chatPlayer(happy, "Okay, I'll get going!")
                    return true
                }
            }
        }
    }

    private suspend fun Dialogue.melzarsPiece() {
        chatPlayer(quiz, "Where is Melzar's map piece?")
        chatNpc(neutral, "Melzar built a castle on the site of the Crandorian refugee camp, north of Rimmington. He's locked himself in there and no one has seen him for years.")
        chatNpc(neutral, "The inside of his castle is like a maze, and it's populated by undead monsters. Maybe, if you could get all the way through the maze, you could find his piece of the map.")
        dragonSlayer.instructionsMelzar.set(player, true)
        dragonSlayer.syncVars(player)
        if (player.inv.contains(MAZE_KEY)) {
            chatNpc(neutral, "You already have the key to Melzar's Maze that I gave you.")
            return
        }
        chatNpc(happy, "Adventurers sometimes go in there to prove themselves, so I can give you this key to Melzar's Maze.")
        if (access.invAdd(access.inv, MAZE_KEY).failure) {
            chatNpc(sad, "You don't seem to have room for it. Come back when you have some space.")
            return
        }
        objbox(MAZE_KEY, "The Guildmaster hands you a key.")
    }

    private companion object {
        const val GUILDMASTER = "npc.guildmaster"
        const val GUILD_DOOR = "loc.championdoor"
    }
}
