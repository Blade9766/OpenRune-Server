package org.rsmod.content.other.castlewars.bots

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.net.rsprot.BotSessions
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.isSuccess
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.other.castlewars.CastleWars
import org.rsmod.content.other.castlewars.CastleWarsGame
import org.rsmod.content.other.castlewars.Team
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

private const val BURN_OP = 3

internal enum class BotRole {
    Attacker,
    Defender,
    Hunter,
}

internal class Bot(val player: Player, val role: BotRole, val style: BotStyle) {
    var order: BotOrder? = null
    var orderedAt: Int = 0
    var ateAt: Int = 0
    var prayedAt: Int = 0
    var threatenedAt: Int = 0
    var restoredAt: Int = 0
    var firedAt: Int = 0
    var lastCoords: CoordGrid = CoordGrid.NULL
    var stillFor: Int = 0

    /** Targets this bot gave up on as unreachable, until the cycle each maps to. */
    val ignored: MutableMap<Player, Int> = HashMap()
}

/**
 * Spawns and removes the Castle Wars bots: server-driven players with no client and no account,
 * never saved. `::cwbots` fills both teams up to [TEAM_SIZE] around whoever is playing, with levels
 * copied from the real participants; the bots leave the world once their game is over.
 */
@Singleton
internal class CastleWarsBots
@Inject
constructor(
    private val game: CastleWarsGame,
    private val registry: PlayerRegistry,
    private val sessions: BotSessions,
    private val equipOp: HeldEquipOp,
    private val brain: BotBrain,
    private val spells: MagicSpellRegistry,
    private val eventBus: EventBus,
) {
    private val bots = LinkedHashMap<Player, Bot>()
    private var nextId = 1

    val count: Int
        get() = bots.size

    fun isBot(player: Player): Boolean = player in bots

    /**
     * Adds up to [limit] bots so each team has [TEAM_SIZE] players, counting the real players
     * already waiting or playing; [requester] sets the levels when nobody real has joined yet.
     */
    fun fill(requester: Player, limit: Int): Int {
        val humans = game.allParticipants().filterNot(::isBot).ifEmpty { listOf(requester) }
        val levels = BotLevels.averageOf(humans)
        var added = 0
        for (team in Team.entries) {
            val present = game.allParticipants().count { game.teamOf(it) == team }
            repeat((TEAM_SIZE - present).coerceAtLeast(0)) {
                if (added >= limit) {
                    return added
                }
                val index = game.allParticipants().count { isBot(it) && game.teamOf(it) == team }
                if (spawn(team, ROLES[index % ROLES.size], STYLES[index % STYLES.size], levels) != null) {
                    added++
                }
            }
        }
        return added
    }

    private fun spawn(team: Team, role: BotRole, style: BotStyle, teamLevels: BotLevels): Bot? {
        val slot = registry.nextFreeSlot() ?: return null
        val id = nextId++
        val player =
            Player().apply {
                accountId = 0
                characterId = 0
                userId = BOT_ID_BASE - id
                accountHash = BOT_ID_BASE - id
                userHash = BOT_ID_BASE - id
                uuid = BOT_ID_BASE - id
                observerUUID = BOT_ID_BASE - id
                members = true
                username = "cwbot$id"
                displayName = NAMES[(id - 1) % NAMES.size]
                coords = game.scatter(team.waitingRoom)
                runEnergy = RUN_ENERGY
                slotId = slot
            }
        sessions.attach(player)
        if (!registry.add(player).isSuccess()) {
            sessions.detach(player)
            return null
        }
        val levels = teamLevels.jittered(Random)
        applyLevels(player, levels)
        equip(player, style, levels)
        if (style == BotStyle.Magic) {
            autocastBestFireSpell(player, levels.magic)
        }
        VarPlayerIntMapSetter.set(player, "varp.option_run", 1)
        player.worn[Wearpos.Back.slot] = InvObj(team.cloak)
        player.rebuildAppearance()
        val bot = Bot(player, role, style)
        bots[player] = bot
        game.botPlayers += player
        if (game.running) {
            game.joinRunningGame(player, team)
        } else {
            game.joinWaitingRoom(player, team)
        }
        return bot
    }

    private fun applyLevels(player: Player, levels: BotLevels) {
        val stats =
            mapOf(
                "stat.attack" to levels.attack,
                "stat.strength" to levels.strength,
                "stat.defence" to levels.defence,
                "stat.hitpoints" to levels.hitpoints,
                "stat.ranged" to levels.ranged,
                "stat.magic" to levels.magic,
                "stat.prayer" to levels.prayer,
            )
        for ((stat, level) in stats) {
            player.statMap.setFineXP(stat, PlayerSkillXPTable.getFineXPFromLevel(level))
            player.statMap.setBaseLevel(stat, level.toByte())
            player.statMap.setCurrentLevel(stat, level.toByte())
        }
        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
    }

    private fun equip(player: Player, style: BotStyle, levels: BotLevels) {
        for (obj in BotLoadout.worn(style, levels)) {
            val slot = player.inv.indices.firstOrNull { player.inv[it] == null } ?: return
            val count = if (obj.endsWith("_arrow")) BotLoadout.arrowCount() else 1
            player.inv[slot] = InvObj(obj, count)
            if (equipOp.equip(player, slot, player.inv) !is HeldEquipResult.Success) {
                wearDirectly(player, slot)
            }
        }
        val food = BotLoadout.food(levels)
        repeat(BotLoadout.FOOD_COUNT) {
            val slot = player.inv.indices.firstOrNull { player.inv[it] == null } ?: return
            player.inv[slot] = InvObj(food)
        }
        repeat(BotLoadout.PRAYER_POTIONS) {
            val slot = player.inv.indices.firstOrNull { player.inv[it] == null } ?: return
            player.inv[slot] = InvObj(BotLoadout.PRAYER_POTION)
        }
    }

    /** Bots skip quest locks on gear (a rune platebody needs Dragon Slayer), so they still wear it. */
    private fun wearDirectly(player: Player, slot: Int) {
        val obj = player.inv[slot] ?: return
        val type = getInvObj(obj)
        val wearpos = Wearpos[type.wearpos1] ?: return
        if (player.worn[wearpos.slot] != null) {
            return
        }
        player.worn[wearpos.slot] = obj
        player.inv[slot] = null
        eventBus.publish(HeldEquipEvents.WearposChange(player, wearpos, type))
        eventBus.publish(HeldEquipEvents.Equip(player, slot, wearpos, type))
    }

    private fun autocastBestFireSpell(player: Player, magic: Int) {
        val (id, _) =
            spells.autocastSpells().entries
                .filter { (_, spell) ->
                    spell.spellbook == Spellbook.Standard && spell.name.startsWith("Fire ") && spell.levelReq <= magic
                }
                .maxByOrNull { it.value.levelReq } ?: return
        VarPlayerIntMapSetter.set(player, "varbit.autocast_spell", id)
        VarPlayerIntMapSetter.set(player, "varbit.autocast_set", 1)
    }

    /** One line per bot for `::cwbotinfo`: where it is, what it is doing and how it is doing. */
    fun describe(): List<String> =
        bots.values.map { bot ->
            val player = bot.player
            val team = game.teamOf(player)?.displayName ?: "none"
            val order =
                when (val current = bot.order) {
                    is BotOrder.Attack -> "attack ${current.target.displayName}"
                    is BotOrder.Walk -> "walk ${current.coords.x},${current.coords.z}"
                    is BotOrder.UseLoc -> "use ${current.loc.removePrefix("loc.castlewars_")}"
                    is BotOrder.NpcOp -> if (current.op == BURN_OP) "burn barricade" else "attack barricade"
                    is BotOrder.Take -> "take ${current.obj.removePrefix("obj.")}"
                    is BotOrder.UseItemOnLoc ->
                        "use ${current.item.removePrefix("obj.")} on ${current.loc.removePrefix("loc.castlewars_")}"
                    BotOrder.SetUpBarricade -> "set up barricade"
                    is BotOrder.Fire -> "fire at ${current.target.x},${current.target.z}"
                    null -> "idle"
                }
            "${player.displayName} $team ${bot.role}/${bot.style} ${player.coords.x},${player.coords.z},${player.coords.level} " +
                "hp=${player.hitpoints} pray=${player.prayerLvl}/${player.overheadIcon ?: "-"} food=${BotBrain.foodCount(player)} rocks=${player.inv.count("obj.castlewars_catapult_rock")} tb=${player.inv.count("obj.tinderbox")} $order " +
                "busy=${player.isDelayed || player.isAccessProtected} since=${player.currentMapClock - player.actionDelay}"
        }

    /** Takes every bot out of the world; nothing about them is saved. */
    fun removeAll() {
        for (player in bots.keys.toList()) {
            remove(player)
        }
    }

    private fun remove(player: Player) {
        game.leaveGame(player)
        game.leaveWaitingRoom(player)
        game.botPlayers -= player
        bots -= player
        registry.del(player)
        sessions.detach(player)
    }

    fun tick(cycle: Int) {
        for (bot in bots.values.toList()) {
            val player = bot.player
            if (!game.running && game.teamOf(player) == null && player.coords in CastleWars.LOBBY_AREA) {
                remove(player)
                continue
            }
            brain.think(bot, cycle)
        }
    }

    private companion object {
        const val TEAM_SIZE = 20
        const val RUN_ENERGY = 10000
        const val BOT_ID_BASE = -1_000_000L

        val ROLES =
            listOf(
                BotRole.Attacker,
                BotRole.Defender,
                BotRole.Hunter,
                BotRole.Attacker,
                BotRole.Defender,
                BotRole.Attacker,
                BotRole.Defender,
                BotRole.Attacker,
                BotRole.Defender,
                BotRole.Attacker,
            )
        val STYLES = listOf(BotStyle.Melee, BotStyle.Ranged, BotStyle.Magic, BotStyle.Melee, BotStyle.Ranged)

        val NAMES =
            listOf(
                "Bot Aldric",
                "Bot Brienne",
                "Bot Cedric",
                "Bot Dagny",
                "Bot Edmund",
                "Bot Freya",
                "Bot Gareth",
                "Bot Hilda",
                "Bot Ingrid",
                "Bot Jorah",
                "Bot Kaspar",
                "Bot Linnea",
                "Bot Magnus",
                "Bot Nessa",
                "Bot Osric",
                "Bot Petra",
                "Bot Quentin",
                "Bot Rowena",
                "Bot Sigurd",
                "Bot Thora",
                "Bot Ulric",
                "Bot Vesna",
                "Bot Wystan",
                "Bot Xenia",
                "Bot Yorick",
                "Bot Zelda",
                "Bot Anselm",
                "Bot Bryony",
                "Bot Conrad",
                "Bot Delia",
                "Bot Eamon",
                "Bot Fenella",
                "Bot Godric",
                "Bot Helga",
                "Bot Ivor",
                "Bot Juno",
                "Bot Leofric",
                "Bot Mirela",
                "Bot Norbert",
                "Bot Odile",
            )
    }
}
