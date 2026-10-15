package org.rsmod.content.other.achievementdiaries

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.achievementdiaries.AchievementDiaries
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiaryJournalScript
@Inject
constructor(
    private val diaries: AchievementDiaries,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen("interface.area_task") {
            player.ifSetEvents("component.area_task:taskbox", 0 until REGION_COUNT, IfEvent.Op1)
        }
        onIfOverlayButton("component.area_task:taskbox") {
            val region = it.comsub
            val opened = protectedAccess.launch(player) { openJournal(region) }
            if (!opened) {
                player.mes("Please finish what you're doing first.")
            }
        }
    }

    private fun ProtectedAccess.openJournal(region: Int) {
        val tiers = diaries.tiers(region)
        if (tiers.isEmpty()) {
            mes("This Achievement Diary isn't available yet.")
            return
        }
        val lines = buildList {
            for (tier in tiers) {
                add("<col=$HEADER_COLOUR>${diaries.tierLabel(tier)}</col>")
                val tasks = diaries.tasks(tier)
                if (tasks.isEmpty()) {
                    add("These tasks aren't available yet.")
                }
                for (task in tasks) {
                    add(if (diaries.isComplete(player, task)) "<str>${task.description}</str>" else task.description)
                }
                add("")
            }
        }
        ifOpenMain("interface.questjournal")
        runClientScript(JOURNAL_RESET_SCRIPT)
        ifSetText("component.questjournal:title", "<col=$HEADER_COLOUR>Achievement Diary - ${tiers.first().name}</col>")
        lines.forEachIndexed { index, line -> ifSetText("component.questjournal:qj${index + 1}", line) }
    }

    private companion object {
        const val REGION_COUNT = 12
        const val JOURNAL_RESET_SCRIPT = 5240
        const val HEADER_COLOUR = "7f0000"
    }
}
