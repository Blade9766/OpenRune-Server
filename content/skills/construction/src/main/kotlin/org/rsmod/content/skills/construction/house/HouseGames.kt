package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * The games being played in each open house's games and combat rooms, keyed by the house's owner.
 *
 * Nothing here is saved: a game ends with the house, and its npcs go with it. A game whose piece is
 * an npc - the attack stone, the elemental balance, the hangman - takes its built loc away while it
 * runs and puts it back when the game is over. A win only earns a prize key when someone else played
 * too and the owner has put coins in the prize chest.
 */
@Singleton
class HouseGames
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val players: PlayerList,
    private val clock: MapClock,
) {
    class Score {
        var shots: Int = 0
        var points: Int = 0
    }

    class RangingRound(val game: Games.RangingGame) {
        val scores: MutableMap<Player, Score> = LinkedHashMap()
    }

    /** An npc standing in for a built loc while its game is on. */
    class Stand(val npc: Npc, val coords: CoordGrid, val loc: String, val angle: LocAngle, val shape: LocShape)

    class StoneRound(val stone: Games.AttackStone, val stand: Stand) {
        val damage: MutableMap<Player, Int> = LinkedHashMap()
        var total: Int = 0
    }

    class BalanceRound(val balance: Games.Balance, val stand: Stand, var airEarth: Int, var waterFire: Int) {
        val casters: MutableSet<Player> = LinkedHashSet()
        var lastCaster: Player? = null
    }

    class HangmanRound(val word: String, val stand: Stand) {
        val guessed: MutableSet<Char> = HashSet()
        val players: MutableSet<Player> = LinkedHashSet()
        var wrong: Int = 0

        val shown: String
            get() = word.map { if (it in guessed) it else '_' }.joinToString(" ")

        val solved: Boolean
            get() = word.all { it in guessed }
    }

    /** Each player's next emote to copy, as an index into [Games.JESTER_EMOTES], and how many they have copied. */
    class JesterRound(val coords: CoordGrid, val angle: LocAngle, val shape: LocShape) {
        val asked: MutableMap<Player, Int> = HashMap()
        val copied: MutableMap<Player, Int> = LinkedHashMap()
    }

    class TreasureHunt(val fairy: Npc, val coords: CoordGrid, val angle: LocAngle, val shape: LocShape) {
        val players: MutableSet<Player> = LinkedHashSet()
        val lastDistance: MutableMap<Player, Int> = HashMap()
    }

    class Room {
        var ranging: RangingRound? = null
        var stone: StoneRound? = null
        var balance: BalanceRound? = null
        var hangman: HangmanRound? = null
        var jester: JesterRound? = null
        var hunt: TreasureHunt? = null

        /** The combat dummy standing with its sandbag attached, if one is. */
        var dummy: Stand? = null

        /** The pets sparring in the menagerie arena. */
        val arena: MutableList<Npc> = ArrayList()

        /**
         * Stands to sit and npcs to take away once their cycle comes, which a player's own script
         * can't be trusted to do: it ends if they log out part way through.
         */
        val due: MutableMap<Any, Int> = LinkedHashMap()

        /** Where each player on the balance beam climbed up from, to put them back when they come off. */
        val beam: MutableMap<Player, CoordGrid> = HashMap()
    }

    private val rooms = HashMap<Long, Room>()

    fun of(owner: Player): Room = rooms.getOrPut(owner.key()) { Room() }

    /** Takes [loc] away and stands [npc] on its spot for the length of a game. */
    fun stand(loc: BoundLocInfo, locName: String, npc: String): Stand {
        locRepo.del(loc, PERMANENT)
        val spawned = Npc(npc, loc.coords)
        spawned.respawns = false
        npcRepo.add(spawned, PERMANENT)
        return Stand(spawned, loc.coords, locName, loc.angle, loc.shape)
    }

    /** Sits [stand] after [ticks], whatever becomes of whoever finished its game. */
    fun sitAfter(owner: Player, stand: Stand, ticks: Int) {
        of(owner).due[stand] = clock.cycle + ticks
    }

    /** Takes [npc] away after [ticks], or as soon as the house closes. */
    fun removeAfter(owner: Player, npc: Npc, ticks: Int) {
        of(owner).due[npc] = clock.cycle + ticks
    }

    /** Carries out whatever in [owner]'s house has come due; run every cycle someone is in it. */
    fun settle(owner: Player) {
        val room = rooms[owner.key()] ?: return
        val now = clock.cycle
        val ready = room.due.filterValues { it <= now }.keys
        for (thing in ready) {
            room.due.remove(thing)
            when (thing) {
                is Stand -> sit(thing)
                is Npc -> {
                    room.arena.remove(thing)
                    remove(thing)
                }
            }
        }
    }

    /** Ends a game's npc and puts its built loc back. */
    fun sit(stand: Stand) {
        npcRepo.del(stand.npc, PERMANENT)
        locRepo.add(stand.coords, stand.loc, PERMANENT, stand.angle, stand.shape)
    }

    /** Puts [into] in [loc]'s place, facing the same way. */
    fun replace(loc: BoundLocInfo, into: String) {
        locRepo.del(loc, PERMANENT)
        locRepo.add(loc.coords, into, PERMANENT, loc.angle, loc.shape)
    }

    fun spawn(npc: String, coords: CoordGrid): Npc {
        val spawned = Npc(npc, coords)
        spawned.respawns = false
        npcRepo.add(spawned, PERMANENT)
        return spawned
    }

    fun remove(npc: Npc) {
        npcRepo.del(npc, PERMANENT)
    }

    /** Drops every game in [owner]'s house; the house is coming down, so no loc is put back. */
    fun close(owner: Player) {
        val room = rooms.remove(owner.key()) ?: return
        val pending = room.due.keys.map { if (it is Stand) it.npc else it as Npc }
        listOfNotNull(room.stone?.stand?.npc, room.balance?.stand?.npc, room.hangman?.stand?.npc, room.hunt?.fairy, room.dummy?.npc)
            .plus(room.arena)
            .plus(pending)
            .distinct()
            .forEach(::remove)
    }

    /**
     * Declares [winner] of [game] to everyone who played, and hands them a prize key when the game
     * had company and [house]'s prize chest has coins in it.
     */
    fun win(house: ActiveHouse, winner: Player, game: String, played: Collection<Player>) {
        for (player in played) {
            if (player !== winner) {
                player.mes("${winner.displayName} has won the $game.")
            }
        }
        winner.mes("You have won the $game!")
        if (played.size < MIN_PLAYERS_FOR_PRIZE || house.owner.prizeCoins <= 0) {
            return
        }
        winner.invAddOrDrop(objRepo, Games.PRIZE_KEY)
        winner.mes("You have been given a prize key. Use it on the prize chest to claim your prize.")
    }

    /** Everyone standing in [house]. */
    fun inside(house: ActiveHouse): List<Player> {
        val region = house.region
        // The region's north-east corner is the first tile of the next one.
        return players.filter { it.coords.x in region.southWest.x until region.northEast.x && it.coords.z in region.southWest.z until region.northEast.z }
    }

    private fun Player.key(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private companion object {
        const val PERMANENT = Int.MAX_VALUE
        const val MIN_PLAYERS_FOR_PRIZE = 2
    }
}

var Player.prizeCoins: Int by intVarp(Games.PRIZE_VARP)
