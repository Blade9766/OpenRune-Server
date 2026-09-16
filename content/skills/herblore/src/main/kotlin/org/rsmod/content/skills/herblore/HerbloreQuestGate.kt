package org.rsmod.content.skills.herblore

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.QuestRequirements

/**
 * Herblore is taught by Kaqemeex at the end of Druidic Ritual; until then nothing that would earn
 * Herblore experience works. Whether this is enforced at all depends on the server's
 * `gameplay.quest-requirements.mode`.
 */
internal suspend fun ProtectedAccess.knowsHerblore(): Boolean {
    if (QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)) {
        return true
    }
    mesbox(
        "You need to complete the Druidic Ritual quest before you can use the Herblore skill. " +
            "Speak to Kaqemeex at the stone circle north of Taverley.",
    )
    return false
}

private const val DRUIDIC_RITUAL = "quest_druidicritual"
