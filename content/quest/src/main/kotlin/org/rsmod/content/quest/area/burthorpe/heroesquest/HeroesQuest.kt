package org.rsmod.content.quest.area.burthorpe.heroesquest

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Heroes' Quest.
 *
 * The stage is the whole cache varp `varp.heroquest`, endstate 15 from `dbrow.quest_heroes`. The
 * values follow the original script: [STAGE_STARTED] once Achietties sets the three tasks, then
 * one run of stages per Shield of Arrav gang for the Master Thief armband:
 * - Phoenix Gang: Straven's briefing [PHOENIX_BRIEFED], Alfonse's wink [PHOENIX_ALFONSE],
 *   Charlie's tour of the kitchen [PHOENIX_CHARLIE], Grip shot dead through the arrow slit
 *   [PHOENIX_KILLED_GRIP] and the armband from Straven [PHOENIX_ARMBAND].
 * - Black Arm Gang: Katrine's briefing [BLACKARM_BRIEFED], the password given at Grubor's door
 *   [BLACKARM_DOOR], Hartigen's papers from Trobert [BLACKARM_PAPERS], Garv letting "Hartigen" in
 *   [BLACKARM_MANSION], the papers handed to Grip [BLACKARM_REPORTED], the candlesticks looted
 *   [BLACKARM_LOOTED] and the armband from Katrine [BLACKARM_ARMBAND].
 *
 * The armband needs one player from each gang: the Black Arm deputy lures Grip to his drinks
 * cabinet, the Phoenix member kills him through the wall, and the deputy takes his keys to the
 * treasure room. The feather and the eel are solo and can be collected in any order.
 */
