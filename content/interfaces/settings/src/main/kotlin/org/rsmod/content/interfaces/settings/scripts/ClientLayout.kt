package org.rsmod.content.interfaces.settings.scripts

import org.rsmod.api.player.ui.ifMoveTop
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** Options of the "Game client layout" dropdown (setting 12), in dropdown order. */
enum class ClientLayout(val topLevel: String) {
    FixedClassic("interface.toplevel"),
    ResizableClassic("interface.toplevel_osrs_stretch"),
    ResizableModern("interface.toplevel_pre_eoc");

    companion object {
        const val SETTING_ID: Int = 12

        fun fromDropdownOption(option: Int): ClientLayout? = entries.getOrNull(option)
    }
}

fun Player.selectClientLayout(layout: ClientLayout, eventBus: EventBus) {
    ifMoveTop(layout.topLevel, eventBus)
}
