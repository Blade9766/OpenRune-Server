package org.rsmod.content.quest.area.tirannwn.templeoflight

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.content.travel.jewellery.JewelleryRequirements
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.safeguardsRestored by intVarBit("varbit.mourning_light_temple_safe_guards")
internal var Player.templeRope by intVarBit("varbit.mourning_temple_rope")
internal var Player.metThorgel by intVarBit("varbit.mourning_light_door_1_c_first_time")
internal var Player.thorgelVisible by intVarBit("varbit.mourning_dwarf_vis")
internal var Player.thorgelTask by intVarBit("varbit.mourning_dwarf_startedtask")

/**
 * Mourning's End Part II (The Temple of Light).
 *
 * The stage is the cache varbit `varbit.mourning_quest_main` (endstate 60 from
 * `dbrow.quest_mourningsendpart2`); the client only tells 0, 1-59 and 60 apart, so the steps in
 * between are this server's: 5 once Arianwyn sends the player to the mines, 10 when Essyllt hands
 * over the new key, 20 after the player finds the dig team outside the temple, 30 when Arianwyn
 * asks for a sample of the black crystal, 40 once Eluned has made the new crystal, 50 when the
 * charged crystal restores the safeguards, and 60 on Arianwyn's thanks.
 *
 * The cache also carries the puzzle's client side: one varbit per beam path, per vertical beam and
 * per light door, the rope, the chest flags, the dispenser trays, the safeguards and Thorgel. The
 * pillars' contents, Thorgel's list and the schema version are server varps (see
 * [TemplePuzzleStore] and [ThorgelList]); everything the client draws is recomputed from them.
 *
 * Holding an item is never progress: a lost key comes from Essyllt or his desk, a lost sample from
 * the black crystal, a lost new crystal (charged or not) from Arianwyn, a lost list from Thorgel and
 * a lost trinket from Arianwyn after the quest.
 */
