package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.heroesquest.npcs.AchiettiesDialogue
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Heroes' Guild's double door. Heroes walk straight through and anyone inside may leave;
 * everyone else is met by Achietties, who starts the quest or asks how it is going.
 */
class HeroesGuildDoor
@Inject
constructor(
    private val passages: GenericPassageScript,
    private val achietties: AchiettiesDialogue,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(DOOR_LEFT) { enter(it.vis, it.type) }
        onOpLoc1(DOOR_RIGHT) { enter(it.vis, it.type) }
    }

    private suspend fun ProtectedAccess.enter(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.x < door.coords.x
        if (leaving || QuestRequirements.hasCompleted(player, HeroesQuest.QUEST_KEY)) {
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        with(achietties) { atGuildDoor() }
    }

    private companion object {
        const val DOOR_LEFT = "loc.herodoor_l"
        const val DOOR_RIGHT = "loc.herodoor_r"
    }
}
