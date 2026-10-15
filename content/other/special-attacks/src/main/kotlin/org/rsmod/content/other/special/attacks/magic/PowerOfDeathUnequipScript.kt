package org.rsmod.content.other.special.attacks.magic

import dev.openrune.util.Wearpos
import org.rsmod.api.player.righthand
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.content.other.special.attacks.magic.PowerOfDeathSpecialAttack.Companion.POWER_OF_DEATH_STAVES
import org.rsmod.content.other.special.attacks.magic.PowerOfDeathSpecialAttack.Companion.powerOfDeathExpiration
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Power of Death only holds "while the staff remains equipped", so swapping the staff out ends it
 * immediately rather than leaving the reduction to run out on its own clock.
 */
class PowerOfDeathUnequipScript : PluginScript() {
    override fun ScriptContext.startup() {
        onWearposChange { player.wearposChange(wearpos) }
    }

    private fun Player.wearposChange(wearpos: Wearpos) {
        if (wearpos != Wearpos.RightHand || powerOfDeathExpiration == 0) {
            return
        }
        if (POWER_OF_DEATH_STAVES.none { righthand.isType(it) }) {
            powerOfDeathExpiration = 0
        }
    }
}
