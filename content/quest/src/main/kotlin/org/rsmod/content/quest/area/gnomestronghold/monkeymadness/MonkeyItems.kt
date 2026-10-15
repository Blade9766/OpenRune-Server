package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.BALL_OF_WOOL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.BANANA
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.DENTURES
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ENCHANTED_BAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_IN_BACKPACK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SPARE_CONTROLS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.UNSTRUNG_AMULET
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The quest's odds and ends: stringing the amulet, listening to enchanted things, poking the monkey. */
class MonkeyItems @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(UNSTRUNG_AMULET, BALL_OF_WOOL) { stringAmulet() }
        onOpHeld4(DENTURES) { listen("The dentures chatter quietly to themselves. You can't make out the words.") }
        onOpHeld4(ENCHANTED_BAR) { listen("The bar hums with the same chatter as the dentures, only louder.") }
        onOpHeld1(SPARE_CONTROLS) { viewControls() }
        onOpHeld1(MONKEY_IN_BACKPACK) { pokeMonkey() }
        onOpHeldU(MONKEY_IN_BACKPACK, BANANA) { feedMonkey() }
    }

    private suspend fun ProtectedAccess.stringAmulet() {
        invDel(player.inv, UNSTRUNG_AMULET)
        invDel(player.inv, BALL_OF_WOOL)
        invAdd(player.inv, AMULET)
        objbox(AMULET, "You string the amulet with the ball of wool. Wearing it should let you talk to the monkeys.")
    }

    private suspend fun ProtectedAccess.listen(text: String) {
        soundSynth(MonkeyMadness.SOUND_OOKS)
        mesbox(text)
    }

    /** Shows the finished picture on the puzzle interface, without any pieces to slide. */
    private suspend fun ProtectedAccess.viewControls() {
        val inv = inv(Hangar.PUZZLE_INV_INACTIVE)
        invClear(inv)
        for ((slot, letter) in Hangar.SOLVED.withIndex()) {
            if (letter != Hangar.GAP) {
                invAdd(inv, Hangar.INACTIVE_PIECES_OBJS[letter - Hangar.FIRST_PIECE], slot = slot)
            }
        }
        VarPlayerIntMapSetter.set(player, Hangar.PUZZLE_MODE_VARP, Hangar.PUZZLE_MODE_INACTIVE)
        invTransmit(inv)
        ifOpenMainModal(Hangar.PUZZLE_INTERFACE)
        mes("The spare controls show the picture the panel should form.")
    }

    private suspend fun ProtectedAccess.pokeMonkey() {
        soundSynth(MonkeyMadness.SOUND_MONKEY_CALLS)
        val line = MONKEY_LINES[random.of(MONKEY_LINES.size)]
        mesbox(line)
    }

    private suspend fun ProtectedAccess.feedMonkey() {
        invDel(player.inv, BANANA)
        soundSynth(MonkeyMadness.SOUND_OOKS)
        mesbox("The monkey snatches the banana, peels it in one movement and eats it in two. It looks at you expectantly.")
    }

    private companion object {
        val MONKEY_LINES =
            listOf(
                "The monkey pokes you back.",
                "The monkey mutters something about bananas.",
                "The monkey rearranges the contents of your backpack and goes back to sleep.",
                "The monkey asks whether you are there yet.",
            )
    }
}
