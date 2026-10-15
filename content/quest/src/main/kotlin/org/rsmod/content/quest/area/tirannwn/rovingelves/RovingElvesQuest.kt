package org.rsmod.content.quest.area.tirannwn.rovingelves

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.toldLie by intVarBit("varbit.roving_told_lie")
internal var Player.guardianSlain by intVarBit("varbit.roving_guardian_slain")
internal var Player.rewardChoice by intVarBit("varbit.roving_reward_choice")
internal var Player.ilfeenChants by intVarBit("varbit.roving_ilfeen_chantcount")

/**
 * Roving Elves.
 *
 * The stage is the whole of the cache varp `varp.roving_elves_quest` (endstate 6 from
 * `dbrow.quest_rovingelves`), with the values the client's quest data uses: 1 after lying to
 * Islwyn, 2 once he accepts the player's help, 3 when Eluned has explained the consecration, 4
 * once she has enchanted the seed, 5 after the seed is planted by the chalice, and 6 on choosing
 * the reward.
 *
 * Holding a seed is never progress: the guardian kill is its own flag
 * (`varbit.roving_guardian_slain`), so a banked or lost seed changes what Eluned says, not where
 * the player is. The reward pick is `varbit.roving_reward_choice`; both live on the server-only
 * `varp.roving_elves_state`.
 *
 * Islwyn, Eluned and Ilfeen stand in Isafdar as multinpcs on `varp.sote_tertiary`: 0 offers
 * Talk-to only, 1 adds Islwyn's Trade or Ilfeen's Enchant, and 2 is Song of the Elves' scout.
 * [syncVars] only moves them between 0 and 1, so a later quest's 2 is left alone.
 */
