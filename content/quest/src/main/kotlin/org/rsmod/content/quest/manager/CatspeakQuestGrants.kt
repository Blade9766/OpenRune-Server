package org.rsmod.content.quest.manager

import org.rsmod.content.other.pets.cats.CatspeakGrants
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Dragon Slayer II leaves the player able to understand cats without the catspeak amulet. */
class CatspeakQuestGrants : PluginScript() {
    override fun ScriptContext.startup() {
        CatspeakGrants.grant { player ->
            QuestRequirements.isOnQuest(player, DRAGON_SLAYER_2) ||
                QuestRequirements.hasCompleted(player, DRAGON_SLAYER_2)
        }
    }

    private companion object {
        const val DRAGON_SLAYER_2 = "quest_dragonslayer2"
    }
}
