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

    /** The message box paginates every four rendered lines, so each page is written to fit one. */
    private suspend fun ProtectedAccess.read() {
        mesbox(
            "<col=800000>Symptoms, in the order I saw them:</col><br>She sleeps, and will not be " +
                "woken.<br>She is cold to the touch.<br>The colour has gone from her wings.",
        )
        mesbox(
            "Nothing she is given stays down.<br>Nothing I brew has any effect.<br>She has not " +
                "held her secateurs since she took ill.<br><col=800000>- Fairy Nuff</col>",
        )
    }
}