@Singleton
class RovingElvesQuest :
    QuestScript(
        QUEST_KEY,
        "varp.roving_elves_quest",
        rewards {
            xp("stat.strength", STRENGTH_XP)
            scroll("10,000 Strength XP", "A crystal bow or crystal shield", "with 500 charges")
        },
        ItemRewardDisplay(NEW_SEED, zoom = 400),
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
        QuestItemDrops.register(OLD_SEED, ::needsSeedDrop)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** Regicide and Waterfall Quest, through the server's quest requirement policy. */
    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, REGICIDE) &&
            QuestRequirements.hasCompleted(player, WATERFALL)

    /** The guardian only drops a seed while it is wanted and the player has none anywhere. */
    fun needsSeedDrop(player: Player): Boolean =
        stage(player) == STAGE_GET_SEED && !player.ownsAnywhere(OLD_SEED) && !player.ownsAnywhere(NEW_SEED)

    fun syncVars(player: Player) {
        setTertiary(player, ISLWYN_VARBIT, if (isComplete(player)) 1 else 0)
        setTertiary(player, ILFEEN_VARBIT, if (player.ilfeenChants >= ILFEEN_CHEAPEST) 1 else 0)
        if (stage(player) != 0) {
            return
        }
        for (varbit in OWN_VARBITS) {
            setVarBit(player, varbit, 0)
        }
    }

    private fun setTertiary(player: Player, varbit: String, value: Int) {
        if (player.vars[varbit] != SOTE_SCOUT) {
            setVarBit(player, varbit, value)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Islwyn</col>, who camps with <col=800000>Eluned</col> in the " +
            "forest of <col=800000>Isafdar</col>, west of Lletya."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            if (stage == STAGE_LIED) {
                line("I told Islwyn I had nothing to do with his grandmother's tomb. He knows I'm hiding something.")
                line("If I want to help I should go back and tell him the truth.")
                return@questJournal
            }
            step(
                stage > STAGE_ACCEPTED,
                "Islwyn's grandmother Glarial was moved from her tomb when I did the Waterfall Quest. " +
                    "I offered to set things right, and he sent me to Eluned.",
            )
            if (stage >= STAGE_GET_SEED) {
                step(
                    stage > STAGE_GET_SEED,
                    "Eluned needs the consecration seed from the Moss Guardian in Glarial's old tomb " +
                        "near Baxtorian Falls. I need Glarial's pebble to get in, and the tomb only " +
                        "opens for those who bring no weapons, armour or runes.",
                )
            }
            if (stage == STAGE_GET_SEED) {
                seedLines(access)
            }
            if (stage >= STAGE_PLANT_SEED) {
                step(
                    stage > STAGE_PLANT_SEED,
                    "Eluned enchanted the seed. I must plant it beside the chalice in Glarial's new " +
                        "resting place inside the waterfall, and I'll need a spade and a rope.",
                )
            }
            if (stage == STAGE_PLANT_SEED && !access.ownsAnywhere(NEW_SEED)) {
                line("I've lost the enchanted seed. Eluned can give me another.")
            }
            if (stage >= STAGE_PLANTED) {
                line("The seed grew into a crystal tree beside the chalice. I should tell Islwyn.")
            }
        }

    private fun QuestJournalBuilder.seedLines(access: ProtectedAccess) {
        val p = access.player
        when {
            access.ownsAnywhere(OLD_SEED) && p.guardianSlain == 1 ->
                line("I have the seed. Eluned can enchant it.")
            p.guardianSlain == 1 -> line("I killed the Moss Guardian but no longer have its seed. I'll need another.")
            else -> line("I still have to defeat the Moss Guardian.")
        }
    }

    private fun QuestJournalBuilder.step(done: Boolean, text: String) {
        if (done) strike(text) else line(text)
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("Islwyn was troubled that his grandmother Glarial's remains had been moved from her tomb.")
            line(
                "Eluned enchanted the consecration seed I took from the Moss Guardian in Glarial's " +
                    "old tomb, and I planted it beside the chalice where she now rests with Baxtorian.",
            )
            val reward = if (player.player.rewardChoice == REWARD_SHIELD) "crystal shield" else "crystal bow"
            line("In thanks Islwyn gave me a $reward.")
        }

    companion object {
        const val QUEST_KEY = "quest_rovingelves"
        const val REGICIDE = "quest_regicide"
        const val WATERFALL = "quest_waterfall"

        const val STAGE_LIED = 1
        const val STAGE_ACCEPTED = 2
        const val STAGE_GET_SEED = 3
        const val STAGE_PLANT_SEED = 4
        const val STAGE_PLANTED = 5
        const val STAGE_COMPLETE = 6

        const val STRENGTH_XP = 10_000.0

        const val REWARD_BOW = 1
        const val REWARD_SHIELD = 2
        const val REWARD_CHARGES = 500

        /** What Islwyn and Ilfeen hand out: the full charge of a newly sung crystal item. */
        const val FULL_CHARGES = 2_500

        /** Ilfeen's price stops falling after this many enchantments, which also unlocks "Enchant". */
        const val ILFEEN_CHEAPEST = 4

        const val SOTE_SCOUT = 2

        const val ISLWYN = "npc.roving_bowyer"
        const val ISLWYN_TALK = "npc.roving_islwyn_1op"
        const val ISLWYN_TRADE = "npc.roving_islwyn_2ops"
        const val ELUNED = "npc.roving_female_woodelf"
        const val ELUNED_TALK = "npc.roving_female_woodelf_1op"
        const val ELUNED_ENCHANT = "npc.roving_female_woodelf_2op"
        const val ILFEEN = "npc.roving_update_female_woodelf"
        const val ILFEEN_TALK = "npc.roving_ilfeen_1op"
        const val ILFEEN_ENCHANT = "npc.roving_ilfeen_2ops"
        const val MOSS_GUARDIAN = "npc.roving_mossgiant"

        const val ISLWYN_VARBIT = "varbit.roving_bowyer"
        const val ILFEEN_VARBIT = "varbit.roving_update_female_woodelf"

        const val OLD_SEED = "obj.roving_old_consecration_seed"
        const val NEW_SEED = "obj.roving_new_consecration_seed"
        const val WEAPON_SEED = "obj.crystal_seed_old"
        const val CRYSTAL_BOW = "obj.crystal_bow"
        const val CRYSTAL_SHIELD = "obj.crystal_shield"
        const val SPADE = "obj.spade"
        const val COINS = "obj.coins"
        const val CRYSTAL_CHARGES = "varobj.crystal_weapon_charges"

        val OWN_VARBITS =
            listOf(
                "varbit.roving_told_lie",
                "varbit.roving_guardian_slain",
                "varbit.roving_reward_choice",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}

internal fun Player.ownsAnywhere(obj: String): Boolean =
    inv.contains(obj) || worn.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

internal fun ProtectedAccess.ownsAnywhere(obj: String): Boolean = player.ownsAnywhere(obj)
