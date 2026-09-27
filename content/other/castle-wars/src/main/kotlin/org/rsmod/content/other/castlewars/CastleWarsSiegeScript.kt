package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Everything a team uses to attack or hold a castle: the gates and side doors, the catapults,
 * the collapsible tunnels under the arena and the barricades players carry out of the supply rooms.
 */
internal class CastleWarsSiegeScript
@Inject
constructor(
    private val game: CastleWarsGame,
    private val worldRepo: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val players: PlayerList,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (team in Team.entries) {
            for (leaf in team.mainDoors) {
                onOpLoc1(leaf.closed) { openMainDoor(team) }
                onOpLoc2(leaf.closed) { attackMainDoor(team) }
                onOpLoc1(leaf.open) { closeMainDoor(team) }
                onOpLoc1(leaf.broken!!) { repairMainDoor(team) }
            }
            onOpLoc1(team.sideDoor.closed) { unlockSideDoor(team) }
            onOpLoc1(team.sideDoor.open) { lockSideDoor(team) }

            onOpLoc1(team.catapultLoc) { operateCatapult(team) }
            onOpLoc1(team.brokenCatapultLoc) { repairCatapult(team) }
            onOpLocU(team.catapultLoc, TINDERBOX) { burnCatapult(team) }
            onOpLocU(team.catapultLoc, EXPLOSIVE) { explodeCatapult(team) }
            onOpLocU(team.burningCatapultLoc, WATER) { extinguishCatapult(team) }

            for (npc in listOf(team.barricadeNpc, team.burningBarricadeNpc)) {
                val type = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)))
                onNpcQueue(type, "queue.death") { game.removeBarricade(this.npc) }
                onNpcQueue(type, "queue.com_retaliate_player") {}
                onOpNpc3(npc) { burnOrExtinguish(it.npc) }
                onOpNpcU(npc) { useOnBarricade(it.npc, it.objType) }
            }
        }
        onOpHeld1(BARRICADE) { setUpBarricade(it.slot) }

        for (tunnel in listOf("loc.castlewars_blocked_tunnel_1", "loc.castlewars_blocked_tunnel_2")) {
            onOpLoc1(tunnel) { mineTunnel(it.loc) }
            onOpLocU(tunnel, EXPLOSIVE) { blastTunnel(it.loc) }
        }
        onOpLoc1("loc.castlewars_cavewall_rockslide") { collapseWall(it.loc, explosive = false) }
        onOpLocU("loc.castlewars_cavewall_rockslide", EXPLOSIVE) { collapseWall(it.loc, explosive = true) }

        onIfModalButton("component.castlewars_catapult:castlewars_x_up") { aim(dx = -1, dz = 0) }
        onIfModalButton("component.castlewars_catapult:castlewars_x_down") { aim(dx = 1, dz = 0) }
        onIfModalButton("component.castlewars_catapult:castlewars_z_up") { aim(dx = 0, dz = 1) }
        onIfModalButton("component.castlewars_catapult:castlewars_z_down") { aim(dx = 0, dz = -1) }
        onIfModalButton("component.castlewars_catapult:fire_catapult_button") { fireCatapult() }
    }

    private fun ProtectedAccess.teamOrNull(): Team? = game.playingTeamOf(player)

    private fun ProtectedAccess.hasObj(obj: String): Boolean = inv.contains(obj) || player.worn.contains(obj)

    /* Gates */

    private fun ProtectedAccess.openMainDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("The door is barred from the inside. You'll have to break it down.")
            return
        }
        game.openMainDoor(owner)
    }

    private fun ProtectedAccess.closeMainDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("You can't close the enemy's door.")
            return
        }
        game.closeMainDoor(owner)
    }

    /**
     * Swings at a closed gate until it gives way or the player does something else. The gate has
     * 100 hitpoints shared by every attacker; crush weapons do extra damage to it, stab weapons less.
     */
    private suspend fun ProtectedAccess.attackMainDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team == owner) {
            mes("You can't attack your own team's door!")
            return
        }
        val weapon = player.worn[Wearpos.RightHand.slot]?.let(::getInvObj)
        val seq = weapon?.paramOrNull(params.attack_anim_stance1)?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
        val speed = weapon?.paramOrNull(params.attackrate) ?: UNARMED_SPEED
        while (game.state(owner).mainDoor == DoorState.Closed) {
            anim(seq ?: "seq.human_unarmedpunch")
            soundSynth("synth.human_block_1")
            val damage = (Random.nextInt(0, doorMaxHit(weapon) + 1) * weaponFactor(weapon)).toInt()
            if (game.damageMainDoor(owner, damage)) {
                soundSynth("synth.explosion")
                return
            }
            delay(speed)
        }
    }

    private fun ProtectedAccess.doorMaxHit(weapon: ItemServerType?): Int {
        val bonus = weapon?.paramOrNull(params.melee_strength) ?: 0
        return 1 + (player.strengthLvl + bonus) / DOOR_HIT_DIVISOR
    }

    private fun weaponFactor(weapon: ItemServerType?): Double {
        val category = weapon?.weaponCategory?.name?.lowercase() ?: return CRUSH_FACTOR
        return when {
            listOf("blunt", "spiked", "bludgeon", "unarmed", "claw").any { it in category } -> CRUSH_FACTOR
            listOf("stab", "spear", "banner", "polearm", "pole").any { it in category } -> STAB_FACTOR
            else -> 1.0
        }
    }

    private suspend fun ProtectedAccess.repairMainDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("You can't repair the enemy's door!")
            return
        }
        if (!hasObj(TOOLKIT)) {
            mes("You need a toolkit to repair the door.")
            return
        }
        anim("seq.human_smithing")
        soundSynth("synth.hammer_and_build")
        delay(REPAIR_TICKS)
        game.repairMainDoor(owner)
        mes("You repair the door.")
    }

    /* Side doors */

    private suspend fun ProtectedAccess.unlockSideDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team == owner) {
            soundSynth("synth.unlock")
            game.setSideDoor(owner, open = true)
            return
        }
        anim("seq.human_picklock_cagedoor")
        soundSynth("synth.pick_lock")
        delay(2)
        if (game.state(owner).sideDoorOpen) {
            return
        }
        val chance = LOCKPICK_MIN + (player.thievingLvl - 1) * (1.0 - LOCKPICK_MIN) / (MAX_LEVEL - 1)
        if (Random.nextDouble() < chance) {
            soundSynth("synth.unlock")
            game.setSideDoor(owner, open = true)
            mes("You manage to pick the lock.")
        } else {
            soundSynth("synth.locked")
            mes("You fail to pick the lock.")
        }
    }

    private fun ProtectedAccess.lockSideDoor(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("You can't lock the enemy's door.")
            return
        }
        soundSynth("synth.locked")
        game.setSideDoor(owner, open = false)
    }

    /* Catapults */

    private fun ProtectedAccess.operateCatapult(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("This isn't your team's catapult.")
            return
        }
        if (!inv.contains(ROCK)) {
            mes("You need a rock to load the catapult with.")
            return
        }
        ifOpenMainModal(CATAPULT_INTERFACE)
        for (button in CATAPULT_BUTTONS) {
            ifSetEvents("component.castlewars_catapult:$button", -1..-1, IfEvent.Op1)
        }
        refreshAim()
    }

    private fun ProtectedAccess.aim(dx: Int, dz: Int) {
        val x = (player.vars[AIM_X] + dx).coerceIn(0, AIM_MAX)
        val z = (player.vars[AIM_Z] + dz).coerceIn(0, AIM_MAX)
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, AIM_X, x)
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, AIM_Z, z)
        refreshAim()
    }

    private fun ProtectedAccess.refreshAim() {
        val x = player.vars[AIM_X]
        val z = player.vars[AIM_Z]
        ifSetModel("component.castlewars_catapult:horizontal_coord_t", DIGIT_MODEL + x / 10)
        ifSetModel("component.castlewars_catapult:horizontal_coord_u", DIGIT_MODEL + x % 10)
        ifSetModel("component.castlewars_catapult:vertical_coord_t", DIGIT_MODEL + z / 10)
        ifSetModel("component.castlewars_catapult:vertical_coord_u", DIGIT_MODEL + z % 10)
        player.ifSetPosition(
            "component.castlewars_catapult:marker",
            x * PIXELS_PER_STEP - MARKER_OFFSET,
            (AIM_MAX - z) * PIXELS_PER_STEP - MARKER_OFFSET,
        )
    }

    /**
     * Launches a rock at the aimed square. Catapults are inaccurate, and everyone in the 5x5
     * square it lands on takes 5 to 15 damage - friend or foe.
     */
    private fun ProtectedAccess.fireCatapult() {
        val team = teamOrNull() ?: return
        if (game.catapultState(team) != CatapultState.Operational) {
            ifClose()
            return
        }
        if (!inv.contains(ROCK)) {
            ifClose()
            mes("You need a rock to load the catapult with.")
            return
        }
        ifClose()
        invDel(inv, ROCK, 1)
        val arena = CastleWars.ARENA
        val target =
            CoordGrid(
                arena.x0 + player.vars[AIM_X] * (arena.x1 - arena.x0) / AIM_MAX + Random.nextInt(-1, 2),
                arena.z0 + player.vars[AIM_Z] * (arena.z1 - arena.z0) / AIM_MAX + Random.nextInt(-1, 2),
                0,
            )
        val source = team.catapultCoords.translate(1, 1)
        worldRepo.soundArea(source, "synth.treevillage_catabow_launch", radius = 10)
        val distance = source.chebyshevDistance(target)
        val flight = FLIGHT_BASE + distance * FLIGHT_PER_TILE
        worldRepo.projAnim(
            ProjAnim(
                spotanim = "spotanim.castlewars_catapult_travel".asRSCM(RSCMType.SPOTANIM),
                startHeight = 60,
                endHeight = 0,
                startTime = LAUNCH_DELAY,
                endTime = LAUNCH_DELAY + flight,
                angle = 60,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = target,
            ),
        )
        mes("You fire the catapult.")
        worldQueues.add((LAUNCH_DELAY + flight) / CLIENT_CYCLES_PER_TICK + 1) { impact(target) }
    }

    private fun impact(target: CoordGrid) {
        worldRepo.spotanimMap(CastleWarsGame.spotanim("spotanim.castlewars_catapult_impact"), target)
        worldRepo.soundArea(target, "synth.treevillage_catabow_hit", radius = 8)
        for (player in players) {
            val c = player.coords
            if (c.level == target.level && c.chebyshevDistance(target) <= 2 && game.isPlaying(player)) {
                player.queueHit(
                    delay = 0,
                    type = HitType.Typeless,
                    damage = Random.nextInt(CATAPULT_MIN_HIT, CATAPULT_MAX_HIT + 1),
                    modifier = NoopPlayerHitModifier,
                )
            }
        }
    }

    private suspend fun ProtectedAccess.burnCatapult(owner: Team) {
        val team = teamOrNull() ?: return
        if (team == owner) {
            mes("You can't set fire to your own team's catapult!")
            return
        }
        anim("seq.human_createfire")
        delay(2)
        soundSynth("synth.fire_lit")
        game.setCatapult(owner, CatapultState.Burning)
        mes("You set the catapult on fire!")
    }

    private fun ProtectedAccess.explodeCatapult(owner: Team) {
        val team = teamOrNull() ?: return
        if (team == owner) {
            mes("You can't blow up your own team's catapult!")
            return
        }
        invDel(inv, EXPLOSIVE, 1)
        worldRepo.spotanimMap(CastleWarsGame.spotanim("spotanim.explodingvial"), owner.catapultCoords.translate(1, 1))
        worldRepo.soundArea(owner.catapultCoords, "synth.explosion")
        game.setCatapult(owner, CatapultState.Broken)
        mes("The catapult is blown apart!")
    }

    private fun ProtectedAccess.extinguishCatapult(owner: Team) {
        teamOrNull() ?: return
        invReplace(inv, WATER, 1, "obj.bucket_empty")
        soundSynth("synth.liquid")
        game.setCatapult(owner, CatapultState.Operational)
        mes("You put out the fire.")
    }

    private suspend fun ProtectedAccess.repairCatapult(owner: Team) {
        val team = teamOrNull() ?: return
        if (team != owner) {
            mes("You can't repair the enemy's catapult!")
            return
        }
        if (!hasObj(TOOLKIT)) {
            mes("You need a toolkit to repair the catapult.")
            return
        }
        anim("seq.human_smithing")
        soundSynth("synth.hammer_and_build")
        delay(REPAIR_TICKS)
        game.setCatapult(owner, CatapultState.Operational)
        mes("You repair the catapult.")
    }

    /* Tunnels */

    private fun ProtectedAccess.pickaxe(): ItemServerType? =
        (player.worn.objs.asSequence() + inv.objs.asSequence())
            .filterNotNull()
            .map(::getInvObj)
            .firstOrNull { it.isContentType("content.mining_pickaxe") }

    private suspend fun ProtectedAccess.mineTunnel(loc: BoundLocInfo) {
        teamOrNull() ?: return
        val pickaxe = pickaxe()
        if (pickaxe == null) {
            mes("You need a pickaxe to clear these rocks.")
            return
        }
        val seq = pickaxe.paramOrNull(params.skill_anim)?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
        repeat(MINE_SWINGS) {
            anim(seq ?: "seq.human_mining_bronze_pickaxe")
            soundSynth("synth.mine_quick")
            delay(MINE_SWING_TICKS)
        }
        if (game.tunnelStage(loc.coords) == CastleWarsGame.TUNNEL_CLEAR) {
            return
        }
        val cleared = game.clearTunnelLayer(loc.coords)
        resetAnim()
        mes(if (cleared) "You clear the last of the rocks." else "You clear away some of the rocks.")
    }

    private fun ProtectedAccess.blastTunnel(loc: BoundLocInfo) {
        teamOrNull() ?: return
        invDel(inv, EXPLOSIVE, 1)
        worldRepo.spotanimMap(CastleWarsGame.spotanim("spotanim.explodingvial"), loc.coords)
        worldRepo.soundArea(loc.coords, "synth.explosion")
        val cleared = game.clearTunnelLayer(loc.coords)
        mes(if (cleared) "The explosion clears the tunnel." else "The explosion blasts away some of the rocks.")
    }

    private suspend fun ProtectedAccess.collapseWall(wall: BoundLocInfo, explosive: Boolean) {
        teamOrNull() ?: return
        val tunnel = CastleWars.CAVE_WALLS[wall.coords] ?: return
        if (game.tunnelStage(tunnel) != CastleWarsGame.TUNNEL_CLEAR) {
            mes("The tunnel is already blocked.")
            return
        }
        if (explosive) {
            invDel(inv, EXPLOSIVE, 1)
            worldRepo.spotanimMap(CastleWarsGame.spotanim("spotanim.explodingvial"), wall.coords)
            worldRepo.soundArea(wall.coords, "synth.explosion")
        } else {
            val pickaxe = pickaxe()
            if (pickaxe == null) {
                mes("You need a pickaxe or an explosive potion to bring the wall down.")
                return
            }
            val seq = pickaxe.paramOrNull(params.skill_anim)?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
            anim(seq ?: "seq.human_mining_bronze_pickaxe")
            soundSynth("synth.mine_quick")
            delay(MINE_SWING_TICKS)
            resetAnim()
        }
        game.collapseTunnel(tunnel)
    }

    /* Barricades */

    private fun ProtectedAccess.setUpBarricade(slot: Int) {
        val team = teamOrNull() ?: return
        val here = coords
        val blocked =
            here in CastleWars.STEPPING_STONES ||
                Team.entries.any { here in it.spawnArea } ||
                game.barricadeAt(here) != null ||
                !game.inArena(here)
        if (blocked) {
            mes("You can't set up a barricade here.")
            return
        }
        if (!game.canPlaceBarricade(team)) {
            mes("Your team already has the maximum number of barricades set up.")
            return
        }
        invDel(inv, BARRICADE, 1, slot = slot)
        anim("seq.human_pickupfloor")
        soundSynth("synth.put_down")
        game.placeBarricade(team, here)
    }

    private suspend fun ProtectedAccess.burnOrExtinguish(npc: Npc) {
        teamOrNull() ?: return
        if (game.isBurning(npc)) {
            extinguishBarricade(npc)
        } else {
            burnBarricade(npc)
        }
    }

    private suspend fun ProtectedAccess.burnBarricade(npc: Npc) {
        if (!inv.contains(TINDERBOX)) {
            mes("You need a tinderbox to set the barricade alight.")
            return
        }
        anim("seq.human_createfire")
        delay(2)
        if (game.barricadeTeam(npc) == null || game.isBurning(npc)) {
            return
        }
        soundSynth("synth.fire_lit")
        game.setBarricadeBurning(npc, burning = true)
    }

    private fun ProtectedAccess.extinguishBarricade(npc: Npc) {
        if (!inv.contains(WATER)) {
            mes("You need a bucket of water to put out the fire.")
            return
        }
        invReplace(inv, WATER, 1, "obj.bucket_empty")
        soundSynth("synth.liquid")
        game.setBarricadeBurning(npc, burning = false)
    }

    private suspend fun ProtectedAccess.useOnBarricade(npc: Npc, obj: ItemServerType) {
        teamOrNull() ?: return
        when (obj.internalName) {
            EXPLOSIVE -> {
                if (game.isBurning(npc)) {
                    mes("The flames stop the potion from reaching the barricade.")
                    return
                }
                invDel(inv, EXPLOSIVE, 1)
                game.destroyBarricade(npc)
            }
            TINDERBOX -> if (!game.isBurning(npc)) burnBarricade(npc)
            WATER -> if (game.isBurning(npc)) extinguishBarricade(npc)
            else -> mes("Nothing interesting happens.")
        }
    }

    private companion object {
        const val TINDERBOX = "obj.tinderbox"
        const val EXPLOSIVE = "obj.castlewars_explosives_potion"
        const val WATER = "obj.bucket_water"
        const val TOOLKIT = "obj.castlewars_toolkit"
        const val ROCK = "obj.castlewars_catapult_rock"
        const val BARRICADE = "obj.castlewars_barricade"

        const val CATAPULT_INTERFACE = "interface.castlewars_catapult"
        val CATAPULT_BUTTONS =
            listOf(
                "castlewars_x_up",
                "castlewars_x_down",
                "castlewars_z_up",
                "castlewars_z_down",
                "fire_catapult_button",
            )
        const val AIM_X = "varbit.castlewars_catapultx"
        const val AIM_Z = "varbit.castlewars_catapultz"
        const val AIM_MAX = 30
        const val DIGIT_MODEL = 4863
        const val PIXELS_PER_STEP = 8
        const val MARKER_OFFSET = 4
        const val LAUNCH_DELAY = 30
        const val FLIGHT_BASE = 40
        const val FLIGHT_PER_TILE = 3
        const val CLIENT_CYCLES_PER_TICK = 30
        const val CATAPULT_MIN_HIT = 5
        const val CATAPULT_MAX_HIT = 15

        const val UNARMED_SPEED = 4
        const val DOOR_HIT_DIVISOR = 10
        const val CRUSH_FACTOR = 1.25
        const val STAB_FACTOR = 0.75
        const val REPAIR_TICKS = 4
        const val LOCKPICK_MIN = 0.06
        const val MAX_LEVEL = 99
        const val MINE_SWINGS = 3
        const val MINE_SWING_TICKS = 3
    }
}
