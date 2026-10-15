package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.JUNA
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.MAGIC_STONE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STONE_BOWL
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Juna's Story op and the item uses on her, plus the minigame's cycle timer and the drink at the
 * end. Talk-to on Juna belongs to [org.rsmod.content.quest.area.lumbridge.dorgeshuun.ZanikRevival],
 * which hands everything that isn't Zanik's revival to [Juna].
 */
class TearsOfGuthixScript
@Inject
constructor(
    private val quest: TearsOfGuthixQuest,
    private val rules: TearsOfGuthixRules,
    private val juna: Juna,
    private val cave: TearsCave,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2(JUNA) { startDialogue { with(juna) { story() } } }
        onOpLocU(JUNA, MAGIC_STONE) { startDialogue { with(juna) { stoneOnJuna() } } }
        onOpLocU(JUNA, STONE_BOWL) { startDialogue { with(juna) { talk() } } }
        onPlayerSoftTimer(TearsCave.TIMER) { cave.tick(player) }
        onPlayerQueue(TearsCave.DRINK_QUEUE) { with(cave) { drink() } }
        onPlayerLogin {
            cave.reset(player)
            remind(player)
        }
    }

    private fun remind(player: Player) {
        if (!quest.isComplete(player) || player.togRemindersOff || !rules.isEligible(player)) {
            return
        }
        player.mes("Juna is ready to hear your stories again in the Chasm of Tears.")
    }
}
