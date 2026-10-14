package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.core.with
import dtx.rs.RSDropTable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.NpcDropReceiveHook
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.drops.tables.monsters.ankouDropTableFreeToPlayWorlds
import org.rsmod.content.drops.tables.monsters.bryophytaDropTableFreeToPlayWorlds
import org.rsmod.content.drops.tables.monsters.farmerDropTableFreeToPlayWorlds
import org.rsmod.content.drops.tables.monsters.oborDropTableFreeToPlayWorlds
import org.rsmod.content.drops.tables.monsters.scurriusNonMvpDropTable
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

/** Tables rolled for every damaging player other than the hero (who rolls the npc's main table). */
private val PARTICIPANT_DROP_TABLES: Map<String, RSDropTable<Player, DropRollItem>> =
    mapOf("npc.rat_boss_normal" to scurriusNonMvpDropTable)

private val ONCE_PER_KILL_OBJS = setOf("obj.rat_boss_spine")

private val FREE_TO_PLAY_DROP_TABLES: Map<String, RSDropTable<Player, DropRollItem>> =
    listOf(
            ankouDropTableFreeToPlayWorlds,
            bryophytaDropTableFreeToPlayWorlds,
            farmerDropTableFreeToPlayWorlds,
            oborDropTableFreeToPlayWorlds,
        )
        .flatMap { table -> table.npcs.map { npc -> npc to table } }
        .toMap()

@Singleton
public class NpcDropTableKillHook
@Inject
constructor(
    private val registry: DropTableRegistry,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val deathDropHooks: Set<NpcDeathDropHook>,
    private val receiveHooks: Set<NpcDropReceiveHook>,
    private val players: PlayerList,
) : NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        val oncePerKill = mutableSetOf<String>()
        val freeToPlayTable = FREE_TO_PLAY_DROP_TABLES[npc.type.internalName]
        val table =
            if (freeToPlayTable != null && !context.hero.members) {
                freeToPlayTable
            } else {
                registry.forNpc(npc, areaChecker)
            }
        table?.let { rollFor(it, context.hero, context, oncePerKill) }

        val participantTable = PARTICIPANT_DROP_TABLES[npc.type.internalName] ?: return
        for (contributor in npc.damageContributions.entries()) {
            if (contributor !is DamageContributor.ByPlayer || contributor.damage <= 0) {
                continue
            }
            val participant = contributor.resolve(players) ?: continue
            if (participant !== context.hero) {
                rollFor(participantTable, participant, context, oncePerKill)
            }
        }
    }

    private fun rollFor(
        table: RSDropTable<Player, DropRollItem>,
        player: Player,
        context: NpcDeathKillContext,
        oncePerKill: MutableSet<String>,
    ) {
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        val dropCoords = context.dropCoords
        val npc = context.npc
        val spawn = { drop: DropRollItem ->
            if (drop.obj !in ONCE_PER_KILL_OBJS || oncePerKill.add(drop.obj)) {
                spawnDrop(drop, dropCoords, duration, player, npc, context.lootTrackerEventId)
            }
        }
        when (
            val result =
                table
                    .roll(
                        player,
                        ArgMap(
                            KillRollContext.npc with npc,
                            KillRollContext.areaChecker with areaChecker,
                        ),
                    ).flatten()
        ) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> spawn(result.result)
            is RollResult.ListOf -> result.results.forEach(spawn)
        }
    }

    private fun spawnDrop(
        drop: DropRollItem,
        coords: CoordGrid,
        duration: Int,
        receiver: Player,
        npc: Npc,
        eventId: Int,
    ) {
        if (drop.isNothing) {
            return
        }
        if (!drop.condition(receiver)) {
            return
        }
        drop.killCondition?.let { killCondition ->
            if (!killCondition(receiver, npc, areaChecker)) {
                return
            }
        }
        val obj =
            drop.killTransformObj?.invoke(receiver, npc, areaChecker)
                ?: drop.transformObj(receiver)
                ?: drop.obj
        val count = drop.rollCount(random)
        CollectionLog.grant(receiver, obj, count)
        if (
            !receiveHooks.any { it.tryReceive(receiver, obj, count) } &&
                !consumedByHook(obj, count, coords, duration, receiver)
        ) {
            val spawned = objRepo.add(obj, coords, duration, receiver, count)
            ClientScripts.lootTrackerAddLoot(receiver, npc.id, eventId, spawned.type, spawned.count)
        }
        for (bonus in drop.bonusDrops) {
            spawnDrop(bonus, coords, duration, receiver, npc, eventId)
        }
    }

    private fun consumedByHook(
        obj: String,
        count: Int,
        coords: CoordGrid,
        duration: Int,
        receiver: Player,
    ): Boolean {
        if (count != 1) {
            return false
        }
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return false
        val context = NpcDeathDropContext(receiver, type, coords, duration, objRepo)
        return deathDropHooks.any { it.tryConsume(context) }
    }
}
