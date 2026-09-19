package org.rsmod.content.quest.area.desert.thegolem

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Golem.
 *
 * The stage is `varbit.golem_a` (bits 0-4 of `varp.golem_quest`, which it shares with unrelated
 * diary reward flags), endstate 10 from `dbrow.quest_golem`. The values follow the cache: the
 * sealed door beneath Uzer turns into the open portal from [STAGE_PORTAL_OPEN].
 * - [STAGE_STARTED]: the golem has asked to be repaired; `varbit.golem_clay` counts the soft clay
 *   used on it and drives which golem the player sees.
 * - [STAGE_REPAIRED]: the golem wants the portal opened. `varbit.golem_b` tracks the trail to the
 *   statuette: 1 letter read, 2 Elissa asked, 3 Varmen's notes read.
 * - [STAGE_ASKED_CURATOR]: the curator has refused to part with the statuette, so his pocket
 *   now holds something worth stealing.
 * - [STAGE_STATUETTE_PLACED]: the fourth statuette is back in its alcove.
 * - [STAGE_PORTAL_OPEN]: all four statuettes face the door and it has ground open.
 * - [STAGE_SEEN_DEMON]: the player has seen Thammaron's skeleton in the throne room.
 * - [STAGE_GOLEM_UNCONVINCED]: the golem refuses to believe the demon is dead.
 *
 * The rest of the quest's memory is on its own cache varbits, which the client reads directly:
 * the alcove statuettes, whether the museum case and the throne have been emptied, whether the
 * golem's skull is open and whether the player has been below the ruins.
 */
