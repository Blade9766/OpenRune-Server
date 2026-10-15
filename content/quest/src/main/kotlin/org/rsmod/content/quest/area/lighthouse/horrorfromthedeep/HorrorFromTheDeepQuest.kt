package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Horror from the Deep.
 *
 * The stage is `varbit.horrorquest` (bits 0-10 of `varp.deephorror` 351); the rest of the varp
 * holds one bit per task (bridge planks, Gunnjorn's key, the unlocked front door, the three
 * lighting repairs, the six items in the strange wall and the opened wall), so the quest manager
 * is given the varbit and every task bit is kept in a [HorrorFlag] attribute and mirrored by
 * [syncVars]. The stage values follow the client's quest helper: 1 started, 2 front door
 * unlocked, 3 briefed by Larrissa inside, 4 light repaired, 5 the young dagannoth slain, and
 * 10 complete (`loc.horror_ladder_top2` shows its post-quest form at 10).
 */
@Singleton
class HorrorFromTheDeepQuest : QuestScript(
    "quest_horrorfromthedeep",
    "varp.deephorror",
    rewards {
        xp("stat.magic", REWARD_XP)
        xp("stat.strength", REWARD_XP)
        xp("stat.ranged", REWARD_XP)
        extra("A damaged prayer book")
    },
    ItemRewardDisplay(CASKET),
    questVarbit = "varbit.horrorquest",
) {
    private val flags: Map<HorrorFlag, QuestAttribute<Boolean>> =
        HorrorFlag.entries.associateWith { quest.attribute(name = "FLAG_${it.name}", default = false) }

    /** God books Jossik has handed over or sold, one bit per [GodBook] ordinal. */
    val unlockedBooks = quest.attribute(name = "UNLOCKED_BOOKS", default = 0)

    override fun ScriptContext.init() {
        onPlayerLogin { syncVars(player) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    operator fun get(player: Player, flag: HorrorFlag): Boolean = flags.getValue(flag).get(player)

    fun set(player: Player, flag: HorrorFlag) {
        flags.getValue(flag).set(player, true)
        syncVars(player)
    }

    fun bridgeRepaired(player: Player): Boolean =
        this[player, HorrorFlag.BridgeLeft] && this[player, HorrorFlag.BridgeRight]

    fun lightRepaired(player: Player): Boolean =
        this[player, HorrorFlag.Tar] && this[player, HorrorFlag.Glass] && this[player, HorrorFlag.Light]

    fun wallFilled(player: Player): Boolean = WALL_ITEMS.all { this[player, it] }

    fun wallStarted(player: Player): Boolean = WALL_ITEMS.any { this[player, it] }

    fun syncVars(player: Player) {
        for (flag in HorrorFlag.entries) {
            val value = if (flags.getValue(flag).get(player)) 1 else 0
            if (player.vars[flag.varbit] != value) {
                VarPlayerIntMapSetter.set(player, flag.varbit, value)
            }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Larrissa</col> outside the <col=800000>Lighthouse</col>, north " +
            "of the <col=800000>Barbarian Outpost</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Larrissa</red> is worried sick about her boyfriend <red>Jossik</red>, the " +
                    "lighthouse keeper. The light has gone out and the front door is locked. She " +
                    "is stranded on the causeway because a storm wrecked the bridge to the east.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                custom(
                    bridgeRepaired(access.player),
                    "I have laid a plank on each side of the broken bridge so she can cross.",
                ).strike()
                custom(
                    access.player.inv.contains(KEY) || this@HorrorFromTheDeepQuest[access.player, HorrorFlag.GotKey],
                    "Her cousin <red>Gunnjorn</red> gave me the spare lighthouse key.",
                ).strike()
            }

            objective(
                "To mend the bridge I need two <red>planks</red>, a <red>hammer</red> and sixty " +
                    "<red>steel nails</red>. Gunnjorn likes agility, so he may be found at an " +
                    "agility course.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED && !bridgeRepaired(access.player) }
            }

            objective("The bridge is mended and I have the key. I should unlock the lighthouse.") {
                visibleWhen {
                    stage(access.player) == STAGE_STARTED &&
                        bridgeRepaired(access.player) &&
                        access.player.inv.contains(KEY)
                }
            }

            objective(
                "The lighthouse is unlocked. Larrissa wants to go inside with me and find out " +
                    "what happened to Jossik.",
            ) {
                visibleWhen { stage(access.player) == STAGE_UNLOCKED }
            }

            objective(
                "The inside of the lighthouse has been torn apart. Before anything else I must " +
                    "repair the <red>lighting mechanism</red> at the top so no ships run aground. " +
                    "Jossik may have left a manual lying around.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BRIEFED }
                custom(this@HorrorFromTheDeepQuest[access.player, HorrorFlag.Tar], "I spread <red>swamp tar</red> on the torch.").strike()
                custom(this@HorrorFromTheDeepQuest[access.player, HorrorFlag.Light], "I lit the torch with a <red>tinderbox</red>.").strike()
                custom(this@HorrorFromTheDeepQuest[access.player, HorrorFlag.Glass], "I mended the lens with <red>molten glass</red>.").strike()
            }

            objective(
                "The light is working again. A strange metal wall blocks the basement, carved " +
                    "with four runes, a sword and an arrow. Jossik's uncle wrote that these are the " +
                    "key to it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LIGHT_FIXED && !wallFilled(access.player) }
                for ((flag, label) in WALL_ITEM_LABELS) {
                    custom(this@HorrorFromTheDeepQuest[access.player, flag], "I placed $label in the wall.").strike()
                }
            }

            objective(
                "The strange wall has opened. Whatever happened to Jossik lies somewhere beyond it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LIGHT_FIXED && wallFilled(access.player) }
            }

            objective(
                "I found Jossik alive but badly hurt in the caves beneath the lighthouse. I killed " +
                    "the dagannoth that attacked us, but Jossik says it was only a baby. Its " +
                    "<red>mother</red> is still down there.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DAGANNOTH_SLAIN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Larrissa asked me to find her boyfriend Jossik, keeper of the Lighthouse north " +
                    "of the Barbarian Outpost. I mended the broken bridge and fetched the spare " +
                    "key from her cousin Gunnjorn.",
            )
            line(
                "Inside, I repaired the lighting mechanism and opened the strange wall in the " +
                    "basement with runes, a sword and an arrow.",
            )
            line(
                "In the caves below I found Jossik and survived a dagannoth and its mother, a " +
                    "horror that changed colour to resist my attacks. From her remains I took an " +
                    "old rusty casket.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_UNLOCKED = 2
        const val STAGE_BRIEFED = 3
        const val STAGE_LIGHT_FIXED = 4
        const val STAGE_DAGANNOTH_SLAIN = 5

        const val REWARD_XP = 4662.5

        const val KEY = "obj.horror_key"
        const val CASKET = "obj.horror_casket"
        const val JOURNAL = "obj.horror_diary1"
        const val DIARY = "obj.horror_diary2"
        const val MANUAL = "obj.horror_diary3"
        const val PLANK = "obj.woodplank"
        const val NAILS = "obj.nails"
        const val HAMMER = "obj.hammer"
        const val SWAMP_TAR = "obj.swamp_tar"
        const val MOLTEN_GLASS = "obj.molten_glass"
        const val TINDERBOX = "obj.tinderbox"

        const val LARRISSA = "npc.horror_girlfriend_prequest"
        const val LARRISSA_INSIDE = "npc.horror_girlfriend_postquest"
        const val JOSSIK = "npc.horror_lighthousekeeeper_well"
        const val JOSSIK_INJURED = "npc.horror_lighthousekeeeper_injured"
        const val GUNNJORN = "npc.gunnjorn"
        const val DAGANNOTH = "npc.horror_dagannoth_jr4"
        const val MOTHER = "npc.horror_dagganoth_air"

        val WALL_ITEMS =
            listOf(
                HorrorFlag.AirRune,
                HorrorFlag.WaterRune,
                HorrorFlag.EarthRune,
                HorrorFlag.FireRune,
                HorrorFlag.Sword,
                HorrorFlag.Arrow,
            )

        private val WALL_ITEM_LABELS =
            listOf(
                HorrorFlag.AirRune to "an <red>air rune</red>",
                HorrorFlag.WaterRune to "a <red>water rune</red>",
                HorrorFlag.EarthRune to "an <red>earth rune</red>",
                HorrorFlag.FireRune to "a <red>fire rune</red>",
                HorrorFlag.Sword to "a <red>sword</red>",
                HorrorFlag.Arrow to "an <red>arrow</red>",
            )
    }
}

enum class HorrorFlag(val varbit: String) {
    WallOpen("varbit.horrordoor"),
    BridgeLeft("varbit.horrorbridgeleft"),
    BridgeRight("varbit.horrorbridgeright"),
    GotKey("varbit.horroragilitykey"),
    FrontDoor("varbit.horrorlighthouseentrance"),
    FireRune("varbit.horrorfire"),
    WaterRune("varbit.horrorwater"),
    EarthRune("varbit.horrorearth"),
    AirRune("varbit.horrorair"),
    Sword("varbit.horrorsword"),
    Arrow("varbit.horrorarrow"),
    Tar("varbit.horrortar"),
    Glass("varbit.horrorglass"),
    Light("varbit.horrorlight"),
}

enum class GodBook(val god: String, val damaged: String, val complete: String) {
    Saradomin("Saradomin", "obj.unfinished_saradominbook", "obj.saradominbook_complete"),
    Zamorak("Zamorak", "obj.unfinished_zamorakbook", "obj.zamorakbook_complete"),
    Guthix("Guthix", "obj.unfinished_guthixbook", "obj.guthixbook_complete"),
    Armadyl("Armadyl", "obj.unfinished_armadylbook", "obj.armadylbook_complete"),
    Bandos("Bandos", "obj.unfinished_bandosbook", "obj.bandosbook_complete"),
    Zaros("Zaros", "obj.unfinished_zarosbook", "obj.zarosbook_complete"),
}
