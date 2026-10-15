package org.rsmod.content.other.combatachievements

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.enums.enum
import jakarta.inject.Inject
import org.rsmod.api.combatachievements.CombatAchievements
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalPauseButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four Combat Achievements windows draw themselves from the task vars; the server only
 * enables their buttons, moves between them and mirrors the task-list filters the client sets.
 */
class CombatAchievementsScript @Inject constructor(private val achievements: CombatAchievements) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin {
            achievements.sync(player)
            achievements.checkKillcounts(player)
        }

        onIfOpen("interface.ca_overview") {
            player.enableMenu("ca_overview", Page.Overview)
            player.ifSetEvents("component.ca_overview:ca_buttons_click", 0..5, IfEvent.Op1)
        }
        onIfOpen("interface.ca_tasks") {
            player.enableMenu("ca_tasks", Page.Tasks)
            for (filter in TaskFilter.entries) {
                player.ifSetEvents(filter.dropdown, 0..MAX_DROPDOWN_ROWS, IfEvent.Op1)
            }
        }
        onIfOpen("interface.ca_bosses") {
            player.enableMenu("ca_bosses", Page.Bosses)
            player.ifSetEvents("component.ca_bosses:bosses_button_click", 0..BOSS_SLOTS, IfEvent.Op1)
            player.ifSetEvents(
                "component.ca_bosses:bosses_button_click_pause",
                0..BOSS_SLOTS,
                IfEvent.PauseButton,
            )
        }
        onIfOpen("interface.ca_boss") {
            player.enableMenu("ca_boss", null)
            player.ifSetEvents("component.ca_boss:ca_boss_stats_click", 0..1, IfEvent.Op1)
            player.ifSetEvents("component.ca_boss:progress_bar", 0..4, IfEvent.Op1)
        }
        onIfOpen("interface.ca_rewards") { player.enableMenu("ca_rewards", Page.Rewards) }

        for (interf in listOf("ca_overview", "ca_tasks", "ca_bosses", "ca_boss", "ca_rewards")) {
            onIfModalButton("component.$interf:burger_menu_frame") { menuClick(it.comsub) }
        }
        onIfModalButton("component.ca_overview:ca_buttons_click") { openTierTasks(it.comsub + 1) }
        onIfModalPauseButton("component.ca_bosses:bosses_button_click_pause") {
            setVar("varbit.ca_boss_selected", it.comsub)
            ifOpenMainModal("interface.ca_boss")
        }
        onIfModalButton("component.ca_boss:ca_boss_stats_click") {
            if (it.comsub == 0) {
                openPage(Page.Bosses)
            }
        }
        onIfModalButton("component.ca_boss:progress_bar") { openBossTasks() }
        for (filter in TaskFilter.entries) {
            onIfModalButton(filter.dropdown) { setVar(filter.varbit, it.comsub - 1) }
        }
    }

    private fun Player.enableMenu(interf: String, page: Page?) {
        ifSetEvents("component.$interf:burger_menu_frame", 0..MENU_ROWS, IfEvent.Op1)
        if (page != null) {
            VarPlayerIntMapSetter.set(this, "varbit.ca_last_opened_interface", page.id)
        }
    }

    private fun ProtectedAccess.menuClick(comsub: Int) {
        val page = Page.entries.firstOrNull { it.menuComsub == comsub } ?: return
        openPage(page)
    }

    private fun ProtectedAccess.openPage(page: Page) {
        ifOpenMainModal(page.interf)
    }

    private fun ProtectedAccess.openTierTasks(tier: Int) {
        setVar(TaskFilter.Tier.varbit, tier)
        setVar(TaskFilter.Type.varbit, 0)
        setVar(TaskFilter.Monster.varbit, 0)
        openPage(Page.Tasks)
    }

    private fun ProtectedAccess.openBossTasks() {
        val selected = player.vars["varbit.ca_boss_selected"]
        val structId = enum<Int, Int>(BOSS_ENUM).backing[selected] ?: return
        val boss = ServerCacheManager.getStruct(structId)?.params?.get(bossIdParam) as? Int ?: return
        setVar(TaskFilter.Tier.varbit, 0)
        setVar(TaskFilter.Type.varbit, 0)
        setVar(TaskFilter.Monster.varbit, boss)
        openPage(Page.Tasks)
    }

    private fun ProtectedAccess.setVar(varbit: String, value: Int) {
        VarPlayerIntMapSetter.set(player, varbit, value.coerceAtLeast(0))
    }

    private enum class Page(val id: Int, val interf: String, val menuComsub: Int) {
        Overview(0, "interface.ca_overview", 3),
        Tasks(1, "interface.ca_tasks", 5),
        Rewards(2, "interface.ca_rewards", 9),
        Bosses(3, "interface.ca_bosses", 7),
    }

    private enum class TaskFilter(val dropdown: String, val varbit: String) {
        Tier("component.ca_tasks:dropdown_tier", "varbit.ca_task_filter_tier"),
        Type("component.ca_tasks:dropdown_type", "varbit.ca_task_filter_type"),
        Monster("component.ca_tasks:dropdown_monster", "varbit.ca_task_filter_monster"),
        Completed("component.ca_tasks:dropdown_completed", "varbit.ca_task_filter_completed"),
    }

    private companion object {
        /** `ca_create_dropdown` draws a frame, a steel box and a row pair per page: 0..9. */
        const val MENU_ROWS = 9
        const val MAX_DROPDOWN_ROWS = 100
        const val BOSS_SLOTS = 100
        const val BOSS_ENUM = 3987

        val bossIdParam: Int by lazy { "param.ca_boss_id".asRSCM() }
    }
}