@Singleton
class TheGolemQuest : QuestScript(
    QUEST_KEY,
    "varp.golem_quest",
    rewards {
        xp("stat.crafting", CRAFTING_XP)
        xp("stat.thieving", THIEVING_XP)
        extra("Carpet rides to Uzer")
    },
    ItemRewardDisplay(STATUETTE, zoom = 180),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    questVarbit = "varbit.golem_a",
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /** Clears every sub-state varbit when the quest is reset to not started. */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            setVarBit(player, varbit, 0)
        }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>clay golem</col> in the ruined city of " +
            "<col=800000>Uzer</col>, east of the <col=800000>Shantay Pass</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            val side = access.player.golemSide
            objective(
                "I found a badly damaged <red>clay golem</red> in the ruins of Uzer. It needs " +
                    "<red>four pieces of soft clay</red> to repair its body.",
            ) {
                visibleWhen { stage == STAGE_STARTED }
                custom(
                    access.player.golemClay > 0,
                    "I have used ${access.player.golemClay} of the four pieces of soft clay.",
                )
            }
            objective(
                "I repaired the golem. It was built to destroy a great <red>demon</red> that " +
                    "attacked the city, and wants me to open the <red>portal</red> to the demon's " +
                    "lair so it can strike the final blow.",
            ) {
                visibleWhen { stage >= STAGE_REPAIRED }
            }
            objective(
                "The four <red>statuettes</red> in the temple beneath the ruins must be turned to " +
                    "the correct pattern to open it, but the golem doesn't know the pattern.",
            ) {
                visibleWhen { stage in STAGE_REPAIRED until STAGE_PORTAL_OPEN }
                custom(side >= SIDE_READ_LETTER, "A letter in the ruins was addressed to Varmen by his wife Elissa, who works at the <red>Digsite</red>.").strike()
                custom(side >= SIDE_ASKED_ELISSA, "Elissa told me Varmen's expedition notes are on a bookcase in the <red>Exam Centre</red>.").strike()
                custom(side >= SIDE_READ_NOTES, "Varmen took one of the statuettes back with him. It is now in the <red>Varrock Museum</red>.").strike()
                stageAtLeast(STAGE_ASKED_CURATOR, "The <red>curator</red> will not part with it. Perhaps he carries the key to its display case.").strike()
                hasItem(CABINET_KEY, "I have stolen the display cabinet key from the curator.").strike()
                stageAtLeast(STAGE_STATUETTE_PLACED, "I put the statuette back in its alcove. Now to turn all four the right way.")
            }
            objective(
                "All four statuettes face the door, and it has ground open. I should step into the " +
                    "<red>portal</red> and see what lies beyond.",
            ) {
                visibleWhen { stage == STAGE_PORTAL_OPEN }
            }
            objective(
                "The demon <red>Thammaron</red> died of his wounds long ago. I should tell the " +
                    "<red>golem</red> its task is done.",
            ) {
                visibleWhen { stage == STAGE_SEEN_DEMON }
            }
            objective(
                "The golem won't believe me. Varmen's notes say golems take their orders from " +
                    "words written on <red>papyrus</red> with a <red>phoenix feather</red> and a " +
                    "natural ink, placed inside the golem's skull.",
            ) {
                visibleWhen { stage == STAGE_GOLEM_UNCONVINCED }
                hasItem(INK, "I have made some black dye from a black mushroom.").strike()
                hasItem(PEN, "I have a phoenix quill pen.").strike()
                hasItem(PROGRAM, "I have written the golem a new program.").strike()
                custom(access.player.golemHeadOpen, "The golem's skull is open.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "In the ruins of Uzer I found a clay golem, the last survivor of an army built " +
                    "to fight the demon Thammaron, and repaired it with soft clay.",
            )
            line(
                "Following a letter to the Digsite and an archaeologist's notes to the Varrock " +
                    "Museum, I stole back the statuette that unlocks the demon's portal.",
            )
            line(
                "Beyond the portal I found only Thammaron's skeleton. I wrote the golem a new " +
                    "program with a phoenix quill, and its mind is finally at rest.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_golem"

        const val STAGE_STARTED = 1
        const val STAGE_REPAIRED = 2
        const val STAGE_ASKED_CURATOR = 3
        const val STAGE_STATUETTE_PLACED = 4
        const val STAGE_PORTAL_OPEN = 5
        const val STAGE_SEEN_DEMON = 6
        const val STAGE_GOLEM_UNCONVINCED = 7

        const val SIDE_READ_LETTER = 1
        const val SIDE_ASKED_ELISSA = 2
        const val SIDE_READ_NOTES = 3

        const val CLAY_NEEDED = 4

        const val CRAFTING_REQ = 20
        const val THIEVING_REQ = 25
        const val CRAFTING_XP = 1000.0
        const val THIEVING_XP = 1000.0

        const val GOLEM = "npc.golem_golem"
        const val ELISSA = "npc.golem_elissa"
        const val PHOENIX = "npc.golem_phoenix"
        const val CURATOR = "npc.curator"

        const val SOFT_CLAY = "obj.softclay"
        const val LETTER = "obj.golem_letter"
        const val NOTES = "obj.golem_notes"
        const val CABINET_KEY = "obj.golem_statuettekey"
        const val STATUETTE = "obj.golem_statuette"
        const val IMPLEMENT = "obj.golem_golemkey"
        const val MUSHROOM = "obj.golem_mushroom"
        const val FEATHER = "obj.golem_phoenixfeather"
        const val INK = "obj.golem_ink"
        const val PEN = "obj.golem_pen"
        const val PROGRAM = "obj.golem_program"
        const val PAPYRUS = "obj.papyrus"
        const val VIAL = "obj.vial_empty"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"
        const val HAMMER = "obj.hammer"
        const val CHISEL = "obj.chisel"

        const val REPAIR_CLAY_SOUND = "synth.golem_repairclay"
        const val PROGRAM_SOUND = "synth.golem_program"
        const val DEMON_DOOR_SOUND = "synth.golem_demondoor"
        const val TELEPORT_SOUND = "synth.golem_teleport"
        const val TURN_STATUE_SOUND = "synth.turn_statue"

        /** Beside the golem's stairs at the front of the temple, facing into the ruins. */
        val RUINS_ARRIVAL = CoordGrid(3491, 3090, 0)

        /** At the foot of the temple stairs below the ruins. */
        val TEMPLE_ARRIVAL = CoordGrid(2722, 4886, 0)

        /** In the corridor before the demon's door. */
        val DOOR_ARRIVAL = CoordGrid(2721, 4910, 0)

        /** Just inside the throne room, in front of the portal back to Uzer. */
        val THRONE_ROOM_ARRIVAL = CoordGrid(2720, 4885, 2)

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.golem_b",
                "varbit.golem_clay",
                "varbit.golem_statuettestatusa",
                "varbit.golem_statuettestatusb",
                "varbit.golem_statuettestatusc",
                "varbit.golem_statuettestatusd",
                "varbit.golem_head_open",
                "varbit.golem_throne_gems",
                "varbit.golem_retrieved_statuette",
                "varbit.golem_seen_underground",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}
