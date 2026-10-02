package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpWorn1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdSpishyusTalks
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Spishyus's test: the fox, the chicken and the bag of grain over the precarious bridge.
 *
 * Picking a piece up puts it straight into its worn slot (the cache gives the fox the weapon slot,
 * the chicken the shield slot and the grain the cape slot), so it shows on the player; removing it
 * sets it down on the bank the player stands on. Where everything is lives in the cache's own
 * multiloc varbits, one pair per piece (`varbit.rd_foxleft`/`rd_foxright` and so on, "left" being
 * the east bank the player starts on), and the puzzle is judged from that configuration through
 * [RiverCrossing], never from the clicks that led to it.
 */
@Singleton
class CrossingRoom @Inject constructor(private val testing: RecruitmentTesting) : PluginScript(), TestRoomScript {
    override val room = TestRoom.CROSSING

    override fun ScriptContext.startup() {
        testing.register(this@CrossingRoom)
        onOpNpc1(SPISHYUS) { startDialogue(it.npc) { talk() } }
        for (cargo in Cargo.entries) {
            for (loc in pieceLocs(cargo)) {
                onOpLoc1(loc) { pickUp(cargo, it.loc) }
            }
            onOpWorn1(obj(cargo)) { putDown(cargo) }
            onOpHeld5(obj(cargo)) { putDown(cargo) }
            onOpHeld2(obj(cargo)) { carryFromInventory(cargo) }
        }
        onOpLoc1(BRIDGE_EAST) { cross(it.loc) }
        onOpLoc1(BRIDGE_WEST) { cross(it.loc) }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        val spishyus = testing.grounds.observer(player, room) ?: return
        val name = player.displayName
        startDialogue(spishyus) {
            chatNpc(happy, "Ah, welcome $name.")
            chatPlayer(quiz, "Hello there. What am I supposed to be doing in this room?")
            chatNpc(neutral, "Well, your task is to take this fox, this chicken and this bag of grain across that bridge there to the other side of the room.")
            chatNpc(neutral, "When you have done that, your task is complete.")
            chatPlayer(quiz, "Is that it?")
            chatNpc(neutral, "Well, it is not quite as simple as that may sound.")
            chatNpc(neutral, "Firstly, you may only carry one of the objects across the room at a time, for the bridge is old and fragile.")
            chatNpc(neutral, "Secondly, the fox wants to eat the chicken, and the chicken wants to eat the grain. Should you ever leave the fox unattended with the chicken, or the grain unattended with the chicken, then")
            chatNpc(neutral, "one of them will be eaten, and you will be unable to complete the test.")
            chatPlayer(neutral, "Okay, I'll see what I can do.")
        }
    }

    override fun leave(player: Player) {}

    private suspend fun org.rsmod.api.player.dialogue.Dialogue.talk() {
        val name = player.displayName
        if (RecruitmentState.passed(player, room)) {
            chatNpc(happy, "Ah, well done on solving my puzzle $name!")
            return
        }
        if (!testing.testing(player, room)) {
            return
        }
        if (player.rdSpishyusTalks == 0) {
            chatPlayer(angry, "Listen, this is really annoying me. Can't you just stop the fox eating the chicken or the chicken eating the grain for me, while I carry the other stuff across the bridge?")
            chatNpc(neutral, "I am afraid not, $name. My task is solely to observe your progress, we have explicit instructions not to assist you in any way..")
            player.rdSpishyusTalks = 1
            return
        }
        chatPlayer(worried, "No, but seriously, please? Think of the poor chicken and grain! Being eaten!")
        chatNpc(sad, "I am truly sorry, but my hands are tied. I suggest you think carefully before taking any items across the bridge with you, to ensure nothing gets eaten while you are not paying attention.")
        chatNpc(neutral, "This is the only help I may give you...")
        chatPlayer(neutral, "Well, thanks I guess...")
    }

