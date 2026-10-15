package org.rsmod.content.quest.area.morytania.ghostsahoy

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.ardougne.undergroundpass.faceTowards
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SCRAP_3
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The pirate wreck north-west of the Ectofuntus: the gangplank, the ship's ladders, the wind over
 * the quarterdeck and the mast that shows the flag's colours when it drops, the captain's locked
 * chest (map scrap 1) and the stepping-stone rocks out to a chest on a rock (map scrap 3). The
 * chest in the hold with the lobster is [GiantLobster].
 *
 * The wind is the player's own `varbit.ahoy_windspeed`, shown on the cache's wind-speed overlay
 * while they stand on the quarterdeck and changed every few ticks by a timer that stops as soon
 * as they leave it.
 */
class Shipwreck
@Inject
constructor(
    private val ahoy: GhostsAhoyQuest,
    private val passages: GenericPassageScript,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GANGPLANK_ON_SHORE) { board() }
        onOpLoc1(GANGPLANK_ON_DECK) { disembark() }
        onOpLoc1(LADDER_UP) { climb(it.loc, it.type, up = true) }
        onOpLoc1(LADDER_DOWN) { climb(it.loc, it.type, up = false) }
        onOpLoc1(MAST) { searchMast() }
        onPlayerSoftTimer(WIND_TIMER) { windTick(player) }
        onOpLoc1(LOCKED_CHEST) { mes("The chest is locked.") }
        onOpLocU(LOCKED_CHEST, CHEST_KEY) { unlockChest(it.loc) }
        onOpLoc1(CLOSED_CHEST) { openChest(it.loc) }
        onOpLoc2(OPEN_CHEST) { mes("The lid of the chest has rusted open.") }
        onApLoc1(ROCK) { jumpTo(it.loc) }
    }

    private suspend fun ProtectedAccess.board() {
        arriveDelay()
        telejump(DECK_LANDING, TeleportType.Exempt)
        mes("You walk up the gangplank onto the wreck.")
    }

    private suspend fun ProtectedAccess.disembark() {
        arriveDelay()
        telejump(SHORE_LANDING, TeleportType.Exempt)
        mes("You walk down the gangplank.")
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo, type: ObjectServerType, up: Boolean) {
        with(passages) { passage(loc, type, 0) }
        if (up && onQuarterdeck(coords)) {
            startWind()
        }
    }

    fun ProtectedAccess.startWind() {
        ahoy.setWindHigh(player, true)
        ifOpenOverlay(WIND_OVERLAY, WIND_TARGET)
        showWind(player)
        softTimer(WIND_TIMER, WIND_INTERVAL)
    }

    /**
     * One change of the wind; ends the overlay and the timer once the player is off the
     * quarterdeck. A soft timer, so it never takes protected access from an open dialogue.
     */
    fun windTick(player: Player) {
        if (!onQuarterdeck(player.coords)) {
            player.clearSoftTimer(WIND_TIMER)
            player.ifCloseOverlay(WIND_OVERLAY, eventBus)
            return
        }
        if (random.of(WIND_CHANGE_ODDS) == 0) {
            ahoy.setWindHigh(player, !ahoy.isWindHigh(player))
            showWind(player)
        }
    }

    private fun showWind(player: Player) {
        val text = if (ahoy.isWindHigh(player)) "<col=ff3000>High</col>" else "<col=00ff00>Low</col>"
        player.ifSetText(WIND_TEXT, text)
    }

    private suspend fun ProtectedAccess.searchMast() {
        arriveDelay()
        if (!onQuarterdeck(coords)) {
            return
        }
        if (WIND_TIMER !in player.softTimerMap) {
            startWind()
        }
        if (ahoy.stage(player) != STAGE_GATHER) {
            mes("A tattered flag flaps from the top of the mast.")
            return
        }
        if (ahoy.isWindHigh(player)) {
            mes("The wind is blowing too hard to make out any details of the flag.")
            return
        }
        ahoy.ensureTargets(player) { random.of(it) }
        val part = FlagPart.entries.firstOrNull { !ahoy.isSeen(player, it) } ?: FlagPart.entries[random.of(FlagPart.entries.size)]
        ahoy.markSeen(player, part)
        mesbox("Squinting up at the tattered flag as the wind drops, you make out that its ${part.label} is ${ahoy.target(player, part)?.label}.")
    }

    private suspend fun ProtectedAccess.unlockChest(loc: BoundLocInfo) {
        arriveDelay()
        if (loc.coords != CAPTAINS_CHEST) {
            mes("The key doesn't fit this chest.")
            return
        }
        if (!ahoy.needsScrap(player, SCRAP_1)) {
            mes("You unlock the chest, but there's nothing of interest inside.")
            return
        }
        val swapped =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = CHEST_KEY.asRSCM()
                    strictCount = 1
                }
                insert {
                    into = pack
                    obj = SCRAP_1.asRSCM()
                    strictCount = 1
                }
            }
        if (swapped.failure) {
            return
        }
        objbox(SCRAP_1, "You unlock the captain's chest. Inside you find a scrap of a map, and the key snaps in the lock.")
    }

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_SEQ)
        if (loc.coords != ROCK_CHEST || !ahoy.needsScrap(player, SCRAP_3)) {
            mes("The chest is empty.")
            return
        }
        if (invAdd(inv, SCRAP_3).failure) {
            mes("You need a free inventory space to take anything from the chest.")
            return
        }
        objbox(SCRAP_3, "You find a scrap of a map in the chest.")
    }

    private suspend fun ProtectedAccess.jumpTo(rock: BoundLocInfo) {
        if (!isWithinApRange(rock, distance = JUMP_RANGE)) {
            return
        }
        val dest = rock.coords
        val distance = coords.chebyshevDistance(dest)
        if (coords.level != dest.level || distance == 0 || distance > JUMP_RANGE) {
            return
        }
        if (player.agilityLvl < AGILITY_REQ) {
            mes("You need an Agility level of $AGILITY_REQ to jump between these rocks.")
            return
        }
        val start = coords
        anim(JUMP_SEQ)
        exactMove(start, dest, delay1 = JUMP_DELAY_CYCLES, delay2 = JUMP_CYCLES, dir = faceTowards(start, dest), teleportType = TeleportType.Exempt)
        if (distance >= LONG_JUMP) {
            player.runEnergy = (player.runEnergy - LONG_JUMP_ENERGY).coerceAtLeast(0)
            UpdateRun.energy(player, player.runEnergy)
        }
        delay(JUMP_TICKS)
    }

    companion object {
        const val GANGPLANK_ON_SHORE = "loc.ahoy_gangplank_shipwreck_off"
        const val GANGPLANK_ON_DECK = "loc.ahoy_gangplank_shipwreck_on"
        const val LADDER_UP = "loc.ahoy_ghostship_ladder"
        const val LADDER_DOWN = "loc.ahoy_ghostship_laddertop"
        const val MAST = "loc.ahoy_mast"
        const val LOCKED_CHEST = "loc.ahoy_chest_locked"
        const val CLOSED_CHEST = "loc.ahoy_chest_closed"
        const val OPEN_CHEST = "loc.ahoy_chest_open"
        const val ROCK = "loc.ahoy_rock_invisible"

        const val WIND_TIMER = "timer.ahoy_wind"
        const val WIND_OVERLAY = "interface.ahoy_windspeed"
        const val WIND_TARGET = "component.toplevel_osrs_stretch:overlay_hud"
        const val WIND_TEXT = "component.ahoy_windspeed:ahoy_windspeed_indicator"
        const val WIND_INTERVAL = 5
        const val WIND_CHANGE_ODDS = 3

        val DECK_LANDING = CoordGrid(3605, 3544, 1)
        val SHORE_LANDING = CoordGrid(3605, 3548, 0)
        val CAPTAINS_CHEST = CoordGrid(3619, 3545, 1)
        val ROCK_CHEST = CoordGrid(3606, 3564, 0)
        val MAST_TILE = CoordGrid(3619, 3543, 2)

        private const val OPEN_SEQ = "seq.human_openchest"
        private const val JUMP_SEQ = "seq.human_longjump"
        private const val JUMP_RANGE = 3
        private const val LONG_JUMP = 3
        private const val LONG_JUMP_ENERGY = Constants.run_max_energy / 20
        private const val JUMP_TICKS = 2
        private const val JUMP_DELAY_CYCLES = 15
        private const val JUMP_CYCLES = 45

        fun onWreck(coords: CoordGrid): Boolean = coords.x in 3600..3625 && coords.z in 3535..3552

        fun onQuarterdeck(coords: CoordGrid): Boolean = coords.level == 2 && onWreck(coords)
    }
}
