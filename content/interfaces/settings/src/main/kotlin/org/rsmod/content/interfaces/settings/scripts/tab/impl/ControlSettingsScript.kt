package org.rsmod.content.interfaces.settings.scripts.tab.impl

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.ironman.IronmanActivity
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.table.SettingsConfigsRow
import org.rsmod.content.interfaces.settings.scripts.SettingUtils
import org.rsmod.content.interfaces.settings.scripts.Settings
import org.rsmod.content.interfaces.settings.scripts.varValue
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ControlSettingsScript
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.settings_side:skull_prevention") { player.toggleSkullPrevention() }

        onIfOverlayButton("component.settings_side:attack_priority_player_buttons") {
            player.selectPlayerPriority(it.comsub - DROPDOWN_ENTRY_OFFSET)
        }

        onIfOverlayButton("component.settings_side:attack_priority_npc_buttons") {
            player.selectNpcPriority(it.comsub - DROPDOWN_ENTRY_OFFSET)
        }

        onIfOverlayButton("component.settings_side:acceptaid") { player.toggleAcceptAid() }
        onIfOverlayButton("component.settings_side:houseoptions") { player.selectHouseOptions() }
        onIfOverlayButton("component.settings_side:bondoptions") { player.selectBondPouch() }
    }

    private fun Player.toggleSkullPrevention() {
        val row = SettingsConfigsRow.all().find { it.settingId == SKULL_PREVENTION_SETTING_ID }
        row?.varValue?.let {
            VarPlayerIntMapSetter.toggle(this, it)
        }
    }

    private fun Player.selectPlayerPriority(option: Int) {
        val setting = Settings.getSetting(PLAYER_ATTACK_OPTIONS_SETTING_ID)
        SettingUtils.setDropdown(this, option, setting)
    }

    private fun Player.selectNpcPriority(option: Int) {
        val setting = Settings.getSetting(NPC_ATTACK_OPTIONS_SETTING_ID)
        SettingUtils.setDropdown(this, option, setting)
    }

    private fun Player.toggleAcceptAid() {
        if (IronmanRestrictions.block(this, IronmanActivity.ACCEPT_AID)) {
            return
        }
        val row = SettingsConfigsRow.all().find { it.settingId == ACCEPT_AID_SETTING_ID }
        row?.varValue?.let {
            VarPlayerIntMapSetter.toggle(this, it)
        }
    }

    private fun Player.selectHouseOptions() {
        protectedAccess.launch(this) { ifOpenSide("interface.poh_options") }
    }

    private fun Player.selectBondPouch() {
        val opened = protectedAccess.launch(this) { ifOpenMainModal("interface.bond_main", -1, -2) }
        if (!opened) {
            mes(constants.dm_busy)
        }
    }

    private companion object {
        /** Side-panel dropdown entries are drawn from child 1, so `comsub - 1` is the option. */
        private const val DROPDOWN_ENTRY_OFFSET = 1
        private const val PLAYER_ATTACK_OPTIONS_SETTING_ID = 55
        private const val NPC_ATTACK_OPTIONS_SETTING_ID = 56
        private const val ACCEPT_AID_SETTING_ID = 59
        private const val SKULL_PREVENTION_SETTING_ID = 206
    }
}
