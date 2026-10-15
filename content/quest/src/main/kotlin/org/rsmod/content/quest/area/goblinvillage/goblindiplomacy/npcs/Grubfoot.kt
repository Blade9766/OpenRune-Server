package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.STAGE_WANT_BLUE
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.STAGE_WANT_BROWN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Grubfoot, the generals' put-upon assistant in their hut. He is a multi-npc whose form follows
 * the player's `varbit.gobdip_grubfoot_vis`, so the op is registered on the base type and the
 * chat head on the brown form.
 */
class Grubfoot
@Inject
constructor(
    private val goblinDiplomacy: GoblinDiplomacyQuest,
    private val random: GameRandom,
) : PluginScript() {

    private val quest
        get() = goblinDiplomacy.quest

    override fun ScriptContext.startup() {
        onOpNpc1(GRUBFOOT) { startDialogue(it.npc) { grubfoot() } }
    }

    private suspend fun Dialogue.grubfoot() {
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            goblinDiplomacy.stage(player) == STAGE_WANT_BLUE -> {
                chatNpc(sad, "Me not like this orange armour. Make me look like that thing.")
                chatPlayer(quiz, "Look like what thing?")
                chatNpc(confused, "That fruit thing. The one that orange. What it called?")
                chatPlayer(quiz, "An orange?")
                chatNpc(sad, "That right. This armour make me look same colour as orange-fruit.")
                shutUpGrubfoot()
            }
            goblinDiplomacy.stage(player) == STAGE_WANT_BROWN -> {
                chatNpc(sad, "Me not like this blue colour.")
                chatPlayer(quiz, "Why not?")
                chatNpc(sad, "Me not know. It just make me feel...")
                chatPlayer(quiz, "Makes you feel blue?")
                chatNpc(sad, "Makes me feel kind of sad.")
                shutUpGrubfoot()
            }
            else -> {
                chatNpc(confused, "Grubfoot wear red armour! Grubfoot wear green armour!")
                chatNpc(confused, "Why they not make up their minds?")
                shutUpGrubfoot()
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        when (random.of(1..5)) {
            1 -> {
                chatNpc(sad, "Me lonely.")
                chatPlayer(quiz, "Why?")
                chatNpc(sad, "Other goblins in village follow either General Wartface or General Bentnoze. Me try to follow both but then me get left out of both groups.")
            }
            2 -> {
                chatNpc(worried, "Don't talk to me!")
                chatPlayer(quiz, "Why not?")
                chatNpc(worried, "Me not allowed to talk. Generals will tell me to shut up whenever I talk.")
            }
            3 -> chatNpc(angry, "Grubfoot do this! Grubfoot do that! They always want different things and they never satisfied!")
            4 -> {
                chatNpc(sad, "Me wish generals wouldn't tell me to shut up.")
                chatNpc(sad, "Sometimes they tell me to shut up when me not even say anything!")
            }
            else -> chatNpc(sad, "Me wish me weren't so small. Other goblins all pick on me and make me do all hard work for generals.")
        }
        shutUpGrubfoot()
    }

    /** Whichever general happens to be listening. */
    private suspend fun Dialogue.shutUpGrubfoot() {
        if (random.of(0..1) == 0) {
            chatNpcSpecific("General Wartface", GoblinGenerals.WARTFACE, angry, "Shut up Grubfoot!")
        } else {
            chatNpcSpecific("General Bentnoze", GoblinGenerals.BENTNOZE, angry, "Shut up Grubfoot!")
        }
    }

    companion object {
        /** The multi-npc in the map; his forms are `catwalk_goblin_brown`, `_orange`, `_blue`. */
        const val GRUBFOOT = "npc.catwalk_goblin"

        /** Chat head for Grubfoot's lines. */
        const val GRUBFOOT_HEAD = "npc.catwalk_goblin_brown"
    }
}
