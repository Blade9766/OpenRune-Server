package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.construction.data.Servant
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseServants
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Servants: hiring them at the Ardougne guild, the chief servant, and a hired servant in its
 * owner's house - talked to, rung for with a bell-pull, or handed items, or talked to by a guest,
 * who can ask to be shown out. The work itself is in [ServantDialogues].
 */
class ServantScript
@Inject
constructor(
    private val dialogues: ServantDialogues,
    private val registry: HouseRegistry,
    private val servants: HouseServants,
    private val houses: HouseAccess,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (servant in Servant.entries) {
            onOpNpc1(servant.guildNpc) { with(dialogues) { guildTalk(it.npc, servant) } }
            onOpNpc1(servant.npc) {
                val owner = servants.ownerOf(it.npc)
                if (owner != null && owner !== player && registry.houseAt(player.coords) != null) {
                    if (with(dialogues) { askToLeave(it.npc) }) {
                        houses.leave(this)
                    }
                } else if (registry.houseAt(player.coords) != null) {
                    with(dialogues) { houseTalk(it.npc) }
                } else {
                    with(dialogues) { guildTalk(it.npc, servant) }
                }
            }
            onOpNpcU(servant.npc) { with(dialogues) { useOn(it.npc, it.objType) } }
        }
        onOpNpc1(CHIEF) { with(dialogues) { chiefTalk(it.npc) } }
        for (bell in BELL_PULLS) {
            onOpLoc1(bell) { with(dialogues) { call(fromBell = true) } }
        }
        onPlayerQueueWithArgs<ServantDialogues.Errand>(ServantDialogues.TRIP_QUEUE) {
            with(dialogues) { finishErrand(it.args) }
        }
    }

    private companion object {
        const val CHIEF = "npc.poh_chief_servant"
        val BELL_PULLS = listOf("loc.poh_bellpull_1", "loc.poh_bellpull_2", "loc.poh_bellpull_3")
    }
}
