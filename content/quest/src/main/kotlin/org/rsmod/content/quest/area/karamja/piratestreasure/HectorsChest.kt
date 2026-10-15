package org.rsmod.content.quest.area.karamja.piratestreasure

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CASKET
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.COINS
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.HECTORS_CHEST
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.HECTORS_CHEST_OPEN
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.PIRATE_MESSAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.STAGE_MESSAGE
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * One-Eyed Hector's chest upstairs in the Blue Moon Inn, the message it holds, and the casket the
 * message leads to. The key is used up when the chest is opened; Frank has spares while the player
 * still needs the message.
 */
class HectorsChest
@Inject
constructor(
    private val treasure: PiratesTreasureQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(HECTORS_CHEST) { mes("The chest is locked.") }
        onOpLocU(HECTORS_CHEST, CHEST_KEY) { unlock(it.loc) }
        onOpHeld1(PIRATE_MESSAGE) { readMessage() }
        onOpHeld1(CASKET) { openCasket() }
    }

    private suspend fun ProtectedAccess.unlock(chest: BoundLocInfo) {
        val stage = treasure.stage(player)
        val wantsMessage =
            stage == STAGE_KEY || (stage == STAGE_MESSAGE && !inv.contains(PIRATE_MESSAGE))
        if (!wantsMessage) {
            mes("You unlock the chest.")
            mes("The chest is empty.")
            return
        }
        if (invDel(inv, CHEST_KEY, 1).failure) {
            return
        }
        anim(OPEN_SEQ)
        soundSynth(OPEN_SOUND)
        locRepo.change(chest, HECTORS_CHEST_OPEN, OPEN_TICKS)
        mes("You unlock the chest.")
        delay(1)
        mes("All that's in the chest is a message...")
        invAddOrDrop(objRepo, PIRATE_MESSAGE)
        if (stage == STAGE_KEY) {
            treasure.quest.setQuestStage(this, STAGE_MESSAGE)
        }
        mes("You take the message from the chest.")
    }

    private fun ProtectedAccess.readMessage() {
        ifOpenMainModal(SCROLL_INTERFACE)
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.scroll:line$line", MESSAGE.getOrElse(line - 1) { "" })
        }
        soundSynth(PAPER_SOUND)
    }

    private suspend fun ProtectedAccess.openCasket() {
        val slotsNeeded = if (inv.contains(COINS)) 1 else 2
        if (inv.freeSpace() < slotsNeeded) {
            mes("You don't have enough inventory space to open the casket.")
            return
        }
        if (invDel(inv, CASKET, 1).failure) {
            return
        }
        invAdd(inv, COINS, CASKET_COINS)
        invAdd(inv, GOLD_RING)
        invAdd(inv, EMERALD)
        mesbox("You open the casket, and find One-Eyed Hector's treasure.")
    }

    private companion object {
        const val OPEN_SEQ = "seq.human_openchest"
        const val OPEN_SOUND = "synth.chest_open"
        const val PAPER_SOUND = "synth.paper_move"
        const val OPEN_TICKS = 50

        const val SCROLL_INTERFACE = "interface.scroll"
        const val SCROLL_LINES = 14

        const val GOLD_RING = "obj.gold_ring"
        const val EMERALD = "obj.emerald"
        const val CASKET_COINS = 450

        val MESSAGE =
            listOf(
                "",
                "",
                "",
                "",
                "",
                "Visit the city of the White Knights.",
                "In the park, Saradomin points to",
                "the X which marks the spot.",
            )
    }
}
