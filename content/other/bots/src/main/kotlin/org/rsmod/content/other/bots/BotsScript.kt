package org.rsmod.content.other.bots

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.bots.core.BotCoordinator
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.BotSettingRows
import org.rsmod.content.other.bots.core.BotState
import org.rsmod.content.other.bots.core.SavedObj
import org.rsmod.content.other.bots.core.SettingControl
import org.rsmod.content.other.bots.core.SettingRow
import org.rsmod.content.other.bots.core.SiteCapabilities
import org.rsmod.content.other.bots.core.copyBehaviourTo
import org.rsmod.content.other.bots.core.withDefaults
import org.rsmod.content.other.bots.manager.BotManagerWindow
import org.rsmod.content.other.bots.manager.BotWindows
import org.rsmod.content.other.bots.manager.Section
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.meister.world.MasterMeister
import org.rsmod.content.other.bots.meister.world.MeisterCommands
import org.rsmod.content.other.bots.world.BotSites
import org.rsmod.content.other.bots.world.BotWorldServices
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Bot Manager: a roster window, a settings window per bot and a read-only view of a bot's
 * items. Every button checks administrator rights again, and every change goes through
 * [BotCoordinator], which validates it, so the windows can never put a bot in a state the
 * `::bot` commands could not.
 */
class BotsScript
@Inject
constructor(
    private val manager: BotManager,
    private val playerRegistry: PlayerRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
    private val mapClock: MapClock,
    private val world: BotWorldServices,
    private val meister: MasterMeister,
    private val window: BotManagerWindow,
) : PluginScript() {
    private val commands = BotCommands(manager, ::onlineNames)
    private val meisterCommands = MeisterCommands(meister)
    private val views = WeakHashMap<Player, View>()

    private sealed interface View {
        data class Settings(val botId: Long, var help: String = DEFAULT_HELP, var status: String = APPLIES_NOW) : View

        data class Items(val botId: Long, var shown: Int? = null) : View

        data class MeisterItems(var shown: Int? = null) : View
    }

    /** The buttons a window last drew, so a refresh only redraws what changed (and keeps hovers). */
    private val drawn = WeakHashMap<Player, MutableMap<String, Pair<String, Boolean>>>()

    override fun ScriptContext.startup() {
        window.windows = Windows()
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
                    commands.run(admin, args) { protectedAccess.launch(admin) { openManager() } }
                }
            }
        }

        onIfClose(SETTINGS) { closed<View.Settings>(player) }
        onIfClose(ITEMS) {
            if (closed<View.Items>(player) || closed<View.MeisterItems>(player)) {
                for (inv in viewerInvs(player)) {
                    player.stopInvTransmit(inv)
                }
            }
        }

        for (row in 0 until SETTING_ROWS) {
            onIfModalButton("$SETTINGS_COM:label$row") { if (isAdmin()) explain(row) }
            onIfModalButton("$SETTINGS_COM:dec$row") { if (isAdmin()) changeSetting(row, Step.Down) }
            onIfModalButton("$SETTINGS_COM:inc$row") { if (isAdmin()) changeSetting(row, Step.Up) }
            onIfModalButton("$SETTINGS_COM:toggle$row") { if (isAdmin()) changeSetting(row, Step.Toggle) }
        }
        for (action in ACTIONS) {
            onIfModalButton("$SETTINGS_COM:act_$action") { if (isAdmin()) settingsAction(action) }
        }
        onIfModalButton("$ITEMS_COM:back") {
            if (isAdmin()) {
                when (val view = views[player]) {
                    is View.Items -> openSettings(view.botId)
                    is View.MeisterItems -> openManager(Section.Overview)
                    else -> {}
                }
            }
        }
    }

    private fun ProtectedAccess.openManager(section: Section? = null) {
        views.remove(player)
        drawn.remove(player)
        window.open(this, section)
    }

    private inner class Windows : BotWindows {
        override fun openBotSettings(access: ProtectedAccess, botId: Long) = access.openSettings(botId)

        override fun openBotItems(access: ProtectedAccess, botId: Long) = access.openItems(botId)

        override fun openMeisterItems(access: ProtectedAccess) = access.openMeisterItems()

        override suspend fun createBot(access: ProtectedAccess) = access.createBot()

        override fun sendHome(botId: Long): String {
            val coordinator = manager.coordinator ?: return "<col=ff0000>Bots are not loaded."
            val record = coordinator.find(botId.toString()) ?: return "<col=ff0000>No such bot."
            return sendHome(coordinator, record)
        }
    }

    private inline fun <reified T : View> closed(player: Player): Boolean {
        if (views[player] !is T) {
            return false
        }
        views.remove(player)
        drawn.remove(player)
        return true
    }

    private fun ProtectedAccess.isAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        ifClose()
        return false
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
    }

    private enum class Step {
        Down,
        Up,
        Toggle,
    }

    private fun ProtectedAccess.openSettings(botId: Long, status: String = APPLIES_NOW) {
        val record = manager.coordinator?.find(botId.toString()) ?: return openManager(Section.Roster)
        views[player] = View.Settings(botId, status = status)
        drawn.remove(player)
        ifOpenMainModal(SETTINGS)
        player.runClientScript(SETTINGS_INIT.asRSCM(RSCMType.CLIENTSCRIPT), "Bot Settings: ${record.name}")
        for (row in 0 until SETTING_ROWS) {
            for (part in listOf("label", "dec", "inc", "toggle")) {
                ifSetEvents("$SETTINGS_COM:$part$row", -1..-1, IfEvent.Op1)
            }
        }
        for (action in ACTIONS) {
            ifSetEvents("$SETTINGS_COM:act_$action", -1..-1, IfEvent.Op1)
        }
        renderSettings()
    }

    private fun settingsView(player: Player): View.Settings? = views[player] as? View.Settings

    private fun capabilities(record: BotRecord): SiteCapabilities {
        val site = BotSites[record.config.site]
        return SiteCapabilities(
            canSell = site?.exchangeRoute?.isNotEmpty() == true,
            usesSupplies = site?.supplies?.isNotEmpty() == true,
        )
    }

    private fun ProtectedAccess.renderSettings() {
        val view = settingsView(player) ?: return
        val coordinator = manager.coordinator ?: return
        val record = coordinator.find(view.botId.toString()) ?: return openManager(Section.Roster)
        val site = BotSites[record.config.site]
        val live = coordinator.isLive(record.id)
        val role = record.config.role.key.replaceFirstChar(Char::uppercaseChar)
        ifSetText("$SETTINGS_COM:info_title", "#${record.id} ${record.name} - $role at ${record.config.site}")
        ifSetText("$SETTINGS_COM:info_state", "${presence(coordinator, record)}  ${record.state.shortLabel}: ${record.activity}")
        val target = coordinator.trace(record.id)?.focus
        ifSetText(
            "$SETTINGS_COM:info_target",
            record.lastError?.let { "<col=ff0000>Last error: $it" } ?: "Last target: ${target ?: "none yet"}",
        )

        val rows = BotSettingRows.of(record.config, capabilities(record))
        for ((index, row) in rows.withIndex()) {
            val usable = row.unavailable == null
            ifSetText("$SETTINGS_COM:label$index", row.label)
            ifSetText("$SETTINGS_COM:value$index", if (usable) row.value else "<col=9f9f9f>${row.value}")
            when (val control = row.control) {
                is SettingControl.Stepper -> {
                    ifSetHide("$SETTINGS_COM:toggle$index", true)
                    ifSetHide("$SETTINGS_COM:dec$index", false)
                    ifSetHide("$SETTINGS_COM:inc$index", false)
                    button(SETTINGS_COM, "dec$index", "-", usable && control.down != null)
                    button(SETTINGS_COM, "inc$index", "+", usable && control.up != null)
                }
                is SettingControl.Toggle -> {
                    ifSetHide("$SETTINGS_COM:dec$index", true)
                    ifSetHide("$SETTINGS_COM:inc$index", true)
                    ifSetHide("$SETTINGS_COM:toggle$index", false)
                    button(SETTINGS_COM, "toggle$index", control.label, usable && control.change != null)
                }
            }
        }

        val sameRole = coordinator.all().count { it.id != record.id && it.config.role == record.config.role }
        button(SETTINGS_COM, "act_power", if (record.enabled) "Disable" else "Enable")
        button(SETTINGS_COM, "act_home", "Send home", live)
        button(SETTINGS_COM, "act_clear", "Clear error", record.state == BotState.Error)
        button(SETTINGS_COM, "act_kit", "Starter kit", !live && site?.kit != null)
        button(SETTINGS_COM, "act_teleport", "Go to bot", live)
        button(SETTINGS_COM, "act_items", "View items")
        button(SETTINGS_COM, "act_more", "More...")
        button(SETTINGS_COM, "act_copy", "Copy to role", sameRole > 0)
        button(SETTINGS_COM, "act_reset", "Reset")
        button(SETTINGS_COM, "act_remove", if (live) "Remove" else "<col=ff0000>Remove", !live)
        button(SETTINGS_COM, "act_back", "Back")
        ifSetText("$SETTINGS_COM:help", view.help)
        ifSetText("$SETTINGS_COM:status", view.status)
    }

    private fun presence(coordinator: BotCoordinator, record: BotRecord): String =
        when {
            coordinator.isLive(record.id) -> "<col=00ff00>In the world</col>"
            !record.enabled -> "<col=9f9f9f>Out of the world</col>"
            !coordinator.settings.masterEnabled -> "<col=ff981f>Queued: master toggle is off</col>"
            else -> "<col=ff981f>Queued for a free slot</col>"
        }

    private fun ProtectedAccess.explain(index: Int) {
        val view = settingsView(player) ?: return
        val row = currentRows(view)?.getOrNull(index) ?: return
        view.help = "<col=ff981f>${row.label}:</col> ${row.help}" + (row.unavailable?.let { " <col=ff0000>$it" } ?: "")
        renderSettings()
    }

    /** Rebuilds the rows from the bot's current config, so a click never applies a stale value. */
    private fun currentRows(view: View.Settings): List<SettingRow>? {
        val record = manager.coordinator?.find(view.botId.toString()) ?: return null
        return BotSettingRows.of(record.config, capabilities(record))
    }

    private fun ProtectedAccess.changeSetting(index: Int, step: Step) {
        val view = settingsView(player) ?: return
        val coordinator = manager.coordinator ?: return
        val row = currentRows(view)?.getOrNull(index) ?: return
        val change =
            when (val control = row.control) {
                is SettingControl.Stepper -> if (step == Step.Down) control.down else if (step == Step.Up) control.up else null
                is SettingControl.Toggle -> if (step == Step.Toggle) control.change else null
            }
        view.status =
            when {
                row.unavailable != null -> "<col=ff0000>${row.label} cannot be changed: ${row.unavailable}"
                change == null -> "<col=ff981f>${row.label} is already at its limit."
                else ->
                    coordinator.updateConfig(view.botId, change)?.let { "<col=ff0000>Not saved: $it" }
                        ?: "<col=00ff00>Saved:</col> ${row.label} is now ${currentRows(view)?.getOrNull(index)?.value}. $APPLIES_NOW"
            }
        renderSettings()
    }

    private suspend fun ProtectedAccess.settingsAction(action: String) {
        val view = settingsView(player) ?: return
        val coordinator = manager.coordinator ?: return
        val record = coordinator.find(view.botId.toString()) ?: return openManager(Section.Roster)
        val id = record.id
        val result: String? =
            when (action) {
                "power" ->
                    if (record.enabled) {
                        coordinator.disable(id)?.let { "<col=ff0000>$it" } ?: "Disabling: it leaves the world once it finishes its current action."
                    } else {
                        coordinator.enable(id)?.let { "<col=ff0000>$it" } ?: ("Enabled.")
                    }
                "home" -> sendHome(coordinator, record)
                "clear" -> coordinator.clearError(id)?.let { "<col=ff0000>$it" } ?: "Error cleared."
                "kit" -> commands.applyKit(coordinator, record)?.let { "<col=ff0000>Starter kit not applied: $it" } ?: "Starter kit applied."
                "teleport" -> {
                    val bot = manager.playerOf(id) ?: return
                    ifClose()
                    telejump(bot.coords)
                    return
                }
                "items" -> return openItems(id)
                "more" -> return moreActions(record)
                "copy" -> return copyToRole(record)
                "reset" -> return resetSettings(record)
                "remove" -> return removeBot(record)
                "back" -> return openManager(Section.Roster)
                else -> null
            }
        view.status = result ?: APPLIES_NOW
        drawn.remove(player)
        renderSettings()
    }

    private fun sendHome(coordinator: BotCoordinator, record: BotRecord): String {
        val bot = manager.playerOf(record.id) ?: return "<col=ff0000>It is not in the world."
        val bank = BotSites[record.config.site]?.bank?.center() ?: return "<col=ff0000>Its site is missing."
        world.telejump(bot, bank)
        return coordinator.restart(record.id)?.let { "<col=ff0000>$it" } ?: "Sent to its bank; it starts its work afresh."
    }

    private suspend fun ProtectedAccess.copyToRole(record: BotRecord) {
        val coordinator = manager.coordinator ?: return
        val others = coordinator.all().filter { it.id != record.id && it.config.role == record.config.role }
        val role = record.config.role.key
        val question = "Copy settings to ${others.size} other $role bot(s)?"
        if (!confirm(question, "Yes, copy them")) {
            return openSettings(record.id)
        }
        val failed = others.count { coordinator.updateConfig(it.id, record.config::copyBehaviourTo) != null }
        openSettings(record.id, "Copied to ${others.size - failed} $role bot(s)${if (failed > 0) "; <col=ff0000>$failed refused" else ""}. $APPLIES_NOW")
    }

    private suspend fun ProtectedAccess.resetSettings(record: BotRecord) {
        if (!confirm("Reset ${record.name}'s settings?", "Yes, reset to the defaults")) {
            return openSettings(record.id)
        }
        val error = manager.coordinator?.updateConfig(record.id) { it.withDefaults() }
        openSettings(record.id, error?.let { "<col=ff0000>Not reset: $it" } ?: "Settings reset to the defaults. $APPLIES_NOW")
    }

    private suspend fun ProtectedAccess.removeBot(record: BotRecord) {
        if (!confirm("Remove ${record.name} for good?", "Yes, remove it and its items")) {
            return openSettings(record.id)
        }
        val error = manager.coordinator?.remove(record.id, force = false)
        if (error != null) {
            return openSettings(record.id, "<col=ff0000>Not removed: $error")
        }
        mes("Removed ${record.name}.")
        openManager(Section.Roster)
    }

    /** The older tools that need typed input: levels, items and the pricing settings. */
    private suspend fun ProtectedAccess.moreActions(record: BotRecord) {
        val id = record.id.toString()
        val actions =
            listOf<Pair<String, suspend () -> Unit>>(
                "Show details in chat" to { commands.run(player, listOf("status", id)) {} },
                "Show decision trace in chat" to { commands.run(player, listOf("trace", id)) {} },
                "Set a level (bot must be disabled)..." to { setLevel(id) },
                "Give an item (bot must be disabled)..." to { giveItem(id) },
                "Change a pricing setting..." to { changePricing(id) },
                "Back" to {},
            )
        actions[pick(record.name, actions.map { it.first })].second.invoke()
        openSettings(record.id)
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

    private suspend fun ProtectedAccess.changePricing(id: String) {
        val key = BotCommands.SETTING_KEYS[pick("Which setting?", BotCommands.SETTING_KEYS)]
        val value = stringDialog("New value for $key:").trim().lowercase()
        commands.run(player, listOf("set", id, key) + value.split(" ")) {}
    }

    private fun viewerInvs(player: Player): List<Inventory> =
        listOf(VIEW_INV, VIEW_WORN, VIEW_BANK).map { player.invMap.getOrPut(it) }

    private fun ProtectedAccess.openItems(botId: Long) {
        val record = manager.coordinator?.find(botId.toString()) ?: return openManager(Section.Roster)
        views[player] = View.Items(botId)
        drawn.remove(player)
        ifOpenMainModal(ITEMS)
        player.runClientScript(ITEMS_INIT.asRSCM(RSCMType.CLIENTSCRIPT), "Items: ${record.name}")
        ifSetEvents("$ITEMS_COM:back", -1..-1, IfEvent.Op1)
        button(ITEMS_COM, "back", "Back")
        for (inv in viewerInvs(player)) {
            inv.fillNulls()
            invTransmit(inv)
        }
        renderItems()
    }

    private data class Holdings(val inv: List<SavedObj>, val worn: List<SavedObj>, val bank: List<SavedObj>, val live: Boolean)

    private fun holdings(botId: Long, record: BotRecord): Holdings {
        val bot = manager.playerOf(botId) ?: return Holdings(record.save.inv, record.save.worn, record.save.bank, live = false)
        fun Inventory.saved(): List<SavedObj> = objs.mapIndexedNotNull { slot, obj -> obj?.let { SavedObj(slot, it.id, it.count, it.vars) } }
        return Holdings(bot.inv.saved(), bot.worn.saved(), bot.invMap.getOrPut(BOT_BANK).saved(), live = true)
    }

    @OptIn(UncheckedType::class)
    private fun ProtectedAccess.renderItems() {
        val view = views[player] as? View.Items ?: return
        val record = manager.coordinator?.find(view.botId.toString()) ?: return openManager(Section.Roster)
        val held = holdings(view.botId, record)
        val signature = listOf(held.inv, held.worn, held.bank).hashCode()
        if (signature != view.shown) {
            view.shown = signature
            for ((inv, objs) in viewerInvs(player).zip(listOf(held.inv, held.worn, held.bank))) {
                inv.fillNulls()
                for (obj in objs) {
                    if (obj.slot in inv.indices) {
                        inv[obj.slot] = InvObj(obj.id, obj.count, obj.vars)
                    }
                }
            }
        }
        val coins = COINS.asRSCM(RSCMType.OBJ)
        val bankCoins = held.bank.filter { it.id == coins }.sumOf { it.count.toLong() }
        val carried = held.inv.filter { it.id == coins }.sumOf { it.count.toLong() }
        ifSetText("$ITEMS_COM:inv_label", "Inventory (${held.inv.size}/28)")
        ifSetText("$ITEMS_COM:worn_label", "Worn")
        ifSetText("$ITEMS_COM:bank_label", "Bank (${held.bank.size} ${if (held.bank.size == 1) "item" else "items"})")
        val source = if (held.live) "Live view; updates every few seconds." else "<col=ff981f>Saved copy: the bot is out of the world."
        ifSetText("$ITEMS_COM:summary", "Coins ${bankCoins.formatAmount} banked, ${carried.formatAmount} carried. $source")
    }

    /* Master Meister: a fixed roster row, the settings window with his own rows and actions, and his items. */

    private fun ProtectedAccess.openMeisterItems() {
        meister.trader ?: return openManager(Section.Roster)
        views[player] = View.MeisterItems()
        drawn.remove(player)
        ifOpenMainModal(ITEMS)
        player.runClientScript(ITEMS_INIT.asRSCM(RSCMType.CLIENTSCRIPT), "Items: ${MeisterTrader.NAME}")
        ifSetEvents("$ITEMS_COM:back", -1..-1, IfEvent.Op1)
        button(ITEMS_COM, "back", "Back")
        for (inv in viewerInvs(player)) {
            inv.fillNulls()
            invTransmit(inv)
        }
        renderMeisterItems()
    }

    @OptIn(UncheckedType::class)
    private fun ProtectedAccess.renderMeisterItems() {
        val view = views[player] as? View.MeisterItems ?: return
        val trader = meister.trader ?: return openManager(Section.Roster)
        val offers = trader.offers()
        val holdings = trader.holdingsView()
        val signature = listOf(holdings, offers).hashCode()
        val (offerInv, wornInv, bankInv) = viewerInvs(player)
        if (signature != view.shown) {
            view.shown = signature
            offerInv.fillNulls()
            wornInv.fillNulls()
            bankInv.fillNulls()
            for ((slot, offer) in offers.withIndex()) {
                offerInv[slot] = InvObj(offer.itemId, maxOf(1, if (offer.sell) offer.remaining else offer.total))
            }
            val shown = holdings.entries.sortedByDescending { it.value.toLong() * trader.guideOf(it.key) }.take(bankInv.size)
            for ((slot, entry) in shown.withIndex()) {
                bankInv[slot] = InvObj(entry.key, entry.value)
            }
        }
        ifSetText("$ITEMS_COM:inv_label", "Exchange offers (${offers.size}/8)")
        ifSetText("$ITEMS_COM:worn_label", "")
        val more = if (holdings.size > bankInv.size) ", top ${bankInv.size}" else ""
        ifSetText("$ITEMS_COM:bank_label", "Holdings: ${holdings.size} kinds$more")
        ifSetText("$ITEMS_COM:summary", "Cash ${trader.cash.formatAmount} coins. ${trader.status.label}. Live view; updates every few seconds.")
    }

    /** Draws a stone button; an unavailable one is grey and has no menu option at all. */
    private fun ProtectedAccess.button(com: String, name: String, label: String, enabled: Boolean = true) {
        val seen = drawn.getOrPut(player) { HashMap() }
        if (seen[name] == label to enabled) {
            return
        }
        seen[name] = label to enabled
        player.runClientScript(BUTTON_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), "$com:$name".asRSCM(RSCMType.COMPONENT), label, if (enabled) 1 else 0)
    }

    private suspend fun ProtectedAccess.confirm(question: String, yes: String): Boolean = pick(question, listOf(yes, "No")) == 0

    /** Picks from the list modal and closes it, so the next dialogue keeps protected access. */
    private suspend fun ProtectedAccess.pick(title: String, choices: List<String>): Int {
        val picked = menu(title, hotkeys = false, choices = choices)
        ifCloseSub(MENU_INTERFACE)
        return picked
    }

    /**
     * An open window counts as "busy", so a normal launch would never refresh one. Rendering only
     * sets text and buttons, which is safe beside it; a suspended dialogue (a confirmation menu) is
     * left alone, as a new script would cancel it.
     */
    @OptIn(InternalApi::class)
    private fun refreshViewers() {
        if (mapClock.cycle % REFRESH_TICKS != 0 || views.isEmpty()) {
            return
        }
        for ((viewer, view) in views.entries.toList()) {
            if (viewer.slotId == -1) {
                views.remove(viewer)
                continue
            }
            if (viewer.isModalButtonProtected) {
                continue
            }
            protectedAccess.launchLenient(viewer) {
                when (view) {
                    is View.Settings -> renderSettings()
                    is View.Items -> renderItems()
                    is View.MeisterItems -> renderMeisterItems()
                }
            }
        }
    }

    private fun onlineNames(): Set<String> {
        val names = HashSet<String>()
        playerRegistry.forEachOnline { names += it.displayName }
        return names
    }

    private companion object {
        private const val SETTINGS = "interface.bot_settings"
        private const val SETTINGS_COM = "component.bot_settings"
        private const val ITEMS = "interface.bot_items"
        private const val ITEMS_COM = "component.bot_items"
        private const val SETTINGS_INIT = "clientscript.bot_settings_init"
        private const val ITEMS_INIT = "clientscript.bot_items_init"
        private const val BUTTON_SCRIPT = "clientscript.bot_button"
        private const val VIEW_INV = "inv.bot_view_inv"
        private const val VIEW_WORN = "inv.bot_view_worn"
        private const val VIEW_BANK = "inv.bot_view_bank"
        private const val BOT_BANK = "inv.bank"
        private const val COINS = "obj.coins"
        private const val SETTING_ROWS = 7
        private const val REFRESH_TICKS = 5
        private const val MENU_INTERFACE = "interface.menu"
        private const val UNRELEASED_STAT = "unreleased"
        private const val APPLIES_NOW = "Changes apply at once and are saved automatically."
        private const val DEFAULT_HELP = "Click a setting's name to see what it does."
        private val ACTIONS =
            listOf("power", "home", "clear", "kit", "teleport", "items", "more", "copy", "reset", "remove", "back")
        private val COLUMNS = listOf("name", "role", "state", "inv", "banked", "threshold", "listings", "coins")
    }
}
