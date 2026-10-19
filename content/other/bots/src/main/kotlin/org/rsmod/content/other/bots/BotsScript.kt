package org.rsmod.content.other.bots

import dev.openrune.definition.type.widget.IfEvent
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
import org.rsmod.content.other.bots.core.SavedObj
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

        onIfClose(ITEMS) {
            if (closed<View.Items>(player) || closed<View.MeisterItems>(player)) {
                for (inv in viewerInvs(player)) {
                    player.stopInvTransmit(inv)
                }
            }
        }

        onIfModalButton("$ITEMS_COM:back") {
            if (isAdmin()) {
                when (val view = views[player]) {
                    is View.Items -> openManager()
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

    private fun sendHome(coordinator: BotCoordinator, record: BotRecord): String {
        val bot = manager.playerOf(record.id) ?: return "<col=ff0000>It is not in the world."
        val bank = BotSites[record.config.site]?.bank?.center() ?: return "<col=ff0000>Its site is missing."
        world.telejump(bot, bank)
        return coordinator.restart(record.id)?.let { "<col=ff0000>$it" } ?: "Sent to its bank; it starts its work afresh."
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
        private const val ITEMS = "interface.bot_items"
        private const val ITEMS_COM = "component.bot_items"
        private const val ITEMS_INIT = "clientscript.bot_items_init"
        private const val BUTTON_SCRIPT = "clientscript.bot_button"
        private const val VIEW_INV = "inv.bot_view_inv"
        private const val VIEW_WORN = "inv.bot_view_worn"
        private const val VIEW_BANK = "inv.bot_view_bank"
        private const val BOT_BANK = "inv.bank"
        private const val COINS = "obj.coins"
        private const val REFRESH_TICKS = 5
        private const val MENU_INTERFACE = "interface.menu"
        private val COLUMNS = listOf("name", "role", "state", "inv", "banked", "threshold", "listings", "coins")
    }
}
