package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The arena's scenery: supply tables, the respawn rooms' energy barriers, ladder and trapdoor,
 * the battlement stairs, altars, taps, stepping stones, the portals out of a game, and climbing
 * ropes thrown over enemy battlements.
 */
internal class CastleWarsArenaScript
@Inject
constructor(private val game: CastleWarsGame, private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (table in CastleWars.TABLE_ITEMS.keys) {
            onOpLoc1(table) { takeFromTable(it.type.internalName, 1) }
            onOpLoc2(table) { takeFromTable(it.type.internalName, TAKE_MANY) }
        }
        onOpLoc1("loc.castlewars_table_runepouch_saradomin") { takeRunePouch(Team.Saradomin) }
        onOpLoc1("loc.castlewars_table_runepouch_zamorak") { takeRunePouch(Team.Zamorak) }

        onOpLoc1("loc.castlewars_saradomin_spawndoor") { passBarrier(Team.Saradomin, it.loc) }
        onOpLoc1("loc.castlewars_zamorak_spawndoor") { passBarrier(Team.Zamorak, it.loc) }
        onOpLoc1("loc.castlewars_saradomin_spawnladder") { climbSpawnLadder(Team.Saradomin) }
        onOpLoc1("loc.castlewars_zamorak_spawnladder") { climbSpawnLadder(Team.Zamorak) }
        onOpLoc1("loc.castlewars_saradomin_spawntrapdoor") { descendTrapdoor(Team.Saradomin) }
        onOpLoc1("loc.castlewars_zamorak_spawntrapdoor") { descendTrapdoor(Team.Zamorak) }
        onOpLoc1("loc.castlewars_saradomin_quit") { quitGame(Team.Saradomin) }
        onOpLoc1("loc.castlewars_zamorak_quit") { quitGame(Team.Zamorak) }

        onOpLoc1("loc.castlewars_outsidestairs_saradomin_linked") { climbLinkedStairs(it.loc) }
        onOpLoc1("loc.castlewars_outsidestairs_zamorak_linked") { climbLinkedStairs(it.loc) }
        onOpLoc1("loc.castlewars_outsidestairs_saradomin") { climbCastleStairs(it.loc) }
        onOpLoc1("loc.castlewars_outsidestairs_zamorak") { climbCastleStairs(it.loc) }

        onOpLoc1("loc.castlewars_altar_saradomin") { prayAtAltar(Team.Saradomin) }
        onOpLoc1("loc.castlewars_altar_zamorak") { prayAtAltar(Team.Zamorak) }
        onOpLocU("loc.castlewars_tap", "obj.bucket_empty") { fillBucket() }
        onOpLoc1("loc.castlewars_steping_stone") { jumpToStone(it.loc) }

        onOpLocU("loc.castlewars_battlements_saradomin_climbable", ROPE) { throwRope(Team.Saradomin, it.loc) }
        onOpLocU("loc.castlewars_battlements_zamorak_climbable", ROPE) { throwRope(Team.Zamorak, it.loc) }
        onOpLoc1(ROPE_LOC) { climbRope(it.loc) }
    }

    private fun ProtectedAccess.requireTeam(): Team? {
        val team = game.playingTeamOf(player)
        if (team == null) {
            mes("You need to be playing a game of Castle Wars to do that.")
        }
        return team
    }

    /* Supply tables */

    private suspend fun ProtectedAccess.takeFromTable(table: String, count: Int) {
        val team = requireTeam() ?: return
        val owner = CastleWars.TEAM_TABLES[table]
        if (owner != null && owner != team) {
            mes("Those supplies belong to the ${owner.displayName} team.")
            return
        }
        val obj = CastleWars.TABLE_ITEMS.getValue(table)
        val stackable = checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))).isStackable
        val wanted = if (stackable) count else minOf(count, inv.freeSpace())
        if (wanted <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        anim("seq.human_pickuptable")
        invAdd(inv, obj, wanted, strict = false)
        delay(1)
    }

    private fun ProtectedAccess.takeRunePouch(owner: Team) {
        val team = requireTeam() ?: return
        if (team != owner) {
            mes("Those supplies belong to the ${owner.displayName} team.")
            return
        }
        if (inv.contains(CastleWars.RUNE_POUCH)) {
            mes("You already have a rune pouch.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        anim("seq.human_pickuptable")
        invAdd(inv, CastleWars.RUNE_POUCH, 1)
    }

    /* Respawn rooms */

    private suspend fun ProtectedAccess.passBarrier(owner: Team, loc: BoundLocInfo) {
        if (game.playingTeamOf(player) != owner) {
            mes("Only the ${owner.displayName} team may pass.")
            return
        }
        val (inside, outside) = CastleWars.SPAWN_BARRIERS[loc.coords] ?: return
        if (game.carriedFlag(player) == owner && coords != inside) {
            mes("You can't take your own standard into the respawn room.")
            return
        }
        val dest = if (coords == inside) outside else inside
        soundSynth("synth.godspell_charge")
        glide(dest, "seq.human_walk_f", 1)
    }

    private suspend fun ProtectedAccess.climbSpawnLadder(owner: Team) {
        if (game.playingTeamOf(player) != owner) {
            mes("Only the ${owner.displayName} team may use this ladder.")
            return
        }
        anim("seq.human_reachforladder")
        delay(2)
        telejump(owner.trapdoorLanding(level = 2), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.descendTrapdoor(owner: Team) {
        if (game.playingTeamOf(player) != owner) {
            mes("Only the ${owner.displayName} team may use this trapdoor.")
            return
        }
        if (game.carriedFlag(player) == owner) {
            mes("You can't take your own standard into the respawn room.")
            return
        }
        anim("seq.human_pickupfloor")
        soundSynth("synth.trapdoor_open")
        delay(2)
        telejump(owner.trapdoorLanding(level = 1), TeleportType.Exempt)
    }

    private fun Team.trapdoorLanding(level: Int): CoordGrid =
        when (this) {
            Team.Saradomin -> CoordGrid(2428, 3074, level)
            Team.Zamorak -> CoordGrid(2371, 3133, level)
        }

    private fun ProtectedAccess.quitGame(owner: Team) {
        if (game.playingTeamOf(player) != owner) {
            return
        }
        mes("You leave the game.")
        player.strongQueue(CastleWarsQueues.LEAVE_GAME, 1)
    }

    private suspend fun ProtectedAccess.climbCastleStairs(loc: BoundLocInfo) {
        val dest = CastleWars.CASTLE_STAIRS[loc.coords] ?: return
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbLinkedStairs(loc: BoundLocInfo) {
        val (low, high) = CastleWars.LINKED_STAIRS[loc.coords] ?: return
        val dest = if (coords.chebyshevDistance(low) <= coords.chebyshevDistance(high)) high else low
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.prayAtAltar(owner: Team) {
        if (game.playingTeamOf(player) != owner) {
            mes("The gods don't answer your prayers here.")
            return
        }
        anim("seq.castlewars_pray_start")
        delay(ALTAR_TICKS)
        statRestore("stat.prayer")
        anim("seq.castlewars_pray_end")
        soundSynth("synth.prayer_recharge")
        mes("You recharge your Prayer points.")
    }

    private suspend fun ProtectedAccess.fillBucket() {
        anim("seq.human_pickuptable")
        soundSynth("synth.tap_fill")
        delay(1)
        invReplace(inv, "obj.bucket_empty", 1, "obj.bucket_water")
        mes("You fill the bucket from the tap.")
    }

    private suspend fun ProtectedAccess.jumpToStone(loc: BoundLocInfo) {
        if (coords == loc.coords) {
            return
        }
        if (coords.chebyshevDistance(loc.coords) > 2) {
            mes("You can't reach that stone from here.")
            return
        }
        if (player.frozen) {
            mes("You can't jump while you're bound.")
            return
        }
        soundSynth("synth.jump")
        glide(loc.coords, "seq.human_steppingstonejump", 2)
    }

    /* Climbing ropes */

    private fun BoundLocInfo.outside(): CoordGrid =
        when (angle) {
            LocAngle.West -> coords.translateX(-1)
            LocAngle.North -> coords.translateZ(1)
            LocAngle.East -> coords.translateX(1)
            LocAngle.South -> coords.translateZ(-1)
        }

    private suspend fun ProtectedAccess.throwRope(owner: Team, wall: BoundLocInfo) {
        val team = requireTeam() ?: return
        if (team == owner) {
            mes("You don't need to climb into your own castle.")
            return
        }
        if (coords != wall.outside()) {
            mes("You need to stand right next to the battlements to do that.")
            return
        }
        invDel(inv, ROPE, 1)
        anim("seq.human_throwrope_up")
        soundSynth("synth.ropeclimb")
        locRepo.add(wall.coords, ROPE_LOC, ROPE_TICKS, wall.angle, LocShape.WallDecorStraightNoOffset)
        delay(2)
        climbOver(wall.coords)
    }

    private suspend fun ProtectedAccess.climbRope(rope: BoundLocInfo) {
        if (coords != rope.outside()) {
            mes("The rope only goes up the wall.")
            return
        }
        climbOver(rope.coords)
    }

    private suspend fun ProtectedAccess.climbOver(top: CoordGrid) {
        if (game.carriedFlag(player) != null) {
            mes("You can't climb while holding a standard.")
            return
        }
        glide(top, "seq.emote_climbing_rope", 2)
    }

    private companion object {
        const val TAKE_MANY = 5
        const val ALTAR_TICKS = 3
        const val ROPE = "obj.castlewars_climbing_rope"
        const val ROPE_LOC = "loc.castlewars_battlements_rope_3"
        const val ROPE_TICKS = 150
    }
}