    /** The configuration the player's vars and worn items describe, or null outside the room. */
    fun load(player: Player): RiverCrossing? {
        val world = testing.grounds.world(player, player.coords) ?: return null
        val playerBank =
            when {
                world.x >= EAST_BANK_MIN_X -> Bank.EAST
                world.x <= WEST_BANK_MAX_X -> Bank.WEST
                else -> return null
            }
        val positions =
            Cargo.entries.associateWith { cargo ->
                when {
                    player.worn.contains(obj(cargo)) || player.inv.contains(obj(cargo)) -> Bank.CARRIED
                    player.vars[eastVarbit(cargo)] == EAST_PRESENT -> Bank.EAST
                    player.vars[westVarbit(cargo)] == WEST_PRESENT -> Bank.WEST
                    else -> Bank.CARRIED
                }
            }
        return RiverCrossing(positions, playerBank)
    }

    private fun save(player: Player, state: RiverCrossing) {
        for (cargo in Cargo.entries) {
            val bank = state.positions.getValue(cargo)
            RecruitmentState.set(player, eastVarbit(cargo), if (bank == Bank.EAST) EAST_PRESENT else EAST_ABSENT)
            RecruitmentState.set(player, westVarbit(cargo), if (bank == Bank.WEST) WEST_PRESENT else WEST_ABSENT)
        }
    }

    private suspend fun ProtectedAccess.pickUp(cargo: Cargo, loc: BoundLocInfo) {
        if (!testing.testing(player, room) || !testing.inRoom(player, loc.coords, room)) {
            return
        }
        val state = load(player) ?: return
        val world = testing.worldOf(player, loc.coords) ?: return
        val bank = if (world.x >= EAST_BANK_MIN_X) Bank.EAST else Bank.WEST
        if (bank != state.player || state.positions[cargo] != bank) {
            return
        }
        val next = state.pickUp(cargo) ?: return
        val slot = wearSlot(cargo)
        if (worn[slot] != null) {
            mes("You need to put down what you're carrying first.")
            return
        }
        if (invAdd(worn, obj(cargo), slot = slot).failure) {
            return
        }
        rebuildAppearance()
        save(player, next)
    }

    private suspend fun ProtectedAccess.carryFromInventory(cargo: Cargo) {
        if (!testing.testing(player, room)) {
            return
        }
        val from = inv.indexOfFirst { it?.id == obj(cargo).asRSCM(RSCMType.OBJ) }
        val slot = wearSlot(cargo)
        if (from < 0 || worn[slot] != null) {
            return
        }
        if (invDel(inv, obj(cargo), slot = from).failure) {
            return
        }
        invAdd(worn, obj(cargo), slot = slot)
        rebuildAppearance()
    }

    private suspend fun ProtectedAccess.putDown(cargo: Cargo) {
        if (!testing.testing(player, room)) {
            return
        }
        val state = load(player) ?: return
        val next = state.putDown(cargo) ?: return
        val wornSlot = wearSlot(cargo)
        val removed =
            if (worn[wornSlot]?.id == obj(cargo).asRSCM(RSCMType.OBJ)) {
                invDel(worn, obj(cargo), slot = wornSlot).success
            } else {
                invDel(inv, obj(cargo)).success
            }
        if (!removed) {
            return
        }
        rebuildAppearance()
        save(player, next)
        if (next.solved) {
            val attempt = testing.attempt(player)
            mes("Congratulations! You have solved this room's puzzle!")
            with(testing) { passRoom(room, attempt) }
        }
    }

    private suspend fun ProtectedAccess.cross(bridge: BoundLocInfo) {
        if (!testing.testing(player, room) || !testing.inRoom(player, bridge.coords, room)) {
            return
        }
        val attempt = testing.attempt(player)
        val state = load(player) ?: return
        when (val outcome = state.cross()) {
            RiverCrossing.Outcome.Overloaded -> {
                startDialogue { chatPlayer(worried, "I really don't think I should be carrying more than 5 Kg across that rickety bridge...") }
            }
            is RiverCrossing.Outcome.Crossed -> {
                walkBridge(state.player)
                if (testing.testing(player, room, attempt)) save(player, outcome.state)
            }
            is RiverCrossing.Outcome.Eaten -> {
                walkBridge(state.player)
                if (!testing.testing(player, room, attempt)) return
                save(player, outcome.state)
                showMeal(player, outcome)
                with(testing) { failRoom(room, attempt) }
            }
        }
    }

