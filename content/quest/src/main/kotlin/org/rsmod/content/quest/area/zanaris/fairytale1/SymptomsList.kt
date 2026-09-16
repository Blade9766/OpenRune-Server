package org.rsmod.content.quest.area.zanaris.fairytale1

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SYMPTOMS_LIST
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Fairy Nuff's notes on the Fairy Queen, written out for a human wizard to read. */
class SymptomsList @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(SYMPTOMS_LIST) { read() }
    }

    private suspend fun ProtectedAccess.read() {
        mesbox(
            "<col=800000>Symptoms, in the order I have seen them:</col><br><br>She sleeps, and " +
                "will not be woken.<br>She is cold to the touch, which no fairy has ever been." +
                "<br>The colour has gone out of her wings.",
        )
        mesbox(
            "Nothing she is given stays down. Nothing I brew has any effect at all.<br><br>She " +
                "has not held her secateurs since the day before she took ill.<br><br>" +
                "<col=800000>- Fairy Nuff</col>",
        )
    }
}
