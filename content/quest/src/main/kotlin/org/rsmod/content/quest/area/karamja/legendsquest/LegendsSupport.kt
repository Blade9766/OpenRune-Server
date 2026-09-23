package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/** Lookups and scheduling shared by every part of Legends' Quest. */
@Singleton
class LegendsSupport
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val launcher: ProtectedAccessLauncher,
) {
    /** The closest npc of [type] within [radius] tiles of [coords] on the same level. */
    fun findNpc(coords: CoordGrid, type: String, radius: Int): Npc? =
        npcRepo
            .findAll(ZoneKey.from(coords), ZONE_RADIUS)
            .filter { it.isType(type) && it.coords.level == coords.level }
            .filter { it.coords.chebyshevDistance(coords) <= radius }
            .minByOrNull { it.coords.chebyshevDistance(coords) }

    fun findNpcs(coords: CoordGrid, radius: Int): List<Npc> =
        npcRepo
            .findAll(ZoneKey.from(coords), ZONE_RADIUS)
            .filter { it.coords.level == coords.level && it.coords.chebyshevDistance(coords) <= radius }
            .toList()

    /**
     * Runs [block] for the player with [uid] once nothing else holds them, for scenes that start
     * from a kill or a timer rather than from anything the player clicked.
     */
    fun launchWhenFree(uid: PlayerUid, block: suspend ProtectedAccess.() -> Unit) {
        launchWhenFree(uid, LAUNCH_ATTEMPTS, block)
    }

    private fun launchWhenFree(uid: PlayerUid, attempts: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            val free = player.queueList.strongQueues == 0 && launcher.launch(player, block = block)
            if (!free && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    fun resolve(uid: PlayerUid): Player? = uid.resolve(playerList)

    private companion object {
        const val ZONE_RADIUS = 3
        const val LAUNCH_ATTEMPTS = 30
    }
}

internal fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
    maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

/** A hit that no armour or prayer stops: burns, falls and scrapes from the caves. */
internal fun ProtectedAccess.hurt(damage: Int) {
    if (damage > 0) {
        takeInstantHit(HitType.Typeless, damage)
    }
}

/** The best axe the player carries or wields and has the Woodcutting level to use. */
internal fun ProtectedAccess.bestAxe(): ItemServerType? =
    bestTool("content.woodcutting_axe", player.woodcuttingLvl)

/** The best pickaxe the player carries or wields and has the Mining level to use. */
internal fun ProtectedAccess.bestPickaxe(): ItemServerType? =
    bestTool("content.mining_pickaxe", player.miningLvl)

private fun ProtectedAccess.bestTool(content: String, level: Int): ItemServerType? {
    val carried = inv.filterNotNull { true } + listOfNotNull(player.righthand)
    return carried
        .map { getInvObj(it) }
        .filter { it.isContentType(content) && level >= (it.paramOrNull(params.levelrequire) ?: 1) }
        .maxByOrNull { it.paramOrNull(params.levelrequire) ?: 1 }
}

/** The animation a tool plays when used, or [fallback] for tools without one. */
internal fun toolAnim(tool: ItemServerType, fallback: String): String {
    val seq = tool.paramOrNull(params.skill_anim) ?: return fallback
    return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
}

/** The server type of [obj], for the hooks that only take a type. */
internal fun objType(obj: String): ItemServerType =
    checkNotNull(dev.openrune.ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "Missing obj: $obj" }

internal fun ProtectedAccess.carries(obj: String): Boolean = inv.count(obj) > 0 || worn.count(obj) > 0
