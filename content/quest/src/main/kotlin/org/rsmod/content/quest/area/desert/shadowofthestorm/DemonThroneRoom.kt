package org.rsmod.content.quest.area.desert.shadowofthestorm

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.AGRITH_NAAR
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BADDEN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BADDEN_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DENATH
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DENATH_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.ERIC
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.ERIC_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.JENNIFER
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.MATTHEW
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.PATRICK
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_PORTAL_CLOSE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIRST_RITUAL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_INFILTRATED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_SECOND_RITUAL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TANYA
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TANYA_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.THRONE_GOLEM
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The cult's meeting place: a private copy of level 2 of the temple region, so that one player's
 * summoning never walks into another's.
 *
 * The room is repopulated from the quest stage every time the player steps through the demon
 * door, which keeps the scene honest across logouts and lets the player leave and come back
 * mid-recruitment. [spawnsFor] is the whole of that decision.
 */
@Singleton
class DemonThroneRoom
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val instances: QuestInstances,
    private val locRepo: LocRepository,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val visits = HashMap<Long, QuestInstances.Visit>()

    override fun ScriptContext.startup() {
        onOpLoc1(CLOSING_PORTAL) { leave() }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) { visits.remove(player.uuid) }
    }

    fun visitFor(player: Player): QuestInstances.Visit? = player.uuid?.let(visits::get)

    fun inside(player: Player): Boolean = visitFor(player) != null

    /** The instance tile matching world tile [world], or [world] itself outside an instance. */
    fun at(player: Player, world: CoordGrid): CoordGrid = visitFor(player)?.at(world) ?: world

    /** Every npc currently standing in this player's copy of the room. */
    fun occupants(player: Player): List<Npc> =
        visitFor(player)?.let(instances::npcsIn) ?: emptyList()

    fun findNpc(player: Player, type: String): Npc? {
        val id = occupants(player)
        return id.firstOrNull { it.isType(type) }
    }

    fun spawn(player: Player, type: String, world: CoordGrid, face: Direction? = null): Npc? {
        val visit = visitFor(player) ?: return null
        return instances.spawn(visit, type, world, face)
    }

    /**
     * Agrith-Naar, awake and looking for the player. Scene npcs are spawned inert, so his own
     * mode has to be put back; he is rooted to the marked floor, as he is on live - the circle
     * is what is holding him here - so his reach has to cover the room instead.
     */
    fun spawnDemon(player: Player): Npc? {
        val demon = spawn(player, AGRITH_NAAR, ThroneRoom.DEMON_TILE) ?: return null
        demon.mode = demon.type.defaultMode
        demon.moveRestrict = MoveRestrict.NoMove
        demon.apRangeOverride = DEMON_REACH
        demon.opPlayer2(player, aiInteractions)
        return demon
    }

    fun despawn(npc: Npc) {
        instances.remove(npc)
    }

    /** Swaps the way home for `loc.agrith_portal_closing`; the magic is visibly failing. */
    fun failPortal(player: Player) {
        val visit = visitFor(player) ?: return
        val coords = visit.at(ThroneRoom.EXIT_PORTAL)
        val type = ServerCacheManager.getObject(THRONE_PORTAL.asRSCM(RSCMType.LOC)) ?: return
        val loc = locRepo.findExact(coords, type) ?: return
        val closing = ServerCacheManager.getObject(CLOSING_PORTAL.asRSCM(RSCMType.LOC)) ?: return
        locRepo.change(loc, closing, PORTAL_FAIL_CYCLES)
    }

    /**
     * Opens a copy of the throne room and puts the player inside it. Returns false when no copy
     * could be made, having already told the player why.
     */
    suspend fun ProtectedAccess.enterThroneRoom(): Boolean {
        val uuid = player.uuid ?: return false
        if (with(instances) { insideCopy() }) {
            mes("You are already inside an instance.")
            return false
        }
        val visit =
            try {
                with(instances) {
                    enterCopy(
                        key = KEY,
                        worldEnter = ThroneRoom.ARRIVAL,
                        exit = ThroneRoom.CORRIDOR,
                        bossName = "Agrith-Naar",
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Throne room entry failed for ${player.displayName}." }
                mes("Shadow of the Storm: the portal would not open (${e::class.simpleName}: ${e.message}).")
                null
            }
        if (visit == null) {
            return false
        }
        visits[uuid] = visit
        // The client needs a cycle to rebuild the region before anything is spawned into it.
        delay(1)
        populate(player)
        return true
    }

    /** Puts the player back in the temple corridor and tears their copy of the room down. */
    suspend fun ProtectedAccess.leave() {
        arriveDelay()
        mes("You step into the portal.")
        soundSynth(SOUND_PORTAL_CLOSE)
        delay(1)
        with(instances) { leaveCopy() }
        visits.remove(player.uuid)
        telejump(ThroneRoom.CORRIDOR, TeleportType.Exempt)
    }

    /** Fills the room with whoever should be standing in it at the player's current stage. */
    fun populate(player: Player) {
        val visit = visitFor(player) ?: return
        for (npc in instances.npcsIn(visit)) {
            instances.remove(npc)
        }
        for (spawn in spawnsFor(player)) {
            instances.spawn(visit, spawn.type, spawn.coords, spawn.face)
        }
        if (sots.stage(player) == STAGE_FIGHT) {
            spawnDemon(player)
        }
    }

    private fun spawnsFor(player: Player): List<Placement> {
        val stage = sots.stage(player)
        val watchers =
            listOf(
                Placement(MATTHEW, ThroneRoom.MATTHEW_TILE, Direction.West),
                Placement(JENNIFER, ThroneRoom.JENNIFER_TILE, Direction.West),
                Placement(PATRICK, ThroneRoom.PATRICK_TILE, Direction.East),
            )
        return when {
            stage < STAGE_INFILTRATED || stage >= STAGE_COMPLETE -> emptyList()

            // Denath's cult, before the player's first turn in the circle.
            stage < STAGE_FIRST_RITUAL ->
                watchers +
                    listOf(
                        Placement(DENATH, ThroneRoom.DENATH_TILE, Direction.South),
                        Placement(TANYA, ThroneRoom.TANYA_TILE, Direction.East),
                        Placement(ERIC, ThroneRoom.ERIC_TILE, Direction.West),
                    )

            // The circle is set; Denath takes the north point and the player the north-west.
            stage == STAGE_FIRST_RITUAL ->
                watchers +
                    listOf(
                        Placement(DENATH_SIGIL, ThroneRoom.CIRCLE_NORTH, Direction.South),
                        Placement(TANYA_SIGIL, ThroneRoom.CIRCLE_NORTH_EAST, Direction.SouthWest),
                        Placement(ERIC_SIGIL, ThroneRoom.CIRCLE_SOUTH_EAST, Direction.NorthWest),
                        Placement(DAVE_SIGIL, ThroneRoom.CIRCLE_SOUTH_WEST, Direction.NorthEast),
                    )

            // After the failed summoning only the two who never entered the circle are left,
            // joined by each caster the player talks back into it.
            stage < STAGE_SECOND_RITUAL -> watchers + recruits(player)

            else -> watchers + secondCircle()
        }
    }

    /** The recruited casters, waiting off to one side until the player calls the circle. */
    private fun recruits(player: Player): List<Placement> = buildList {
        if (player.daveConvinced >= RECRUITED) {
            add(Placement(DAVE, ThroneRoom.DAVE_TILE, Direction.South))
        }
        if (player.baddenAtUzer >= RECRUITED) {
            add(Placement(BADDEN, ThroneRoom.TANYA_TILE, Direction.East))
        }
        if (player.reenAtUzer >= RECRUITED) {
            add(Placement(REEN, ThroneRoom.ERIC_TILE, Direction.West))
        }
        if (player.golemConvinced >= GOLEM_RECRUITED) {
            add(Placement(THRONE_GOLEM, ThroneRoom.GOLEM_TILE, Direction.South))
        }
    }

    /** The four casters on their points, with the north point left free for the player. */
    private fun secondCircle(): List<Placement> =
        listOf(
            Placement(REEN_SIGIL, ThroneRoom.CIRCLE_NORTH_EAST, Direction.SouthWest),
            Placement(BADDEN_SIGIL, ThroneRoom.CIRCLE_SOUTH_EAST, Direction.NorthWest),
            Placement(DAVE_SIGIL, ThroneRoom.CIRCLE_SOUTH_WEST, Direction.NorthEast),
            Placement(GOLEM_SIGIL, ThroneRoom.CIRCLE_NORTH_WEST, Direction.SouthEast),
        ) +
            emptyList()

    private data class Placement(val type: String, val coords: CoordGrid, val face: Direction?)

    companion object {
        const val KEY = "sots_throne_room"
        const val CLOSING_PORTAL = "loc.agrith_portal_closing"

        /** The portal home, on the throne room's south wall. */
        const val THRONE_PORTAL = "loc.golem_demon_portal"

        /** How long the failing portal stays in place before the ordinary one comes back. */
        const val PORTAL_FAIL_CYCLES = 500

        /** Rooted to the circle, he still reaches every corner of the room with fire or a pull. */
        const val DEMON_REACH = 16

        private val logger = InlineLogger()
    }
}
