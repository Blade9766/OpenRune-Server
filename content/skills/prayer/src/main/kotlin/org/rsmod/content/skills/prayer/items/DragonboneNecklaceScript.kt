package org.rsmod.content.skills.prayer.items

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.prayer.PrayerSkillAction
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onEvent
import org.rsmod.game.inv.isAnyType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DragonboneNecklaceScript @Inject constructor(private val areas: AreaChecker) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingActionCompleteEvent> {
            val action = (context as? SkillingActionContext.Prayer)?.action
            if (action !is PrayerSkillAction.BuryComplete || action.ashes) return@onEvent
            val neck = player.worn[Wearpos.Front.slot]
            if (!neck.isAnyType("obj.dragonbone_necklace", "obj.bonecrusher_necklace")) {
                return@onEvent
            }
            if (areas.inArea("area.catacombs_of_kourend", player.coords)) return@onEvent
            val restore = action.catacombsBonePrayerRestore
            if (restore > 0) {
                player.statHeal("stat.prayer", constant = restore, percent = 0)
            }
        }
    }
}