@Singleton
class HeroesQuest @Inject constructor(private val arrav: ShieldOfArravQuest) :
    QuestScript(
        QUEST_KEY,
        "varp.heroquest",
        rewards {
            xp("stat.attack", COMBAT_XP)
            xp("stat.defence", COMBAT_XP)
            xp("stat.strength", COMBAT_XP)
            xp("stat.hitpoints", COMBAT_XP)
            xp("stat.ranged", RANGED_XP)
            xp("stat.fishing", FISHING_XP)
            xp("stat.cooking", COOKING_XP)
            xp("stat.woodcutting", WOODCUTTING_XP)
            xp("stat.firemaking", FIREMAKING_XP)
            xp("stat.smithing", SMITHING_XP)
            xp("stat.mining", MINING_XP)
            xp("stat.herblore", HERBLORE_XP)
            scroll(
                "3,075 Attack, Strength, Defence",
                "and Hitpoints XP",
                "2,075 Ranged XP, 2,725 Fishing XP",
                "2,825 Cooking, 2,575 Mining XP",
                "Smithing, Herblore, Woodcutting, Firemaking XP",
                "Access to the Heroes' Guild",
            )
        },
        ItemRewardDisplay(DRAGON_BATTLEAXE, zoom = 190),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {
    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isInProgress(player: Player): Boolean = quest.isQuestInProgress(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isPhoenix(player: Player): Boolean = arrav.isPhoenix(player)

    fun isBlackArm(player: Player): Boolean = arrav.isBlackArm(player)

    /** Phoenix members who have reached [stage] on their gang's run, or finished the quest. */
    fun phoenixAt(player: Player, stage: Int): Boolean =
        isPhoenix(player) && (stage(player) in stage..PHOENIX_ARMBAND || isComplete(player))

    /** Black Arm members who have reached [stage] on their gang's run, or finished the quest. */
    fun blackArmAt(player: Player, stage: Int): Boolean =
        isBlackArm(player) && (stage(player) in stage..BLACKARM_ARMBAND || isComplete(player))

    fun meetsStartRequirements(player: Player): Boolean =
        player.questPoints >= REQUIRED_QUEST_POINTS && hasRequiredQuests(player)

    fun hasRequiredQuests(player: Player): Boolean =
        REQUIRED_QUESTS.all { QuestRequirements.hasCompleted(player, it) }

    fun hasRequiredSkills(player: Player): Boolean =
        player.cookingLvl >= REQUIRED_COOKING &&
            player.fishingLvl >= REQUIRED_FISHING &&
            player.herbloreLvl >= REQUIRED_HERBLORE &&
            player.miningLvl >= REQUIRED_MINING

    fun hasQuestPoints(player: Player): Boolean = player.questPoints >= REQUIRED_QUEST_POINTS

    fun start(access: ProtectedAccess) {
        if (!isStarted(access.player)) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    fun setStage(access: ProtectedAccess, stage: Int) {
        quest.setQuestStage(access, stage)
    }

    /** For callers without a protected access (kill hooks); never used for the final stage. */
    fun jumpTo(player: Player, stage: Int) {
        quest.jumpToStage(player, stage)
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun owns(access: ProtectedAccess, obj: String): Boolean =
        access.inv.count(obj) > 0 || access.bank.count(obj) > 0 || access.worn.count(obj) > 0

    override fun subTitle(): String =
        "talking to <col=800000>Achietties</col> by the entrance to the " +
            "<col=800000>Heroes' Guild</col>, south of <col=800000>Burthorpe</col>. You will " +
            "need a friend in the opposite gang to help you complete this quest."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = player.player
            objective(
                "<red>Achietties</red> will let me into the <red>Heroes' Guild</red> if I bring " +
                    "her an <red>Entranan Firebird feather</red>, a <red>Master Thieves' " +
                    "armband</red> and a cooked <red>Lava Eel</red>.",
            ) {
                visibleWhen { isStarted(p) }
            }
            armbandObjectives(player, p)
            featherObjectives(player)
            eelObjectives(player)
            objective(
                "I have everything Achietties asked for. I should take them to her outside the " +
                    "<red>Heroes' Guild</red>.",
            ) {
                visibleWhen {
                    HAND_IN_ITEMS.all { access.inv.count(it) > 0 }
                }
            }
        }

    private fun QuestJournalBuilder.armbandObjectives(access: ProtectedAccess, p: Player) {
        val hasArmband = access.inv.count(ARMBAND) > 0
        objective(
            "The armband is for master thieves. My old contacts in the <red>" +
                (if (isPhoenix(p)) "Phoenix Gang" else "Black Arm Gang") +
                "</red> in <red>Varrock</red> may know how to earn one.",
        ) {
            visibleWhen { stage(p) == STAGE_STARTED && !hasArmband }
        }
        objective(
            "<red>Straven</red> says stealing <red>Scarface Pete's candlesticks</red> in " +
                "<red>Brimhaven</red> would do. I should say the word <red>'gherkin'</red> to " +
                "<red>Alfonse</red>, the waiter in the <red>Shrimp and Parrot</red>.",
        ) {
            visibleWhen { stage(p) == PHOENIX_BRIEFED }
        }
        objective(
            "Alfonse sent me to <red>Charlie the cook</red> in the kitchen at the back of the " +
                "restaurant.",
        ) {
            visibleWhen { stage(p) == PHOENIX_ALFONSE }
        }
        objective(
            "Charlie showed me a secret way through <red>Mr Olbors' garden</red> to a blocked " +
                "side entrance of the mansion. A <red>Black Arm</red> player may be able to get " +
                "me a key and lure the head guard, <red>Grip</red>, where I can shoot him.",
        ) {
            visibleWhen { stage(p) == PHOENIX_CHARLIE }
        }
        objective(
            "I killed <red>Grip</red>. My Black Arm partner should find two candlesticks in the " +
                "treasure room and give me one to take to <red>Straven</red>.",
        ) {
            visibleWhen { stage(p) == PHOENIX_KILLED_GRIP }
            custom(
                access.inv.count(CANDLESTICK) > 0,
                "I have one of <red>Scarface Pete's candlesticks</red>. I should take it to " +
                    "<red>Straven</red>.",
            )
        }
        objective(
            "<red>Katrine</red> says stealing <red>Scarface Pete's candlesticks</red> in " +
                "<red>Brimhaven</red> would do. The gang's hideout is in the alleyway on " +
                "<red>Palm Street</red>, and the password is <red>'four leafed clover'</red>.",
        ) {
            visibleWhen { stage(p) == BLACKARM_BRIEFED }
        }
        objective(
            "I'm in the Black Arm Gang's Brimhaven hideout. I should talk to <red>Trobert</red>, " +
                "who runs it.",
        ) {
            visibleWhen { stage(p) == BLACKARM_DOOR }
        }
        objective(
            "I'm to pose as <red>Hartigen</red>, a Black Knight deserter hired as Grip's " +
                "deputy. The guards at <red>Scarface Pete's mansion</red> will expect me to look " +
                "like one: a <red>black full helm</red>, <red>platebody</red> and " +
                "<red>platelegs</red>.",
        ) {
            visibleWhen { stage(p) == BLACKARM_PAPERS }
        }
        objective(
            "I'm inside the mansion. I should report for duty to the head guard, <red>Grip</red>.",
        ) {
            visibleWhen { stage(p) == BLACKARM_MANSION }
        }
        objective(
            "Grip keeps the treasure room keys in his jacket. A <red>Phoenix Gang</red> player " +
                "could shoot him from the room behind his <red>drinks cabinet</red> if I lure him " +
                "there. Grip gave me a key that might help them get in.",
        ) {
            visibleWhen { stage(p) == BLACKARM_REPORTED }
        }
        objective(
            "I found two of <red>Scarface Pete's candlesticks</red>. I should give one to my " +
                "partner and take the other to <red>Katrine</red>.",
        ) {
            visibleWhen { stage(p) == BLACKARM_LOOTED }
        }
        objective("I have a <red>Master Thieves' armband</red>.") {
            visibleWhen { hasArmband }
        }
    }

    private fun QuestJournalBuilder.featherObjectives(access: ProtectedAccess) {
        val p = access.player
        objective(
            "Entranan Firebirds live on <red>Entrana</red>. Their feathers are too hot to touch " +
                "without something cold, like the <red>Ice Queen's</red> gloves.",
        ) {
            visibleWhen { isInProgress(p) && access.inv.count(FEATHER) == 0 }
        }
        objective("I have an <red>Entranan Firebird feather</red>.") {
            visibleWhen { isInProgress(p) && access.inv.count(FEATHER) > 0 }
        }
    }

    private fun QuestJournalBuilder.eelObjectives(access: ProtectedAccess) {
        val p = access.player
        objective(
            "<red>Gerrant</red> in <red>Port Sarim</red> should know how to catch a " +
                "<red>Lava Eel</red>.",
        ) {
            visibleWhen { isInProgress(p) && !owns(access, SLIME) && !knowsOil(access) }
        }
        objective(
            "Lava eels need a fishing rod coated in <red>Blamish Oil</red>: blamish snail slime " +
                "mixed into a <red>Harralander potion (unf)</red>. The eels live in the lava of " +
                "<red>Taverley Dungeon</red>.",
        ) {
            visibleWhen { isInProgress(p) && knowsOil(access) && access.inv.count(LAVA_EEL) == 0 }
            custom(
                access.inv.count(RAW_LAVA_EEL) > 0,
                "I've caught a <red>Lava Eel</red>. It needs cooking.",
            )
        }
        objective("I have a cooked <red>Lava Eel</red>.") {
            visibleWhen { isInProgress(p) && access.inv.count(LAVA_EEL) > 0 }
        }
    }

    private fun knowsOil(access: ProtectedAccess): Boolean =
        owns(access, SLIME) || owns(access, BLAMISH_OIL) || owns(access, OILY_ROD)

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            val route =
                if (isPhoenix(player.player)) {
                    "I earned my Master Thieves' armband by shooting Grip, Scarface Pete's head " +
                        "guard, through a hole in the wall of his mansion."
                } else {
                    "I earned my Master Thieves' armband by posing as Grip's new deputy and " +
                        "robbing Scarface Pete's treasure room."
                }
            line("Achietties set me three tasks to prove I was a hero.")
            line(route)
            line(
                "I took the Ice Queen's gloves to pick up an Entranan Firebird's feather, and " +
                    "caught and cooked a Lava Eel with an oiled fishing rod.",
            )
            line("I am now a member of the Heroes' Guild.")
        }

    companion object {
        const val QUEST_KEY = "quest_heroes"

        const val STAGE_STARTED = 1
        const val PHOENIX_BRIEFED = 2
        const val PHOENIX_ALFONSE = 3
        const val PHOENIX_CHARLIE = 4
        const val PHOENIX_KILLED_GRIP = 5
        const val PHOENIX_ARMBAND = 6
        const val BLACKARM_BRIEFED = 7
        const val BLACKARM_DOOR = 8
        const val BLACKARM_PAPERS = 9
        const val BLACKARM_MANSION = 10
        const val BLACKARM_REPORTED = 11
        const val BLACKARM_LOOTED = 12
        const val BLACKARM_ARMBAND = 13
        const val STAGE_COMPLETE = 15

        const val REQUIRED_QUEST_POINTS = 55
        const val REQUIRED_COOKING = 53
        const val REQUIRED_FISHING = 53
        const val REQUIRED_HERBLORE = 25
        const val REQUIRED_MINING = 50

        val REQUIRED_QUESTS =
            listOf(
                "quest_shieldofarrav",
                "quest_lostcity",
                "quest_merlinscrystal",
                "quest_dragonslayer1",
            )

        const val COMBAT_XP = 3075.0
        const val RANGED_XP = 2075.0
        const val FISHING_XP = 2725.0
        const val COOKING_XP = 2825.0
        const val WOODCUTTING_XP = 1575.0
        const val FIREMAKING_XP = 1575.0
        const val SMITHING_XP = 2275.0
        const val MINING_XP = 2575.0
        const val HERBLORE_XP = 1325.0

        const val DRAGON_BATTLEAXE = "obj.dragon_battleaxe"
        const val FEATHER = "obj.hot_feather"
        const val ARMBAND = "obj.master_thief_armband"
        const val LAVA_EEL = "obj.lava_eel"
        const val RAW_LAVA_EEL = "obj.raw_lava_eel"
        const val CANDLESTICK = "obj.petecandlestick"
        const val MISC_KEY = "obj.misc_key"
        const val GRIP_KEYS = "obj.grip_keys"
        const val ID_PAPERS = "obj.id_papers"
        const val SLIME = "obj.blamish_snail_slime"
        const val BLAMISH_OIL = "obj.blamish_oil"
        const val OILY_ROD = "obj.oily_fishing_rod"
        const val FISHING_ROD = "obj.fishing_rod"
        const val ICE_GLOVES = "obj.ice_gloves"
        const val WHISKY = "obj.whisky"

        val HAND_IN_ITEMS = listOf(FEATHER, ARMBAND, LAVA_EEL)

        val BLACK_ARMOUR = listOf("obj.black_full_helm", "obj.black_platebody", "obj.black_platelegs")
    }
}

internal val Player.questPoints: Int by intVarp("varp.qp")
