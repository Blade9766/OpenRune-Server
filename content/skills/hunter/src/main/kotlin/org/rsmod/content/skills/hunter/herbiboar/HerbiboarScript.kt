package org.rsmod.content.skills.hunter.herbiboar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Herbiboar tracking on Fossil Island. Inspecting a start object rolls a hidden path through
 * [HerbiboarTrails] that passes [MIN_SEARCHES]..[MAX_SEARCHES] search spots and ends at a tunnel.
 * Each search reveals the next footprint segment (and may turn up fossils or confuse the player);
 * attacking the final tunnel stuns the herbiboar, which is shown only to that player through
 * `varbit.fossil_herbiboar_visible` and can then be harvested for herbs.
 */
class HerbiboarScript
@Inject
constructor(
    private val playerList: PlayerList,
    private val clock: MapClock,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val pets: PetRewards,
    private val objRepo: ObjRepository,
    private val rumours: RumourTracker,
) : PluginScript() {
    private class Trail(val path: List<HerbiboarSegment>, val spots: List<Int>) {
        var revealed: Int = 0
        var stunnedAt: Int = -1

        val tunnel: HerbiboarSpot
            get() = HerbiboarTrails.spots[spots.last()]

        val complete: Boolean
            get() = revealed == path.size

        val currentSpot: Int
            get() = spots[revealed]
    }

    private val trails = HashMap<PlayerUid, Trail>()
    private val herbiboars = HashMap<Int, Npc>()

    override fun ScriptContext.startup() {
        for (index in 1..HERBIBOAR_COUNT) {
            val type = ServerCacheManager.getNpc("npc.fossil_herbiboar_$index".asRSCM(RSCMType.NPC))
            if (type != null) {
                type.moveRestrict = MoveRestrict.NoMove
                type.defaultMode = NpcMode.None
                type.wanderRange = 0
            }
        }
        onEvent<NpcStateEvents.Create> { herbiboarIndex(npc)?.let { herbiboars[it] = npc } }
        onEvent<GameLifecycle.LateCycle> { tick() }
        onPlayerLogin { clearVars(player) }
        onPlayerLogout {
            clearVars(player)
            trails.remove(player.uid)
        }

        for (start in STARTS) {
            onOpLoc1(start) { inspectStart(it.loc) }
            onOpLoc2(start) { checkCount() }
            onOpLoc3(start) { toggleWarning() }
        }
        for (clue in CLUES) {
            onOpLoc1(clue) { inspectClue(it.loc) }
        }
        onOpLoc1(TUNNEL) { attackTunnel(it.loc) }
        onOpLoc2(TUNNEL) { searchTunnel(it.loc) }
        onOpNpc1(HERBIBOAR_VISIBLE) { harvest(it.npc) }
    }

    private fun herbiboarIndex(npc: Npc): Int? =
        (1..HERBIBOAR_COUNT).firstOrNull {
            npc.type.id == "npc.fossil_herbiboar_$it".asRSCM(RSCMType.NPC)
        }

    private fun tick() {
        if (trails.isEmpty() || clock.cycle % CHECK_INTERVAL != 0) {
            return
        }
        for ((uid, trail) in trails.entries.toList()) {
            val player = uid.resolve(playerList) ?: continue
            val dx = abs(player.coords.x - FOREST_CENTRE_X)
            val dz = abs(player.coords.z - FOREST_CENTRE_Z)
            if (maxOf(dx, dz) > LEAVE_DISTANCE) {
                abandon(player, trail)
                continue
            }
            if (trail.stunnedAt >= 0 && clock.cycle - trail.stunnedAt >= STUN_CYCLES) {
                abandon(player, trail)
                player.mes("The herbiboar has recovered and burrowed away.")
            }
        }
    }

    private suspend fun ProtectedAccess.inspectStart(loc: BoundLocInfo) {
        val start = HerbiboarTrails.spotWithStart(loc.coords) ?: return
        if (player.hunterLvl < HUNTER_LEVEL) {
            mes("You need a Hunter level of at least $HUNTER_LEVEL to track a herbiboar.")
            return
        }
        val current = trails[player.uid]
        if (current != null && current.stunnedAt >= 0 && !player.warningDisabled()) {
            val choice =
                menu(
                    "Abandon the stunned herbiboar?",
                    "Yes, start a new trail.",
                    "No, I'll harvest it first.",
                )
            ifClose()
            if (choice != 0) {
                return
            }
        }
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        trails.remove(player.uid)?.let { abandon(player, it) }
        val trail = generate(start.index)
        if (trail == null) {
            mes("You find no tracks here.")
            return
        }
        trails[player.uid] = trail
        VarPlayerIntMapSetter.set(player, TRAIL_STARTED, 1)
        reveal(player, trail)
        mes("You find some tracks.")
    }

    private suspend fun ProtectedAccess.inspectClue(loc: BoundLocInfo) {
        val spot = HerbiboarTrails.spotWithClue(loc.coords) ?: return
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        val trail = trails[player.uid]
        if (trail == null || trail.complete || trail.revealed == 0 || trail.currentSpot != spot.index) {
            mes("Nothing seems to be out of place here.")
            return
        }
        val last = trail.revealed == trail.path.size - 1
        if (!last && random.of(CONFUSE_CHANCE) == 0) {
            abandon(player, trail)
            mes(
                "The creature has successfully confused you with its tracks, leading you round in " +
                    "circles. You'll need to start again."
            )
            return
        }
        if (!last) {
            rollFossil()
        }
        reveal(player, trail)
        if (trail.complete) {
            VarPlayerIntMapSetter.set(player, TRAIL_FINISH, trail.tunnel.herbiboar)
            mes("You find tracks leading into a tunnel.")
        } else {
            mes("You find some tracks.")
        }
    }

    private fun ProtectedAccess.rollFossil() {
        val roll = random.of(FOSSIL_ROLL)
        var threshold = 0
        for ((obj, weight) in FOSSILS) {
            threshold += weight
            if (roll < threshold) {
                val count = if (obj == NUMULITE) random.of(NUMULITE_MIN, NUMULITE_MAX) else 1
                invAddOrDrop(objRepo, obj, count)
                mes("You find something while searching.")
                return
            }
        }
    }

    private suspend fun ProtectedAccess.searchTunnel(loc: BoundLocInfo) {
        faceSquare(loc.coords)
        anim(INSPECT_SEQ)
        delay(INSPECT_CYCLES)
        if (isTargetTunnel(loc)) {
            mes("You hear something moving about inside the tunnel.")
        } else {
            mes("You search the tunnel but find nothing.")
        }
    }

    private suspend fun ProtectedAccess.attackTunnel(loc: BoundLocInfo) {
        val trail = trails[player.uid]
        if (trail == null || trail.stunnedAt >= 0 || !isTargetTunnel(loc)) {
            mes("There's nothing in this tunnel.")
            return
        }
        faceSquare(loc.coords)
        anim(ATTACK_SEQ)
        delay(ATTACK_CYCLES)
        if (trails[player.uid] !== trail || trail.stunnedAt >= 0) {
            return
        }
        clearSegments(player)
        VarPlayerIntMapSetter.set(player, TRAIL_FINISH, 0)
        VarPlayerIntMapSetter.set(player, HERBIBOAR_VISIBLE_VARBIT, trail.tunnel.herbiboar)
        herbiboars[trail.tunnel.herbiboar]?.anim(APPEAR_SEQ, delay = 0, priority = 0)
        trail.stunnedAt = clock.cycle
        mes("You stun the herbiboar as it bursts out of the tunnel.")
    }

    private suspend fun ProtectedAccess.harvest(npc: Npc) {
        val trail = trails[player.uid]
        val index = herbiboarIndex(npc)
        if (trail == null || trail.stunnedAt < 0 || index != trail.tunnel.herbiboar) {
            return
        }
        if (player.herbloreLvl < HERBLORE_LEVEL) {
            mes("You need a Herblore level of at least $HERBLORE_LEVEL to harvest the herbiboar.")
            return
        }
        val secateurs = inv.contains(MAGIC_SECATEURS) || MAGIC_SECATEURS in player.worn
        val bonus = if (secateurs) 1 else 0
        if (inv.freeSpace() < MAX_HERBS + bonus) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        faceEntitySquare(npc)
        anim(HARVEST_SEQ)
        delay(HARVEST_CYCLES)
        if (trails[player.uid] !== trail) {
            return
        }
        trails.remove(player.uid)
        clearVars(player)
        npc.anim(BURROW_SEQ, delay = 0, priority = 0)
        val herbs = List(random.of(MIN_HERBS, MAX_HERBS) + bonus) { rollHerb() }
        herbs.forEach { invAdd(inv, it) }
        statAdvance(TrapManager.STAT, hunterXp() * xpMods.get(player, TrapManager.STAT))
        if (herbs.size > 1) {
            val herbXp = HERBLORE_XP_PER_HERB * (herbs.size - 1)
            statAdvance(HERBLORE, herbXp * xpMods.get(player, HERBLORE))
        }
        val caught = player.vars[COUNT_VARP] + 1
        VarPlayerIntMapSetter.set(player, COUNT_VARP, caught)
        mes("You harvest herbs from the herbiboar, whereupon it escapes.")
        mes("Your herbiboar harvest count is: <col=ff0000>$caught</col>.")
        rumours.onCatch(player, RUMOUR_KEY)
        repeat(herbs.size) {
            if (random.of(PET_CHANCE) == 0) {
                pets.give(player, PET)
                return
            }
        }
    }

    private fun ProtectedAccess.hunterXp(): Double {
        val level = player.statBase(TrapManager.STAT)
        return if (level < XP_STEP_LEVEL) {
            BASE_XP + LOW_XP_PER_LEVEL * (level - HUNTER_LEVEL)
        } else {
            STEP_XP + HIGH_XP_PER_LEVEL * (level - XP_STEP_LEVEL)
        }
    }

    private fun ProtectedAccess.rollHerb(): String {
        val level = player.herbloreLvl
        val weights = HERBS.filter { level in it.levels }.map { it to it.weightAt(level) }
        var roll = random.randomDouble() * weights.sumOf { it.second }
        for ((herb, weight) in weights) {
            roll -= weight
            if (roll < 0) {
                return herb.obj
            }
        }
        return weights.last().first.obj
    }

    private fun ProtectedAccess.checkCount() {
        mes("Your herbiboar harvest count is: <col=ff0000>${player.vars[COUNT_VARP]}</col>.")
    }

    private fun ProtectedAccess.toggleWarning() {
        val disabled = !player.warningDisabled()
        VarPlayerIntMapSetter.set(player, WARNING_VARBIT, if (disabled) 1 else 0)
        if (disabled) {
            mes("You will no longer be warned before abandoning a stunned herbiboar.")
        } else {
            mes("You will now be warned before abandoning a stunned herbiboar.")
        }
    }

    private fun Player.warningDisabled(): Boolean = vars[WARNING_VARBIT] != 0

    private fun ProtectedAccess.isTargetTunnel(loc: BoundLocInfo): Boolean {
        val trail = trails[player.uid] ?: return false
        return trail.complete && HerbiboarTrails.spotWithTunnel(loc.coords) === trail.tunnel
    }

    private fun generate(start: Int): Trail? {
        val searches = random.of(MIN_SEARCHES, MAX_SEARCHES)
        repeat(GENERATE_ATTEMPTS) {
            val path = ArrayList<HerbiboarSegment>()
            val spots = arrayListOf(start)
            var spot = start
            while (path.size < searches) {
                val options =
                    HerbiboarTrails.segmentsAt(spot).filter {
                        it !in path && HerbiboarTrails.spots[it.other(spot)].clues.isNotEmpty()
                    }
                if (options.isEmpty()) {
                    break
                }
                val segment = options[random.of(options.size)]
                path += segment
                spot = segment.other(spot)
                spots += spot
            }
            if (path.size < searches) {
                return@repeat
            }
            val finals =
                HerbiboarTrails.segmentsAt(spot).filter {
                    it !in path && HerbiboarTrails.spots[it.other(spot)].tunnel != null
                }
            if (finals.isEmpty()) {
                return@repeat
            }
            val last = finals[random.of(finals.size)]
            path += last
            spots += last.other(spot)
            return Trail(path, spots)
        }
        return null
    }

    private fun reveal(player: Player, trail: Trail) {
        val index = trail.revealed
        if (index > 0) {
            setSegment(player, trail, index - 1, fade = true)
        }
        setSegment(player, trail, index, fade = false)
        trail.revealed = index + 1
    }

    private fun setSegment(player: Player, trail: Trail, index: Int, fade: Boolean) {
        val segment = trail.path[index]
        val fromA = trail.spots[index] == segment.a
        val forward = fromA == segment.forwardFromA
        val base = if (forward) VISIBLE else VISIBLE_REVERSED
        VarPlayerIntMapSetter.set(player, segment.varbit, if (fade) base + FADE_OFFSET else base)
    }

    private fun abandon(player: Player, trail: Trail) {
        if (trails[player.uid] === trail) {
            trails.remove(player.uid)
        }
        clearVars(player)
    }

    private fun clearSegments(player: Player) {
        for (segment in HerbiboarTrails.segments) {
            if (player.vars[segment.varbit] != 0) {
                VarPlayerIntMapSetter.set(player, segment.varbit, 0)
            }
        }
    }

    private fun clearVars(player: Player) {
        clearSegments(player)
        for (varbit in listOf(TRAIL_STARTED, TRAIL_FINISH, HERBIBOAR_VISIBLE_VARBIT)) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    private class Herb(val obj: String, val levels: IntRange, val low: Double, val high: Double) {
        fun weightAt(level: Int): Double {
            val span = (levels.last - levels.first).coerceAtLeast(1)
            return low + (high - low) * (level - levels.first) / span
        }
    }

    private companion object {
        const val HUNTER_LEVEL = 80
        const val HERBLORE_LEVEL = 31
        const val HERBIBOAR_COUNT = 9
        const val MIN_SEARCHES = 3
        const val MAX_SEARCHES = 4
        const val GENERATE_ATTEMPTS = 50
        const val CONFUSE_CHANCE = 50
        const val VISIBLE = 3
        const val VISIBLE_REVERSED = 4
        const val FADE_OFFSET = 2
        const val INSPECT_CYCLES = 2
        const val ATTACK_CYCLES = 2
        const val HARVEST_CYCLES = 3
        const val CHECK_INTERVAL = 5
        const val STUN_CYCLES = 100
        const val LEAVE_DISTANCE = 80
        const val FOREST_CENTRE_X = 3705
        const val FOREST_CENTRE_Z = 3845

        const val BASE_XP = 1950.0
        const val LOW_XP_PER_LEVEL = 30.0
        const val XP_STEP_LEVEL = 95
        const val STEP_XP = 2385.0
        const val HIGH_XP_PER_LEVEL = 19.0
        const val MIN_HERBS = 1
        const val MAX_HERBS = 3
        const val HERBLORE_XP_PER_HERB = 25.0
        const val PET_CHANCE = 6500

        const val FOSSIL_ROLL = 3500
        const val NUMULITE = "obj.fossil_numulite"
        const val NUMULITE_MIN = 5
        const val NUMULITE_MAX = 24

        const val HERBLORE = "stat.herblore"
        const val MAGIC_SECATEURS = "obj.fairy_enchanted_secateurs"
        const val PET = "obj.herbiboarpet"
        const val RUMOUR_KEY = "Herbiboar"
        const val COUNT_VARP = "varp.kc_herbiboar"
        const val WARNING_VARBIT = "varbit.fossil_herbiboar_already_caught_ignore_warning"
        const val HERBIBOAR_VISIBLE_VARBIT = "varbit.fossil_herbiboar_visible"
        const val TRAIL_STARTED = "varbit.hunting_trails_used_fossil"
        const val TRAIL_FINISH = "varbit.hunting_trail_ends_fossil"

        const val TUNNEL = "loc.hunting_trail_end_fossil"
        const val HERBIBOAR_VISIBLE = "npc.fossil_herbiboar_visible"
        const val INSPECT_SEQ = "seq.hunting_searching_bushes"
        const val ATTACK_SEQ = "seq.human_unarmedkick"
        const val HARVEST_SEQ = "seq.human_herbing_grind_npc"
        const val APPEAR_SEQ = "seq.fossil_npc_herbiboar_appear"
        const val BURROW_SEQ = "seq.fossil_npc_herbiboar_burrow"

        val FOSSILS =
            listOf(
                NUMULITE to 1260,
                "obj.fossil_small_unid" to 70,
                "obj.fossil_medium_unid" to 35,
                "obj.fossil_large_unid" to 28,
                "obj.fossil_rare_unid" to 7,
            )

        val HERBS =
            listOf(
                Herb("obj.unidentified_marentill", 31..80, 40.0, 10.0),
                Herb("obj.unidentified_tarromin", 31..77, 35.0, 10.0),
                Herb("obj.unidentified_harralander", 31..76, 30.0, 10.0),
                Herb("obj.unidentified_ranarr", 31..99, 3.0, 8.0),
                Herb("obj.unidentified_lantadyme", 41..99, 10.0, 30.0),
                Herb("obj.unidentified_dwarf_weed", 62..99, 10.0, 30.0),
                Herb("obj.unidentified_snapdragon", 74..99, 5.0, 15.0),
                Herb("obj.unidentified_torstol", 77..99, 2.0, 8.0),
            )

        val STARTS = (1..5).map { "loc.hunting_trail_spawn_fossil$it" }

        val CLUES =
            (0..9).map { "loc.hunting_trail_clue9_$it" } +
                (0..9).map { "loc.hunting_trail_clue10_$it" } +
                "loc.hunting_trail_clue11_0"
    }
}
