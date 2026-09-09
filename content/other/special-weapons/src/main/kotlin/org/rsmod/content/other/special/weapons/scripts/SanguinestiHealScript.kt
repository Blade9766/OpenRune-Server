package org.rsmod.content.other.special.weapons.scripts

import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.other.special.weapons.magic.PoweredStaffWeapons.SanguinestiHeal
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lands the Sanguinesti staff's passive heal together with the hit that triggered it (queued by
 * `PoweredStaffWeapons`).
 */
class SanguinestiHealScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs<SanguinestiHeal>("queue.sanguinesti_heal") {
            val heal = it.args
            statHeal("stat.hitpoints", constant = heal.amount, percent = 0)
            spotanim(heal.spotanim)
        }
    }
}
