package org.rsmod.content.quest.area.falador.recruitmentdrive

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

/**
 * The player's private copy of the Temple Knight training grounds (map square 38_77, all seven rooms).
 * Every script works in world coordinates; [local] and [world] translate between them and the copy.
 * The copy and the observers in it last for one visit: Sir Tiffy's teleport makes it, and failing,
 * quitting, finishing, dying or logging out ends it.
 *
 * Kept per player in memory only: an instance does not outlive the session that made it.
 */
@Singleton
open class TestingGrounds
@Inject
constructor(
    private val instances: QuestInstances,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
) {
    class Space(val dx: Int, val dz: Int, val visit: QuestInstances.Visit?) {
        val observers = mutableMapOf<TestRoom, Npc>()
        var leye: Npc? = null
    }

    private val spaces = HashMap<PlayerUid, Space>()

    fun space(player: Player): Space? = spaces[player.uid]

    fun inside(player: Player): Boolean = spaces.containsKey(player.uid)

    fun local(player: Player, world: CoordGrid): CoordGrid? {
        val space = space(player) ?: return null
        return CoordGrid(world.x + space.dx, world.z + space.dz, world.level)
    }

    fun world(player: Player, local: CoordGrid): CoordGrid? {
        val space = space(player) ?: return null
        return CoordGrid(local.x - space.dx, local.z - space.dz, local.level)
    }

    /** The room the player stands in, by world tile. */
    fun roomOf(player: Player): TestRoom? = world(player, player.coords)?.let(TestRoom::at)

    /** Creates the copy and puts the player at [arrival] inside it. */
    open fun ProtectedAccess.open(arrival: CoordGrid): Boolean {
        if (inside(player)) {
            return true
        }
        val visit = with(instances) { enterCopy(INSTANCE_KEY, arrival, FALADOR_PARK) } ?: return false
        val origin = visit.at(arrival)
        val space = Space(origin.x - arrival.x, origin.z - arrival.z, visit)
        spaces[player.uid] = space
        for (room in TestRoom.entries) {
            space.observers[room] = instances.spawn(visit, room.observer, room.observerTile)
        }
        return true
    }

    /** Ends the visit and moves the player to [destination]. */
    open fun ProtectedAccess.close(destination: CoordGrid) {
        val space = spaces.remove(player.uid)
        if (space != null) {
            space.leye?.let(::removeNpc)
            space.observers.values.forEach(::removeNpc)
            if (space.visit != null) {
                with(instances) { leaveCopy() }
            }
        }
        telejump(destination, TeleportType.Exempt)
    }

    /** Forgets the copy without moving the player, for logout and death (the instance ends itself). */
    open fun forget(player: Player) {
        val space = spaces.remove(player.uid) ?: return
        space.leye?.let(::removeNpc)
        space.observers.values.forEach(::removeNpc)
    }

    fun ProtectedAccess.moveTo(world: CoordGrid) {
        val target = local(player, world) ?: return
        telejump(target, TeleportType.Exempt)
    }

    fun observer(player: Player, room: TestRoom): Npc? = space(player)?.observers?.get(room)?.takeIf { it.isSlotAssigned }

    open fun spawnNpc(player: Player, type: String, world: CoordGrid): Npc? {
        val space = space(player) ?: return null
        val visit = space.visit ?: return null
        return instances.spawn(visit, type, world)
    }

    open fun spawnObj(player: Player, type: String, world: CoordGrid) {
        val at = local(player, world) ?: return
        objRepo.add(type, at, OBJ_DURATION, receiver = player)
    }

    open fun removeNpc(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** For tests: treats the world map itself as the player's copy. */
    fun adoptWorld(player: Player): Space = Space(0, 0, null).also { spaces[player.uid] = it }

    companion object {
        const val INSTANCE_KEY = "recruitment_drive"

        /** Ground items last as long as any visit could; the copy goes with them. */
        const val OBJ_DURATION = 6000

        /** In front of Sir Tiffy Cashien's bench in Falador Park. */
        val FALADOR_PARK = CoordGrid(2997, 3374, 0)
    }
}
