package org.rsmod.content.quest.area.zanaris.fairytale1

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.MAGIC_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_SEEN_MORTIFER
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The Nature Spirit's part in Growing Pains. He lives in the Mort Myre grotto and belongs to the
 * Nature Spirit quest, so his own script calls in here rather than registering a second set of
 * hooks on him.
 */
@Singleton
class SecateursEnchantment @Inject constructor(private val fairytale: Fairytale1Quest) {

    /** True while the spirit has something to say about secateurs. */
    fun hasBusiness(player: Player): Boolean =
        fairytale.stage(player) == STAGE_SEEN_MORTIFER && fairytale.mortiferListGiven.get(player) ||
            (fairytale.isComplete(player) && SECATEURS in player.inv)

    suspend fun talk(dialogue: Dialogue, spirit: Npc?) {
        with(dialogue) {
            if (fairytale.isComplete(player)) {
                replaceLostPair(spirit)
                return
            }
            chatNpc(quiz, "You carry a necromancer's reading with you. What is it you want of me?")
            chatPlayer(
                neutral,
                "A Tanglefoot has the Fairy Queen's secateurs. Malignius Mortifer says you can " +
                    "enchant a pair that will cut it.",
            )
            chatNpc(
                neutral,
                "A Tanglefoot. Then the growing of the whole world is stopped, and that is very " +
                    "much my business. Show me what he gave you.",
            )
            val missing = missingItems(player)
            if (missing.isNotEmpty()) {
                chatNpc(sad, "You do not have everything yet.")
                for (item in missing) {
                    objbox(item, "You still need ${itemName(item)}.")
                }
                chatNpc(neutral, "Bring the rest and I will do the work gladly.")
                return
            }
            enchant(spirit)
        }
    }

    private suspend fun Dialogue.enchant(spirit: Npc?) {
        chatNpc(happy, "Everything is here. Stand back, my friend - this is not a gentle spell.")
        for (item in fairytale.ingredients(player)) {
            access.invDel(access.inv, item)
        }
        spirit?.anim(CAST_SEQ)
        spirit?.spotanim(CAST_SPOTANIM)
        access.spotanim(ENCHANT_SPOTANIM)
        access.soundSynth(ENCHANT_SOUND)
        access.delay(ENCHANT_TICKS)
        access.invReplace(access.inv, SECATEURS, 1, MAGIC_SECATEURS)
        fairytale.advanceTo(access, STAGE_HAS_SECATEURS)
        objbox(MAGIC_SECATEURS, "The spirit works the enchantment into the blades. Your secateurs are now magic secateurs.")
        chatNpc(
            neutral,
            "Wield them, and nothing that grows can stand against you. Do not put them away when " +
                "you go into the Tanglefoot's tunnel; nothing else will mark it.",
        )
    }

    private suspend fun Dialogue.replaceLostPair(spirit: Npc?) {
        chatPlayer(sad, "I've lost the magic secateurs you made for me.")
        chatNpc(
            neutral,
            "Then we shall make another pair. The reading is done; the enchantment remembers it.",
        )
        spirit?.anim(CAST_SEQ)
        spirit?.spotanim(CAST_SPOTANIM)
        access.spotanim(ENCHANT_SPOTANIM)
        access.soundSynth(ENCHANT_SOUND)
        access.delay(ENCHANT_TICKS)
        access.invReplace(access.inv, SECATEURS, 1, MAGIC_SECATEURS)
        objbox(MAGIC_SECATEURS, "Your secateurs are magic secateurs once more.")
    }

    private fun missingItems(player: Player): List<String> {
        val needed = fairytale.ingredients(player) + SECATEURS
        return needed.filterNot { player.inv.contains(it) }
    }

    private fun itemName(item: String): String {
        val type = ServerCacheManager.getItem(item.asRSCM(RSCMType.OBJ))
        return type?.name?.lowercase() ?: "something"
    }

    private companion object {
        const val CAST_SEQ = "seq.human_castteleport"
        const val CAST_SPOTANIM = "spotanim.druidicspirit_effect"
        const val ENCHANT_SPOTANIM = "spotanim.fairy_flower_enchant"
        const val ENCHANT_SOUND = "synth.spirit_transform"
        const val ENCHANT_TICKS = 3
    }
}
