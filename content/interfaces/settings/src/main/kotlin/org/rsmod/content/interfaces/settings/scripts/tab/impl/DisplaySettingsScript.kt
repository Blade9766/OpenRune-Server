package org.rsmod.content.interfaces.settings.scripts.tab.impl

import jakarta.inject.Inject
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.settings.scripts.ClientLayout
import org.rsmod.content.interfaces.settings.scripts.selectClientLayout
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DisplaySettingsScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    private var Player.zoomDisabled by boolVarBit("varbit.camera_zoom_mouse_disabled")

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.settings_side:brightness_bobble_container") {
            player.resyncVar("varbit.option_brightness_remember")
        }
        onIfOverlayButton("component.settings_side:zoom_toggle") {
            player.zoomDisabled = !player.zoomDisabled
        }
        onIfOverlayButton("component.settings_side:display_dynamic_setting_1_buttons") {
            val layout = ClientLayout.fromDropdownOption(it.comsub - 1) ?: return@onIfOverlayButton
            player.selectClientLayout(layout, eventBus)
        }
    }
}
