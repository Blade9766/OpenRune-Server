package org.rsmod.content.skills.hunter.birdhouse

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bird house trapping on Fossil Island. Each of the four spaces keeps its tier/stage and seed count
 * in a persistent server varp (`state = transform value | seedUnits << 5`) plus the epoch minute it
 * was baited, and mirrors the transform value onto the space's client varp. Baited houses fill
 * with birds after [FILL_MINUTES] of real time, online or not.
 */
class BirdhouseScript
@Inject
constructor(
    private val playerList: PlayerList,
    private val clock: MapClock,
    private val random: GameRandom,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val invisibleLevels: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { refresh(player) }
        onEvent<GameLifecycle.LateCycle> { tick() }

        onOpLoc1(NOT_BUILT) { build(it.loc, null) }
        onOpLocU(NOT_BUILT) { build(it.loc, it.objType.internalName) }
        for (house in Birdhouse.entries) {
            onOpLoc2(house.builtLoc) { addSeeds(it.loc, null) }
            onOpLocU(house.builtLoc) { addSeeds(it.loc, it.objType.internalName) }
            onOpLoc2(house.seededLoc) { mes("The bird house is already full of seeds.") }
            onOpLoc3(house.seededLoc) { dismantleEarly(it.loc) }
            onOpLoc1(house.readyLoc) { mes("The bird house is full of birds, ready to be emptied.") }
            onOpLoc2(house.readyLoc) { mes("The birds have eaten all of the seeds.") }
            onOpLoc3(house.readyLoc) { empty(it.loc, reset = false) }
            onOpLoc4(house.readyLoc) { empty(it.loc, reset = true) }
        }
    }

    private fun tick() {
        if (clock.cycle % REFRESH_INTERVAL != 0) {
            return
        }
        for (player in playerList) {
            refresh(player)
        }
    }

    private fun refresh(player: Player) {
        for (space in BirdhouseSpace.entries) {
            val value = transform(player, space)
            val house = Birdhouse.forValue(value)
            if (house != null && value == house.seededValue) {
                val elapsed = epochMinute() - player.vars[space.timeVarp]
                if (elapsed >= FILL_MINUTES) {
                    setState(player, space, house.readyValue, 0)
                    continue
                }
            }
            syncClient(player, space, value)
        }
    }

    private suspend fun ProtectedAccess.build(loc: BoundLocInfo, used: String?) {
        val space = spaceOf(loc) ?: return
        if (transform(player, space) != 0) {
            return
        }
        val house =
            if (used != null) {
                Birdhouse.entries.firstOrNull { it.obj == used } ?: return
            } else {
                Birdhouse.entries.lastOrNull { it.obj in player.inv && hunterLevel() >= it.level }
            }
        if (house == null) {
            mes(
                if (Birdhouse.entries.any { it.obj in player.inv }) {
                    "You don't have the Hunter level to place any of your bird houses."
                } else {
                    "You need a bird house to place here."
                }
            )
            return
        }
        if (hunterLevel() < house.level) {
            mes("You need a Hunter level of ${house.level} to place this bird house.")
            return
        }
        anim(BUILD_SEQ)
        delay(1)
        if (transform(player, space) != 0 || invDel(inv, house.obj).failure) {
            return
        }
        setState(player, space, house.builtValue, 0)
        mes("You place the bird house.")
    }

    private fun ProtectedAccess.addSeeds(loc: BoundLocInfo, used: String?) {
        val space = spaceOf(loc) ?: return
        val house = Birdhouse.forValue(transform(player, space)) ?: return
        if (transform(player, space) != house.builtValue) {
            return
        }
        var units = seedUnits(player, space)
        val seeds = if (used != null) listOf(used) else BIRDHOUSE_SEEDS.keys.filter { it in player.inv }
        if (seeds.none { it in BIRDHOUSE_SEEDS }) {
            mes("You need some seeds to fill the bird house.")
            return
        }
        for (seed in seeds) {
            val worth = BIRDHOUSE_SEEDS[seed] ?: continue
            while (units < SEED_UNITS && seed in player.inv) {
                invDel(inv, seed)
                units += worth
            }
        }
        if (units >= SEED_UNITS) {
            setState(player, space, house.seededValue, 0)
            VarPlayerIntMapSetter.set(player, space.timeVarp, epochMinute())
            mes("You fill the bird house with seeds.")
        } else {
            setState(player, space, house.builtValue, units)
            mes("You add some seeds to the bird house. It needs more to attract birds.")
        }
    }

    private suspend fun ProtectedAccess.dismantleEarly(loc: BoundLocInfo) {
        val space = spaceOf(loc) ?: return
        val house = Birdhouse.forValue(transform(player, space)) ?: return
        val confirm =
            menu(
                "Dismantle the unfinished bird house?",
                "Yes, dismantle it and lose the seeds.",
                "No.",
            )
        ifClose()
        if (confirm != 0 || transform(player, space) != house.seededValue) {
            return
        }
        setState(player, space, 0, 0)
        player.invAddOrDrop(objRepo, CLOCKWORK)
        mes("You dismantle the bird house, recovering the clockwork.")
    }

    private suspend fun ProtectedAccess.empty(loc: BoundLocInfo, reset: Boolean) {
        val space = spaceOf(loc) ?: return
        val house = Birdhouse.forValue(transform(player, space)) ?: return
        if (transform(player, space) != house.readyValue) {
            return
        }
        anim(BUILD_SEQ)
        delay(1)
        if (transform(player, space) != house.readyValue) {
            return
        }
        setState(player, space, 0, 0)
        val feathers = FEATHER_AMOUNTS[random.of(FEATHER_AMOUNTS.size)]
        val nests = rollNests(house)
        player.invAddOrDrop(objRepo, CLOCKWORK)
        player.invAddOrDrop(objRepo, FEATHER, feathers)
        nests.forEach { player.invAddOrDrop(objRepo, it) }
        objRepo.add(Obj.fromOwner(player, player.coords, RAW_BIRD_MEAT, BIRD_COUNT), MEAT_DURATION)
        statAdvance(TrapManager.STAT, house.xp * xpMods.get(player, TrapManager.STAT))
        val nestText =
            when (nests.size) {
                0 -> ""
                1 -> " and a bird nest"
                else -> " and ${nests.size} bird nests"
            }
        mes("You dismantle and discard the trap, retrieving $feathers feathers$nestText.")
        if (reset && house.obj in player.inv && hunterLevel() >= house.level) {
            invDel(inv, house.obj)
            setState(player, space, house.builtValue, 0)
            mes("You place a new bird house.")
        }
    }

    /**
     * One seed nest roll, then [NEST_ROLLS] rolls on the nest table whose chance scales from half
     * of [Birdhouse.nestHigh] at level 50 (and below) to all of it at 99. Successful rolls first try
     * for a clue nest until one clue has been given.
     */
    private fun ProtectedAccess.rollNests(house: Birdhouse): List<String> {
        val level = hunterLevel()
        val nests = ArrayList<String>()
        if (random.randomDouble() < SkillingSuccessRate.successRate(0, SEED_NEST_HIGH, level, MAX_LEVEL)) {
            nests += SEED_NEST
        }
        val scale = if (level < HALF_LEVEL) 0.5 else 0.5 + 0.5 * (level - HALF_LEVEL) / (MAX_LEVEL - HALF_LEVEL)
        val chance = house.nestHigh / 1000.0 * scale
        var clueGiven = false
        val rabbitFoot = RABBIT_FOOT in player.worn
        repeat(NEST_ROLLS) {
            if (random.randomDouble() >= chance) {
                return@repeat
            }
            if (!clueGiven) {
                rollClueNest()?.let {
                    nests += it
                    clueGiven = true
                    return@repeat
                }
            }
            nests += rollNestTable(rabbitFoot)
        }
        return nests
    }

    private fun ProtectedAccess.rollClueNest(): String? {
        val roll = random.of(CLUE_ROLL)
        var threshold = 0
        for ((nest, weight) in CLUE_NESTS) {
            threshold += weight
            if (roll < threshold) {
                return nest.takeUnless { it in player.inv }
            }
        }
        return null
    }

    private fun rollNestTable(rabbitFoot: Boolean): String {
        val table = if (rabbitFoot) NEST_TABLE_RABBIT_FOOT else NEST_TABLE
        var roll = random.of(table.sumOf { it.second })
        for ((nest, weight) in table) {
            roll -= weight
            if (roll < 0) {
                return nest
            }
        }
        return EMPTY_NEST
    }

    private fun ProtectedAccess.hunterLevel(): Int =
        player.hunterLvl + invisibleLevels.get(player, TrapManager.STAT)

    private fun spaceOf(loc: BoundLocInfo): BirdhouseSpace? =
        BirdhouseSpace.entries.firstOrNull { it.loc.asRSCM(RSCMType.LOC) == loc.id }

    private fun transform(player: Player, space: BirdhouseSpace): Int =
        player.vars[space.stateVarp] and STATE_MASK

    private fun seedUnits(player: Player, space: BirdhouseSpace): Int =
        player.vars[space.stateVarp] shr UNITS_SHIFT

    private fun setState(player: Player, space: BirdhouseSpace, value: Int, units: Int) {
        VarPlayerIntMapSetter.set(player, space.stateVarp, value or (units shl UNITS_SHIFT))
        syncClient(player, space, value)
    }

    private fun syncClient(player: Player, space: BirdhouseSpace, value: Int) {
        val varp = ServerCacheManager.getVarp(space.clientVarp) ?: return
        if (player.vars[varp] != value) {
            VarPlayerIntMapSetter.set(player, varp, value)
        }
    }

    private companion object {
        const val NOT_BUILT = "loc.birdhouse_not_built"
        const val CLOCKWORK = "obj.poh_clockwork_mechanism"
        const val FEATHER = "obj.feather"
        const val RAW_BIRD_MEAT = "obj.spit_raw_bird_meat"
        const val RABBIT_FOOT = "obj.hunting_strung_rabbit_foot"
        const val SEED_NEST = "obj.bird_nest_seeds_jan2019"
        const val EMPTY_NEST = "obj.bird_nest_empty"
        const val BUILD_SEQ = "seq.human_pickupfloor"

        const val FILL_MINUTES = 50
        const val REFRESH_INTERVAL = 100
        const val SEED_UNITS = 10
        const val STATE_MASK = 0x1F
        const val UNITS_SHIFT = 5
        const val BIRD_COUNT = 10
        const val MEAT_DURATION = 200
        const val MAX_LEVEL = 99
        const val HALF_LEVEL = 50
        const val SEED_NEST_HIGH = 200
        const val NEST_ROLLS = 5
        const val CLUE_ROLL = 1500

        val FEATHER_AMOUNTS = listOf(30, 40, 50, 60)

        val CLUE_NESTS =
            listOf(
                "obj.wc_clue_nest_elite" to 1,
                "obj.wc_clue_nest_hard" to 2,
                "obj.wc_clue_nest_medium" to 3,
                "obj.wc_clue_nest_easy" to 4,
                "obj.wc_clue_nest_beginner" to 30,
            )

        val NEST_TABLE =
            listOf(
                "obj.bird_nest_ring" to 32,
                "obj.bird_nest_egg_red" to 1,
                "obj.bird_nest_egg_green" to 1,
                "obj.bird_nest_egg_blue" to 1,
                EMPTY_NEST to 65,
            )

        val NEST_TABLE_RABBIT_FOOT =
            listOf(
                "obj.bird_nest_ring" to 32,
                "obj.bird_nest_egg_red" to 1,
                "obj.bird_nest_egg_green" to 1,
                "obj.bird_nest_egg_blue" to 1,
                EMPTY_NEST to 60,
            )
    }
}
