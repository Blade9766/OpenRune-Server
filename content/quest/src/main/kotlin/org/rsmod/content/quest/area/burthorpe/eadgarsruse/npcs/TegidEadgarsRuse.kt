package org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.EadgarsRuseQuest

/**
 * Tegid's part in Eadgar's Ruse: talked or threatened out of one of his dirty robes. Tegid's own
 * script belongs to Mourning's End Part I and calls [askForRobe] after its small talk.
 */
object TegidEadgarsRuse {

    /** Returns false, having said nothing, when the player has no use for a robe. */
    suspend fun Dialogue.askForRobe(): Boolean {
        if (!EadgarsRuseQuest.needsRobe(player)) {
            return false
        }
        chatPlayer(
            quiz,
            "You wouldn't be able to spare any of those dirty robes by any chance? It's a matter " +
                "of the utmost importance.",
        )
        chatNpc(angry, "What? No! These are my robes!")
        val picked =
            choice3(
                "Fine.", 1,
                "Sanfew won't be happy...", 2,
                "You'll give me those robes right now...", 3,
            )
        when (picked) {
            1 -> {
                chatPlayer(neutral, "Fine.")
                return true
            }
            2 -> {
                chatPlayer(
                    neutral,
                    "I'm sure Sanfew won't be happy when I tell him it's your fault he can't perform " +
                        "the purification ritual.",
                )
                chatNpc(sad, "What? Oh well, if it's a matter of that much importance, I suppose you can borrow one...")
            }
            else -> {
                chatPlayer(angry, "You'll give me those robes right now...or I'm going to cut down trees until you do.")
                chatNpc(shocked, "You wouldn't dare!")
                chatPlayer(neutral, "Of course I would. They're just trees.")
                chatNpc(angry, "You monster! Take the robes and leave this place!")
            }
        }
        if (access.invAdd(access.inv, EadgarsRuseQuest.ROBE).failure) {
            access.mes("You don't have enough room to carry the robe.")
        }
        return true
    }
}
