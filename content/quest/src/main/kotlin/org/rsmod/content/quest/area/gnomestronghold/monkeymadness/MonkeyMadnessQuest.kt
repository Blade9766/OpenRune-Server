package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Monkey Madness I.
 *
 * The stage lives in `varp.mm_main` (endstate 9 from `dbrow.quest_monkeymadness1`); no cache
 * varbit shares that varp, so the quest manager's writes clobber nothing. The per-gnome progress
 * the client's npc multis read (Daero, Waydar and Lumdo grow a Travel option) lives in the varbits
 * on `varp.mm_gnomes`, which are mirrored from quest attributes by [syncVars] so a reset or relog
 * puts everything back.
 */
@Singleton
class MonkeyMadnessQuest : QuestScript(
    "quest_monkeymadness1",
    "varp.mm_main",
    rewards {
        item(COINS, COIN_REWARD)
        item(DIAMOND, DIAMOND_REWARD)
        extra("Ability to wield dragon scimitars, greegrees and the M'speak amulet")
    },
    ItemRewardDisplay(KARAMJAN_GREEGREE),
    Quest.QUEST_COMPLETE_2_JINGLE,
) {
    val hangarVisited = quest.attribute(name = "HANGAR_VISITED", default = false)
    val glidersReady = quest.attribute(name = "GLIDERS_READY", default = false)
    val lumdoRefused = quest.attribute(name = "LUMDO_REFUSED", default = false)
    val lumdoOrdered = quest.attribute(name = "LUMDO_ORDERED", default = false)
    val reachedApeAtoll = quest.attribute(name = "REACHED_APE_ATOLL", default = false)

    /** Piece order of the reinitialisation puzzle, 25 letters with `.` for the gap; empty until started. */
    val puzzle = quest.attribute(name = "PUZZLE", default = "")

    val metLumo = quest.attribute(name = "MET_LUMO", default = false)
    val metKaram = quest.attribute(name = "MET_KARAM", default = false)
    val metGarkor = quest.attribute(name = "MET_GARKOR", default = false)

    /** How far Zooknock's work has come; see the `ZOOKNOCK_*` constants. */
    val zooknockStage = quest.attribute(name = "ZOOKNOCK", default = ZOOKNOCK_NOT_MET)
    val bananasGiven = quest.attribute(name = "BANANAS_GIVEN", default = 0)
    val talismanLent = quest.attribute(name = "TALISMAN_LENT", default = false)
    val talismanLost = quest.attribute(name = "TALISMAN_LOST", default = false)

    /** How far the audience with Awowogei has come; see the `AWOWOGEI_*` constants. */
    val awowogeiStage = quest.attribute(name = "AWOWOGEI", default = AWOWOGEI_NOT_MET)
    val elderGuardSpoken = quest.attribute(name = "ELDER_GUARD", default = false)
    val sigilGiven = quest.attribute(name = "SIGIL_GIVEN", default = false)
    val seenPlot = quest.attribute(name = "SEEN_PLOT", default = false)
    val trainingClaimed = quest.attribute(name = "TRAINING_CLAIMED", default = false)
    val demonSlain = quest.attribute(name = "DEMON_SLAIN", default = false)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] if it is not already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun syncVars(player: Player) {
        val stage = stage(player)
        VarPlayerIntMapSetter.set(player, "varbit.mm_narnode", stage.coerceAtMost(NARNODE_VARBIT_MAX))
        VarPlayerIntMapSetter.set(player, "varbit.mm_daero", if (hangarVisited.get(player)) DAERO_TRAVEL else 0)
        VarPlayerIntMapSetter.set(player, "varbit.mm_waydar", if (glidersReady.get(player)) WAYDAR_TRAVEL else 0)
        VarPlayerIntMapSetter.set(player, "varbit.mm_lumdo", if (lumdoOrdered.get(player)) LUMDO_TRAVEL else 0)
        VarPlayerIntMapSetter.set(player, "varbit.mm_garkor", if (metGarkor.get(player)) 1 else 0)
        VarPlayerIntMapSetter.set(player, "varbit.mm_zooknock", zooknockStage.get(player))
        VarPlayerIntMapSetter.set(player, "varbit.mm_karam", if (metKaram.get(player)) 1 else 0)
        VarPlayerIntMapSetter.set(player, "varbit.mm_lumo", if (metLumo.get(player)) 1 else 0)
    }

    fun hasGreegree(player: Player): Boolean = GREEGREES.any { player.inv.contains(it) || player.worn.contains(it) }

    /** The journal DSL takes obj names without their `obj.` prefix. */
    private fun String.bare(): String = removePrefix("obj.")

    override fun subTitle(): String =
        "talking to <col=800000>King Narnode Shareen</col> on the ground floor of the " +
            "<col=800000>Grand Tree</col>, in the Tree Gnome Stronghold."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>King Narnode</red> has lost contact with the <red>10th squad</red> he sent to " +
                    "close down Glough's shipyard on Karamja.",
            ) {}

            objective(
                "I should take the <red>gnome royal seal</red> to the shipyard south of the Karamja " +
                    "glider and find out what happened to the squad.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                hasItem(ROYAL_SEAL.bare(), "I have the royal seal.").strike()
            }

            objective(
                "<red>G.L.O. Caranock</red> says the squad's gliders were blown off course by the " +
                    "wind. I should report back to <red>King Narnode</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CARANOCK_MET }
            }

            objective(
                "The King has given me <red>orders</red> for <red>Daero</red>, his new head tree " +
                    "guardian, who can be found on the first floor of the Grand Tree by the Blurberry Bar.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_ORDERS }
                hasItem(NARNODE_ORDERS.bare(), "I have Narnode's orders.").strike()
            }

            objective(
                "Daero has taken me to a secret <red>glider hangar</red>. <red>Waydar</red> needs the " +
                    "gliders reinitialised before he can fly me south.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HANGAR && !glidersReady.get(access.player) }
            }

            objective(
                "The gliders are ready. <red>Waydar</red> will fly me south to find the 10th squad.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HANGAR && glidersReady.get(access.player) }
            }

            objective(
                "The squad crashed on an island east of a large atoll full of monkeys. <red>Lumdo</red> " +
                    "can row me across, but he wants an order from <red>Waydar</red> first.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CRASH_ISLAND }
                attribute(lumdoOrdered, "Waydar has ordered Lumdo to take me to the atoll.", finalise = false).preserveObjective().strike()
            }

            objective(
                "I am on <red>Ape Atoll</red>. The monkeys are not friendly. I should find <red>Sergeant " +
                    "Garkor</red> and the rest of the 10th squad.",
            ) {
                visibleWhen { stage(access.player) == STAGE_APE_ATOLL && !metGarkor.get(access.player) }
                attribute(metLumo, "Lumo is locked up in the monkey jail with Bunkdo and Carado.", finalise = false).preserveObjective().strike()
                attribute(metKaram, "Karam is hiding in the long grass of the village.", finalise = false).preserveObjective().strike()
            }

            objective(
                "Garkor wants me to become a monkey. <red>Zooknock</red>, at the end of the dungeon " +
                    "under the south of the island, can make a <red>M'speak amulet</red> from a " +
                    "<red>gold bar</red>, a <red>m'amulet mould</red> and <red>monkey dentures</red> " +
                    "from the crates in Marim's warehouse.",
            ) {
                visibleWhen { stage(access.player) == STAGE_APE_ATOLL && metGarkor.get(access.player) && zooknockStage.get(access.player) < ZOOKNOCK_BAR_GIVEN }
                hasItem(MOULD.bare(), "I have the m'amulet mould.").strike()
                hasItem(DENTURES.bare(), "I have the monkey dentures.").strike()
                hasItem(GOLD_BAR.bare(), "I have a gold bar.").strike()
            }

            objective(
                "Zooknock enchanted my gold bar. I should smith it in the <red>m'amulet mould</red> " +
                    "somewhere holy to the monkeys: the <red>wall of flames</red> under the temple " +
                    "trapdoor should do. Then I need a <red>ball of wool</red> to string the amulet.",
            ) {
                visibleWhen { stage(access.player) == STAGE_APE_ATOLL && zooknockStage.get(access.player) == ZOOKNOCK_BAR_GIVEN }
                hasItem(ENCHANTED_BAR.bare(), "I have the enchanted bar.").strike()
                hasItem(UNSTRUNG_AMULET.bare(), "I have an unstrung M'speak amulet.").strike()
                hasItem(AMULET.bare(), "I have a M'speak amulet.").strike()
            }

            objective(
                "To make a <red>greegree</red> Zooknock needs a <red>monkey talisman</red> and some " +
                    "<red>monkey bones</red>. The <red>Monkey Child</red> in the banana plantation " +
                    "west of the jail has a talisman; his aunt must not see me.",
            ) {
                visibleWhen { stage(access.player) == STAGE_APE_ATOLL && zooknockStage.get(access.player) == ZOOKNOCK_AMULET_EXPLAINED }
                hasItem(TALISMAN.bare(), "I have a monkey talisman.").strike()
                hasItem(MONKEY_BONES.bare(), "I have some monkey bones.").strike()
            }

            objective(
                "I can turn into a monkey with my <red>greegree</red>. With the <red>M'speak amulet</red> " +
                    "on I should talk to <red>Garkor</red> again.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MONKEY && awowogeiStage.get(access.player) == AWOWOGEI_NOT_MET && !elderGuardSpoken.get(access.player) }
            }

            objective(
                "The <red>Elder Guards</red> won't let a common monkey in to see King Awowogei. " +
                    "<red>Kruk</red>, at the watchtowers west of the town, might.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MONKEY && awowogeiStage.get(access.player) == AWOWOGEI_NOT_MET && elderGuardSpoken.get(access.player) }
            }

            objective(
                "<red>Awowogei</red> will consider an alliance if I free one of his people from the " +
                    "<red>Ardougne Zoo</red>. As a monkey I should speak to the <red>Monkey Minder</red> " +
                    "there. I must walk back; teleporting would frighten the monkey out of my pack.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MONKEY && awowogeiStage.get(access.player) == AWOWOGEI_TASK_GIVEN }
                hasItem(MONKEY_IN_BACKPACK.bare(), "I have a monkey in my backpack.").strike()
            }

            objective(
                "Awowogei is pleased with his freed subject. <red>Garkor</red> wants to hear what I learned.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ALLIANCE && !sigilGiven.get(access.player) }
            }

            objective(
                "Glough and Awowogei have raised a <red>Jungle Demon</red>. Garkor gave me the " +
                    "<red>10th squad sigil</red>; when I wear it Zooknock will teleport me and the " +
                    "squad straight to the demon. I should prepare for a hard fight first.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ALLIANCE && sigilGiven.get(access.player) && !demonSlain.get(access.player) }
                hasItem(SIGIL.bare(), "I have the 10th squad sigil.").strike()
            }

            objective("The Jungle Demon is dead. I should tell <red>King Narnode</red> what happened.") {
                visibleWhen { stage(access.player) == STAGE_ALLIANCE && demonSlain.get(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Narnode sent me after his missing 10th squad. G.L.O. Caranock at the shipyard " +
                    "blamed the wind, so the King had Daero fly me south from a secret hangar with " +
                    "Flight Commander Waydar.",
            )
            line(
                "The squad had crashed on Crash Island and Sergeant Garkor's men were held on Ape " +
                    "Atoll. With Zooknock's help I made a M'speak amulet and a greegree and walked " +
                    "among the monkeys as one of them.",
            )
            line(
                "King Awowogei made me free a monkey from the Ardougne Zoo, but he was plotting with " +
                    "Glough all along. Wearing the 10th squad sigil took us to their Jungle Demon and " +
                    "we destroyed it.",
            )
            line("King Narnode rewarded me, and Daero offered to train me in the ways of the gnome military.")
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_CARANOCK_MET = 2
        const val STAGE_HAS_ORDERS = 3
        const val STAGE_HANGAR = 4
        const val STAGE_CRASH_ISLAND = 5
        const val STAGE_APE_ATOLL = 6
        const val STAGE_MONKEY = 7
        const val STAGE_ALLIANCE = 8
        const val STAGE_COMPLETE = 9

        const val ZOOKNOCK_NOT_MET = 0
        const val ZOOKNOCK_MET = 1
        const val ZOOKNOCK_BAR_GIVEN = 2
        const val ZOOKNOCK_AMULET_EXPLAINED = 3
        const val ZOOKNOCK_GREEGREE_MADE = 4

        const val AWOWOGEI_NOT_MET = 0
        const val AWOWOGEI_TASK_GIVEN = 1
        const val AWOWOGEI_MONKEY_FREED = 2

        /** Values the npc multis on `varp.mm_gnomes` switch on. */
        const val NARNODE_VARBIT_MAX = 15
        const val DAERO_TRAVEL = 4
        const val WAYDAR_TRAVEL = 1
        const val LUMDO_TRAVEL = 3

        const val COIN_REWARD = 10_000
        const val DIAMOND_REWARD = 3
        const val TRAINING_XP_MAJOR = 35_000.0
        const val TRAINING_XP_MINOR = 20_000.0
        const val RECOMMENDED_COMBAT = 65
        const val GLOUGH_FEE = 200_000

        const val ROYAL_SEAL = "obj.mm_gnome_royal_seal"
        const val NARNODE_ORDERS = "obj.mm_narnode_orders"
        const val SPARE_CONTROLS = "obj.mm_reinitialisation_hint"
        const val DENTURES = "obj.mm_monkey_dentures"
        const val MOULD = "obj.mm_monkey_amulet_mould"
        const val GOLD_BAR = "obj.gold_bar"
        const val ENCHANTED_BAR = "obj.mm_enchanted_gold_bar"
        const val UNSTRUNG_AMULET = "obj.mm_amulet_of_monkey_speak_without_string"
        const val AMULET = "obj.mm_amulet_of_monkey_speak"
        const val BALL_OF_WOOL = "obj.ball_of_wool"
        const val TALISMAN = "obj.mm_monkey_talisman"
        const val MONKEY_BONES = "obj.mm_normal_monkey_bones"
        const val MONKEY_SKULL = "obj.mm_ancient_monkey_skull"
        const val KARAMJAN_GREEGREE = "obj.mm_monkey_greegree_for_normal_monkey"
        const val MONKEY_IN_BACKPACK = "obj.mm_monkey_in_backpack"
        const val SIGIL = "obj.mm_sigil"
        const val BANANA = "obj.banana"
        const val COINS = "obj.coins"
        const val DIAMOND = "obj.diamond"
        const val EYE_OF_GNOME = "obj.mm_eye_of_gnome"
        const val MONKEY_NUTS = "obj.mm_monkey_nuts"
        const val MONKEY_BAR = "obj.mm_monkey_bar"
        const val BANANA_STEW = "obj.mm_banana_stew"
        const val MONKEY_WRENCH = "obj.mm_monkey_wrench"

        val GREEGREES =
            listOf(
                "obj.mm_monkey_greegree_for_normal_monkey",
                "obj.mm_monkey_greegree_for_small_ninja_monkey",
                "obj.mm_monkey_greegree_for_medium_ninja_monkey",
                "obj.mm_monkey_greegree_for_normal_gorilla",
                "obj.mm_monkey_greegree_for_bearded_gorilla",
                "obj.mm_monkey_greegree_for_ancient_monkey_skull",
                "obj.mm_monkey_greegree_for_small_zombie_monkey",
                "obj.mm_monkey_greegree_for_large_zombie_monkey",
            )

        const val NARNODE = "npc.grandtree_narnode"
        const val NARNODE_HEAD = "npc.grandtree_narnode_1op"
        const val CARANOCK = "npc.mm_caranock"
        const val DAERO = "npc.mm_daero"
        const val DAERO_HEAD = "npc.mm_daero_1op"
        const val WAYDAR = "npc.mm_waydar"
        const val WAYDAR_HEAD = "npc.mm_waydar_1op"
        const val LUMDO = "npc.mm_lumdo"
        const val LUMDO_HEAD = "npc.mm_lumdo_1op"
        const val HANGAR_GLOUGH = "npc.grandtree_glough_visible"
        const val GARKOR = "npc.mm_garkor"
        const val GARKOR_HEAD = "npc.mm_garkor_aa"
        const val KARAM = "npc.mm_karam"
        const val KARAM_TALKER = "npc.mm_karam_talker"
        const val KARAM_HEAD = "npc.mm_karam_aa1"
        const val LUMO = "npc.mm_lumo"
        const val LUMO_HEAD = "npc.mm_lumo_aa"
        const val BUNKDO = "npc.mm_bunkdo"
        const val CARADO = "npc.mm_carado"
        const val ZOOKNOCK = "npc.mm_zooknock"
        const val ZOOKNOCK_HEAD = "npc.mm_zooknock_aa"
        const val WAYMOTTIN = "npc.mm_waymottin"
        const val BUNKWICKET = "npc.mm_bunkwicket"
        const val TREFAJI = "npc.mm_trefaji"
        const val ABERAB = "npc.mm_aberab"
        const val MONKEY_CHILD = "npc.mm_monkey_child"
        const val MONKEYS_AUNT = "npc.mm_monkeys_aunt"
        const val ELDER_GUARD_1 = "npc.mm_elder_guard_1"
        const val ELDER_GUARD_2 = "npc.mm_elder_guard_2"
        const val KRUK = "npc.mm_kruk_multi"
        const val KRUK_HEAD = "npc.mm_kruk"
        const val AWOWOGEI_HEAD = "npc.mm_awowogei_cutscene"
        const val MONKEY_MINDER = "npc.mm_monkey_minder"
        const val ZOO_MONKEY = "npc.mm_zoo_monkey"
        const val SLEEPING_GUARD = "npc.mm_sleeping_monkey_guard"
        const val BONZARA = "npc.mm_bonzara"
        const val JUNGLE_DEMON = "npc.mm_demon"
        const val UWOGO = "npc.mm_uwogo"
        const val MUROWOI = "npc.mm_murowoi"
        const val LOFU = "npc.mm_lofu"
        const val DENADU = "npc.mm_denadu"
        const val HAFUBA = "npc.mm_hafuba"
        const val PADULAH = "npc.mm_padulah"

        const val THRONE = "loc.mm_throne"
    }
}
