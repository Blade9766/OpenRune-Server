package org.rsmod.content.quest.area.seers.elementalworkshop

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elemental Workshop I.
 *
 * The stage is the cache varbit `varbit.elementalworkshop` the client's quest list reads, endstate
 * 2 from `dbrow.quest_elementalworkshop1`: [STAGE_STARTED] once the battered book has been read,
 * [STAGE_COMPLETE] once the first elemental shield comes off the workbench.
 *
 * Everything in between is a flag on the cache varp `varp.elemental_workshop_bits`, most of them
 * the cache's own varbits, which also drive the workshop's multilocs per player:
 * - `elemental_workshop_book`: the book has been slashed open and the key found.
 * - `elemental_workshop_key`: the odd looking wall has been unlocked; it opens freely from then on.
 * - `elemental_workshop_gate2` / `_gate1`: the east and west water controls (red 0, green 1).
 * - `elemental_workshop_switch`: the water wheel is turning.
 * - `elemental_workshop_bellows` / `_bellows_switch`: the bellows are patched / pumping.
 * - `elemental_workshop_fire`: the furnace is lit.
 * - `elemental_workshop_ore_found` / `_metal_smelted`: two server-only flags on spare bits.
 *
 * None of the machinery ever needs undoing, so a finished workshop stays usable for making more
 * shields after the quest.
 */
