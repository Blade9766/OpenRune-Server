package org.rsmod.content.skills.hunter.goatpit

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Goat hunting on Wyrmscraig. The goat pit is a per-player multiloc on `varbit.goat_pit_state`
 * (empty, spiked, one goat, some goats, full) with the goat count in `varbit.goat_pit_inpit`.
 * Prodding a goat with a cattleprod drives it a few tiles straight away from the player; if that
 * line crosses a spiked pit with room, the goat falls in. Clearing the pit harvests each goat
 * and breaks the spikes.
 */
class GoatPitScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val worldQueues: WorldQueueList,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val rumours: RumourTracker,
    private val pets: PetRewards,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(GOAT) { prod(it.npc) }
        onOpNpc1(GEOFF) { talkToGeoff(it.npc) }
        onOpLoc1(PROD_SUPPLY) { takeProd() }
        onOpLoc1(SPIKES_SUPPLY) { takeSpikes(1) }
        onOpLoc2(SPIKES_SUPPLY) { takeSpikes(5) }
        onOpLoc3(SPIKES_SUPPLY) { takeSpikes(10) }
        onOpLoc4(SPIKES_SUPPLY) { takeSpikes(countDialog()) }
        onOpLoc1(PIT_EMPTY) { line() }
        onOpLoc2(PIT_EMPTY) { inspect() }
        onOpLoc1(PIT_SPIKED) { inspect() }
        onOpLoc2(PIT_SPIKED) { inspect() }
        for (pit in PIT_WITH_GOATS) {
            onOpLoc1(pit) { clear() }
            onOpLoc2(pit) { inspect() }
        }
    }

    private suspend fun ProtectedAccess.talkToGeoff(npc: Npc) =
        startDialogue(npc) {
            chatNpc(
                happy,
                "Line the goat pit with some wooden spikes, then give the goats a prod with a " +
                    "cattleprod to send them in. Help yourself to the supplies here.",
            )
            chatNpc(
                neutral,
                "Once the pit's full, clear it out. The spikes won't survive, so you'll need to " +
                    "line it again after.",
            )
        }

    private fun ProtectedAccess.takeProd() {
        if (CATTLEPROD in player.inv || player.righthand?.isType(CATTLEPROD) == true) {
            mes("You already have a cattleprod.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        invAdd(inv, CATTLEPROD)
        mes("You take a cattleprod from the supply.")
    }

    private fun ProtectedAccess.takeSpikes(count: Int) {
        if (count <= 0) {
            return
        }
        if (inv.freeSpace() == 0 && SPIKES !in player.inv) {
            mes("You don't have enough inventory space.")
            return
        }
        invAdd(inv, SPIKES, count)
    }

    private suspend fun ProtectedAccess.line() {
        if (player.statBase(TrapManager.STAT) < LEVEL) {
            mes("You need a Hunter level of $LEVEL to hunt goats.")
            return
        }
        if (SPIKES !in player.inv) {
            mes("You need some wooden spikes to line the pit.")
            return
        }
        anim(LINE_SEQ)
        delay(1)
        if (player.vars[STATE] != State.Empty.value) {
            return
        }
        invDel(inv, SPIKES)
        setState(player, 0)
        mes("You line the pit with wooden spikes.")
    }

    private fun ProtectedAccess.inspect() {
        val count = player.vars[IN_PIT]
        if (player.vars[STATE] == State.Empty.value) {
            mes("The pit is empty. It needs to be lined with wooden spikes before it can catch goats.")
            return
        }
        mes(
            "The pit contains $count goat remains, out of ${capacity(player)}. The spikes are " +
                "still sharp."
        )
    }

    private suspend fun ProtectedAccess.clear() {
        while (player.vars[IN_PIT] > 0) {
            if (inv.isFull()) {
                mes("You don't have enough inventory space.")
                return
            }
            anim(CLEAR_SEQ)
            delay(1)
            val remaining = player.vars[IN_PIT]
            if (remaining <= 0) {
                return
            }
            invAdd(inv, if (random.of(LOOT_ROLL) < HORN_WEIGHT) HORN else FUR)
            statAdvance(TrapManager.STAT, harvestXp() * xpMods.get(player, TrapManager.STAT))
            rumours.onCatch(player, RUMOUR_KEY)
            pets.rollSkillingPet(player, PET, TrapManager.STAT, PET_CHANCE)
            if (remaining == 1) {
                VarPlayerIntMapSetter.set(player, IN_PIT, 0)
                VarPlayerIntMapSetter.set(player, STATE, State.Empty.value)
                mes("You clear the last goat out of the pit, breaking the spikes.")
            } else {
                setState(player, remaining - 1)
            }
        }
    }

    private suspend fun ProtectedAccess.prod(goat: Npc) {
        if (player.statBase(TrapManager.STAT) < LEVEL) {
            mes("You need a Hunter level of $LEVEL to hunt goats.")
            return
        }
        val weapon = player.righthand
        if (weapon == null) {
            mes("You should use a cattleprod if you want to prod the goats.")
            return
        }
        if (!weapon.isType(CATTLEPROD)) {
            mes("You don't think your current weapon would work as a cattleprod.")
            return
        }
        faceEntitySquare(goat)
        anim(PROD_SEQ)
        delay(1)
        val dx = (goat.coords.x - player.coords.x).sign
        val dz = (goat.coords.z - player.coords.z).sign
        if (dx == 0 && dz == 0) {
            return
        }
        val path = (1..PROD_DISTANCE).map { goat.coords.translate(dx * it, dz * it) }
        val pitTile = path.firstOrNull { it.inPit() }
        val open = player.vars[STATE] != State.Empty.value && player.vars[IN_PIT] < capacity(player)
        val edge = path.takeWhile { !it.inPit() }
        goat.noneMode()
        edge.lastOrNull()?.let(goat::walk)
        if (pitTile == null || !open) {
            worldQueues.add(edge.size + 1) { if (goat.isAliveInWorld()) goat.defaultMode() }
            return
        }
        val prodder = player
        worldQueues.add(edge.size + 1) { fallIn(prodder, goat) }
    }

    private fun fallIn(player: Player, goat: Npc) {
        if (!goat.isAliveInWorld()) {
            return
        }
        goat.defaultMode()
        val count = player.vars[IN_PIT]
        if (player.vars[STATE] == State.Empty.value || count >= capacity(player)) {
            return
        }
        npcRepo.despawn(goat, GOAT_RESPAWN)
        setState(player, count + 1)
        player.statAdvance(TrapManager.STAT, PROD_XP * xpMods.get(player, TrapManager.STAT))
        if (count + 1 >= capacity(player)) {
            player.mes("The pit is now filled with goats.")
        }
    }

    private fun setState(player: Player, count: Int) {
        VarPlayerIntMapSetter.set(player, IN_PIT, count)
        val state =
            when {
                count == 0 -> State.Spiked
                count >= capacity(player) -> State.Full
                count == 1 -> State.One
                else -> State.Some
            }
        VarPlayerIntMapSetter.set(player, STATE, state.value)
    }

    private fun capacity(player: Player): Int {
        val level = player.statBase(TrapManager.STAT)
        return CAPACITIES.last { level >= it.first }.second
    }

    private fun ProtectedAccess.harvestXp(): Double {
        val level = player.statBase(TrapManager.STAT)
        return if (level < XP_STEP_LEVEL) {
            BASE_XP + LOW_XP_PER_LEVEL * (level - LEVEL)
        } else {
            STEP_XP + (level - XP_STEP_LEVEL)
        }
    }

    private fun CoordGrid.inPit(): Boolean =
        x in PIT_X until PIT_X + PIT_SIZE && z in PIT_Z until PIT_Z + PIT_SIZE

    private enum class State(val value: Int) {
        Empty(0),
        Spiked(1),
        One(2),
        Some(3),
        Full(4),
    }

    private companion object {
        const val GOAT = "npc.goat_pit_goat"
        const val GEOFF = "npc.goat_pit_helper"
        const val PROD_SUPPLY = "loc.goat_pit_prod"
        const val SPIKES_SUPPLY = "loc.goat_pit_spikes"
        const val PIT_EMPTY = "loc.goat_pit_multi_empty"
        const val PIT_SPIKED = "loc.goat_pit_multi_spikes"
        const val STATE = "varbit.goat_pit_state"
        const val IN_PIT = "varbit.goat_pit_inpit"
        const val CATTLEPROD = "obj.cattleprod"
        const val SPIKES = "obj.goat_pit_spikes"
        const val HORN = "obj.desert_goat_horn"
        const val FUR = "obj.goat_pit_fur"
        const val PET = "obj.goatpitpet"
        const val RUMOUR_KEY = "WyrmscraigGoat"
        const val PROD_SEQ = "seq.cattleprod"
        const val LINE_SEQ = "seq.human_pickupfloor"
        const val CLEAR_SEQ = "seq.human_pickupfloor"

        const val LEVEL = 60
        const val PIT_X = 2572
        const val PIT_Z = 2195
        const val PIT_SIZE = 3
        const val PROD_DISTANCE = 6
        const val GOAT_RESPAWN = 15
        const val PROD_XP = 20.0
        const val BASE_XP = 100.0
        const val LOW_XP_PER_LEVEL = 3.0
        const val XP_STEP_LEVEL = 80
        const val STEP_XP = 160.0
        const val LOOT_ROLL = 4
        const val HORN_WEIGHT = 3
        const val PET_CHANCE = 40_000

        val PIT_WITH_GOATS =
            listOf("loc.goat_pit_multi_one", "loc.goat_pit_multi_some", "loc.goat_pit_multi_full")

        val CAPACITIES = listOf(60 to 16, 69 to 18, 77 to 20, 85 to 22, 93 to 24)
    }
}
