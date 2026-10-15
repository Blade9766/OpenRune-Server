package org.rsmod.content.slayer.rewards

import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerRewardsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
        }

        onIfModalButton(CONFIRM_COMPONENT) {
            SlayerRewardsHandler.onConfirmButton(this, it.comsub)
        }

        onIfModalButton(BUY_COMPONENT) {
            SlayerRewardsHandler.onBuyItem(this, it.comsub, it.op, it.obj)
        }
    }

    companion object {
        const val BUY_COMPONENT = "component.slayer_rewards:buy_items"
        const val CONFIRM_COMPONENT = "component.slayer_rewards:confirm_button"
    }
}
