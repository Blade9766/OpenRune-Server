package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.BANK_BOOTH
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.BANK_WALL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.COAL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.FURNACE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.FURNACE_FUELLED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.FURNACE_REPAIRED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BANK_OPEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_REPAIRS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_STOCKED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STORE_WALL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.TINDERBOX
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The store's wall, the bank and the furnace. Each is a cache multiloc on its own varbit, so a
 * repair changes the town for the player who made it and nobody else.
 *
 * The furnace keeps its three steps apart (`burgh_furnace_fix` 1 repaired, 2 fuelled, 3 lit).
 * Lighting it moves the quest on before the Castle Drakan scene plays, so an interrupted scene
 * neither loses progress nor asks for the coal again.
 */
class BurghRepairs
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val repairs: Repairs,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(WALL_CLICKZONE) {
            arriveDelay()
            if (it.loc.coords == BurghCoords.BANK_WALL) bankWall() else storeWall()
        }
        onOpLoc1(BOOTH_DAMAGED) {
            arriveDelay()
            with(repairs) {
                repair(
                    describe = "This bank booth is in an awful condition. There's a lot of broken glass and most of the wooden structure of the booth has been destroyed.",
                    question = "Fix the booth?",
                    allowed = iaom.stage(player) >= STAGE_STORE_STOCKED,
                    cost = Repairs.Cost(planks = 2, nails = 8, swampPaste = 1),
                    varbit = BANK_BOOTH,
                    done = "You use some swamp paste to fix the bits of glass back together and then repair the structure of the booth with some wooden planks and nails.",
                )
            }
        }
        onOpLoc1(BOOTH_REPAIRED) {
            arriveDelay()
            if (iaom.isBankOpen(player)) {
                tryOpenBank()
            } else {
                mesbox("While the booth looks functional, there still isn't anyone working behind the booth to serve you.")
            }
        }
        onOpLoc1(DEPOSIT_BOX) {
            arriveDelay()
            if (iaom.isBankOpen(player)) {
                ifOpenMainModal(DEPOSIT_INTERFACE)
            } else {
                mes("There's nobody running the bank yet.")
            }
        }
        onOpLoc1(FURNACE_BROKEN) {
            arriveDelay()
            with(repairs) {
                repair(
                    describe = "There's a large hole in the chimney of the furnace which is probably why it doesn't work.",
                    question = "Fix the chimney?",
                    allowed = iaom.stage(player) >= STAGE_BANK_OPEN,
                    cost = Repairs.Cost(steelBars = 2),
                    varbit = FURNACE,
                    value = FURNACE_REPAIRED,
                    done = "You use some steel bars to fix the hole in the furnace chimney.",
                )
            }
        }
        onOpLoc1(FURNACE_REPAIRED_LOC) { refuel() }
        onOpLoc1(FURNACE_COAL_LOC) { light() }
    }

    private suspend fun ProtectedAccess.storeWall() {
        with(repairs) {
            repair(
                describe = WALL_TEXT,
                question = "Fix the wall?",
                allowed = iaom.stage(player) >= STAGE_STORE_REPAIRS,
                cost = Repairs.Cost(planks = 3, nails = 12),
                varbit = STORE_WALL,
                done = "You use some wooden planks and nails to fix up the wall.",
            )
        }
    }

    private suspend fun ProtectedAccess.bankWall() {
        with(repairs) {
            repair(
                describe = WALL_TEXT,
                question = "Fix the wall?",
                allowed = iaom.stage(player) >= STAGE_STORE_STOCKED,
                cost = Repairs.Cost(planks = 3, nails = 12),
                varbit = BANK_WALL,
                done = "You use some wooden planks and nails to fix up the wall.",
            )
        }
    }

    private suspend fun ProtectedAccess.refuel() {
        arriveDelay()
        mesbox("The furnace has been repaired now. However, it needs some fuel before it can be used.")
        if (!confirm("Refuel the furnace?")) return
        if (COAL !in inv) {
            objbox(COAL, "You need some coal to refuel the furnace.")
            return
        }
        if (player.vars[FURNACE] != FURNACE_REPAIRED || invDel(inv, COAL).failure) {
            return
        }
        VarPlayerIntMapSetter.set(player, FURNACE, FURNACE_FUELLED)
        anim(FUEL_SEQ)
        objbox(COAL, "You place some coal into the newly fixed furnace.")
    }

    private suspend fun ProtectedAccess.light() {
        arriveDelay()
        mesbox("The furnace has been repaired and refuelled. It just needs lighting now.")
        if (!confirm("Light the furnace?")) return
        if (TINDERBOX !in inv) {
            objbox(TINDERBOX, "You need a tinderbox to light the furnace.")
            return
        }
        if (player.vars[FURNACE] != FURNACE_FUELLED) {
            return
        }
        VarPlayerIntMapSetter.set(player, FURNACE, FURNACE_LIT)
        iaom.advanceTo(this, STAGE_FURNACE_LIT)
        anim(LIGHT_SEQ)
        soundSynth(LIGHT_SOUND)
        objbox(TINDERBOX, "You light the furnace...")
        drakanScene()
    }

    /**
     * Vanstrom and Gadderanks at Castle Drakan. The castle is far outside the player's loaded map,
     * so the scene is told over a black screen rather than with a camera fly-over.
     */
    private suspend fun ProtectedAccess.drakanScene() {
        try {
            hideEntityOps()
            minimapHideMap()
            fadeOverlay(startColour = 0, startTransparency = 255, endColour = 0, endTransparency = 0, clientDuration = FADE_DURATION)
            delay(FADE_TICKS)
            startDialogue {
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, neutral, "Gadderanks.")
                chatNpcSpecific(GADDERANKS_NAME, GADDERANKS, worried, "My lord... We've finished the latest round of tithing. I'm afraid we haven't collected the full amount.")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, neutral, "I saw your wife today, Gadderanks. She's well.")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, neutral, "I hope she stays that way.")
                chatNpcSpecific(GADDERANKS_NAME, GADDERANKS, worried, "My lord Vanstrom, there are precious few human settlements left worth tithing.")
                chatNpcSpecific(GADDERANKS_NAME, GADDERANKS, worried, "Mort'ton is a dead town full of the afflicted, and Canifis alone can't make up the difference. Where am I to find the rest?")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, angry, "Your problems do not interest me, Gadderanks. Just do the job you were given.")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, quiz, "Wait... do you smell that? Smoke. It's coming from Burgh de Rott.")
                chatNpcSpecific(GADDERANKS_NAME, GADDERANKS, confused, "But Burgh de Rott is abandoned.")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, neutral, "Burgh de Rott was abandoned. That is clearly no longer the case.")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM, laugh, "It seems you have a fresh appointment, Gadderanks. I trust there will be no more delays.")
            }
        } finally {
            closeFadeOverlay()
            showEntityOps()
            minimapReset()
        }
        startDialogue { chatNpcSpecific(GABRIELA_NAME, GABRIELA, shocked, "It's Gadderanks! He's found us!") }
    }

    private suspend fun ProtectedAccess.confirm(question: String): Boolean {
        var yes = false
        startDialogue { yes = choice2("Yes.", true, "No.", false, title = question) }
        return yes
    }

    internal companion object {
        const val WALL_CLICKZONE = "loc.burgh_boared_up_wall_clickzone"
        const val BOOTH_DAMAGED = "loc.burgh_bankbooth_damaged"
        const val BOOTH_REPAIRED = "loc.burgh_bankbooth_repaired"
        const val DEPOSIT_BOX = "loc.burgh_bank_deposit_box"
        const val DEPOSIT_INTERFACE = "interface.bank_depositbox"
        const val FURNACE_BROKEN = "loc.burgh_furnace_broken"
        const val FURNACE_REPAIRED_LOC = "loc.burgh_furnace_repaired"
        const val FURNACE_COAL_LOC = "loc.burgh_furnace_coal_loaded"

        const val WALL_TEXT =
            "The wall has a large hole in it surrounded by rotten wood and rusty metal. It looks " +
                "like it was once boarded up with nails and wooden planks."

        const val VANSTROM = "npc.route_vanstrom_klause_sitting"
        const val VANSTROM_NAME = "Vanstrom Klause"
        const val GADDERANKS = "npc.burgh_gadderanks_cutscene"
        const val GADDERANKS_NAME = "Gadderanks"
        const val GABRIELA = "npc.burgh_bed_man_wife"
        const val GABRIELA_NAME = "Gabriela"

        const val FUEL_SEQ = "seq.human_pickuptable"
        const val LIGHT_SEQ = "seq.human_pickuptable"
        const val LIGHT_SOUND = "synth.fire_lit"
        const val FADE_DURATION = 60
        const val FADE_TICKS = 2
    }
}