@Singleton
class ElementalWorkshopQuest :
    QuestScript(
        QUEST_KEY,
        "varp.elemental_workshop_bits",
        rewards {
            xp("stat.crafting", REWARD_XP)
            xp("stat.smithing", REWARD_XP)
            scroll(
                "5,000 Crafting XP",
                "5,000 Smithing XP",
                "An elemental shield",
                "The ability to make",
                "more elemental shields",
            )
        },
        ItemRewardDisplay(SHIELD, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
        questVarbit = "varbit.elementalworkshop",
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearWhenReset)
    }

    override fun subTitle(): String =
        "searching the bookcase in the house south of the anvil in " +
            "<col=800000>Seers' Village</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val read = "I found an old, battered book in a Seers' Village bookcase. It tells of a " +
                "forgotten workshop where the elements were worked into metal."
            if (p.keyFound == 0) {
                line(read)
                line("The book says the way in is kept within its pages. Perhaps something sharp would help me look closer.")
                return@questJournal
            }
            strike(read)
            val key = "I cut a battered key out of the book's spine."
            if (p.wallUnlocked == 0) {
                strike(key)
                line("There must be a lock somewhere in Seers' Village that this key fits. The smithy has an odd looking wall.")
                return@questJournal
            }
            strike("$key It unlocked an odd looking wall in the smithy, and stairs led down into the workshop.")

            if (isWheelRunning(p)) {
                strike("I set the water controls and started the water wheel.")
            } else {
                line("The <col=800000>water wheel</col> in the northern room is still. Its water controls need setting before the lever will start it.")
            }
            when {
                isBellowsPumping(p) -> strike("I patched the bellows in the eastern room and set them pumping.")
                isBellowsFixed(p) -> line("The <col=800000>bellows</col> are patched, but their lever has to be pulled while the wheel turns.")
                else -> line("The <col=800000>bellows</col> in the eastern room have a hole in them that wants stitching shut.")
            }
            if (isFurnaceLit(p)) {
                strike("I relit the furnace in the southern room with lava from the trough.")
            } else {
                line("The <col=800000>furnace</col> in the southern room is cold. The lava trough beside it might be the way to light it.")
            }
            when {
                p.metalSmelted == 1 -> strike("I smelted elemental ore and coal into elemental metal.")
                p.oreFound == 1 -> line("I have taken <col=800000>elemental ore</col> from a living rock. It needs smelting with four coal in a working furnace.")
                else -> line("The rocks in the western room are not what they seem. The book's instructions call for <col=800000>elemental ore</col>.")
            }
            if (p.metalSmelted == 1) {
                line("Following the book, I should be able to work the metal into a shield at the <col=800000>workbench</col>, with a hammer to hand.")
            }
            line("")
            line("<col=800000>The back pages of the book hold the workshop's old maintenance notes, if I get stuck.</col>")
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I found a battered book in Seers' Village telling of a forgotten workshop, and a key hidden in its spine.")
            line("The key opened an odd looking wall in the smithy, and beneath it lay the Elemental Workshop.")
            line("I restarted the water wheel, patched the bellows and relit the furnace, then fought a living rock for its ore.")
            line("I smelted the ore into elemental metal and worked it into an elemental shield.")
            line("I can return to the workshop to make more shields whenever I like.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun start(access: ProtectedAccess) {
        if (stage(access.player) == 0) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun isWheelRunning(player: Player): Boolean = player.wheel == 1

    fun isBellowsFixed(player: Player): Boolean = player.bellowsFixed == 1

    fun isBellowsPumping(player: Player): Boolean = player.bellowsPumping == 1

    fun isFurnaceLit(player: Player): Boolean = player.furnaceLit == 1

    fun isWorkshopUnlocked(player: Player): Boolean = player.wallUnlocked == 1

    fun hasFoundKey(player: Player): Boolean = player.keyFound == 1

    fun markKeyFound(player: Player) {
        player.keyFound = 1
    }

    fun unlockWorkshop(player: Player) {
        player.wallUnlocked = 1
    }

    fun eastControl(player: Player): Int = player.eastGate

    fun westControl(player: Player): Int = player.westGate

    fun setControls(player: Player, east: Int, west: Int) {
        player.eastGate = east
        player.westGate = west
    }

    fun startWheel(player: Player) {
        player.wheel = 1
    }

    fun fixBellows(player: Player) {
        player.bellowsFixed = 1
    }

    fun startBellows(player: Player) {
        player.bellowsPumping = 1
    }

    fun lightFurnace(player: Player) {
        player.furnaceLit = 1
    }

    fun markOreFound(player: Player) {
        player.oreFound = 1
    }

    fun markMetalSmelted(player: Player) {
        player.metalSmelted = 1
    }

    private fun clearWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.keyFound = 0
        player.wallUnlocked = 0
        player.eastGate = 0
        player.westGate = 0
        player.wheel = 0
        player.bellowsFixed = 0
        player.bellowsPumping = 0
        player.furnaceLit = 0
        player.oreFound = 0
        player.metalSmelted = 0
    }

    companion object {
        const val QUEST_KEY = "quest_elementalworkshop1"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 2

        const val REWARD_XP = 5000.0
        const val REQUIRED_LEVEL = 20

        const val CONTROL_CLOSED = 0
        const val CONTROL_OPEN = 1

        const val BOOK = "obj.elemental_workshop_shield_book"
        const val SLASHED_BOOK = "obj.elemental_workshop_shield_book_slashed"
        const val KEY = "obj.elemental_workshop_key"
        const val BOWL = "obj.elemental_workshop_lava_bowl"
        const val LAVA_BOWL = "obj.elemental_workshop_lava_bowl_full"
        const val ORE = "obj.elemental_workshop_ore"
        const val METAL = "obj.elemental_workshop_bar"
        const val SHIELD = "obj.elemental_shield"

        const val KNIFE = "obj.knife"
        const val HAMMER = "obj.hammer"
        const val NEEDLE = "obj.needle"
        const val THREAD = "obj.thread"
        const val LEATHER = "obj.leather"
        const val COAL = "obj.coal"
        const val BRONZE_PICKAXE = "obj.bronze_pickaxe"

        const val COAL_PER_BAR = 4
    }
}

private var Player.keyFound: Int by intVarBit("varbit.elemental_workshop_book")
private var Player.wallUnlocked: Int by intVarBit("varbit.elemental_workshop_key")
private var Player.westGate: Int by intVarBit("varbit.elemental_workshop_gate1")
private var Player.eastGate: Int by intVarBit("varbit.elemental_workshop_gate2")
private var Player.wheel: Int by intVarBit("varbit.elemental_workshop_switch")
private var Player.bellowsFixed: Int by intVarBit("varbit.elemental_workshop_bellows")
private var Player.furnaceLit: Int by intVarBit("varbit.elemental_workshop_fire")
private var Player.bellowsPumping: Int by intVarBit("varbit.elemental_workshop_bellows_switch")
private var Player.oreFound: Int by intVarBit("varbit.elemental_workshop_ore_found")
private var Player.metalSmelted: Int by intVarBit("varbit.elemental_workshop_metal_smelted")
