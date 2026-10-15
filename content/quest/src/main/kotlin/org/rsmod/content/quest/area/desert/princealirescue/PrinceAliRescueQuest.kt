package org.rsmod.content.quest.area.desert.princealirescue

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Prince Ali Rescue.
 *
 * The stage is the whole cache varp `varp.princequest` (endstate 110), which also drives the jail
 * multinpcs: Lady Keli is visible up to [STAGE_JOE_DRUNK], the imprisoned Prince up to 90, Joe up
 * to [STAGE_ALI_ESCAPED] and the Prince in the palace from [STAGE_ALI_ESCAPED].
 * - [STAGE_STARTED]: Hassan sent the player to Osman.
 * - [STAGE_BRIEFED]: Osman explained the plan; the key and the disguise are being gathered.
 * - [STAGE_PREPARED]: Leela has seen the key and disguise and pointed the player at Joe.
 * - [STAGE_JOE_DRUNK]: Joe has had his three beers.
 * - [STAGE_KELI_TIED]: Keli is tied up in the cupboard and the cell door can be unlocked.
 * - [STAGE_ALI_ESCAPED]: the Prince has escaped in disguise.
 * - [STAGE_COMPLETE]: Hassan has paid the reward.
 *
 * Progress the stage cannot hold sits on the server-only `varp.princeali_state`: how far the key
 * copy has got ([KEY_NONE] / [KEY_MADE] / [KEY_GIVEN]), whether the player has met Leela, and
 * whether Keli has heard the player wants to join her gang.
 */
