package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.OLIVE_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_207
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_208
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_LIT_ALTAR
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_TOLD_OF_TEMPLE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.TINDERBOX
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.templeRepaired: Int by intVarp("varp.temple_repaired_p")
private var Player.templeResourcesPercent: Int by intVarp("varp.temple_resources_p")
private var Player.templeSanctity: Int by intVarp("varp.temple_sanctity_p")
private var Player.templeResources: Int by intVarBit("varbit.temple_resources")
private var Player.madePermSerum: Int by intVarBit("varbit.made_perm_serum")

/**
 * The ruined temple of Flamtaer, north-east of Mort'ton.
 *
 * Its fifteen broken walls are shared by everyone: each is repaired through ten stages with a
 * hammer and the player's own pool of building resources, while nearby shades batter them back
 * down. A player's resources are kept in tenths of a percent in `varbit.temple_resources`, fed by
 * sets of one timber beam (or plank), one limestone brick and five swamp paste. Repairing earns
 * sanctity, which drains by 1% a minute and is lost on logout. Once every wall stands the fire
 * altar can be lit, and its sacred flame turns olive oil into sacred oil (1% sanctity a dose,
 * from 10%) and Serum 207 into Serum 208 (5% a dose, from 20%).
 *
 * The flamtaer_status overlay's client script prints `varp.temple_repaired_p`,
 * `varp.temple_resources_p` and `varp.temple_sanctity_p` as percentages.
 */
