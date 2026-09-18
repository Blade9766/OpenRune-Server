package org.rsmod.content.quest.area.desert.touristtrap

import dev.openrune.ServerCacheManager
import jakarta.inject.Singleton
import org.rsmod.api.player.feet
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Tourist Trap.
 *
 * The stage lives in `varp.desertrescue` (197) and runs 0..30 from `dbrow.quest_touristtrap`; the
 * values in between are Jagex's own, as preserved by 2004Scape's `desertrescue.constant`.
 *
 * Everything the client draws differently for this quest - Irena crying or smiling, Ana in the
 * mine or next to her mother, the male slave or the escaping one in his clothes, the bent cell
 * bars, the barrel by the surface winch and the barrel on the wooden cart - is a cache varbit on
 * `varp.tourtrap_qip_var` (907). Where Ana is along the escape route is one number,
 * [anaLocation], and [syncVars] derives all of those varbits from it and the stage. The rest of
 * the quest's memory is on the server-only `varp.touristtrap_state`.
 */
@Singleton
class TouristTrapQuest : QuestScript(
    QUEST_KEY,
    "varp.desertrescue",
    rewards { extra("2 x 4,650 XP in skills of your choice") },
    ItemRewardDisplay(BRONZE_DART, zoom = 200),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
        onPlayerLogin {
            player.ttSiadDistracted = false
            player.ttCaptainDuel = false
            syncVars(player)
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    /** Moves the stage forward to [stage]; never back, so replayed steps cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** Advances to [to] only when the player is exactly at [from]. */
    fun advanceFrom(access: ProtectedAccess, from: Int, to: Int) {
        if (stage(access.player) == from) {
            advanceTo(access, to)
        }
    }

    fun completeQuest(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    /** Puts Ana somewhere new along the escape route and redraws everything that shows her. */
    fun moveAna(player: Player, location: Int) {
        player.ttAnaLocation = location
        syncVars(player)
    }

    /**
     * Redraws the quest's cache varbits from the stage and [anaLocation], and clears every flag
     * when `::resetquest` puts the stage back to zero.
     *
     * An Ana who is supposed to be in the player's arms but is no longer in their pack has been
     * lost somehow (dropped through a death, or the barrel destroyed); the guards find her and
     * she goes back to the mine.
     */
    fun syncVars(player: Player) {
        val stage = stage(player)
        if (stage == 0) {
            player.ttEvictions = 0
            player.ttBet = 0
            player.ttAskedAlZaba = false
            player.ttAlZabaDebunked = false
            player.ttSeenSailingBooks = false
            player.ttSiadDistracted = false
            player.ttTentAccess = false
            player.ttAnaLocation = ANA_IN_MINE
            player.ttCartReady = false
            player.ttCaptainDuel = false
            player.ttMetAna = false
            setVarBit(player, CELL_BARS_VARBIT, 0)
            setVarBit(player, PICKLOCK_VARBIT, 0)
            setVarBit(player, SLAVE_VARBIT, 0)
        }
        if (player.ttAnaLocation == ANA_CARRIED && ANA_IN_A_BARREL !in player.inv) {
            player.ttAnaLocation = ANA_IN_MINE
        }
        val location = player.ttAnaLocation
        setVarBit(player, ANA_MINE_VARBIT, if (location == ANA_IN_MINE) 0 else 1)
        setVarBit(player, WINCH_BARREL_VARBIT, if (location == ANA_WINCH_BARREL) 1 else 0)
        setVarBit(player, WAGON_VARBIT, if (location == ANA_ON_WAGON) 1 else 0)
        setVarBit(player, ANA_AT_PASS_VARBIT, if (location == ANA_WITH_IRENA) 1 else 0)
        setVarBit(player, IRENA_VARBIT, if (stage >= STAGE_SAVED_ANA) 1 else 0)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Irena</col>, just south of the <col=800000>Shantay Pass</col> " +
            "in the <col=800000>Kharidian Desert</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = stage(access.player)
            objective(
                "<red>Irena</red>'s daughter <red>Ana</red> has gone missing in the desert. " +
                    "Footprints lead south towards the <red>Desert Mining Camp</red>.",
            ) {
                visibleWhen { stage in STAGE_STARTED until STAGE_KILLED_CAPTAIN }
            }
            objective(
                "The <red>Mercenary Captain</red> keeps the key to the camp gate on his belt. " +
                    "His men say he can be goaded into fighting his own battles.",
            ) {
                visibleWhen { stage == STAGE_APPROACHED_CAPTAIN }
            }
            objective(
                "I have the key to the camp gate. Guards search everyone going in, so I should " +
                    "leave my weapons and armour behind and wear only <red>desert clothes</red>.",
            ) {
                visibleWhen { stage == STAGE_KILLED_CAPTAIN }
            }
            objective(
                "A <red>male slave</red> by the eastern rocks wants his chains undone and a set " +
                    "of <red>desert clothes</red>. His slave clothes would get me into the mine.",
            ) {
                visibleWhen { stage in STAGE_ENTERED_CAMP..STAGE_FREED_SLAVE }
            }
            objective(
                "Dressed as a slave I can go through the big wooden doors into the mine, but " +
                    "the guards will throw me in a cell if they catch me in anything else.",
            ) {
                visibleWhen { stage == STAGE_TRADED_CLOTHES || stage == STAGE_ENTERED_MINE }
            }
            objective(
                "The guard at the cave wants a whole <red>Tenti pineapple</red> before he lets " +
                    "me through. The Tenti's live in tents west of the camp.",
            ) {
                visibleWhen { stage == STAGE_FINDING_PINEAPPLE }
            }
            objective(
                "<red>Al Shabim</red> of the Bedabin will trade a pineapple for the plans in " +
                    "<red>Captain Siad</red>'s chest, upstairs in the camp. He gave me a key.",
            ) {
                visibleWhen { stage == STAGE_GIVEN_BEDABIN_KEY }
            }
            objective(
                "I have the <red>technical plans</red>. I should take them to " +
                    "<red>Al Shabim</red> at the Bedabin camp.",
            ) {
                visibleWhen { stage == STAGE_RETRIEVED_PLANS }
            }
            objective(
                "Al Shabim wants the weapon in the plans made. The anvil is in the tent north " +
                    "of his; I need a <red>hammer</red>, a <red>bronze bar</red> and " +
                    "<red>10 feathers</red>.",
            ) {
                visibleWhen { stage in STAGE_SHOWN_PLANS..STAGE_FINISHED_DART }
            }
            objective(
                "Al Shabim gave me a <red>Tenti pineapple</red>. The cave guard in the mine " +
                    "should let me through for it.",
            ) {
                visibleWhen { stage == STAGE_LEARNED_DARTS }
            }
            objective(
                "Beyond the cave there is a mine cart and plenty of empty barrels. Ana is being " +
                    "worked somewhere in the mine; a barrel might be the way to smuggle her out.",
            ) {
                visibleWhen { stage in STAGE_GIVEN_PINEAPPLE..STAGE_USED_MINE_CART }
            }
            objective(
                "Ana is in a barrel. The barrels go by mine cart to the lift, the lift goes to " +
                    "the surface, and the surface barrels leave the camp on the wooden cart.",
            ) {
                visibleWhen { stage in STAGE_CAUGHT_ANA..STAGE_ANA_ON_WAGON }
            }
            objective(
                "We got out of the camp! I should take Ana back to <red>Irena</red> by the " +
                    "<red>Shantay Pass</red>.",
            ) {
                visibleWhen { stage == STAGE_ESCAPED }
            }
            objective(
                "Ana is home. <red>Irena</red> wants to reward me for bringing her back.",
            ) {
                visibleWhen { stage in STAGE_SAVED_ANA until STAGE_COMPLETE }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Irena, crying by the Shantay Pass, sent me after her daughter Ana, lost in the " +
                    "desert. Her footprints led to the Desert Mining Camp.",
            )
            line(
                "I goaded the Mercenary Captain into a duel and took the gate key from him, then " +
                    "freed a slave from his chains and swapped my desert clothes for his rags.",
            )
            line(
                "The cave guard in the mine wanted a Tenti pineapple. Al Shabim of the Bedabin " +
                    "traded me one for Captain Siad's plans and a prototype dart made from them, " +
                    "and taught me to make darts of my own.",
            )
            line(
                "I squeezed Ana into a barrel and sent her up with the rock: by mine cart, by the " +
                    "lift to the surface, and out through the gates on the wooden cart. Irena was " +
                    "overjoyed, and Ana gave me a wrought iron key she had found in the tunnels.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_touristtrap"

        const val STAGE_STARTED = 1
        const val STAGE_APPROACHED_CAPTAIN = 3
        const val STAGE_KILLED_CAPTAIN = 4
        const val STAGE_ENTERED_CAMP = 5
        const val STAGE_SPOKEN_SLAVE = 6
        const val STAGE_FREED_SLAVE = 7
        const val STAGE_TRADED_CLOTHES = 8
        const val STAGE_ENTERED_MINE = 9
        const val STAGE_FINDING_PINEAPPLE = 10
        const val STAGE_GIVEN_BEDABIN_KEY = 11
        const val STAGE_RETRIEVED_PLANS = 12
        const val STAGE_SHOWN_PLANS = 13
        const val STAGE_MADE_DART_TIP = 14
        const val STAGE_FINISHED_DART = 15
        const val STAGE_LEARNED_DARTS = 16
        const val STAGE_GIVEN_PINEAPPLE = 17
        const val STAGE_USED_MINE_CART = 18
        const val STAGE_CAUGHT_ANA = 19
        const val STAGE_ANA_IN_MINE_CART = 20
        const val STAGE_RETRIEVED_ANA_MINE_CART = 21
        const val STAGE_ANA_ON_LIFT = 22
        const val STAGE_ANA_AT_SURFACE = 23
        const val STAGE_RETRIEVED_ANA_SURFACE = 24
        const val STAGE_ANA_ON_WAGON = 25
        const val STAGE_ESCAPED = 26
        const val STAGE_SAVED_ANA = 27
        const val STAGE_PICKED_FIRST_SKILL = 28
        const val STAGE_PICKED_SECOND_SKILL = 29
        const val STAGE_COMPLETE = 30

        /** Irena's reward, awarded twice in skills of the player's choice. */
        const val SKILL_REWARD_XP = 4650.0

        const val SMITHING_REQ = 20
        const val FLETCHING_REQ = 10

        /* Where Ana is, stored in [anaLocation]. */
        const val ANA_IN_MINE = 0
        const val ANA_CARRIED = 1
        const val ANA_WITH_MINE_CART_BARRELS = 2
        const val ANA_ON_LIFT = 3
        const val ANA_WINCH_BARREL = 4
        const val ANA_ON_WAGON = 5
        const val ANA_WITH_IRENA = 6
        const val ANA_GONE_HOME = 7

        /* Cache varbits on `varp.tourtrap_qip_var`. */
        const val CELL_BARS_VARBIT = "varbit.tourtrap_qip_cell_wall"
        const val PICKLOCK_VARBIT = "varbit.tourtrap_qip_picklock_attempts"
        const val SLAVE_VARBIT = "varbit.tourtrap_qip_mineslave"
        const val ANA_MINE_VARBIT = "varbit.tourtrap_qip_ana_state"
        const val IRENA_VARBIT = "varbit.tourtrap_qip_irena_state"
        const val WINCH_BARREL_VARBIT = "varbit.tourtrap_qip_ana_winchside_barrel"
        const val WAGON_VARBIT = "varbit.tourtrap_qip_flatback_cart_ana"
        const val ANA_AT_PASS_VARBIT = "varbit.tourtrap_ana_visible_shanty_pass"

        /* Multi-npcs, keyed by their base type: ops and hits arrive on the base, never the form. */
        const val IRENA_SAD = "npc.tourtrap_qip_irena_multi_sad"
        const val IRENA_HAPPY = "npc.tourtrap_qip_irena_multi_happy"
        const val ANA_IN_MINE_NPC = "npc.tourtrap_qip_ana_multi"
        const val ANA_AT_PASS_NPC = "npc.tourtrap_qip_ana_shanty_multi"
        const val ANA_BARREL_HEAD = "npc.anabarrel"
        const val MALE_SLAVE_MULTI = "npc.tourtrap_qip_mineslave_clothes_multi"
        const val CAPTAIN = "npc.desertminingcaptain"
        const val CAPTAIN_SIAD = "npc.capt_siad"
        const val AL_SHABIM = "npc.al_shabim"
        const val BEDABIN_NOMAD = "npc.bedabin"
        const val BEDABIN_GUARD = "npc.bedabin_guard"
        const val CART_DRIVER = "npc.mining_cart_driver"

        val MERCENARIES =
            listOf(
                "npc.tourtrap_qip_desert_mining_merc_1",
                "npc.tourtrap_qip_desert_mining_merc_2",
                "npc.tourtrap_qip_desert_mining_merc_3",
                "npc.tourtrap_qip_desert_mining_merc_4",
            )

        /** Every guard inside the compound and down the mine. */
        val CAMP_GUARDS =
            listOf(
                "npc.tourtrap_qip_desert_mining_guard_1",
                "npc.tourtrap_qip_desert_mining_guard_2",
                "npc.tourtrap_qip_desert_mining_guard_3",
                "npc.tourtrap_qip_desert_mining_guard_4",
                "npc.tourtrap_qip_desert_mining_guard_5",
                "npc.tourtrap_qip_desert_mining_guard_6",
                "npc.tourtrap_qip_desert_mining_guard_still_1",
                "npc.tourtrap_qip_desert_mining_guard_still_2",
                "npc.tourtrap_qip_desert_mining_guard_still_3",
                "npc.tourtrap_qip_rowdy_desert_mining_guard_1",
                "npc.tourtrap_qip_still_desert_mining_guard",
            )

        const val METAL_KEY = "obj.metal_key"
        const val CELL_DOOR_KEY = "obj.thcelldoorkey"
        const val WROUGHT_IRON_KEY = "obj.thgoodminekey"
        const val BEDABIN_KEY = "obj.thbedobinkey"
        const val TECHNICAL_PLANS = "obj.thcaptplans"
        const val PROTOTYPE_DART_TIP = "obj.thprotodarttip"
        const val PROTOTYPE_DART = "obj.thprotodart"
        const val TENTI_PINEAPPLE = "obj.tentipineapple"
        const val EMPTY_BARREL = "obj.thminebarrel_empty"
        const val ANA_IN_A_BARREL = "obj.thanainabarrel"
        const val PUNISHMENT_ROCK = "obj.thpunishrock"
        const val DESERT_SHIRT = "obj.desert_shirt"
        const val DESERT_ROBE = "obj.desert_robe"
        const val DESERT_BOOTS = "obj.desert_boots"
        const val SLAVE_SHIRT = "obj.slave_shirt"
        const val SLAVE_ROBE = "obj.slave_robe"
        const val SLAVE_BOOTS = "obj.slave_boots"
        const val BRONZE_BAR = "obj.bronze_bar"
        const val BRONZE_DART = "obj.bronze_dart"
        const val FEATHER = "obj.feather"
        const val HAMMER = "obj.hammer"
        const val COINS = "obj.coins"

        const val SOUND_BEND_BARS = "synth.tt_bend_bars"
        const val SOUND_CART_LOOP = "synth.tt_cart_loop"
        const val SOUND_FALL_BACK = "synth.tt_fall_back"
        const val SOUND_SQUEEZE_OUT = "synth.tt_squeeze_out"
        const val SOUND_WINCHING = "synth.tt_winching_loop"
        const val SOUND_KNOCKING = "synth.tt_knocking"
        const val SOUND_LOCKED = "synth.locked"
        const val SOUND_PICK_LOCK = "synth.pick_lock"
        const val SOUND_EAT = "synth.eat"
        const val SOUND_ANVIL = "synth.anvil02"
        const val SOUND_CURTAIN = "synth.curtain_open"
    }
}

/** How often the Mercenary Captain's guards have "dealt with" the player: 0..3, then it wraps. */
var Player.ttEvictions by intVarBit("varbit.touristtrap_evictions")

/**
 * The bet on the duel with the captain: 0 none, 1..4 the stake in fives, 5 settled (collected,
 * paid off, or lost to the clean-up fee).
 */
var Player.ttBet by intVarBit("varbit.touristtrap_bet")

/** Set once the captain has sent the player after Al Zaba Bhasim. */
var Player.ttAskedAlZaba by boolVarBit("varbit.touristtrap_asked_al_zaba")

/** Set once Al Shabim has explained that Al Zaba Bhasim does not exist. */
var Player.ttAlZabaDebunked by boolVarBit("varbit.touristtrap_al_zaba_debunked")

/** Set once the south-west bookcase in Captain Siad's office has been searched. */
var Player.ttSeenSailingBooks by boolVarBit("varbit.touristtrap_seen_sailing_books")

/** Set while Captain Siad is looking the other way; cleared by the next chest or desk search. */
var Player.ttSiadDistracted by boolVarBit("varbit.touristtrap_siad_distracted")

/** Set once the Bedabin guard has seen the plans and lets the player into the anvil tent. */
var Player.ttTentAccess by boolVarBit("varbit.touristtrap_tent_access")

/** Where Ana is along the escape route; one of the `ANA_*` constants. */
var Player.ttAnaLocation by intVarBit("varbit.touristtrap_ana_location")

/** Set once the mine cart driver has agreed to take the player out with his last load. */
var Player.ttCartReady by boolVarBit("varbit.touristtrap_cart_ready")

/** Set while the Mercenary Captain is fighting the player one-on-one. */
var Player.ttCaptainDuel by boolVarBit("varbit.touristtrap_captain_duel")

/** Set once the player has spoken to Ana in the mine; she greets them differently after. */
var Player.ttMetAna by boolVarBit("varbit.touristtrap_met_ana")

internal fun setVarBit(player: Player, varbit: String, value: Int) {
    if (player.vars[varbit] != value) {
        VarPlayerIntMapSetter.set(player, varbit, value)
    }
}

internal fun Player.wearingSlaveRobes(): Boolean =
    torso.isType(TouristTrapQuest.SLAVE_SHIRT) &&
        legs.isType(TouristTrapQuest.SLAVE_ROBE) &&
        feet.isType(TouristTrapQuest.SLAVE_BOOTS)

internal fun Player.wearingAnySlaveClothes(): Boolean =
    torso.isType(TouristTrapQuest.SLAVE_SHIRT) ||
        legs.isType(TouristTrapQuest.SLAVE_ROBE) ||
        feet.isType(TouristTrapQuest.SLAVE_BOOTS)

/** A pickaxe is the one thing a slave may hold; anything else in the hand is a weapon. */
internal fun Player.wieldingWeapon(): Boolean {
    val weapon = righthand ?: return false
    val name = ServerCacheManager.getItem(weapon.id)?.name ?: return true
    return !name.endsWith("pickaxe", ignoreCase = true)
}

/** Anything worn where armour goes, other than the desert or slave outfit. */
internal fun Player.wearingArmour(): Boolean {
    if (hat != null || lefthand != null || hands != null) {
        return true
    }
    val torso = torso
    if (torso != null && !torso.isType(TouristTrapQuest.DESERT_SHIRT) &&
        !torso.isType(TouristTrapQuest.SLAVE_SHIRT)) {
        return true
    }
    val legs = legs
    if (legs != null && !legs.isType(TouristTrapQuest.DESERT_ROBE) &&
        !legs.isType(TouristTrapQuest.SLAVE_ROBE)) {
        return true
    }
    val feet = feet
    return feet != null && !feet.isType(TouristTrapQuest.DESERT_BOOTS) &&
        !feet.isType(TouristTrapQuest.SLAVE_BOOTS)
}
