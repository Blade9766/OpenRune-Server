package org.rsmod.content.other.bots.manager

import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wires the Bot Manager's buttons to [BotManagerWindow]; every click checks administrator rights first. */
class BotManagerScript
@Inject
constructor(
    private val window: BotManagerWindow,
    private val playerRegistry: PlayerRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfClose(Painter.INTERFACE) { window.closed(player) }
        onEvent<GameLifecycle.LateCycle> { refreshViewers() }

        on("nav", 9) { i -> Section.ofNav(i)?.let { window.navigate(this, it) } }
        on("mh_tab", 5) { i -> window.navigate(this, Section.TABS[i]) }

        on("ru_search") { window.searchRules(this) }
        on("ru_add") { window.addRule(this) }
        on("ru_bulk") { window.bulkRules(this) }
        on("ru_prev") { window.rulePage(this, -1) }
        on("ru_next") { window.rulePage(this, 1) }
        on("ed_check") { window.toggleRule(this, null) }
        on("ed_remove") { window.removeRule(this) }
        for (r in 0 until BotManagerWindow.RULE_ROWS) {
            onIfModalButton(Painter.com("ru_r$r")) { if (window.isAdmin(this)) window.selectRule(this, r, history = it.op == IfButtonOp.Op2) }
            on("ru_r${r}_check") { window.toggleRule(this, r) }
        }
        on("ed_f", 6) { i -> window.editField(this, i) }

        on("kn_prev") { window.knobPage(this, -1) }
        on("kn_next") { window.knobPage(this, 1) }
        for (r in 0 until BotManagerWindow.KNOB_ROWS) {
            on("kn_r${r}_label") { window.knobAction(this, r, "label") }
            on("kn_r${r}_dec") { window.knobAction(this, r, "dec") }
            on("kn_r${r}_inc") { window.knobAction(this, r, "inc") }
            on("kn_r${r}_toggle") { window.knobToggle(this, r) }
        }

        on("of_cancelall") { window.cancelAll(this) }
        on("of_cancel") { window.cancelOffer(this, replace = false) }
        on("of_replace") { window.cancelOffer(this, replace = true) }
        on("of_r", BotManagerWindow.OFFER_ROWS) { i -> window.selectOffer(this, i) }

        on("hi_search") { window.historyFilter(this, "search") }
        on("hi_side") { window.historyFilter(this, "side") }
        on("hi_range") { window.historyFilter(this, "range") }
        on("hi_sort") { window.historyFilter(this, "sort") }
        on("hi_export") { window.exportHistory(this) }
        on("hi_r", BotManagerWindow.HISTORY_ROWS) { i -> window.selectFill(this, i) }

        on("ov_b", 6) { i -> window.overviewAction(this, i) }

        on("ro_search") { window.rosterFilter(this, "search") }
        on("ro_fstate") { window.rosterFilter(this, "state") }
        on("ro_frole") { window.rosterFilter(this, "role") }
        on("ro_fdiff") { window.rosterFilter(this, "pace") }
        on("ro_create") { window.createBot(this) }
        on("ro_bulk") { window.rosterBulk(this) }
        on("ro_prev") { window.rosterPage(this, -1) }
        on("ro_next") { window.rosterPage(this, 1) }
        on("ro_r", BotManagerWindow.ROSTER_ROWS) { i -> window.selectBot(this, i) }
        on("rd_a", 6) { i -> window.botAction(this, i) }

        on("ft_b", 3) { i -> window.footerAction(this, i) }
    }

    private fun ScriptContext.on(name: String, action: suspend ProtectedAccess.() -> Unit) {
        onIfModalButton(Painter.com(name)) { if (window.isAdmin(this)) action() }
    }

    private fun ScriptContext.on(prefix: String, count: Int, action: suspend ProtectedAccess.(Int) -> Unit) {
        for (i in 0 until count) {
            onIfModalButton(Painter.com("$prefix$i")) { if (window.isAdmin(this)) action(i) }
        }
    }

    /**
     * An open window counts as "busy", so a normal launch would never refresh one. Rendering only
     * sets text, icons and buttons, which is safe beside it; a suspended dialogue is left alone.
     */
    @OptIn(InternalApi::class)
    private fun refreshViewers() {
        if (clock.cycle % REFRESH_TICKS != 0) return
        playerRegistry.forEachOnline { viewer ->
            if (window.isOpen(viewer) && !viewer.isModalButtonProtected) {
                protectedAccess.launchLenient(viewer) { window.refresh(this) }
            }
        }
    }

    private companion object {
        private const val REFRESH_TICKS = 5
    }
}
