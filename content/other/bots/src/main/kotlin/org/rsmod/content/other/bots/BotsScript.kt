package org.rsmod.content.other.bots

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.BotState
import org.rsmod.content.other.bots.core.BotStatusView
import org.rsmod.content.other.bots.world.BotSites
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BotsScript
@Inject
constructor(
    private val manager: BotManager,
    private val playerRegistry: PlayerRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
    private val mapClock: MapClock,
) : PluginScript() {
    private val commands = BotCommands(manager, ::onlineNames)
    private val viewers = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> {
            manager.tick()
            refreshViewers()
        }
        onEvent<SessionStateEvent.Logout> { manager.onBotLoggedOut(player) }
        onEvent<SessionStateEvent.Delete> { manager.onPlayerDeleted(player) }
        onEvent<GameLifecycle.Shutdown> { manager.shutdown() }

        onCommand("bot") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Manage gathering bots (::bot help)"
            invalidArgs = BotCommands.USAGE
            cheat {
                if (args.firstOrNull() == "help") {
                    player.mes(BotCommands.USAGE)
                } else {
                    val admin = player
                    commands.run(admin, args) { protectedAccess.launch(admin) { openStatus() } }
                }
            }
        }

        onIfClose(INTERFACE) { viewers.remove(player) }
        onIfModalButton("$COMPONENT:prev") { if (isAdmin()) page(-1) }
        onIfModalButton("$COMPONENT:next") { if (isAdmin()) page(1) }
        onIfModalButton("$COMPONENT:refresh") { if (isAdmin()) render() }
        onIfModalButton("$COMPONENT:master") { if (isAdmin()) toggleMaster() }
        onIfModalButton("$COMPONENT:create") { if (isAdmin()) createBot() }
        for (row in 0 until ROWS) {
            onIfModalButton("$COMPONENT:row$row") { if (isAdmin()) rowAction(row, it.op) }
        }
    }

    private fun ProtectedAccess.isAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        ifClose()
        return false
    }

    private fun ProtectedAccess.openStatus() {
        viewers[player] = 0
        ifOpenMainModal(INTERFACE)
        for (row in 0 until ROWS) {
            ifSetEvents("$COMPONENT:row$row", -1..-1, IfEvent.Op1, IfEvent.Op2)
        }
        for (button in BUTTONS) {
            ifSetEvents("$COMPONENT:$button", -1..-1, IfEvent.Op1)
        }
        render()
    }

    private fun ProtectedAccess.page(delta: Int) {
        val total = manager.coordinator?.all()?.size ?: 0
        val pages = maxOf(1, (total + ROWS - 1) / ROWS)
        viewers[player] = ((viewers[player] ?: 0) + delta).coerceIn(0, pages - 1)
        render()
    }

    private fun ProtectedAccess.toggleMaster() {
        val coordinator = manager.coordinator ?: return
        val enabled = !coordinator.settings.masterEnabled
        coordinator.setMaster(enabled)
        mes("Bot master toggle is now ${if (enabled) "ON" else "OFF"}.")
        render()
    }

    private suspend fun ProtectedAccess.rowAction(row: Int, op: IfButtonOp) {
        val coordinator = manager.coordinator ?: return
        val view = pageOf(viewers[player] ?: 0).getOrNull(row) ?: return
        val id = view.record.id
        when (op) {
            IfButtonOp.Op1 -> {
                val error = if (view.record.enabled) coordinator.disable(id) else coordinator.enable(id)
                mes(error ?: "${view.record.name} is now ${if (view.record.enabled) "disabled" else "enabled"}.")
                render()
            }
            else -> manage(view.record.id)
        }
    }

    /** Role, then a site for it, then a name; the site's starter kit comes with it. */
    private suspend fun ProtectedAccess.createBot() {
        val sites = BotSites.all().values
        val roles = BotRoleType.entries.filter { role -> sites.any { it.roleType == role } }
        val role = roles[pick("Role for the new bot", roles.map { it.key.replaceFirstChar(Char::uppercaseChar) })]
        val roleSites = sites.filter { it.roleType == role }
        val site = roleSites[pick("Where should it work?", roleSites.map { "${it.key}: ${it.description}" })]
        val name =
            when (pick("Name", listOf("Random silly name", "Type a name"))) {
                0 -> emptyList()
                else -> stringDialog("Bot name:").trim().split(" ").filter { it.isNotBlank() }
            }
        commands.run(player, listOf("create", role.key, site.key) + name) {}
        openStatus()
    }

    private suspend fun ProtectedAccess.manage(botId: Long) {
        val record = manager.coordinator?.find(botId.toString()) ?: return
        val id = botId.toString()
        val actions =
            buildList<Pair<String, suspend () -> Unit>> {
                add("Show details" to { commands.run(player, listOf("status", id)) {} })
                add((if (record.enabled) "Disable" else "Enable") to { commands.run(player, listOf(if (record.enabled) "disable" else "enable", id)) {} })
                if (record.state == BotState.Error) {
                    add("Clear error" to { commands.run(player, listOf("clear", id)) {} })
                }
                add("Apply starter kit" to { commands.run(player, listOf("kit", id)) {} })
                add("Set a level..." to { setLevel(id) })
                add("Give an item..." to { giveItem(id) })
                add("Change a setting..." to { changeSetting(id) })
                add("Teleport to bot" to { teleportTo(botId) })
                add("Remove bot" to { removeBot(id, record.name) })
            }
        val choice = pick(record.name, actions.map { it.first })
        actions[choice].second.invoke()
        openStatus()
    }

    private suspend fun ProtectedAccess.setLevel(id: String) {
        val stats =
            ServerCacheManager.getStats().values
                .map { RSCM.getReverseMapping(RSCMType.STAT, it.id).removePrefix("stat.") }
                .filter { it != UNRELEASED_STAT }
                .sorted()
        val stat = stats[pick("Which skill?", stats.map { it.replaceFirstChar(Char::uppercaseChar) })]
        val level = countDialog("New ${stat.replaceFirstChar(Char::uppercaseChar)} level (1-99):")
        commands.run(player, listOf("level", id, stat, level.toString())) {}
    }

    private suspend fun ProtectedAccess.giveItem(id: String) {
        val item = stringDialog("Item to give (for example iron_scimitar):").trim().lowercase().replace(' ', '_')
        val count = countDialog("How many?")
        val target = listOf("inv", "worn", "bank")[pick("Put it where?", listOf("Inventory", "Worn", "Bank"))]
        commands.run(player, listOf("give", id, item, count.toString(), target)) {}
    }

    private suspend fun ProtectedAccess.changeSetting(id: String) {
        val key = BotCommands.SETTING_KEYS[pick("Which setting?", BotCommands.SETTING_KEYS)]
        val value = stringDialog("New value for $key:").trim().lowercase()
        commands.run(player, listOf("set", id, key) + value.split(" ")) {}
    }

    private fun ProtectedAccess.teleportTo(botId: Long) {
        val bot = manager.playerOf(botId) ?: return mes("That bot is not in the world.")
        telejump(bot.coords)
    }

    private suspend fun ProtectedAccess.removeBot(id: String, name: String) {
        if (pick("Remove $name?", listOf("Yes, remove it", "No")) == 0) {
            commands.run(player, listOf("remove", id)) {}
        }
    }

    /** Picks from the list modal and closes it, so the next dialogue keeps protected access. */
    private suspend fun ProtectedAccess.pick(title: String, choices: List<String>): Int {
        val picked = menu(title, hotkeys = false, choices = choices)
        ifCloseSub(MENU_INTERFACE)
        return picked
    }

    private fun refreshViewers() {
        if (mapClock.cycle % REFRESH_TICKS != 0 || viewers.isEmpty()) {
            return
        }
        for (viewer in viewers.keys.toList()) {
            if (viewer.slotId == -1) {
                viewers.remove(viewer)
                continue
            }
            if (!viewer.isAccessProtected) {
                protectedAccess.launch(viewer) { render() }
            }
        }
    }

    private fun ProtectedAccess.render() {
        val coordinator = manager.coordinator ?: return
        val page = viewers[player] ?: 0
        val views = pageOf(page)
        for (row in 0 until ROWS) {
            val view = views.getOrNull(row)
            for ((column, value) in cells(view)) {
                ifSetText("$COMPONENT:row${row}_$column", value)
            }
        }
        val all = coordinator.all()
        val active = all.count { coordinator.isLive(it.id) }
        val pages = maxOf(1, (all.size + ROWS - 1) / ROWS)
        val master = coordinator.settings.masterEnabled
        ifSetText("$COMPONENT:master", "Master: ${if (master) "<col=00ff00>ON" else "<col=ff0000>OFF"}")
        ifSetText(
            "$COMPONENT:summary",
            "Active $active/${coordinator.settings.maxActiveBots} - ${all.size} bots - page ${page + 1}/$pages",
        )
    }

    private fun pageOf(page: Int): List<BotStatusView> =
        manager.coordinator?.status()?.drop(page * ROWS)?.take(ROWS).orEmpty()

    private fun cells(view: BotStatusView?): List<Pair<String, String>> {
        if (view == null) {
            return COLUMNS.map { it to "" }
        }
        val record = view.record
        val stateColour =
            when (record.state) {
                BotState.Error -> "<col=ff0000>"
                BotState.Disabled -> "<col=9f9f9f>"
                BotState.WaitingForSupplies -> "<col=ff981f>"
                else -> ""
            }
        val name = if (record.enabled) record.name else "<col=9f9f9f>${record.name}"
        return listOf(
            "name" to name,
            "role" to record.config.role.key.replaceFirstChar(Char::uppercaseChar),
            "state" to "$stateColour${if (record.activity.startsWith("Resting")) "Resting" else record.state.shortLabel}",
            "inv" to "${view.inventoryUsed}/${view.inventoryCapacity}",
            "banked" to view.bankedSaleItems.formatAmount,
            "threshold" to record.config.sale.threshold.formatAmount,
            "listings" to view.activeListings.toString(),
            "coins" to view.coins.formatAmount,
        )
    }

    private fun onlineNames(): Set<String> {
        val names = HashSet<String>()
        playerRegistry.forEachOnline { names += it.displayName }
        return names
    }

    private companion object {
        private const val INTERFACE = "interface.bot_status"
        private const val COMPONENT = "component.bot_status"
        private const val ROWS = 10
        private const val REFRESH_TICKS = 5
        private const val MENU_INTERFACE = "interface.menu"
        private val BUTTONS = listOf("prev", "next", "refresh", "create", "master")
        private const val UNRELEASED_STAT = "unreleased"
        private val COLUMNS = listOf("name", "role", "state", "inv", "banked", "threshold", "listings", "coins")
    }
}
