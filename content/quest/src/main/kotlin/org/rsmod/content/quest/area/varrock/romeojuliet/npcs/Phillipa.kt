package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.PHILLIPA
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_HAS_MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_JULIET_CRYPT
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Phillipa, Juliet's cousin, who keeps an eye on her upstairs in the Leptoc mansion. */
class Phillipa @Inject constructor(private val quest: RomeoJulietQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(PHILLIPA) { startDialogue(it.npc) { phillipa() } }
    }

    private suspend fun Dialogue.phillipa() {
        val stage = quest.stage(player)
        when {
            stage < STAGE_HAS_MESSAGE -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    happy,
                    "Hello! I'm Phillipa, Juliet's cousin. I keep an eye on her to make sure that " +
                        "dashing young Romeo doesn't whisk her away from under our noses!",
                )
                chatNpc(
                    happy,
                    "He would, you know. He's ever so dashing and daring... in a damp-dishcloth " +
                        "sort of way.",
                )
            }
            stage < STAGE_JULIET_CRYPT -> {
                chatNpc(
                    happy,
                    "Oh, hello. Juliet's told me what you're doing for her and Romeo, and I'm " +
                        "ever so grateful. She deserves a little happiness.",
                )
                chatNpc(
                    laugh,
                    "And Romeo's just the sort of clown to make her laugh. Hysterically, some " +
                        "might say.",
                )
                chatNpc(happy, "He always brings tears to my eyes - tears of joy at his silly antics!")
                chatPlayer(happy, "Well, I do like to play cupid.")
            }
            stage == STAGE_JULIET_CRYPT -> {
                chatNpc(quiz, "Oh, hello again! How was I? Do you think I was convincing?")
                chatPlayer(happy, "Oh, yes, completely!")
                chatNpc(
                    happy,
                    "Oh, good! I do hope Juliet's pleased too. Dashing young Romeo must be beside " +
                        "himself! Have you told him the good news yet?",
                )
                chatPlayer(neutral, "Not yet, but I'm about to!")
                chatNpc(happy, "Lucky you! I can't wait to see the look on his face!")
            }
            else -> {
                chatNpc(happy, "Oh, hello! Romeo's taking me to the Blue Moon Inn this evening!")
                chatPlayer(neutral, "Does Juliet know?")
                chatNpc(shifty, "Juliet? Oh... I'm sure she'll be very happy for us.")
            }
        }
    }
}
