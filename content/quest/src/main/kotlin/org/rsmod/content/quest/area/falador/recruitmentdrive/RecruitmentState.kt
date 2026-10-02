package org.rsmod.content.quest.area.falador.recruitmentdrive

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/** The attempt's room order, as [RoomOrder] index + 1 (0 when no attempt is under way). */
internal var Player.rdOrder by intVarBit("varbit.rd_order")
internal var Player.rdSpokeToTiffy by intVarBit("varbit.rd_spoke_to_tiffy")

internal var Player.rdStatueAnswer by intVarBit("varbit.rd_statue_answer")
internal var Player.rdStatuePhase by intVarBit("varbit.rd_statue_phase")
internal var Player.rdStatueLayout by intVarBit("varbit.rd_room_order")

internal var Player.rdRiddle by intVarBit("varbit.rd_riddle")
internal var Player.rdRiddleClue by intVarBit("varbit.rd_riddle_clue")
internal var Player.rdLogicRiddle by intVarBit("varbit.rd_logic_riddle")
internal var Player.rdPatiencePhase by intVarBit("varbit.rd_patience_phase")
internal var Player.rdPatienceTicks by intVarBit("varbit.rd_patience_ticks")
internal var Player.rdCheeversTalks by intVarBit("varbit.rd_cheevers_talks")
internal var Player.rdSpishyusTalks by intVarBit("varbit.rd_room1_intro_chat")

/**
 * The saved attempt: the room order on `varbit.rd_order` and the passed rooms on the cache's
 * `varbit.rd_roomN_complete`. Both are permanent, so passed rooms survive a logout or a death and the
 * player resumes at the first room not yet passed. Failing a room or leaving through an entrance
 * portal clears them.
 */
object RecruitmentState {
    const val STATUE_MEMORISING = 1
    const val STATUE_READY = 2

    const val PATIENCE_WAITING = 1
    const val PATIENCE_PASSED = 2

    /** Every var the rooms keep while the player is inside one; cleared between rooms. */
    val ROOM_VARBITS =
        listOf(
            "varbit.rd_statue_answer",
            "varbit.rd_statue_phase",
            "varbit.rd_room_order",
            "varbit.rd_riddle",
            "varbit.rd_riddle_clue",
            "varbit.rd_logic_riddle",
            "varbit.rd_patience_phase",
            "varbit.rd_patience_ticks",
            "varbit.rd_cheevers_talks",
            "varbit.rd_room1_intro_chat",
            "varbit.rd_lock_a",
            "varbit.rd_lock_b",
            "varbit.rd_lock_c",
            "varbit.rd_lock_d",
            "varbit.rd_foxleft",
            "varbit.rd_foxright",
            "varbit.rd_chickleft",
            "varbit.rd_chickright",
            "varbit.rd_grainleft",
            "varbit.rd_grainright",
            "varbit.rd_room6_stone_door",
            "varbit.rd_react_on_spade",
            "varbit.rd_gypsum_in_tin",
            "varbit.rd_water_in_tin",
            "varbit.rd_room6_hint1",
            "varbit.rd_got_vinegar",
            "varbit.rd_got_water",
            "varbit.rd_got_copsulph",
            "varbit.rd_got_gypsum",
            "varbit.rd_got_salt",
            "varbit.rd_got_n2o",
            "varbit.rd_got_tin",
            "varbit.rd_got_copore",
            "varbit.rd_spare_water",
        )

    fun order(player: Player): List<TestRoom>? {
        val code = player.rdOrder
        return if (code == 0) null else RoomOrder.decode(code - 1)
    }

    fun setOrder(player: Player, order: List<TestRoom>) {
        player.rdOrder = RoomOrder.encode(order) + 1
    }

    fun passed(player: Player, room: TestRoom): Boolean = player.vars[room.completeVarbit] == 1

    fun markPassed(player: Player, room: TestRoom) {
        set(player, room.completeVarbit, 1)
    }

    fun passedCount(player: Player): Int = order(player)?.count { passed(player, it) } ?: 0

    fun allPassed(player: Player): Boolean = order(player) != null && passedCount(player) == RoomOrder.SIZE

    /** The first room of the attempt not yet passed, or null when there is none or all are passed. */
    fun currentRoom(player: Player): TestRoom? = order(player)?.firstOrNull { !passed(player, it) }

    /** The room after [room] in the attempt's order. */
    fun next(player: Player, room: TestRoom): TestRoom? {
        val order = order(player) ?: return null
        val index = order.indexOf(room)
        return if (index < 0) null else order.getOrNull(index + 1)
    }

    /**
     * The var holding each room's answer, rolled once per attempt: it outlives a logout, a death and
     * re-entering the room, and only goes when the room is passed or the attempt ends.
     */
    val ANSWER_VARBITS =
        mapOf(
            TestRoom.STATUES to "varbit.rd_statue_answer",
            TestRoom.ACROSTIC to "varbit.rd_riddle",
            TestRoom.LOGIC to "varbit.rd_logic_riddle",
        )

    /** Clears every room's state, except the answer of [keep] (the room being entered or resumed). */
    fun clearRoom(player: Player, keep: TestRoom? = null) {
        val kept = keep?.takeUnless { passed(player, it) }?.let(ANSWER_VARBITS::get)
        for (varbit in ROOM_VARBITS) {
            if (varbit != kept) set(player, varbit, 0)
        }
    }

    fun clearAttempt(player: Player) {
        clearRoom(player)
        for (room in TestRoom.entries) {
            set(player, room.completeVarbit, 0)
        }
        player.rdOrder = 0
    }

    fun set(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != value) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }
}
