package org.rsmod.content.areas.city.portsarim.npcs

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SarahsFarmScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.dog") { petDog() }
    }

    private suspend fun ProtectedAccess.petDog() {
        arriveDelay()
        anim("seq.human_pickupfloor")
        delay(2)
        mes("He tries to lick your hands as you pet him.")
    }
}