@Singleton
class MourningsEndPart2Quest @Inject constructor(private val part1: MourningsEndQuest) :
    QuestScript(
        QUEST_KEY,
        "varp.mourning_quest_part2",
        rewards {
            xp("stat.agility", AGILITY_XP)
            item(TRINKET)
            extra("Access to the Death Altar")
        },
        ItemRewardDisplay(CYAN_CRYSTAL, zoom = 400),
        questVarbit = "varbit.mourning_quest_main",
    ) {

    private val lights = TempleLights()

    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
        JewelleryRequirements.register(DARK_BEASTS_TELEPORT) {
            if (unlocked(it)) null else "You need to complete Mourning's End Part II to use this teleport."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** Arianwyn offers the quest once Part I is done for real, or by the quest requirement policy. */
    fun mayStart(player: Player): Boolean = stage(player) == 0 && part1.unlocked(player)

    /**
     * The quest counts as done for what it unlocks: really completed, or not started at all while
     * the quest requirement policy treats it as complete.
     */
    fun unlocked(player: Player): Boolean =
        isComplete(player) || (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    /** The temple guards itself again: from the restoration on, and for anyone the quest is done for. */
    fun guarded(player: Player): Boolean = player.safeguardsRestored == 1 || unlocked(player)

    /** Mirrors and crystals may be moved while the quest is under way and the temple is unguarded. */
    fun puzzleOpen(player: Player): Boolean = isStarted(player) && !guarded(player)

    /**
     * Redraws the player's temple from their saved pillars once they are on the quest. At stage 0
     * (never started, or reset by `::resetquest`) every Part II var goes back to zero instead.
     */
    fun syncVars(player: Player) {
        if (stage(player) == 0) {
            for (varp in RESET_VARPS) {
                if (player.vars[varp] != 0) VarPlayerIntMapSetter.set(player, varp, 0)
            }
            for (varbit in RESET_VARBITS) {
                setVarBit(player, varbit, 0)
            }
        } else {
            lights.refresh(player)
        }
        TempleShadows.syncPeace(player, this)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Arianwyn</col> in <col=800000>Lletya</col>, once " +
            "<col=800000>Mourning's End Part I</col> is complete."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            step(stage > STAGE_STARTED, "Arianwyn wants to know how close the Iorwerth elves are to the Temple of Light. I should get into the mourners' mine beneath their Headquarters, in disguise.")
            if (stage >= STAGE_KEY) {
                step(stage > STAGE_KEY, "Essyllt gave me a key to the mine and asked me to find a dig team that went missing looking for the temple.")
            }
            if (stage >= STAGE_FOUND) {
                step(stage > STAGE_FOUND, "I found the dig team dead outside the temple. The other mourners don't know yet, so I should tell Arianwyn.")
            }
            if (stage >= STAGE_SAMPLE) {
                step(stage > STAGE_SAMPLE, "The temple's safeguards have failed. Arianwyn wants a sample of the black crystal that powers them, from somewhere in the temple.")
            }
            if (stage >= STAGE_CRYSTAL) {
                step(stage > STAGE_CRYSTAL, "Eluned made a new crystal from my sample. I must charge it on the altar at the far end of the temple, then place it with the black crystal.")
            }
            if (stage == STAGE_CRYSTAL) {
                line("Light pillars, mirrors and coloured crystals open the doors of light. The crystal dispenser on the middle floor resets the puzzle.")
                if (p.metThorgel == 1) {
                    line("Thorgel, a dwarf by the altar, will trade a death talisman for the items on his list.")
                }
            }
            if (stage >= STAGE_RESTORED) {
                line("The safeguards are restored. I should tell Arianwyn in Lletya.")
            }
        }

    private fun QuestJournalBuilder.step(done: Boolean, text: String) {
        if (done) strike(text) else line(text)
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I found the Temple of Light beneath West Ardougne before the mourners could, and charged a new crystal on the Death Altar inside it.")
            line("With the crystal in place the temple's safeguards are restored, though some dwarves have dug their way into its back chamber.")
        }

    companion object {
        const val QUEST_KEY = "quest_mourningsendpart2"
        const val PART_ONE = MourningsEndQuest.QUEST_KEY

        const val STAGE_STARTED = 5
        const val STAGE_KEY = 10
        const val STAGE_FOUND = 20
        const val STAGE_SAMPLE = 30
        const val STAGE_CRYSTAL = 40
        const val STAGE_RESTORED = 50
        const val STAGE_COMPLETE = 60

        const val AGILITY_XP = 60_000.0

        /** The slayer ring's teleport into the mines, which Part II opens up. */
        const val DARK_BEASTS_TELEPORT = "Dark Beasts"

        const val NEW_KEY = "obj.mourning_excavation_key"
        const val SAMPLE = "obj.mourning_crystal_sample"
        const val NEW_CRYSTAL = "obj.mourning_crystal_new_sample"
        const val CHARGED_CRYSTAL = "obj.mourning_crystal_new_powered"
        const val TRINKET = "obj.mourning_crystal_trinket"
        const val JOURNAL = "obj.mourning_ederns_journal"
        const val COLOUR_WHEEL = "obj.mourning_colour_wheel"
        const val NOTES = "obj.mourning_notes"
        const val ITEM_LIST = "obj.mourning_deathalter_list"
        const val CHISEL = "obj.chisel"
        const val ROPE = "obj.rope"
        const val CYAN_CRYSTAL = "obj.mourning_crystal_cyan"
        const val DEATH_TALISMAN = "obj.death_talisman"

        /** What gets a player into the Death Altar without Thorgel's talisman, carried or worn. */
        val ALTAR_KEYS = listOf("obj.death_talisman", "obj.catalytic_talisman", "obj.tiara_death", "obj.tiara_catalytic")

        /** Capes that open every altar while worn. */
        val ALTAR_CAPES = listOf("obj.skillcape_runecrafting", "obj.skillcape_runecrafting_trimmed", "obj.skillcape_max")

        private val RESET_VARPS = TemplePuzzleStore.VARPS + ThorgelList.DELIVERED_VARPS + "varp.mourning2_state"

        private val RESET_VARBITS =
            TempleItem.entries.map { TemplePuzzle.trayVarbit(it) } + TempleChest.entries.map { it.varbit } +
                listOf(
                    TemplePuzzle.STARTER_VARBIT,
                    "varbit.mourning_light_temple_safe_guards",
                    "varbit.mourning_temple_rope",
                    "varbit.mourning_light_door_1_c_first_time",
                    "varbit.mourning_dwarf_vis",
                    "varbit.mourning_dwarf_startedtask",
                ) + TempleGeometry.links.map { it.varbit } + TempleGeometry.gapVarbits.values +
                TempleGeometry.crosses.keys + TempleGeometry.doors.map { it.varbit }

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

/** A death or catalytic talisman or tiara carried or worn, or a cape that opens every altar worn. */
internal fun Player.hasAltarAccess(): Boolean =
    MourningsEndPart2Quest.ALTAR_KEYS.any { it in inv || it in worn } || MourningsEndPart2Quest.ALTAR_CAPES.any { it in worn }
