package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal enum class FlagState(val varValue: Int) {
    Safe(0),
    Taken(1),
    Dropped(2),
}

internal enum class DoorState {
    Closed,
    Open,
    Broken,
}

internal enum class CatapultState {
    Operational,
    Burning,
    Broken,
}

/** Everything that belongs to one side of a running game. */
internal class TeamState(val team: Team) {
    val waiting = LinkedHashSet<Player>()
    val playing = LinkedHashSet<Player>()
    var score = 0
    var flag = FlagState.Safe
    var carrier: Player? = null
    var carriedSince = 0
    var droppedAt: CoordGrid? = null
    var mainDoor = DoorState.Closed
    var mainDoorHp = CastleWars.DOOR_HITPOINTS
    var sideDoorOpen = false
    var catapult = CatapultState.Operational
    var catapultBurnEnds = 0
    val barricades = ArrayList<Npc>()

    val size: Int
        get() = waiting.size + playing.size
}

/**
 * Owns every Castle Wars game: the waiting rooms and the countdown, the running game's scores,
 * standards, doors, tunnels, catapults and barricades, and the varbits the status overlays and
 * waiting-room timer draw from. Scripts route player actions here; anything that needs protected
 * access on a player (moving them in and out of the arena) goes through the enter/leave strong
 * queues handled by [CastleWarsScript].
 */
