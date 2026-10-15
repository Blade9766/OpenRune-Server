package org.rsmod.content.quest.area.falador.knightssword.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_RELDO
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Player

/** Reldo's Knight's Sword topic: what the library knows of the Imcando dwarves. */
@Singleton
class ImcandoLore @Inject constructor(private val ks: KnightsSwordQuest) {

    fun reldoOption(player: Player): String? =
        if (ks.stage(player) in STAGE_STARTED until STAGE_COMPLETE) OPTION else null

    suspend fun Dialogue.reldoImcando() {
        chatPlayer(quiz, OPTION)
        if (ks.stage(player) > STAGE_STARTED) {
            chatNpc(neutral, "As I said, the last of them is thought to live south of Port Sarim, near the coast. Thurgo, I believe his name is.")
            chatNpc(happy, "And don't forget the redberry pie! Every account agrees on that.")
            return
        }
        chatNpc(happy, "The Imcando! Now there's a subject. Why do you ask?")
        chatPlayer(neutral, "A friend needs a sword copied. It's very old, and I'm told nobody alive could make it.")
        chatNpc(neutral, "That sounds like Imcando work. They were a clan of dwarves, and the finest smiths this side of the Lumbridge river.")
        chatNpc(sad, "Most were driven from their homes in the Fourth Age and never seen again. The few books that mention them disagree on almost everything.")
        chatNpc(neutral, "Almost. One of them, Thurgo, is said to keep to himself on the coast south of Port Sarim. He won't welcome visitors.")
        ks.advanceTo(access, STAGE_RELDO)
        chatNpc(happy, "But every account agrees on one thing: the Imcando adore redberry pie. A pie might open a door a stranger never could.")
        chatPlayer(happy, "Thurgo, south of Port Sarim, likes pie. Thank you, Reldo!")
    }

    private companion object {
        const val OPTION = "What do you know about the Imcando dwarves?"
    }
}