@Singleton
class PrinceAliRescueQuest :
    QuestScript(
        QUEST_KEY,
        "varp.princequest",
        rewards {
            item(COINS, REWARD_COINS, label = "700 Coins")
            extra("Free passage through the Al Kharid toll gate")
        },
        ItemRewardDisplay(KEY, zoom = 300),
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearWhenReset)
        onOpHeldU(YELLOW_DYE, WIG) { dyeWig() }
    }

    private fun ProtectedAccess.dyeWig() {
        if (invDel(inv, YELLOW_DYE).failure || invDel(inv, WIG).failure) {
            return
        }
        invAdd(inv, BLOND_WIG)
        mes("You dye the wig blond.")
    }

    override fun subTitle(): String =
        "talking to <col=800000>Chancellor Hassan</col> in the <col=800000>palace</col> in " +
            "<col=800000>Al Kharid</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start =
                "Chancellor Hassan of Al Kharid needs help with an urgent problem. He told me to " +
                    "speak to Osman, the Emir's spymaster, just outside the palace."
            if (stage == STAGE_STARTED) {
                line(start)
                return@questJournal
            }
            strike(start)
            val briefing =
                "Prince Ali has been kidnapped by Lady Keli's bandits and is held in the abandoned " +
                    "jail east of Draynor Village. I need to get him out without any bloodshed."
            if (stage == STAGE_BRIEFED) {
                line(briefing)
                gathering(p)
                return@questJournal
            }
            strike(briefing)
            strike("I gathered a copy of the cell key and a disguise to make the Prince look like Keli.")
            if (stage == STAGE_PREPARED) {
                line(
                    "Leela told me to deal with the Prince's guard, Joe, without violence. Maybe " +
                        "three beers would do the trick.",
                )
                return@questJournal
            }
            strike("I got Joe the guard too drunk to notice anything.")
            if (stage == STAGE_JOE_DRUNK) {
                line("I need to tie Lady Keli up with a rope before I free the Prince.")
                return@questJournal
            }
            strike("I tied Lady Keli up and hid her in a cupboard.")
            if (stage < STAGE_ALI_ESCAPED) {
                line(
                    "I should unlock the cell door with the key and give the Prince his disguise " +
                        "so he can slip past the guards.",
                )
                return@questJournal
            }
            strike("Prince Ali escaped in his disguise.")
            line("I should return to Chancellor Hassan in Al Kharid for my reward.")
        }

    private fun QuestJournalBuilder.gathering(p: Player) {
        val key = keyState(p)
        when {
            key == KEY_GIVEN && hasKey(p) -> strike("Leela gave me the copy of the key.")
            key == KEY_GIVEN ->
                line("I have lost the key Leela gave me. She can sort me out with another.")
            key == KEY_MADE ->
                line("Osman is having a copy of the key made. He will send it to Leela.")
            KEY_PRINT in p.inv ->
                line("I have an imprint of the key. Osman needs it and a bronze bar to make a copy.")
            else ->
                line(
                    "Lady Keli keeps the only key to the cell. If I can get her to show it to me, I " +
                        "could take an imprint with some soft clay.",
                )
        }
        if (BLOND_WIG in p.inv) {
            strike("I have a blond wig.")
        } else if (WIG in p.inv) {
            line("I have a wig. I need to dye it yellow.")
        } else {
            line("Ned, the old sailor in Draynor Village, might be able to make a wig from wool.")
        }
        if (SKIN_PASTE in p.inv) {
            strike("I have some skin paste.")
        } else {
            line("Aggie, the witch in Draynor Village, could make skin paste for the Prince.")
        }
        if (PINK_SKIRT in p.inv) {
            strike("I have a pink skirt.")
        } else {
            line("I need a pink skirt like Keli's. Thessalia in Varrock sells them.")
        }
        if (hasKey(p) && hasDisguise(p)) {
            line("I have everything. I should let Leela know.")
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Chancellor Hassan and the spymaster Osman asked me to rescue Prince Ali, kidnapped " +
                    "by Lady Keli and held in the jail east of Draynor Village.",
            )
            line(
                "With the help of Osman's daughter Leela I made a copy of the cell key and a " +
                    "disguise, got the guard drunk and tied Keli up.",
            )
            line("Prince Ali escaped disguised as Keli, and the Emir rewarded me.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun setStage(access: ProtectedAccess, stage: Int) {
        quest.setQuestStage(access, stage)
    }

    fun keyState(player: Player): Int = player.princeAliKey

    fun setKeyState(player: Player, state: Int) {
        player.princeAliKey = state
    }

    fun metLeela(player: Player): Boolean = player.princeAliMetLeela

    fun setMetLeela(player: Player) {
        player.princeAliMetLeela = true
    }

    fun keliRecruit(player: Player): Boolean = player.princeAliKeliRecruit

    fun setKeliRecruit(player: Player) {
        player.princeAliKeliRecruit = true
    }

    fun hasKey(player: Player): Boolean = KEY in player.inv

    fun hasDisguise(player: Player): Boolean =
        BLOND_WIG in player.inv && SKIN_PASTE in player.inv && PINK_SKIRT in player.inv

    /** The key Leela handed over is gone from both the pack and the bank. */
    fun ProtectedAccess.lostKey(): Boolean =
        keyState(player) == KEY_GIVEN && inv.count(KEY) == 0 && bank.count(KEY) == 0

    /** Ned and Aggie make disguise pieces from the briefing until the Prince is out. */
    fun gatheringDisguise(player: Player): Boolean = stage(player) in STAGE_BRIEFED until STAGE_ALI_ESCAPED

    private fun clearWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.princeAliKey = KEY_NONE
        player.princeAliMetLeela = false
        player.princeAliKeliRecruit = false
    }

    companion object {
        const val QUEST_KEY = "quest_princealirescue"
        const val SHIELD_OF_ARRAV = "quest_shieldofarrav"

        const val STAGE_STARTED = 10
        const val STAGE_BRIEFED = 20
        const val STAGE_PREPARED = 30
        const val STAGE_JOE_DRUNK = 40
        const val STAGE_KELI_TIED = 50
        const val STAGE_ALI_ESCAPED = 100
        const val STAGE_COMPLETE = 110

        const val KEY_NONE = 0
        const val KEY_MADE = 1
        const val KEY_GIVEN = 2

        const val REWARD_COINS = 700
        const val LOST_KEY_PRICE = 15
        const val BEERS_NEEDED = 3
        const val WOOL_PER_WIG = 3

        const val COINS = "obj.coins"
        const val KEY = "obj.princeskey"
        const val KEY_PRINT = "obj.keyprint"
        const val SOFT_CLAY = "obj.softclay"
        const val BRONZE_BAR = "obj.bronze_bar"
        const val WIG = "obj.plainwig"
        const val BLOND_WIG = "obj.blondwig"
        const val YELLOW_DYE = "obj.yellowdye"
        const val SKIN_PASTE = "obj.skinpaste"
        const val PINK_SKIRT = "obj.pink_skirt"
        const val BEER = "obj.beer"
        const val ROPE = "obj.rope"
        const val BALL_OF_WOOL = "obj.ball_of_wool"
        const val JUG_OF_WATER = "obj.jug_water"
        const val ASHES = "obj.ashes"
        const val POT_OF_FLOUR = "obj.pot_flour"
        const val BUCKET_OF_WATER = "obj.bucket_water"
        const val REDBERRIES = "obj.redberries"

        const val HASSAN = "npc.hassan"
        const val OSMAN = "npc.osman"
        const val LEELA = "npc.leela"
        const val JOE = "npc.joe_vis"
        const val LADY_KELI = "npc.lady_keli_vis"
        const val PRINCE_ALI_CELL = "npc.prince_ali_vis_blackeye"
        const val PRINCE_ALI_PALACE = "npc.prince_ali_vis"

        const val CELL_DOOR = "loc.alidoor"
    }
}

private var Player.princeAliKey by intVarBit("varbit.princeali_key")
private var Player.princeAliMetLeela by boolVarBit("varbit.princeali_met_leela")
private var Player.princeAliKeliRecruit by boolVarBit("varbit.princeali_keli_recruit")