    private suspend fun ProtectedAccess.walkBridge(from: Bank) {
        val path = if (from == Bank.EAST) EAST_TO_WEST else EAST_TO_WEST.reversed()
        val facing = if (from == Bank.EAST) FACE_WEST else FACE_EAST
        val start = testing.grounds.local(player, path.first()) ?: return
        telejump(start)
        mes("You carefully walk across the rickety bridge...")
        for (tile in path.drop(1)) {
            val step = testing.grounds.local(player, tile) ?: return
            exactMove(coords, step, delay1 = 0, delay2 = STEP_CYCLES, dir = facing, teleportType = org.rsmod.api.player.hook.TeleportType.Exempt)
            delay(1)
        }
    }

    /** The fox grows fat, or the sack is left empty, on the bank the player walked away from. */
    private fun showMeal(player: Player, eaten: RiverCrossing.Outcome.Eaten) {
        val east = eaten.bank == Bank.EAST
        when (eaten.eater) {
            Cargo.FOX -> {
                RecruitmentState.set(player, if (east) eastVarbit(Cargo.FOX) else westVarbit(Cargo.FOX), if (east) EAST_EATEN else WEST_EATEN)
                RecruitmentState.set(player, if (east) eastVarbit(Cargo.CHICKEN) else westVarbit(Cargo.CHICKEN), if (east) EAST_ABSENT else WEST_ABSENT)
            }
            else ->
                RecruitmentState.set(player, if (east) eastVarbit(Cargo.GRAIN) else westVarbit(Cargo.GRAIN), if (east) EAST_EATEN else WEST_EATEN)
        }
    }

    companion object {
        const val SPISHYUS = "npc.rd_observer_room_1"
        const val BRIDGE_EAST = "loc.rd_bridge_left"
        const val BRIDGE_WEST = "loc.rd_bridge_right"

        /** `rd_*left` varbits (east bank): 0 there, 1 gone, 2 the eaten form. */
        const val EAST_PRESENT = 0
        const val EAST_ABSENT = 1
        const val EAST_EATEN = 2

        /** `rd_*right` varbits (west bank): 0 gone, 1 there, 3 the eaten form. */
        const val WEST_ABSENT = 0
        const val WEST_PRESENT = 1
        const val WEST_EATEN = 3

        /** East of the bridge (x 2483) is the starting bank; west of it (x 2477) the far one. */
        const val EAST_BANK_MIN_X = 2484
        const val WEST_BANK_MAX_X = 2476

        const val STEP_CYCLES = 30
        const val FACE_EAST = 1536
        const val FACE_WEST = 512

        val EAST_TO_WEST = (2484 downTo 2476).map { CoordGrid(it, 4972, 0) }

        fun obj(cargo: Cargo): String =
            when (cargo) {
                Cargo.FOX -> "obj.rd_fox"
                Cargo.CHICKEN -> "obj.rd_chicken"
                Cargo.GRAIN -> "obj.rd_sack"
            }

        fun eastVarbit(cargo: Cargo): String =
            when (cargo) {
                Cargo.FOX -> "varbit.rd_foxleft"
                Cargo.CHICKEN -> "varbit.rd_chickleft"
                Cargo.GRAIN -> "varbit.rd_grainleft"
            }

        fun westVarbit(cargo: Cargo): String =
            when (cargo) {
                Cargo.FOX -> "varbit.rd_foxright"
                Cargo.CHICKEN -> "varbit.rd_chickright"
                Cargo.GRAIN -> "varbit.rd_grainright"
            }

        /** The visible locs of each piece, on either bank (the multilocs resolve to these). */
        fun pieceLocs(cargo: Cargo): List<String> =
            when (cargo) {
                Cargo.FOX -> listOf("loc.rd_fox_normal")
                Cargo.CHICKEN -> listOf("loc.rd_chicken_normal")
                Cargo.GRAIN -> listOf("loc.rd_sack_full")
            }

        fun wearSlot(cargo: Cargo): Int =
            checkNotNull(ServerCacheManager.getItem(obj(cargo).asRSCM(RSCMType.OBJ))).wearpos1
    }
}
