package org.rsmod.content.skills.hunter.pitfall

import dev.openrune.map.MapSingletons.collision
import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pitfall trapping. Each pit is a multiloc driven by its own `hunt_pitfall_state` varbit, so pit
 * state is per player: setting, collapsing and emptying a pit only ever changes that player's
 * varbit. A teased big cat or antelope attacks the player; jumping a spiked pit with it close behind
 * rolls the catch, and a creature that evades a pit will not try the same pit again.
 */
class PitfallScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val traps: TrapManager,
    private val aiInteractions: AiPlayerInteractions,
    private val xpMods: XpModifiers,
    private val invisibleLevels: InvisibleLevels,
    private val rumours: RumourTracker,
) : PluginScript() {
    private class Hunt {
        val pitCycles = HashMap<Int, Int>()
        var teased: Npc? = null
        val evaded = HashMap<Int, Int>()
    }

    private val hunts = HashMap<PlayerUid, Hunt>()

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { tick() }
        onPlayerLogin { clearPits(player) }
        onPlayerLogout {
            clearPits(player)
            hunts.remove(player.uid)
        }

        onOpLoc3("loc.hunting_pitfall_invis_empty") { setPit(it.loc) }
        onOpLoc1("loc.hunting_pitfall_invis_set") { jump(it.loc) }
        onOpLoc2("loc.hunting_pitfall_invis_set") { dismantle(it.loc) }
        for (full in FULL_LOCS) {
            onOpLoc2(full) { collect(it.loc) }
        }
        for (creature in PitfallCreature.entries) {
            onOpNpc1(creature.npc) { tease(it.npc, creature) }
        }
    }

    private fun hunt(player: Player): Hunt = hunts.getOrPut(player.uid) { Hunt() }

    private fun pitOf(loc: BoundLocInfo): Int? =
        (1..Pits.COUNT).firstOrNull { loc.internalName == Pits.baseLoc(it) }

    private fun state(player: Player, pit: Int): Int = player.vars[Pits.varbit(pit)]

    private fun setState(player: Player, pit: Int, value: Int) {
        VarPlayerIntMapSetter.set(player, Pits.varbit(pit), value)
        val hunt = hunt(player)
        if (value == Pits.EMPTY) {
            hunt.pitCycles.remove(pit)
        } else {
            hunt.pitCycles[pit] = mapClock.cycle
        }
    }

    private fun clearPits(player: Player) {
        for (pit in 1..Pits.COUNT) {
            if (player.vars[Pits.varbit(pit)] != Pits.EMPTY) {
                VarPlayerIntMapSetter.set(player, Pits.varbit(pit), Pits.EMPTY)
            }
        }
    }

    private fun tick() {
        if (hunts.isEmpty()) {
            return
        }
        for ((uid, hunt) in hunts.entries.toList()) {
            val player = uid.resolve(playerList) ?: continue
            val expired = hunt.pitCycles.filterValues { mapClock >= it + TrapManager.TRAP_LIFETIME }
            for (pit in expired.keys) {
                setState(player, pit, Pits.EMPTY)
                player.mes("Your pitfall trap has collapsed.")
            }
        }
    }

    private suspend fun ProtectedAccess.setPit(loc: BoundLocInfo) {
        val pit = pitOf(loc) ?: return
        val creature = PitfallCreature.forPit(pit) ?: return
        if (player.hunterLvl < creature.level) {
            mes("You need a Hunter level of ${creature.level} to set up this pitfall trap.")
            return
        }
        val active = hunt(player).pitCycles.size + traps.countOwnedBy(player)
        val max = traps.maxTraps(player, loc.coords)
        if (active >= max) {
            val plural = if (max == 1) "trap" else "traps"
            mes("You don't have a high enough Hunter level to set up more than $max $plural.")
            return
        }
        val log = LOGS.firstOrNull { inv.contains(it) }
        if (KNIVES.none { inv.contains(it) } || log == null) {
            mes("You need a knife and some logs to set up this trap.")
            return
        }
        stopAction()
        faceSquare(loc.coords)
        anim(SET_SEQ)
        delay(SET_CYCLES)
        if (state(player, pit) != Pits.EMPTY || !inv.contains(log)) {
            return
        }
        invDel(inv, log)
        setState(player, pit, Pits.SET)
        mes("You set up the pitfall trap.")
    }

    private suspend fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val pit = pitOf(loc) ?: return
        if (state(player, pit) != Pits.SET) {
            return
        }
        anim(SET_SEQ)
        delay(1)
        setState(player, pit, Pits.EMPTY)
        mes("You dismantle the trap.")
    }

    private suspend fun ProtectedAccess.tease(npc: Npc, creature: PitfallCreature) {
        val spear = player.righthand.isType(SPEAR)
        if (!spear && !inv.contains(TEASING_STICK)) {
            mes("You need a teasing stick to tease the ${creature.displayName}.")
            return
        }
        faceEntitySquare(npc)
        anim(TEASE_SEQ)
        delay(1)
        if (!npc.isSlotAssigned || !npc.isVisible) {
            return
        }
        hunt(player).teased = npc
        npc.opPlayer2(player, aiInteractions)
        mes("You tease the ${creature.displayName}.")
    }

    private suspend fun ProtectedAccess.jump(loc: BoundLocInfo) {
        val pit = pitOf(loc) ?: return
        if (state(player, pit) != Pits.SET) {
            return
        }
        val start = coords
        val landing = landingTile(loc, start) ?: return
        if (!collision.isZoneValid(landing)) {
            return
        }
        stopAction()
        anim(JUMP_SEQ)
        exactMove(
            start = start,
            end = landing,
            delay1 = JUMP_TAKEOFF,
            delay2 = JUMP_LANDING,
            dir = faceTowards(start, landing),
            teleportType = TeleportType.Exempt,
        )
        delay(JUMP_TICKS)
        val hunt = hunt(player)
        val prey = hunt.teased ?: return
        val creature = PitfallCreature.byNpc[prey.type.internalName] ?: return
        if (!isChasing(prey, loc, start, landing) || hunt.evaded[prey.uid.packed] == pit) {
            return
        }
        delay(1)
        if (!prey.isSlotAssigned || !prey.isVisible || state(player, pit) != Pits.SET) {
            return
        }
        if (rollCatch(creature)) {
            fallIn(prey, pit, start, landing)
        } else {
            evade(prey, pit, landing, creature)
        }
    }

    private suspend fun ProtectedAccess.fallIn(
        prey: Npc,
        pit: Int,
        start: CoordGrid,
        landing: CoordGrid,
    ) {
        hunt(player).teased = null
        prey.anim(FALL_SEQ, delay = 0, priority = 0)
        setState(player, pit, Pits.CATCHING)
        npcRepo.despawn(prey, PREY_RESPAWN)
        delay(2)
        val turned = landing.x == start.x
        setState(player, pit, if (turned) Pits.FULL_TURNED else Pits.FULL)
    }

    private fun ProtectedAccess.evade(
        prey: Npc,
        pit: Int,
        landing: CoordGrid,
        creature: PitfallCreature,
    ) {
        hunt(player).evaded[prey.uid.packed] = pit
        prey.anim(LEAP_SEQ, delay = 0, priority = 0)
        val beside = landing.translate((landing.x - coords.x).sign, (landing.z - coords.z).sign)
        prey.teleport(collision, if (beside == landing) landing else beside)
        mes("The ${creature.displayName} leaps over the pit.")
    }

    private fun ProtectedAccess.rollCatch(creature: PitfallCreature): Boolean {
        if (player.hunterLvl < creature.level) {
            return false
        }
        val level = player.hunterLvl + invisibleLevels.get(player, TrapManager.STAT)
        var chance = SkillingSuccessRate.successRate(creature.low, creature.high, level, 99)
        if (player.righthand.isType(SPEAR)) {
            chance += SPEAR_BONUS
        }
        return random.randomDouble() < chance
    }

    private fun isChasing(
        prey: Npc,
        pit: BoundLocInfo,
        start: CoordGrid,
        landing: CoordGrid,
    ): Boolean {
        if (!prey.isSlotAssigned || !prey.isVisible || prey.coords.level != landing.level) {
            return false
        }
        if (prey.coords.chebyshevDistance(start) > CHASE_RANGE) {
            return false
        }
        val maxX = pit.x + pit.adjustedWidth - 1
        val maxZ = pit.z + pit.adjustedLength - 1
        val x = prey.coords.x
        val z = prey.coords.z
        return when {
            landing.z > maxZ -> z <= maxZ
            landing.z < pit.z -> z >= pit.z
            landing.x > maxX -> x <= maxX
            else -> x >= pit.x
        }
    }

    private suspend fun ProtectedAccess.collect(loc: BoundLocInfo) {
        val pit = pitOf(loc) ?: return
        val state = state(player, pit)
        if (state != Pits.FULL && state != Pits.FULL_TURNED) {
            return
        }
        val creature = PitfallCreature.forPit(pit) ?: return
        val loot = creature.guaranteed.toMutableList()
        val fur = creature.fur
        val tatty = creature.tattyFur
        if (fur != null && tatty != null) {
            loot += if (statRandom(TrapManager.STAT, creature.furLow, creature.furHigh, invisibleLevels)) fur else tatty
        }
        if (inv.freeSpace() < loot.size) {
            mes("You don't have enough inventory space to do that.")
            return
        }
        anim(SET_SEQ)
        delay(1)
        if (state(player, pit) != state) {
            return
        }
        setState(player, pit, Pits.EMPTY)
        loot.forEach { invAdd(inv, it) }
        creature.splinters?.let { invAdd(inv, it, random.of(2, 6)) }
        statAdvance(TrapManager.STAT, creature.xp * xpMods.get(player, TrapManager.STAT))
        mes("You've caught a ${creature.displayName}.")
        rumours.onCatch(player, creature.name)
    }

    private val PitfallCreature.splinters: String?
        get() = if (this == PitfallCreature.SunlightAntelope) "obj.sunfiresplinter" else null

    private fun landingTile(loc: BoundLocInfo, from: CoordGrid): CoordGrid? {
        val minX = loc.x
        val maxX = loc.x + loc.adjustedWidth - 1
        val minZ = loc.z
        val maxZ = loc.z + loc.adjustedLength - 1
        return when {
            from.x < minX && from.z in minZ..maxZ -> CoordGrid(maxX + 1, from.z, from.level)
            from.x > maxX && from.z in minZ..maxZ -> CoordGrid(minX - 1, from.z, from.level)
            from.z < minZ && from.x in minX..maxX -> CoordGrid(from.x, maxZ + 1, from.level)
            from.z > maxZ && from.x in minX..maxX -> CoordGrid(from.x, minZ - 1, from.level)
            else -> null
        }
    }

    private fun faceTowards(from: CoordGrid, to: CoordGrid): Int {
        val dx = (to.x - from.x).sign
        val dz = (to.z - from.z).sign
        return when {
            dz > 0 -> constants.em_face_north
            dz < 0 -> constants.em_face_south
            dx > 0 -> constants.em_face_east
            else -> constants.em_face_west
        }
    }

    private companion object {
        const val SET_CYCLES = 3
        const val JUMP_TICKS = 2
        const val JUMP_TAKEOFF = 15
        const val JUMP_LANDING = 45
        const val CHASE_RANGE = 6
        const val PREY_RESPAWN = 50
        const val SPEAR_BONUS = 0.05
        const val SET_SEQ = "seq.human_laytrap"
        const val TEASE_SEQ = "seq.hunting_teasing_animal"
        const val JUMP_SEQ = "seq.human_jump_hurdle"
        const val FALL_SEQ = "seq.hunter_bigcat_fall_in_pit"
        const val LEAP_SEQ = "seq.hunter_bigcat_leap_npc"
        const val TEASING_STICK = "obj.hunting_teasing_stick"
        const val SPEAR = "obj.hg_hunter_spear"

        val KNIVES = listOf("obj.knife", "obj.fletching_knife")

        val LOGS =
            listOf(
                "obj.logs",
                "obj.oak_logs",
                "obj.willow_logs",
                "obj.teak_logs",
                "obj.maple_logs",
                "obj.mahogany_logs",
                "obj.yew_logs",
                "obj.magic_logs",
            )

        val FULL_LOCS =
            listOf(
                "loc.hunter_pitfall_full_graahk",
                "loc.hunter_pitfall_full_larupia",
                "loc.hunter_pitfall_full_kyatt",
                "loc.hunter_pitfall_full_graahk_180",
                "loc.hunter_pitfall_full_larupia_180",
                "loc.hunter_pitfall_full_kyatt_180",
                "loc.hunter_pitfall_full",
            )
    }
}
