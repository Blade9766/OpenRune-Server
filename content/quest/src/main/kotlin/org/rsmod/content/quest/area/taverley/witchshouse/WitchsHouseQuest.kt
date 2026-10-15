package org.rsmod.content.quest.area.taverley.witchshouse

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Witch's House.
 *
 * Stages (stored in `varp.ballquest`, endstate 7 from `dbrow.quest_witchshouse`):
 * - [STAGE_STARTED]: the boy has asked for his ball back.
 * - [STAGE_FOUND_MAGNET]: the magnet is out of the basement cupboard.
 * - [STAGE_UNLOCKED_DOOR]: the mouse carried the magnet into the wall and the back door opened.
 * - [STAGE_READ_DIARY]: the diary was read after the door opened. Nora no longer locks the back
 *   door again when she catches the player.
 * - [STAGE_DEFEATED_EXPERIMENT]: the experiment in the shed is dead; the ball can be taken.
 * - [STAGE_COMPLETE]: the ball is back with the boy.
 */
@Singleton
class WitchsHouseQuest : QuestScript(
    "quest_witchshouse",
    "varp.ballquest",
    rewards { xp("stat.hitpoints", 6325.0) },
    ItemRewardDisplay(BALL, zoom = 180),
) {
    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to the <col=800000>boy</col> by the hedges in the north of " +
            "<col=800000>Taverley</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "A <red>boy</red> in Taverley kicked his ball over the hedge into the garden of " +
                    "the scary old lady who lives there. She has locked it in her <red>shed</red>, " +
                    "and he wants me to get it back.",
            ) {}

            objective(
                "The house's front door is locked. The key must be hidden nearby, and I should " +
                    "read anything I find inside for clues about getting into the garden.",
            ) {
                visibleWhen { stage(access.player) in STAGE_STARTED until STAGE_UNLOCKED_DOOR }
                hasItem("witches_doorkey", "I have found the key to the front door.").strike()
            }

            objective(
                "The back door has no lock or handle. It is opened by fitting a magnet to the " +
                    "harness of the <red>mouse</red> that lives in the porch wall, and mice like " +
                    "<red>cheese</red>. The magnet is in a cupboard behind an electrified gate in " +
                    "the <red>basement</red>, so I will need some <red>gloves</red>.",
            ) {
                visibleWhen { stage(access.player) in STAGE_STARTED until STAGE_UNLOCKED_DOOR }
                stageAtLeast(STAGE_FOUND_MAGNET, "I have the magnet from the cupboard.").strike()
            }

            objective(
                "The back door is open. The witch patrols her garden, so I must keep behind the " +
                    "hedges. The diary says the shed key is hidden in the <red>fountain</red>.",
            ) {
                visibleWhen { stage(access.player) in STAGE_UNLOCKED_DOOR until STAGE_DEFEATED_EXPERIMENT }
                hasItem("witches_shedkey", "I have the key to the shed.").strike()
            }

            objective(
                "Something unnatural guards the ball in the shed. The diary called it her " +
                    "<red>experiment</red>, and I will have to defeat it before I can take the ball.",
            ) {
                visibleWhen { stage(access.player) in STAGE_UNLOCKED_DOOR until STAGE_DEFEATED_EXPERIMENT }
            }

            objective(
                "I have destroyed the witch's experiment. I should take the ball from the shed " +
                    "and give it back to the <red>boy</red>, without letting the witch see me.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DEFEATED_EXPERIMENT }
                hasItem("ball", "I have the boy's ball.").strike()
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "A boy in Taverley kicked his ball into the garden of Nora T. Hagg, a witch who " +
                    "locked it away in her shed.",
            )
            line(
                "I found the key under a plant pot, took a magnet from her basement and let her " +
                    "absurd mouse-powered security system open the back door for me.",
            )
            line(
                "Keeping behind the hedges, I found the shed key in the fountain and fought " +
                    "the witch's shapeshifting experiment through all four of its forms.",
            )
            line("The boy has his ball back.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** Nora's spell undoes the back door: the player has to go through the mouse again. */
    fun relockBackDoor(player: Player) {
        if (stage(player) == STAGE_UNLOCKED_DOOR) {
            quest.jumpToStage(player, STAGE_STARTED)
        }
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_FOUND_MAGNET = 2
        const val STAGE_UNLOCKED_DOOR = 3
        const val STAGE_READ_DIARY = 5
        const val STAGE_DEFEATED_EXPERIMENT = 6
        const val STAGE_COMPLETE = 7

        const val RECOMMENDED_COMBAT = 35

        const val BOY = "npc.ballboy"
        const val NORA = "npc.nora_t_hagg"
        const val MOUSE = "npc.witchrat"

        const val BALL = "obj.ball"
        const val DIARY = "obj.witches_diary"
        const val DOOR_KEY = "obj.witches_doorkey"
        const val MAGNET = "obj.magnet"
        const val SHED_KEY = "obj.witches_shedkey"
        const val CHEESE = "obj.cheese"

        /** Where Nora's spell leaves a trespasser: on the path beside the boy. */
        val EVICTION_TILE = CoordGrid(2929, 3456, 0)

        /** The garden around Nora's lawn, reached through the back door, including the fountain. */
        fun inGarden(coords: CoordGrid): Boolean =
            coords.level == 0 &&
                (coords.x in 2901..2933 && coords.z in 3459..3466 && !inPorch(coords) ||
                    coords.x in 2908..2913 && coords.z in 3467..3476)

        /** The little porch between the house and the back door, where the mouse hole is. */
        fun inPorch(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2901..2903 && coords.z in 3466..3467

        fun inShed(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2934..2937 && coords.z in 3459..3467
    }
}
