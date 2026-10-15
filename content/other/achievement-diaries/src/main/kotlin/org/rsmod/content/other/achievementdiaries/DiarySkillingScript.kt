package org.rsmod.content.other.achievementdiaries

import jakarta.inject.Inject
import org.rsmod.api.achievementdiaries.AchievementDiaries
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.script.onEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiarySkillingScript
@Inject
constructor(private val diaries: AchievementDiaries, private val areas: AreaChecker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            when (val source = product.source) {
                is SkillingProductSource.Fishing ->
                    if (product.item == "obj.raw_anchovies" && areas.inArea("area.al_kharid", player.coords)) {
                        diaries.complete(player, "dbrow.diary_lumbridge_easy_anchovies")
                    }
                is SkillingProductSource.Mining ->
                    if (product.item == "obj.iron_ore" && source.rock.coords.inAlKharidMine()) {
                        diaries.complete(player, "dbrow.diary_lumbridge_easy_iron_ore")
                    }
                else -> Unit
            }
        }
    }

    private fun CoordGrid.inAlKharidMine(): Boolean =
        level == 0 && x in AL_KHARID_MINE_X && z in AL_KHARID_MINE_Z

    private companion object {
        val AL_KHARID_MINE_X = 3290..3310
        val AL_KHARID_MINE_Z = 3270..3320
    }
}
