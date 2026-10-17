package org.rsmod.content.other.bots.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetNpcHead
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.game.entity.Player

/**
 * Writes the Bot Manager's components for one viewer, remembering what the client already shows
 * so a periodic refresh only sends what changed (and never redraws a hovered button).
 */
class Painter(private val player: Player) {
    private val shown = HashMap<String, Any>()

    fun reset() = shown.clear()

    fun text(name: String, value: String) {
        if (changed("t:$name", value)) player.ifSetText(com(name), value)
    }

    fun position(name: String, x: Int, y: Int) {
        if (changed("p:$name", x to y)) player.ifSetPosition(com(name), x, y)
    }

    fun hide(name: String, hidden: Boolean) {
        if (changed("h:$name", hidden)) player.ifSetHide(com(name), hidden)
    }

    fun button(name: String, label: String, state: ButtonState = ButtonState.Normal) {
        hide(name, false)
        if (changed("b:$name", label to state)) {
            player.runClientScript(BUTTON.asRSCM(RSCMType.CLIENTSCRIPT), com(name).asRSCM(RSCMType.COMPONENT), label, state.id)
        }
    }

    fun check(name: String, checked: Boolean, label: String) {
        hide(name, false)
        if (changed("c:$name", checked to label)) {
            player.runClientScript(CHECK.asRSCM(RSCMType.CLIENTSCRIPT), com(name).asRSCM(RSCMType.COMPONENT), if (checked) 1 else 0, label)
        }
    }

    fun dot(name: String, colour: Int) {
        if (changed("d:$name", colour)) {
            player.runClientScript(DOT.asRSCM(RSCMType.CLIENTSCRIPT), com(name).asRSCM(RSCMType.COMPONENT), colour)
        }
    }

    /** Shows [itemId] in an icon graphic, or hides the icon when it is null or unknown. */
    fun icon(name: String, itemId: Int?, count: Int = 1) {
        val obj = itemId?.let { runCatching { RSCM.getReverseMapping(RSCMType.OBJ, it) }.getOrNull() }
        if (obj == null) {
            hide(name, true)
            return
        }
        if (changed("o:$name", obj to count)) player.ifSetObj(com(name), obj, count)
        hide(name, false)
    }

    fun npcHead(name: String, npc: String) {
        if (changed("n:$name", npc)) runCatching { player.ifSetNpcHead(com(name), npc) }
    }

    private fun changed(key: String, value: Any): Boolean {
        if (shown[key] == value) return false
        shown[key] = value
        return true
    }

    enum class ButtonState(val id: Int) {
        Disabled(0),
        Normal(1),
        Selected(2),
    }

    companion object {
        const val INTERFACE: String = "interface.bot_manager"
        private const val BUTTON = "clientscript.bm_button"
        private const val CHECK = "clientscript.bm_check"
        private const val DOT = "clientscript.bm_dot"

        fun com(name: String): String = "component.bot_manager:$name"
    }
}
