package org.rsmod.content.quest.area.ikov

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Temple of Ikov.
 *
 * The stage lives in `varp.ikov` (26), which runs 0..80 from `dbrow.quest_templeofikov`. Nothing
 * in the cache pins the values in between, so they are multiples of ten: the quest is started,
 * then one of the two endings is chosen, then it is over.
 *
 * Which Lucien is standing where is the cache's business: `npc.ikov_lucien1` (the inn) and
 * `npc.ikov_lucien2` (the house) are both multi-npcs on [LUCIEN_VARBIT], so the script only ever
 * moves that varbit and the two of them appear and vanish on their own. The varbit sits on
 * `varp.dov_primary`, which Defender of Varrock will one day own; that quest is not in yet, and
 * whoever adds it has to leave bits 29-30 alone.
 *
 * Everything else the quest remembers - the disarmed trap, the fitted lever, the slain Fire
 * Warrior, Winelda's roots and which chest currently holds ice arrows - is a varbit on
 * `varp.ikov_state`, a server-only varp added for this plugin.
 */
@Singleton
class TempleOfIkovQuest : QuestScript(
    QUEST_KEY,
    "varp.ikov",
    rewards {
        xp("stat.ranged", RANGED_XP)
        xp("stat.fletching", FLETCHING_XP)
    },
    ItemRewardDisplay(ARMADYL_PENDANT, zoom = 130),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncLucien)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /**
     * Puts Lucien where the stage says he should be, and clears the quest's own flags when
     * `::resetquest` takes the stage back to zero.
     *
     * He stays at the house once the quest is over, whichever way it ended: the guardians' pendant
     * only laid him out for a while, and he has something to say about it.
     */
    private fun syncLucien(player: Player) {
        val stage = stage(player)
        val visibility =
            when {
                stage == STAGE_ARMADYL -> LUCIEN_HOUSE_ATTACKABLE
                stage >= STAGE_LUCIEN -> LUCIEN_HOUSE_TALKABLE
                else -> LUCIEN_AT_INN
            }
        setVarBit(player, LUCIEN_VARBIT, visibility)
        if (stage != 0) {
            return
        }
        player.ikovTrapDisarmed = false
        player.ikovTrapLeverPulled = false
        player.ikovFireWarriorSlain = false
        player.ikovWineldaPaid = false
        player.ikovLeverFitted = false
        player.ikovGateUnlocked = false
        player.ikovArrowChest = 0
        player.ikovSidedWithLucien = false
    }

    override fun subTitle(): String =
        "talking to <col=800000>Lucien</col> in the <col=800000>Flying Horse Inn</col>, in the " +
            "north-west corner of <col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Lucien</red> wants the <red>Staff of Armadyl</red> brought out of the " +
                    "<red>Temple of Ikov</red>, south of the <red>Ranging Guild</red>. He gave " +
                    "me a <red>pendant</red> to get past the guardians' magic, and told me to " +
                    "bring the staff to his house near the <red>Grand Exchange</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                custom(
                    access.player.ikovLeverFitted,
                    "I have fitted the <red>lever</red> to its bracket.",
                )
                    .strike()
                custom(
                    access.player.ikovFireWarriorSlain,
                    "The <red>Fire Warrior of Lesarkus</red> is dead. Only <red>ice arrows</red> " +
                        "could touch him.",
                )
                    .strike()
                custom(
                    access.player.ikovWineldaPaid,
                    "<red>Winelda</red> has had her twenty <red>limpwurt roots</red> and will " +
                        "throw me across the lava.",
                )
                    .strike()
            }

            objective(
                "The <red>Guardians of Armadyl</red> say Lucien is a <red>Mahjarrat</red> and " +
                    "must never hold the staff. They gave me an <red>Armadyl pendant</red>; " +
                    "wearing it, I am to kill him at his house near the <red>Grand Exchange</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ARMADYL }
            }

            objective(
                "I have the <red>Staff of Armadyl</red>. <red>Lucien</red> is waiting for it at " +
                    "his house near the <red>Grand Exchange</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LUCIEN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Lucien, drinking alone in the Flying Horse Inn, sent me into the Temple of Ikov " +
                    "south of the Ranging Guild for the Staff of Armadyl, and gave me a pendant " +
                    "to carry me through the Chamber of Fear.",
            )
            line(
                "I found the boots of lightness in the webbed room below the temple, crossed the " +
                    "lava bridge light enough not to fall, and carried the lever back to its " +
                    "bracket. The chests along the icy path gave up ice arrows, the only thing " +
                    "the Fire Warrior of Lesarkus could feel, and twenty limpwurt roots bought " +
                    "me Winelda's magic across the lava.",
            )
            if (access.player.ikovSidedWithLucien) {
                line(
                    "The last temple of Armadyl in Gielinor stood behind a pushed wall. I took " +
                        "the staff from under its guardians' noses and carried it to Lucien, who " +
                        "paid me and kept the power for himself.",
                )
            } else {
                line(
                    "The last temple of Armadyl in Gielinor stood behind a pushed wall. Its " +
                        "guardians named Lucien a Mahjarrat and gave me the Armadyl pendant, and " +
                        "I killed him with it at his house near the Grand Exchange.",
                )
            }
        }

    companion object {
        const val QUEST_KEY = "quest_templeofikov"

        const val STAGE_STARTED = 10

        /** Sided with the guardians: carrying the Armadyl pendant, Lucien has to die. */
        const val STAGE_ARMADYL = 20

        /** Sided with Lucien: carrying the staff to his house. */
        const val STAGE_LUCIEN = 30

        const val STAGE_COMPLETE = 80

        const val RANGED_XP = 10_500.0
        const val FLETCHING_XP = 8000.0

        /** Searching the lever for traps is the only Thieving in the quest. */
        const val THIEVING_REQ = 42

        /** Twenty roots, handed over in one go, is Winelda's price. */
        const val LIMPWURT_ROOTS = 20

        /** Anything heavier than this falls off the lava bridge. */
        const val BRIDGE_WEIGHT_LIMIT_GRAMS = -1000

        const val BRIDGE_DAMAGE = 20
        const val TRAP_DAMAGE = 20

        /**
         * Picks which Lucien exists: 0 the inn, 1 the house (talk only), 2 the house
         * (attackable), 3 neither. It is the cache's own multi-npc varbit for both of them.
         */
        const val LUCIEN_VARBIT = "varbit.ikov_lucien_vis"

        const val LUCIEN_AT_INN = 0
        const val LUCIEN_HOUSE_TALKABLE = 1
        const val LUCIEN_HOUSE_ATTACKABLE = 2

        /**
         * The two Luciens by their base multi-npc names. Ops, hits and the death queue all
         * arrive keyed on the base type, never on the `_vis` variant the client is drawing.
         */
        const val LUCIEN_INN_NPC = "npc.ikov_lucien1"
        const val LUCIEN_HOUSE_NPC = "npc.ikov_lucien2"
        const val WINELDA = "npc.ikov_winelda"
        const val FIRE_WARRIOR = "npc.ikov_firewarrior"
        const val GUARDIAN_MALE = "npc.ikov_guardianmale"
        const val GUARDIAN_FEMALE = "npc.ikov_guardianfemale"

        val GUARDIANS = listOf(GUARDIAN_MALE, GUARDIAN_FEMALE)

        const val PENDANT_OF_LUCIEN = "obj.ikov_pendantoflucien"
        const val ARMADYL_PENDANT = "obj.ikov_pendantofarmardyl"
        const val STAFF_OF_ARMADYL = "obj.ikov_staffofarmardyl"
        const val BOOTS_OF_LIGHTNESS = "obj.ikov_bootsoflightness"
        const val SHINY_KEY = "obj.ikov_shinykey"
        const val LEVER = "obj.ikov_lever"
        const val ICE_ARROWS = "obj.ice_arrow"
        const val LIMPWURT_ROOT = "obj.limpwurt_root"

        /** Sound ids from `dbrow.synth_ikov`. */
        const val SOUND_FIRE_TELEPORT = "synth.ikov_fire_teleport"
        const val SOUND_FOUND_ICE_ARROWS = "synth.ikov_found_ice_arrows"
        const val SOUND_LAVA_BRIDGE = "synth.ikov_lava_bridge"
        const val SOUND_LOCKED_DOOR = "synth.ikov_lockeddoor"
    }
}