@Singleton
internal class CastleWarsGame
@Inject
constructor(
    private val players: PlayerList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
    private val npcHitModifier: NpcHitModifier,
) {
    private val teams = Team.entries.associateWith(::TeamState)
    private val joinedAt = HashMap<Player, Int>()
    private val collapsed = HashMap<CoordGrid, Int>()
    private val burningBarricades = HashMap<Npc, Int>()

    var running = false
        private set

    private var ticksLeft = 0
    private var countdown = CastleWars.BREAK_TICKS
    private var dirty = true

    /** Server-driven players from `::cwbots`; they play like anyone else but earn nothing. */
    val botPlayers: MutableSet<Player> = HashSet()

    /** Wins since the server started, for the lobby scoreboards. */
    val seasonWins: MutableMap<Team, Int> = Team.entries.associateWith { 0 }.toMutableMap()

    init {
        for (tunnel in Team.entries.flatMap(Team::tunnels)) {
            collapsed[tunnel] = TUNNEL_BLOCKED
        }
    }

    fun state(team: Team): TeamState = teams.getValue(team)

    fun teamOf(player: Player): Team? =
        teams.values.firstOrNull { player in it.playing || player in it.waiting }?.team

    fun playingTeamOf(player: Player): Team? = teams.values.firstOrNull { player in it.playing }?.team

    fun waitingTeamOf(player: Player): Team? = teams.values.firstOrNull { player in it.waiting }?.team

    fun isPlaying(player: Player): Boolean = playingTeamOf(player) != null

    fun isParticipant(player: Player): Boolean = teamOf(player) != null

    fun allParticipants(): List<Player> = teams.values.flatMap { it.playing + it.waiting }

    fun inArena(coords: CoordGrid): Boolean =
        coords in CastleWars.ARENA ||
            (coords in CastleWars.TUNNELS && Team.entries.none { coords in it.waitingArea })

    /* Joining and leaving */

    /** The team a player entering through [portal] (null for Guthix) would join, or a refusal. */
    fun chooseTeam(portal: Team?): Result<Team> {
        if (portal == null) {
            val (sara, zam) = state(Team.Saradomin) to state(Team.Zamorak)
            val team =
                when {
                    sara.size < zam.size -> Team.Saradomin
                    zam.size < sara.size -> Team.Zamorak
                    running && sara.score != zam.score ->
                        if (sara.score < zam.score) Team.Saradomin else Team.Zamorak
                    else -> Team.entries.random()
                }
            return Result.success(team)
        }
        if (state(portal).size > state(portal.opponent).size) {
            return Result.failure(IllegalStateException("The ${portal.displayName} team is full, try again later!"))
        }
        return Result.success(portal)
    }

    fun joinWaitingRoom(player: Player, team: Team) {
        state(team).waiting += player
        if (running) {
            player.mes("A game is in progress. You will join it if a place becomes available.")
        }
        dirty = true
    }

    fun leaveWaitingRoom(player: Player) {
        teams.values.forEach { it.waiting -= player }
        dirty = true
    }

    /** Removes [player] from a running game; their standard is dropped where they stand. */
    fun leaveGame(player: Player, dropAt: CoordGrid = player.coords) {
        val team = playingTeamOf(player) ?: return
        dropCarriedFlag(player, dropAt)
        state(team).playing -= player
        joinedAt -= player
        dirty = true
    }

    fun joinRunningGame(player: Player, team: Team) {
        moveIntoGame(player, team)
        dirty = true
    }

    private fun moveIntoGame(player: Player, team: Team) {
        val state = state(team)
        state.waiting -= player
        state.playing += player
        joinedAt[player] = mapClock.cycle
        player.strongQueue(CastleWarsQueues.ENTER_GAME, 1)
    }

    /* The game loop */

    fun tick() {
        if (running) {
            ticksLeft--
            if (ticksLeft % CastleWars.TICKS_PER_MINUTE == 0) {
                dirty = true
            }
            fillVacancies()
            checkCarriers()
            processBurning()
            if (ticksLeft <= 0) {
                endGame()
            }
        } else if (teams.values.all { it.waiting.size >= CastleWars.MIN_PLAYERS_PER_TEAM }) {
            if (--countdown % CastleWars.TICKS_PER_MINUTE == 0) {
                dirty = true
            }
            if (countdown <= 0) {
                startGame()
            }
        } else if (countdown != CastleWars.BREAK_TICKS) {
            countdown = CastleWars.BREAK_TICKS
            dirty = true
        }
        if (dirty) {
            dirty = false
            allParticipants().forEach(::syncVars)
        }
    }

    fun startGame() {
        if (running) {
            return
        }
        resetArena()
        running = true
        ticksLeft = CastleWars.GAME_TICKS
        for (state in teams.values) {
            for (player in state.waiting.toList()) {
                moveIntoGame(player, state.team)
            }
        }
        dirty = true
    }

    /** Lets waiting players into a running game while their team is short of the other one. */
    private fun fillVacancies() {
        for (state in teams.values) {
            val other = state(state.team.opponent)
            while (state.waiting.isNotEmpty() && (state.playing.size < other.playing.size || state.playing.isEmpty())) {
                val player = state.waiting.first()
                player.mes("A place has opened up in the game, and you join the ${state.team.displayName} team.")
                moveIntoGame(player, state.team)
            }
        }
    }

    fun endGame() {
        if (!running) {
            return
        }
        running = false
        val sara = state(Team.Saradomin)
        val zam = state(Team.Zamorak)
        val winner =
            when {
                sara.score > zam.score -> Team.Saradomin
                zam.score > sara.score -> Team.Zamorak
                else -> null
            }
        if (winner != null) {
            seasonWins[winner] = seasonWins.getValue(winner) + 1
        }
        val elapsed = CastleWars.GAME_TICKS - ticksLeft
        val required = minOf(CastleWars.REWARD_MIN_TICKS, elapsed * 3 / 4)
        for (state in teams.values) {
            for (player in state.playing.toList()) {
                dropCarriedFlag(player, player.coords)
                val played = mapClock.cycle - (joinedAt[player] ?: mapClock.cycle)
                reward(player, state.team, winner, played >= required)
                player.strongQueue(CastleWarsQueues.LEAVE_GAME, 1)
            }
            state.playing.clear()
        }
        joinedAt.clear()
        countdown = CastleWars.BREAK_TICKS
        resetArena()
        dirty = true
    }

    private fun reward(player: Player, team: Team, winner: Team?, earned: Boolean) {
        if (player in botPlayers) {
            return
        }
        val own = state(team).score
        val other = state(team.opponent).score
        VarPlayerIntMapSetter.set(player, "varp.castlewars_games_played", player.vars["varp.castlewars_games_played"] + 1)
        val tickets =
            when (winner) {
                null -> {
                    player.midiJingle(CastleWars.JINGLE_DRAW)
                    player.mes("The game has ended in a draw.")
                    if (own == 0) 1 else 2
                }
                team -> {
                    player.midiJingle(CastleWars.JINGLE_VICTORY)
                    player.mes("The ${team.displayName} team has won the game! Congratulations!")
                    VarPlayerIntMapSetter.set(player, "varp.castlewars_wins", player.vars["varp.castlewars_wins"] + 1)
                    if (other == 0) 3 else 2
                }
                else -> {
                    player.midiJingle(CastleWars.JINGLE_DEFEAT)
                    player.mes("The ${winner.displayName} team has won the game. Better luck next time.")
                    VarPlayerIntMapSetter.set(player, "varp.castlewars_loss", player.vars["varp.castlewars_loss"] + 1)
                    1
                }
            }
        if (!earned) {
            player.mes("You did not take part in enough of the game to earn any tickets.")
            return
        }
        val pending = pendingTickets.getOrDefault(player, 0) + tickets
        pendingTickets[player] = pending
    }

    /** Tickets are handed out once the player is back in the lobby, where they have protected access. */
    val pendingTickets: MutableMap<Player, Int> = HashMap()

    /* Var syncing */

    fun markDirty() {
        dirty = true
    }

    fun syncVars(player: Player) {
        val team = teamOf(player)
        for (state in teams.values) {
            val t = state.team
            set(player, t.teamVarbit, if (team == t) 1 else 0)
            set(player, t.scoreVarbit, state.score.coerceAtMost(255))
            set(player, t.flagVarbit, state.flag.varValue)
            set(player, t.mainDoorVarbit, if (state.mainDoor == DoorState.Broken) 0 else state.mainDoorHp)
            set(player, t.sideDoorVarbit, if (state.sideDoorOpen) 1 else 0)
            set(player, t.catapultVarbit, if (state.catapult == CatapultState.Operational) 0 else 1)
            t.tunnelVarbits.forEachIndexed { index, varbit ->
                set(player, varbit, if (collapsed[t.tunnels[index]] == TUNNEL_CLEAR) 1 else 0)
            }
        }
        val barricades = team?.let { state(it).barricades.size } ?: 0
        set(player, "varbit.castlewars_barricades_inside", barricades.coerceAtMost(15))
        set(player, "varbit.castlewars_barricades_outside", 0)
        set(player, "varbit.castlewars_world_client", 0)
        set(player, "varp.castlewars_timer", timerMinutes(player))
        refreshHint(player)
    }

    private fun timerMinutes(player: Player): Int {
        val ticks =
            when {
                isPlaying(player) -> ticksLeft
                running -> ticksLeft + CastleWars.BREAK_TICKS
                teams.values.all { it.waiting.size >= CastleWars.MIN_PLAYERS_PER_TEAM } -> countdown
                else -> 0
            }
        return (ticks + CastleWars.TICKS_PER_MINUTE - 1) / CastleWars.TICKS_PER_MINUTE
    }

    fun clearVars(player: Player) {
        for (team in Team.entries) {
            set(player, team.teamVarbit, 0)
        }
        set(player, "varp.castlewars_timer", 0)
        set(player, "varbit.castlewars_bracelet_active", 0)
        HintArrows.hintStop(player)
    }

    private fun set(player: Player, varName: String, value: Int) {
        if (player.vars[varName] != value) {
            VarPlayerIntMapSetter.set(player, varName, value)
        }
    }

    /** Points [player] at the enemy standard if it is out of its stand, else at their own. */
    private fun refreshHint(player: Player) {
        val team = playingTeamOf(player)
        if (team == null) {
            return
        }
        val target = listOf(state(team.opponent), state(team)).firstOrNull { it.flag != FlagState.Safe }
        val carrier = target?.carrier
        val dropped = target?.droppedAt
        when {
            carrier != null && carrier != player -> HintArrows.hintPlayer(player, carrier)
            dropped != null -> HintArrows.hintCoord(player, dropped)
            else -> HintArrows.hintStop(player)
        }
    }

    /* Standards */

    fun carrierOf(team: Team): Player? = state(team).carrier

    fun carriedFlag(player: Player): Team? = teams.values.firstOrNull { it.carrier == player }?.team

    fun takeFromStand(flagTeam: Team, player: Player) {
        val state = state(flagTeam)
        swapLoc(flagTeam.standCoords, flagTeam.standLoc, flagTeam.emptyStandLoc, LocShape.CentrepieceDiagonal, flagTeam.standAngle)
        state.flag = FlagState.Taken
        state.carrier = player
        state.carriedSince = mapClock.cycle
        announce("The ${flagTeam.displayName} standard has been taken!")
        dirty = true
    }

    fun pickUpDropped(flagTeam: Team, player: Player) {
        val state = state(flagTeam)
        val at = state.droppedAt ?: return
        removeLoc(at, flagTeam.droppedBannerLoc)
        state.droppedAt = null
        state.flag = FlagState.Taken
        state.carrier = player
        state.carriedSince = mapClock.cycle
        dirty = true
    }

    fun canTakeFrom(carrier: Player): Boolean {
        val state = teams.values.firstOrNull { it.carrier == carrier } ?: return false
        return mapClock.cycle - state.carriedSince >= CastleWars.TAKE_FROM_DELAY
    }

    fun passFlag(flagTeam: Team, to: Player) {
        val state = state(flagTeam)
        state.carrier = to
        state.carriedSince = mapClock.cycle
        dirty = true
    }

    /** Puts [flagTeam]'s standard back on its stand, wherever it was. */
    fun returnFlag(flagTeam: Team) {
        val state = state(flagTeam)
        state.droppedAt?.let { removeLoc(it, flagTeam.droppedBannerLoc) }
        state.droppedAt = null
        state.carrier = null
        if (state.flag != FlagState.Safe) {
            swapLoc(flagTeam.standCoords, flagTeam.emptyStandLoc, flagTeam.standLoc, LocShape.CentrepieceDiagonal, flagTeam.standAngle)
        }
        state.flag = FlagState.Safe
        dirty = true
    }

    fun score(team: Team, flagTeam: Team) {
        state(team).score++
        announce("The ${team.displayName} team has captured the ${flagTeam.displayName} standard!")
        returnFlag(flagTeam)
    }

    /** Called once the carrier's hands are empty: puts the standard on the ground or home. */
    fun flagDropped(flagTeam: Team, coords: CoordGrid) {
        val state = state(flagTeam)
        state.carrier = null
        if (coords in CastleWars.STEPPING_STONES || !inArena(coords) || collision.isWalkBlocked(coords) ||
            coords in flagTeam.spawnArea || coords in flagTeam.opponent.spawnArea
        ) {
            returnFlag(flagTeam)
            announce("The ${flagTeam.displayName} standard has been returned to its stand.")
            return
        }
        locRepo.add(coords, flagTeam.droppedBannerLoc, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        state.droppedAt = coords
        state.flag = FlagState.Dropped
        announce("The ${flagTeam.displayName} standard has been dropped!")
        dirty = true
    }

    private fun dropCarriedFlag(player: Player, coords: CoordGrid) {
        val flagTeam = carriedFlag(player) ?: return
        removeBanner(player)
        flagDropped(flagTeam, coords)
    }

    /** Takes a carried standard out of [player]'s hands without spawning it anywhere. */
    fun removeBanner(player: Player) {
        for (team in Team.entries) {
            val bannerId = team.banner.asRSCM(RSCMType.OBJ)
            for (inv in listOf(player.worn, player.inv)) {
                for (slot in inv.indices) {
                    if (inv[slot]?.id == bannerId) {
                        inv[slot] = null
                    }
                }
            }
        }
        player.rebuildAppearance()
    }

    private fun checkCarriers() {
        for (state in teams.values) {
            val carrier = state.carrier ?: continue
            if (!carrier.isValid() || !inArena(carrier.coords)) {
                dropCarriedFlag(carrier, carrier.coords)
                continue
            }
            if (carrier.coords in state.team.castle && playingTeamOf(carrier) == state.team) {
                removeBanner(carrier)
                returnFlag(state.team)
                carrier.mes("You return your team's standard to its stand.")
            }
        }
    }

    private fun Player.isValid(): Boolean = players.any { it === this }

    private fun announce(text: String) {
        for (player in teams.values.flatMap { it.playing }) {
            player.mes(text)
        }
    }

    /* Doors */

    fun openMainDoor(team: Team) {
        val state = state(team)
        if (state.mainDoor != DoorState.Closed) {
            return
        }
        for (leaf in team.mainDoors) {
            removeLoc(leaf.coords, leaf.closed)
            locRepo.add(leaf.openCoords, leaf.open, Int.MAX_VALUE, leaf.openAngle, LocShape.WallStraight)
        }
        state.mainDoor = DoorState.Open
        worldRepo.soundArea(team.mainDoors.first().coords, "synth.bigdoor_open")
    }

    fun closeMainDoor(team: Team) {
        val state = state(team)
        if (state.mainDoor != DoorState.Open) {
            return
        }
        for (leaf in team.mainDoors) {
            removeLoc(leaf.openCoords, leaf.open)
            locRepo.add(leaf.coords, leaf.closed, Int.MAX_VALUE, leaf.angle, LocShape.WallStraight)
        }
        state.mainDoor = DoorState.Closed
        worldRepo.soundArea(team.mainDoors.first().coords, "synth.bigdoor_close")
    }

    /** Knocks [damage] off [team]'s gate; true when that broke it down. */
    fun damageMainDoor(team: Team, damage: Int): Boolean {
        val state = state(team)
        if (state.mainDoor != DoorState.Closed) {
            return false
        }
        state.mainDoorHp = (state.mainDoorHp - damage).coerceAtLeast(0)
        dirty = true
        if (state.mainDoorHp > 0) {
            return false
        }
        for (leaf in team.mainDoors) {
            removeLoc(leaf.coords, leaf.closed)
            locRepo.add(leaf.coords, leaf.broken!!, Int.MAX_VALUE, leaf.angle, LocShape.WallStraight)
        }
        state.mainDoor = DoorState.Broken
        announce("The ${team.displayName} castle's gate has been broken down!")
        return true
    }

    fun repairMainDoor(team: Team) {
        val state = state(team)
        if (state.mainDoor != DoorState.Broken) {
            return
        }
        for (leaf in team.mainDoors) {
            removeLoc(leaf.coords, leaf.broken!!)
            locRepo.add(leaf.coords, leaf.closed, Int.MAX_VALUE, leaf.angle, LocShape.WallStraight)
        }
        state.mainDoor = DoorState.Closed
        state.mainDoorHp = CastleWars.DOOR_HITPOINTS
        dirty = true
    }

    fun setSideDoor(team: Team, open: Boolean) {
        val state = state(team)
        if (state.sideDoorOpen == open) {
            return
        }
        val leaf = team.sideDoor
        if (open) {
            removeLoc(leaf.coords, leaf.closed)
            locRepo.add(leaf.openCoords, leaf.open, Int.MAX_VALUE, leaf.openAngle, LocShape.WallStraight)
        } else {
            removeLoc(leaf.openCoords, leaf.open)
            locRepo.add(leaf.coords, leaf.closed, Int.MAX_VALUE, leaf.angle, LocShape.WallStraight)
        }
        state.sideDoorOpen = open
        dirty = true
    }

    fun doorTeam(coords: CoordGrid): Team? =
        Team.entries.firstOrNull { team ->
            team.mainDoors.any { it.coords == coords || it.openCoords == coords } ||
                team.sideDoor.coords == coords || team.sideDoor.openCoords == coords
        }

    /* Tunnels */

    fun tunnelStage(tunnel: CoordGrid): Int = collapsed[tunnel] ?: TUNNEL_CLEAR

    /** Chips one layer off a blocked tunnel; true when that cleared it. */
    fun clearTunnelLayer(tunnel: CoordGrid): Boolean {
        val stage = tunnelStage(tunnel)
        val angle = CastleWars.TUNNEL_ANGLES.getValue(tunnel)
        when (stage) {
            TUNNEL_BLOCKED -> {
                removeLoc(tunnel, "loc.castlewars_blocked_tunnel_1")
                locRepo.add(tunnel, "loc.castlewars_blocked_tunnel_2", Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
                collapsed[tunnel] = TUNNEL_PARTIAL
            }
            TUNNEL_PARTIAL -> {
                removeLoc(tunnel, "loc.castlewars_blocked_tunnel_2")
                collapsed[tunnel] = TUNNEL_CLEAR
            }
            else -> return true
        }
        dirty = true
        return collapsed[tunnel] == TUNNEL_CLEAR
    }

    /** Brings the cave roof down on [tunnel], killing anyone standing under it. */
    fun collapseTunnel(tunnel: CoordGrid, crush: Boolean = true) {
        if (tunnelStage(tunnel) != TUNNEL_CLEAR) {
            return
        }
        val angle = CastleWars.TUNNEL_ANGLES.getValue(tunnel)
        locRepo.add(tunnel, "loc.castlewars_blocked_tunnel_1", Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
        collapsed[tunnel] = TUNNEL_BLOCKED
        dirty = true
        if (!crush) {
            return
        }
        worldRepo.soundArea(tunnel, "synth.cave_collapse", radius = 10)
        worldRepo.spotanimMap(spotanim("spotanim.rockfall"), tunnel)
        for (player in players) {
            val c = player.coords
            if (c.level == tunnel.level && c.x in tunnel.x..tunnel.x + 1 && c.z in tunnel.z..tunnel.z + 1) {
                player.mes("You are crushed by the falling rocks!")
                player.queueHit(delay = 1, type = HitType.Typeless, damage = player.hitpoints, modifier = NoopPlayerHitModifier)
            }
        }
        dirty = true
    }

    /* Catapults */

    fun catapultState(team: Team): CatapultState = state(team).catapult

    fun setCatapult(team: Team, next: CatapultState) {
        val state = state(team)
        if (state.catapult == next) {
            return
        }
        val from =
            when (state.catapult) {
                CatapultState.Operational -> team.catapultLoc
                CatapultState.Burning -> team.burningCatapultLoc
                CatapultState.Broken -> team.brokenCatapultLoc
            }
        val into =
            when (next) {
                CatapultState.Operational -> team.catapultLoc
                CatapultState.Burning -> team.burningCatapultLoc
                CatapultState.Broken -> team.brokenCatapultLoc
            }
        swapLoc(team.catapultCoords, from, into, LocShape.CentrepieceDiagonal, team.catapultAngle)
        state.catapult = next
        if (next == CatapultState.Burning) {
            state.catapultBurnEnds = mapClock.cycle + CATAPULT_BURN_TICKS
        }
        dirty = true
    }

    fun catapultTeam(coords: CoordGrid): Team? = Team.entries.firstOrNull { it.catapultCoords == coords }

    /* Barricades */

    fun barricadeTeam(npc: Npc): Team? = teams.values.firstOrNull { npc in it.barricades }?.team

    fun canPlaceBarricade(team: Team): Boolean = state(team).barricades.size < CastleWars.BARRICADE_LIMIT

    fun barricadeAt(coords: CoordGrid): Npc? =
        teams.values.flatMap { it.barricades }.firstOrNull { it.coords == coords }

    fun placeBarricade(team: Team, coords: CoordGrid): Npc {
        val npc = Npc(team.barricadeNpc, coords)
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.noneMode()
        state(team).barricades += npc
        dirty = true
        return npc
    }

    fun setBarricadeBurning(npc: Npc, burning: Boolean): Npc? {
        val team = barricadeTeam(npc) ?: return null
        val hitpoints = npc.hitpoints
        val coords = npc.coords
        removeBarricade(npc)
        val replacement = Npc(if (burning) team.burningBarricadeNpc else team.barricadeNpc, coords)
        npcRepo.add(replacement, Int.MAX_VALUE)
        replacement.noneMode()
        replacement.hitpoints = hitpoints
        state(team).barricades += replacement
        if (burning) {
            burningBarricades[replacement] = mapClock.cycle
        }
        dirty = true
        return replacement
    }

    fun isBurning(npc: Npc): Boolean = npc in burningBarricades

    fun removeBarricade(npc: Npc) {
        val tracked = teams.values.any { npc in it.barricades }
        burningBarricades -= npc
        teams.values.forEach { it.barricades -= npc }
        if (tracked) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
        dirty = true
    }

    fun destroyBarricade(npc: Npc) {
        worldRepo.spotanimMap(spotanim("spotanim.explodingvial"), npc.coords)
        worldRepo.soundArea(npc.coords, "synth.explosion")
        removeBarricade(npc)
    }

    private fun processBurning() {
        val now = mapClock.cycle
        for ((npc, since) in burningBarricades.toList()) {
            if ((now - since) % BURN_HIT_INTERVAL == 0 && now != since) {
                npc.queueHit(delay = 0, type = HitType.Typeless, damage = BURN_DAMAGE, modifier = npcHitModifier)
            }
        }
        for (state in teams.values) {
            if (state.catapult == CatapultState.Burning && now >= state.catapultBurnEnds) {
                setCatapult(state.team, CatapultState.Broken)
                announce("The ${state.team.displayName} catapult has burnt down!")
            }
        }
    }

    /* Resetting */

    private fun resetArena() {
        for (state in teams.values) {
            val team = state.team
            state.carrier?.let(::removeBanner)
            returnFlag(team)
            state.score = 0
            state.carriedSince = 0
            when (state.mainDoor) {
                DoorState.Open -> closeMainDoor(team)
                DoorState.Broken -> repairMainDoor(team)
                DoorState.Closed -> Unit
            }
            state.mainDoorHp = CastleWars.DOOR_HITPOINTS
            setSideDoor(team, open = false)
            setCatapult(team, CatapultState.Operational)
            for (npc in state.barricades.toList()) {
                removeBarricade(npc)
            }
        }
        for (tunnel in Team.entries.flatMap(Team::tunnels)) {
            while (tunnelStage(tunnel) == TUNNEL_PARTIAL) {
                clearTunnelLayer(tunnel)
            }
            collapseTunnel(tunnel, crush = false)
        }
        burningBarricades.clear()
        dirty = true
    }

    /* Loc helpers */

    private fun removeLoc(coords: CoordGrid, name: String): LocInfo? {
        val type = ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: return null
        val loc = locRepo.findExact(coords, type) ?: return null
        locRepo.del(loc, Int.MAX_VALUE)
        return loc
    }

    private fun swapLoc(coords: CoordGrid, from: String, into: String, shape: LocShape, angle: LocAngle) {
        removeLoc(coords, from)
        locRepo.add(coords, into, Int.MAX_VALUE, angle, shape)
    }

    /** A free tile within two squares of [centre], for spreading arrivals out. */
    fun scatter(centre: CoordGrid): CoordGrid {
        repeat(SCATTER_TRIES) {
            val candidate = centre.translate(Random.nextInt(-2, 3), Random.nextInt(-2, 3))
            if (!collision.isWalkBlocked(candidate)) {
                return candidate
            }
        }
        return centre
    }

    companion object {
        const val TUNNEL_CLEAR = 0
        const val TUNNEL_PARTIAL = 1
        const val TUNNEL_BLOCKED = 2

        private const val CATAPULT_BURN_TICKS = 25
        private const val BURN_HIT_INTERVAL = 3
        private const val BURN_DAMAGE = 5
        private const val SCATTER_TRIES = 10

        fun spotanim(name: String): SpotanimType = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))
    }
}
