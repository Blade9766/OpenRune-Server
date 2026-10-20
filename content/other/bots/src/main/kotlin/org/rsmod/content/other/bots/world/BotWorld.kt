package org.rsmod.content.other.bots.world

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import dev.openrune.types.varp.VarpLifetime
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Collections
import java.util.IdentityHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.inv.map.InvMapInit
import org.rsmod.api.net.rsprot.BotSessions
import org.rsmod.api.player.queueDeath
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
    private val styled: MutableSet<Player> = Collections.newSetFromMap(IdentityHashMap())

    fun botIdOf(player: Player): Long? = byPlayer[player]

    /** From now on [botId]'s current look is saved with it instead of being generated from its id. */
    fun keepAppearance(botId: Long) {
        actors[botId]?.player?.let { styled += it }
    }

    fun player(botId: Long): Player? = actors[botId]?.player

    /** Moves a bot as `::tele` would; for testing how it finds its way back. */
    fun telejump(botId: Long, coords: CoordGrid): Boolean {
        val player = player(botId) ?: return false
        services.telejump(player, coords)
        return true
    }

    /** Kills a bot as `::die pvm` would; for testing what it does after a death. */
    fun kill(botId: Long): Boolean {
        val player = player(botId) ?: return false
        player.queueDeath()
        return true
    }

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
        val saved = record.save.appearance
        if (saved != null) {
            BotAppearance.apply(player, saved)
            styled += player
        } else {
            BotAppearance.randomize(player, record.id)
        }
        player.runEnergy = constants.run_max_energy
        player.slotId = slot
        sessions.attach(player)
        val result = playerRegistry.add(player)
        if (!result.isSuccess()) {
            sessions.detach(player)
            logger.warn { "Bot ${record.name} could not be registered: $result" }
            return null
        }
        val behaviour = BotRoleBehaviour.of(site, record.config.role, record.config.essenceSource, services)
        behaviour.prepare(player)
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))
        player.rebuildAppearance()
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
        styled -= player
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
        for ((varp, value) in save.vars) {
            player.vars.backing[varp] = value
        }
        val minHitpointsXp = PlayerSkillXPTable.getFineXPFromLevel(MIN_HITPOINTS)
        val xp = save.xp + (HITPOINTS to maxOf(save.xp[HITPOINTS] ?: 0, minHitpointsXp))
        for ((stat, fineXp) in xp) {
            val level = PlayerSkillXPTable.getLevelFromFineXP(fineXp).coerceIn(1, 99)
            val current = save.levels[stat]?.coerceIn(if (stat == HITPOINTS) 1 else 0, MAX_BOOSTED_LEVEL) ?: level
            player.statMap.setFineXP(stat, fineXp)
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
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

    @OptIn(InternalApi::class)
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
            levels =
                ServerCacheManager.getStats().values.associate { stat ->
                    val name = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
                    name to player.statMap.getCurrentLevel(name).toInt()
                },
            appearance = if (player in styled) BotAppearance.snapshot(player) else null,
            vars =
                player.vars.backing.int2IntEntrySet()
                    .filter { it.intValue != 0 && ServerCacheManager.getVarp(it.intKey)?.scope == VarpLifetime.Perm }
                    .associate { it.intKey to it.intValue },
        )

    private fun savedObjs(inv: Inventory): List<SavedObj> =
        inv.objs.mapIndexedNotNull { slot, obj -> obj?.let { SavedObj(slot, it.id, it.count, it.vars) } }

    private companion object {
        private const val BANK = "inv.bank"
        private const val HITPOINTS = "stat.hitpoints"
        private const val MIN_HITPOINTS = 10
        private const val MAX_BOOSTED_LEVEL = 125
    }
}
