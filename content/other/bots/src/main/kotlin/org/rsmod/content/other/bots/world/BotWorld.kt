package org.rsmod.content.other.bots.world

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.inv.map.InvMapInit
import org.rsmod.api.net.rsprot.BotSessions
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.isSuccess
import org.rsmod.content.other.bots.core.BotActor
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotSave
import org.rsmod.content.other.bots.core.BotSpawner
import org.rsmod.content.other.bots.core.SavedCoords
import org.rsmod.content.other.bots.core.SavedObj
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.world.WorldType
import org.rsmod.map.CoordGrid

/**
 * Brings bot characters in and out of the world. A bot is an ordinary [Player] with no network
 * session (it gets a [BotSessions] avatar so others can see it) and a negative account id, which
 * keeps it out of every account-save path; its state is persisted by the bot store.
 */
@Singleton
class BotWorld
@Inject
constructor(
    private val playerRegistry: PlayerRegistry,
    private val eventBus: EventBus,
    private val invMapInit: InvMapInit,
    private val sessions: BotSessions,
    private val services: BotWorldServices,
) : BotSpawner {
    private val logger = InlineLogger()
    private val actors = HashMap<Long, PlayerBotActor>()
    private val byPlayer = IdentityHashMap<Player, Long>()

    fun botIdOf(player: Player): Long? = byPlayer[player]

    fun player(botId: Long): Player? = actors[botId]?.player

    override fun spawn(record: BotRecord): BotActor? {
        val site = BotSites[record.config.site]
        if (site == null) {
            logger.warn { "Bot ${record.name}: unknown site '${record.config.site}'." }
            return null
        }
        val slot = playerRegistry.nextFreeSlot() ?: return null
        val player = Player()
        assignIdentity(player, record)
        player.coords = record.save.coords?.let { CoordGrid(it.x, it.z, it.level) } ?: site.bank.center()
        invMapInit.init(player)
        restore(player, record.save)
        player.slotId = slot
        sessions.attach(player)
        val result = playerRegistry.add(player)
        if (!result.isSuccess()) {
            sessions.detach(player)
            logger.warn { "Bot ${record.name} could not be registered: $result" }
            return null
        }
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))
        player.rebuildAppearance()
        val behaviour = BotRoleBehaviour.of(site, record.config.role, record.config.essenceSource, services)
        val actor = PlayerBotActor(player, site, behaviour, services)
        actors[record.id] = actor
        byPlayer[player] = record.id
        return actor
    }

    override fun snapshot(botId: Long): BotSave? = actors[botId]?.player?.let(::toSave)

    override fun despawn(botId: Long): BotSave? {
        val actor = actors.remove(botId) ?: return null
        byPlayer.remove(actor.player)
        val save = toSave(actor.player)
        val player = actor.player
        if (player.slotId != -1 && !player.loggingOut) {
            eventBus.publish(SessionStateEvent.Logout(player))
            playerRegistry.del(player)
            sessions.detach(player)
        }
        return save
    }

    /** Releases the avatar of a bot the engine removed from the world by itself. */
    fun onDeleted(player: Player) {
        if (player.accountId < 0) {
            sessions.detach(player)
        }
    }

    /** The engine is logging a bot out by itself (shutdown, forced disconnect). */
    fun forget(botId: Long): BotSave? {
        val actor = actors.remove(botId) ?: return null
        byPlayer.remove(actor.player)
        return toSave(actor.player)
    }

    private fun assignIdentity(player: Player, record: BotRecord) {
        val id = -record.id
        player.accountId = id.toInt()
        player.characterId = id.toInt()
        player.userId = id
        player.accountHash = id
        player.userHash = id
        player.uuid = id
        player.observerUUID = id
        player.worldType = WorldType.DEFAULT
        player.username = "bot.${record.id}"
        player.displayName = record.name
        player.members = true
        player.modLevel = Rights.NONE
    }

    @OptIn(UncheckedType::class)
    private fun restore(player: Player, save: BotSave) {
        fill(player.inv, save.inv)
        fill(player.worn, save.worn)
        fill(player.invMap.getOrPut(BANK), save.bank)
        val minHitpointsXp = PlayerSkillXPTable.getFineXPFromLevel(MIN_HITPOINTS)
        val xp = save.xp + (HITPOINTS to maxOf(save.xp[HITPOINTS] ?: 0, minHitpointsXp))
        for ((stat, fineXp) in xp) {
            val level = PlayerSkillXPTable.getLevelFromFineXP(fineXp).coerceIn(1, 99)
            player.statMap.setFineXP(stat, fineXp)
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, level.toByte())
        }
    }

    @OptIn(UncheckedType::class)
    private fun fill(inv: Inventory, objs: List<SavedObj>) {
        for (obj in objs) {
            if (obj.slot !in inv.indices || ServerCacheManager.getItem(obj.id) == null) {
                logger.warn { "Dropping invalid saved obj $obj from ${inv.internalName}." }
                continue
            }
            inv.objs[obj.slot] = InvObj(obj.id, obj.count, obj.vars)
        }
    }

    private fun toSave(player: Player): BotSave =
        BotSave(
            coords = SavedCoords(player.coords.x, player.coords.z, player.coords.level),
            inv = savedObjs(player.inv),
            worn = savedObjs(player.worn),
            bank = savedObjs(player.invMap.getOrPut(BANK)),
            xp =
                ServerCacheManager.getStats().values.associate { stat ->
                    val name = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
                    name to player.statMap.getFineXP(name)
                },
        )

    private fun savedObjs(inv: Inventory): List<SavedObj> =
        inv.objs.mapIndexedNotNull { slot, obj -> obj?.let { SavedObj(slot, it.id, it.count, it.vars) } }

    private companion object {
        private const val BANK = "inv.bank"
        private const val HITPOINTS = "stat.hitpoints"
        private const val MIN_HITPOINTS = 10
    }
}