/** Set once the trapped lever has been searched; the spikes only fire while it is clear. */
var Player.ikovTrapDisarmed by boolVarBit("varbit.ikov_trap_disarmed")

/** Set once the Fire Warrior of Lesarkus has been killed; the door he guards then opens. */
var Player.ikovFireWarriorSlain by boolVarBit("varbit.ikov_firewarrior_slain")

/** Set once the trapped lever has been pulled and the door west of the alcove is unbolted. */
var Player.ikovTrapLeverPulled by boolVarBit("varbit.ikov_trap_lever_pulled")

/** Set once Winelda has had her twenty limpwurt roots. */
var Player.ikovWineldaPaid by boolVarBit("varbit.ikov_winelda_paid")

/** Set once the lever from the storeroom has been fitted to its bracket. */
var Player.ikovLeverFitted by boolVarBit("varbit.ikov_lever_fitted")

/** Set once the fitted lever has been pulled and the gates onto the icy path stand open. */
var Player.ikovGateUnlocked by boolVarBit("varbit.ikov_gate_unlocked")

/** Which of the six chests along the icy path currently holds ice arrows, 0 to 5. */
var Player.ikovArrowChest by intVarBit("varbit.ikov_arrow_chest")

/** Set when the staff was carried to Lucien rather than the guardians being helped. */
var Player.ikovSidedWithLucien by boolVarBit("varbit.ikov_sided_with_lucien")

internal fun setVarBit(player: Player, varbit: String, value: Int) {
    if (player.vars[varbit] != value) {
        VarPlayerIntMapSetter.set(player, varbit, value)
    }
}
