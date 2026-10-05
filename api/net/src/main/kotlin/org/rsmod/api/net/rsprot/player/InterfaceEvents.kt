package org.rsmod.api.net.rsprot.player

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.game.ui.UserInterfaceMap

internal object InterfaceEvents {
    fun isEnabled(
        ui: UserInterfaceMap,
        component: ComponentType,
        comsub: Int,
        event: IfEvent,
    ): Boolean {
        // A static component's events come from the cache, unless the server has set its own.
        val verifyStaticEvents = comsub == -1
        return if (verifyStaticEvents) {
            component.hasEvent(event) || ui.hasEvent(component, comsub, event)
        } else {
            ui.hasEvent(component, comsub, event)
        }
    }
}
