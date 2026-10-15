package org.rsmod.content.quest.area.mortton.shades

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shades of Mort'ton.
 *
 * The stage lives in the whole of `varp.morttonquest` (339), which carries no varbits and drives
 * no multi npcs or locs, so the values follow RuneLite's quest helper: 5 once the diary is read,
 * 10 with a serum made, 15 when Razmire asks for five shades and 5 more per shade slain up to 40,
 * 45 once Razmire has his remains, 47 once Ulsquire has seen them, 50 when he has explained the
 * temple, 55 with his olive oil in hand, 60 once the sacred flame is lit, 65 with sacred oil, 70
 * with pyre logs, 75 once they are on a pyre, 80 when a shade has been cremated, and the
 * endstate [STAGE_COMPLETE].
 *
 * Razmire and Ulsquire are not cache multis: a dose of serum transforms the npc for everyone for
 * a while. Their cures are mirrored into `varp.morttonmulti` (340) the way the client's quest
 * helpers read it: bit 1 / 3 while Ulsquire / Razmire are temporarily cured, bit 5 / 6 once
 * Serum 208 has cured them for good.
 */
@Singleton
class ShadesOfMorttonQuest : QuestScript(
    "quest_shadesofmortton",
    "varp.morttonquest",
    rewards {
        xp("stat.herblore", HERBLORE_XP)
        xp("stat.crafting", CRAFTING_XP)
        extra("Shades of Mort'ton minigame")
    },
    ItemRewardDisplay(SERUM_207[3]),
) {
    val askedAboutRemains = quest.attribute(name = "ASKED_REMAINS", default = false)
    val askedAboutTemple = quest.attribute(name = "ASKED_TEMPLE", default = false)
    val searchedTable = quest.attribute(name = "SEARCHED_TABLE", default = false)
    val apothecaryBonus = quest.attribute(name = "APOTHECARY_BONUS", default = false)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun meetsRequirements(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, PRIEST_IN_PERIL)

    fun shadesKilled(player: Player): Int =
        ((stage(player) - STAGE_KILL_SHADES) / STAGE_PER_SHADE).coerceIn(0, SHADES_NEEDED)

    fun isCured(player: Player, bit: Int): Boolean = player.morttonMulti and (1 shl bit) != 0

    fun setCured(player: Player, bit: Int, cured: Boolean) {
        player.morttonMulti =
            if (cured) player.morttonMulti or (1 shl bit) else player.morttonMulti and (1 shl bit).inv()
    }

    override fun subTitle(): String =
        "searching the shelf in the south-western house of <col=800000>Mort'ton</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "I found the diary of <red>Herbi Flax</red>, who was working on a cure for the " +
                    "affliction of Mort'ton. It describes <red>Serum 207</red>: ashes added to a " +
                    "<red>tarromin potion (unf)</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_READ_DIARY }
            }

            objective(
                "I've made some Serum 207. I should try it on one of the afflicted townsfolk, " +
                    "perhaps <red>Razmire Keelgan</red> in the general store.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MADE_SERUM }
            }

            objective(
                "Razmire wants me to kill five <red>Loar Shades</red> around Mort'ton and bring " +
                    "him their <red>remains</red>.",
            ) {
                visibleWhen { stage(access.player) in STAGE_KILL_SHADES until STAGE_ALL_SHADES }
                custom(true, "I have slain ${shadesKilled(access.player)} of $SHADES_NEEDED shades.")
            }

            objective("I've slain five shades. I should take the remains back to Razmire.") {
                visibleWhen { stage(access.player) == STAGE_ALL_SHADES }
            }

            objective(
                "Razmire took two of the remains and thinks <red>Ulsquire Shauncy</red>, next " +
                    "door, might like to see one.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GAVE_RAZMIRE }
            }

            objective("Ulsquire is studying the remains. I should ask him what he found out.") {
                visibleWhen { stage(access.player) == STAGE_SHOWN_ULSQUIRE }
                attribute(askedAboutRemains, "He wants to cremate the shades with pyre logs.", strike = true)
                attribute(askedAboutTemple, "He told me about the temple of Flamtaer.", strike = true)
            }

            objective(
                "Ulsquire believes the ruined temple of <red>Flamtaer</red>, north-east of " +
                    "Mort'ton, could sanctify oil. I need <red>limestone bricks</red>, " +
                    "<red>timber beams</red>, <red>swamp paste</red> and a <red>hammer</red> to " +
                    "rebuild it, then I should light its fire altar.",
            ) {
                visibleWhen { stage(access.player) in STAGE_TOLD_OF_TEMPLE until STAGE_LIT_ALTAR }
            }

            objective(
                "The sacred flame burns. With enough sanctity I can use <red>olive oil</red> on " +
                    "it to make <red>sacred oil</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LIT_ALTAR }
            }

            objective(
                "I have sacred oil. Ulsquire says the ancients used it on <red>logs</red> to make " +
                    "<red>pyre logs</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SACRED_OIL }
            }

            objective(
                "I should place my pyre logs on one of the <red>funeral pyres</red> south-west " +
                    "of Mort'ton, add some <red>Loar remains</red> and light it.",
            ) {
                visibleWhen { stage(access.player) in STAGE_PYRE_LOGS until STAGE_CREMATED }
            }

            objective("I have laid a shade to rest. I should tell Ulsquire.") {
                visibleWhen { stage(access.player) == STAGE_CREMATED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I found Herbi Flax's diary in Mort'ton and made Serum 207, which briefly cured " +
                    "Razmire Keelgan and Ulsquire Shauncy of their affliction.",
            )
            line(
                "I slew five Loar Shades for Razmire, then helped rebuild the temple of Flamtaer " +
                    "and sanctified oil in its sacred flame.",
            )
            line("With pyre logs I cremated a shade and set its spirit free.")
        }

    companion object {
        const val STAGE_READ_DIARY = 5
        const val STAGE_MADE_SERUM = 10
        const val STAGE_KILL_SHADES = 15
        const val STAGE_PER_SHADE = 5
        const val STAGE_ALL_SHADES = 40
        const val STAGE_GAVE_RAZMIRE = 45
        const val STAGE_SHOWN_ULSQUIRE = 47
        const val STAGE_TOLD_OF_TEMPLE = 50
        const val STAGE_GOT_OLIVE_OIL = 55
        const val STAGE_LIT_ALTAR = 60
        const val STAGE_SACRED_OIL = 65
        const val STAGE_PYRE_LOGS = 70
        const val STAGE_LOGS_ON_PYRE = 75
        const val STAGE_CREMATED = 80
        const val STAGE_COMPLETE = 85

        const val SHADES_NEEDED = 5
        const val HERBLORE_XP = 2000.0
        const val CRAFTING_XP = 2000.0
        const val APOTHECARY_XP = 335.0

        const val PRIEST_IN_PERIL = "quest_priestinperil"

        const val ULSQUIRE_TEMP_BIT = 1
        const val RAZMIRE_TEMP_BIT = 3
        const val ULSQUIRE_PERM_BIT = 5
        const val RAZMIRE_PERM_BIT = 6

        const val DIARY = "obj.serum_book"
        const val LOAR_REMAINS = "obj.shade_bones1"
        const val ASHES = "obj.ashes"
        const val TARROMIN_UNF = "obj.tarrominvial"
        const val ASHES_VIAL = "obj.ashesvial"
        const val VIAL_WATER = "obj.vial_water"
        const val VIAL_EMPTY = "obj.vial_empty"
        const val TARROMIN = "obj.tarromin"
        const val TINDERBOX = "obj.tinderbox"
        const val OLIVE_OIL_3 = "obj.oliveoil3"

        /** Indexed by dose count minus one. */
        val SERUM_207 = listOf("obj.mort_serum1", "obj.mort_serum2", "obj.mort_serum3", "obj.mort_serum4")
        val SERUM_208 =
            listOf("obj.mort_serum_perm1", "obj.mort_serum_perm2", "obj.mort_serum_perm3", "obj.mort_serum_perm4")
        val OLIVE_OIL = listOf("obj.oliveoil1", "obj.oliveoil2", "obj.oliveoil3", "obj.oliveoil4")
        val SACRED_OIL = listOf("obj.sacred_oil1", "obj.sacred_oil2", "obj.sacred_oil3", "obj.sacred_oil4")

        const val RAZMIRE = "npc.razmire_keelgan"
        const val RAZMIRE_AFFLICTED = "npc.razmire_keelgan_afflicted"
        const val ULSQUIRE = "npc.ulsquire_shauncy"
        const val ULSQUIRE_AFFLICTED = "npc.ulsquire_shauncy_afflicted"
        const val LOAR_SHADOW = "npc.shadeshadow_level1"
        const val LOAR_SHADE = "npc.shade_level1"
    }
}

internal var Player.morttonMulti: Int by intVarp("varp.morttonmulti")

internal object MorttonCoords {
    val SHELF = CoordGrid(3481, 3279, 0)
    val SMASHED_TABLE = CoordGrid(3482, 3278, 0)
    val FIRE_ALTAR = CoordGrid(3506, 3316, 0)

    /** The temple's ruined walls ring the fire altar two tiles out on every side. */
    fun inTemple(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3500..3512 && coords.z in 3310..3322

    fun inTown(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3456..3519 && coords.z in 3264..3327
}