@Singleton
class FlamtaerTemple
@Inject
constructor(
    private val shades: ShadesOfMorttonQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val wallStages = HashMap<CoordGrid, Int>()
    private val visitors = HashSet<Player>()
    private var altarState = AltarState.Broken
    private var flameExpiry = 0
    private var ticks = 0

    val repairPercent: Int
        get() = wallStages.values.sum() * 100 / (WALLS.size * MAX_STAGE)

    override fun ScriptContext.startup() {
        for (coords in WALLS.keys) {
            wallStages[coords] = 0
        }
        for (types in WALL_TYPES.values) {
            for (type in types) {
                onOpLoc1(type) { repair(it.loc.coords) }
            }
        }
        onOpLoc1(ALTAR_UNLIT) { lightAltar() }
        onOpLoc1(ALTAR_LIT) { mes("The sacred flame burns brightly.") }
        onOpLocU(ALTAR_BROKEN) { mes("You're not quite sure how to fix this.") }
        onOpLocU(ALTAR_UNLIT) { mes("The fire altar needs to be lit first.") }
        for (oil in OLIVE_OIL) {
            onOpLocU(ALTAR_LIT, oil) { sanctifyOil(oil) }
        }
        for (serum in SERUM_207) {
            onOpLocU(ALTAR_LIT, serum) { perfectSerum(serum) }
        }
        onPlayerCoordsChanged {
            if (lastKnownCoords != player.coords) {
                updateVisitor(player)
            }
        }
        onPlayerLogin {
            player.templeSanctity = 0
            player.templeResourcesPercent = player.templeResources / UNITS_PER_PERCENT
        }
        onPlayerLogout { visitors.remove(player) }
        worldQueues.add(1) { tick() }
    }

    private fun updateVisitor(player: Player) {
        val inside = MorttonCoords.inTemple(player.coords)
        if (inside && visitors.add(player)) {
            player.ifOpenOverlay(OVERLAY, OVERLAY_TARGET, eventBus)
            syncOverlay(player)
        } else if (!inside && visitors.remove(player)) {
            player.ifCloseOverlay(OVERLAY, eventBus)
        }
    }

    private fun syncOverlay(player: Player) {
        player.templeRepaired = repairPercent
        player.templeResourcesPercent = player.templeResources / UNITS_PER_PERCENT
    }

    private fun tick() {
        worldQueues.add(1) { tick() }
        ticks++
        if (ticks % DECAY_INTERVAL == 0) {
            shadeAttack()
        }
        if (ticks % SANCTITY_DRAIN_INTERVAL == 0) {
            for (player in playerList) {
                if (player.templeSanctity > 0) {
                    player.templeSanctity--
                }
            }
        }
        if (altarState == AltarState.Lit && ticks >= flameExpiry) {
            setAltar(if (repairPercent >= FULLY_REPAIRED) AltarState.Unlit else AltarState.Broken)
        }
        visitors.removeIf { !it.isSlotAssigned }
    }

    private fun shadeAttack() {
        val damaged = wallStages.filterValues { it > 0 }.keys
        if (damaged.isEmpty()) {
            return
        }
        val shade = nearbyShades().randomOrNull() ?: return
        val wall = damaged.random()
        shade.faceSquare(wall)
        shade.anim(SHADE_WALL_SEQ)
        world.soundArea(shade, SHADE_ATTACK_SOUND)
        setStage(wall, wallStages.getValue(wall) - 1)
    }

    private fun nearbyShades(): List<Npc> =
        npcRepo.findAll(ZoneKey.from(MorttonCoords.FIRE_ALTAR), SHADE_ZONE_RADIUS)
            .filter { it.id in shadeIds && it.isAliveInWorld() && MorttonCoords.inTemple(it.coords) }
            .toList()

    private suspend fun ProtectedAccess.repair(wall: CoordGrid) {
        if (!shades.isComplete(player) && shades.stage(player) < STAGE_TOLD_OF_TEMPLE) {
            mes("You're not quite sure how to fix this.")
            return
        }
        if (statBase("stat.crafting") < CRAFTING_LEVEL) {
            mesbox("You need a Crafting level of $CRAFTING_LEVEL to repair the temple.")
            return
        }
        val hammer = HAMMERS.firstOrNull { it in inv || it in player.worn }
        if (hammer == null) {
            mes("You need a hammer to repair the temple.")
            return
        }
        if (player.templeResources < REPAIR_COST && !addResourceSet()) {
            resourcesNeeded()
            return
        }
        val stage = wallStages.getValue(wall)
        val bracelet = BRACELET in player.worn
        faceSquare(wall)
        anim(HAMMER_SEQ)
        soundSynth(HAMMER_SOUND)
        delay(HAMMER_TICKS)
        player.templeResources -= REPAIR_COST
        player.templeResourcesPercent = player.templeResources / UNITS_PER_PERCENT
        gainSanctity(if (bracelet) BRACELET_SANCTITY else REPAIR_SANCTITY)
        statAdvance("stat.crafting", REPAIR_XP)
        if (stage >= MAX_STAGE) {
            mes("You reinforce the temple wall.")
            return
        }
        var step = if (hammer == FLAMTAER_HAMMER) FLAMTAER_STEP else HAMMER_STEP
        if (bracelet) step++
        val repaired = (stage + step).coerceAtMost(MAX_STAGE)
        mes("You repair the temple wall.")
        if (repaired < MAX_STAGE) {
            keepRepairing(player, wall)
        }
        setStage(wall, repaired)
    }

    /**
     * Swapping the wall's loc ends the repair script, so the next swing is relaunched from the
     * world queue for as long as the player stays put and is not doing anything else.
     */
    private fun keepRepairing(player: Player, wall: CoordGrid) {
        val standing = player.coords
        worldQueues.add(1) {
            if (!player.isSlotAssigned || player.coords != standing) {
                return@add
            }
            if (wallStages.getValue(wall) >= MAX_STAGE) {
                return@add
            }
            launcher.launch(player) { repair(wall) }
        }
    }

    private fun ProtectedAccess.addResourceSet(): Boolean {
        val timber = TIMBER.firstOrNull { it in inv } ?: return false
        if (BRICK !in inv || inv.count(PASTE) < PASTE_PER_SET) {
            return false
        }
        invDel(inv, timber)
        invDel(inv, BRICK)
        invDel(inv, PASTE, PASTE_PER_SET)
        player.templeResources = (player.templeResources + UNITS_PER_SET).coerceAtMost(MAX_UNITS)
        player.templeResourcesPercent = player.templeResources / UNITS_PER_PERCENT
        mes("You add some building materials to your resources.")
        return true
    }

    private suspend fun ProtectedAccess.resourcesNeeded() {
        mesbox(
            "~ Repairing the temple ~<br><col=0000ff>To repair the temple you need to increase " +
                "your material resource pool by bringing limestone bricks, timber beams (or " +
                "wooden planks) and swamp paste.</col>",
        )
        val lines = buildList {
            add(strike("Timber beams", TIMBER.any { it in inv }))
            add(strike("Limestone bricks", BRICK in inv))
            add(strike("Swamp paste", inv.count(PASTE) >= PASTE_PER_SET))
        }
        mesbox("~ Resources needed ~<br>" + lines.joinToString("<br>"))
    }

    private fun strike(text: String, have: Boolean): String = if (have) "<str>$text</str>" else text

    private fun ProtectedAccess.gainSanctity(amount: Int) {
        val before = player.templeSanctity
        player.templeSanctity = (before + amount).coerceAtMost(MAX_SANCTITY)
        if (before == 0 && player.templeSanctity > 0) {
            mes("You are now allowed to light the holy fire altar.")
        }
    }

    private suspend fun ProtectedAccess.lightAltar() {
        if (TINDERBOX !in inv) {
            mes("You need a tinderbox to light the fire altar.")
            return
        }
        if (player.templeSanctity <= 0) {
            mes("You need to help repair the temple before you may light the holy fire altar.")
            return
        }
        anim(LIGHT_SEQ)
        soundSynth(TINDERBOX_SOUND)
        delay(LIGHT_TICKS)
        if (altarState != AltarState.Unlit) {
            return
        }
        soundSynth(FIRE_SOUND)
        mes("You light the holy fire altar.")
        if (shades.stage(player) in STAGE_TOLD_OF_TEMPLE until STAGE_LIT_ALTAR) {
            shades.advanceTo(this, STAGE_LIT_ALTAR)
        }
        flameExpiry = ticks + FLAME_TICKS
        setAltar(AltarState.Lit)
    }

    private suspend fun ProtectedAccess.sanctifyOil(oil: String) {
        if (!shades.isComplete(player) && shades.stage(player) < STAGE_TOLD_OF_TEMPLE) {
            mes("You're not quite sure what to do with this.")
            return
        }
        val doses = OLIVE_OIL.indexOf(oil) + 1
        if (player.templeSanctity < OIL_MIN_SANCTITY || player.templeSanctity < doses * OIL_COST) {
            mes("You need at least $OIL_MIN_SANCTITY% sanctity to sanctify oil in the sacred flame.")
            return
        }
        anim(POUR_SEQ)
        soundSynth(POUR_SOUND)
        delay(1)
        player.templeSanctity -= doses * OIL_COST
        invReplace(inv, oil, 1, SACRED_OIL[doses - 1])
        mes("You carefully sanctify the oil in the sacred flame.")
        if (shades.stage(player) in STAGE_TOLD_OF_TEMPLE until STAGE_SACRED_OIL) {
            shades.advanceTo(this, STAGE_SACRED_OIL)
        }
    }

    private suspend fun ProtectedAccess.perfectSerum(serum: String) {
        val doses = SERUM_207.indexOf(serum) + 1
        if (statBase("stat.herblore") < SERUM_LEVEL) {
            mesbox("You need a Herblore level of $SERUM_LEVEL to do this.")
            return
        }
        if (player.templeSanctity < SERUM_MIN_SANCTITY || player.templeSanctity < doses * SERUM_COST) {
            mes("You need at least $SERUM_MIN_SANCTITY% sanctity to change the serum in the sacred flame.")
            return
        }
        anim(POUR_SEQ)
        soundSynth(POUR_SOUND)
        delay(1)
        player.templeSanctity -= doses * SERUM_COST
        invReplace(inv, serum, 1, SERUM_208[doses - 1])
        player.madePermSerum = 1
        mes("The serum bubbles in the sacred flame and becomes Serum 208.")
    }

    private fun setStage(wall: CoordGrid, stage: Int) {
        val previous = wallStages.getValue(wall)
        if (previous == stage) {
            return
        }
        val types = WALL_TYPES.getValue(WALLS.getValue(wall))
        val current = findLoc(wall, types[previous]) ?: return
        wallStages[wall] = stage
        locRepo.change(current, locType(types[stage]), Int.MAX_VALUE)
        val repaired = repairPercent
        for (visitor in visitors) {
            visitor.templeRepaired = repaired
        }
        if (repaired >= FULLY_REPAIRED && altarState == AltarState.Broken) {
            setAltar(AltarState.Unlit)
            for (visitor in visitors) {
                visitor.mes("The holy fire altar appears sanctified, it can now be lit!")
            }
        } else if (repaired < FULLY_REPAIRED && altarState == AltarState.Unlit) {
            setAltar(AltarState.Broken)
        }
    }

    private fun setAltar(state: AltarState) {
        val current = findLoc(MorttonCoords.FIRE_ALTAR, altarState.loc) ?: return
        altarState = state
        locRepo.change(current, locType(state.loc), Int.MAX_VALUE)
    }

    private fun findLoc(coords: CoordGrid, type: String): LocInfo? =
        locRepo.findExact(coords, locType(type))

    private fun locType(name: String): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "Missing loc: $name" }

    private enum class AltarState(val loc: String) {
        Broken(ALTAR_BROKEN),
        Unlit(ALTAR_UNLIT),
        Lit(ALTAR_LIT),
    }

    private enum class WallKind {
        Straight,
        Corner,
    }

    private companion object {
        const val OVERLAY = "interface.flamtaer_status"
        const val OVERLAY_TARGET = "component.toplevel_osrs_stretch:overlay_hud"

        const val ALTAR_BROKEN = "loc.templefire_altar_nofire_broken"
        const val ALTAR_UNLIT = "loc.templefire_altar_nofire"
        const val ALTAR_LIT = "loc.templefire_altar"

        const val MAX_STAGE = 10
        const val FULLY_REPAIRED = 100
        const val CRAFTING_LEVEL = 20
        const val SERUM_LEVEL = 15

        const val UNITS_PER_PERCENT = 10
        const val UNITS_PER_SET = 50
        const val MAX_UNITS = 1000
        const val REPAIR_COST = 3
        const val PASTE_PER_SET = 5

        const val MAX_SANCTITY = 100
        const val REPAIR_SANCTITY = 1
        const val BRACELET_SANCTITY = 2
        const val OIL_MIN_SANCTITY = 10
        const val OIL_COST = 1
        const val SERUM_MIN_SANCTITY = 20
        const val SERUM_COST = 5

        const val HAMMER_STEP = 1
        const val FLAMTAER_STEP = 2
        const val REPAIR_XP = 5.0

        const val HAMMER_TICKS = 3
        const val LIGHT_TICKS = 3
        const val DECAY_INTERVAL = 50
        const val SANCTITY_DRAIN_INTERVAL = 100
        const val FLAME_TICKS = 1000
        const val SHADE_ZONE_RADIUS = 2

        const val HAMMER_SEQ = "seq.human_hammer_hit"
        const val LIGHT_SEQ = "seq.human_createfire"
        const val POUR_SEQ = "seq.human_pickuptable"
        const val SHADE_WALL_SEQ = "seq.shade_attack_wall"
        const val HAMMER_SOUND = "synth.hammering_1"
        const val TINDERBOX_SOUND = "synth.tinderbox_strike"
        const val FIRE_SOUND = "synth.fire_lit"
        const val POUR_SOUND = "synth.oil_pour"
        const val SHADE_ATTACK_SOUND = "synth.shade_attack"

        const val FLAMTAER_HAMMER = "obj.flamtaer_hammer"
        const val BRACELET = "obj.flamtaer_bracelet"
        val HAMMERS = listOf(FLAMTAER_HAMMER, "obj.imcando_hammer", "obj.hammer")
        val TIMBER = listOf("obj.timberbeam", "obj.woodplank")
        const val BRICK = "obj.limestonebrick"
        const val PASTE = "obj.swamppaste"

        val shadeIds =
            setOf("npc.shadeshadow_level1", "npc.shade_level1").map { it.asRSCM(RSCMType.NPC) }.toSet()

        /** The south wall's middle tile is left open as the doorway. */
        val WALLS =
            mapOf(
                CoordGrid(3504, 3314, 0) to WallKind.Corner,
                CoordGrid(3504, 3318, 0) to WallKind.Corner,
                CoordGrid(3508, 3314, 0) to WallKind.Corner,
                CoordGrid(3508, 3318, 0) to WallKind.Corner,
                CoordGrid(3504, 3315, 0) to WallKind.Straight,
                CoordGrid(3504, 3316, 0) to WallKind.Straight,
                CoordGrid(3504, 3317, 0) to WallKind.Straight,
                CoordGrid(3505, 3314, 0) to WallKind.Straight,
                CoordGrid(3505, 3318, 0) to WallKind.Straight,
                CoordGrid(3506, 3318, 0) to WallKind.Straight,
                CoordGrid(3507, 3314, 0) to WallKind.Straight,
                CoordGrid(3507, 3318, 0) to WallKind.Straight,
                CoordGrid(3508, 3315, 0) to WallKind.Straight,
                CoordGrid(3508, 3316, 0) to WallKind.Straight,
                CoordGrid(3508, 3317, 0) to WallKind.Straight,
            )

        /** Each kind's loc per stage, from the rubble at 0 to the rebuilt wall at [MAX_STAGE]. */
        val WALL_TYPES =
            mapOf(
                WallKind.Straight to listOf("loc.templewall_base") + (1..MAX_STAGE).map { "loc.templewall_$it" },
                WallKind.Corner to
                    listOf("loc.templewallcorner_base") + (1..MAX_STAGE).map { "loc.templewallcorner_$it" },
            )
    }
}
