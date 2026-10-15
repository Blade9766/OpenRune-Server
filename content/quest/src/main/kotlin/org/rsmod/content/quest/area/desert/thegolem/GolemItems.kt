package org.rsmod.content.quest.area.desert.thegolem

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.FEATHER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.INK
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.MUSHROOM
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PAPYRUS
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PEN
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PROGRAM
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_GOLEM_UNCONVINCED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.VIAL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Making the golem's new instructions: black mushrooms crushed into black dye, a phoenix feather
 * dipped in it for a quill, and the quill used to write on papyrus. The program can only be
 * written once the golem has refused to believe the demon is dead.
 */
class GolemItems @Inject constructor(private val golem: TheGolemQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(PESTLE_AND_MORTAR, MUSHROOM) { crushMushroom() }
        onOpHeldU(FEATHER, INK) { dipFeather() }
        onOpHeldU(PEN, PAPYRUS) { writeProgram() }
        onOpHeldU(FEATHER, PAPYRUS) { mes("You will need some kind of ink to write with.") }
        onOpHeld4(INK) { emptyDye(it.slot) }
    }

    private suspend fun ProtectedAccess.crushMushroom() {
        anim(GRIND_SEQ)
        if (invDel(inv, MUSHROOM).failure) {
            return
        }
        if (invDel(inv, VIAL).failure) {
            mesbox("You crush the mushroom, but you have no vial to put the dye in and it goes everywhere!")
            return
        }
        invAdd(inv, INK)
        mesbox("You crush the mushroom and pour the juice into a vial.")
    }

    private suspend fun ProtectedAccess.dipFeather() {
        if (invDel(inv, FEATHER).failure) {
            return
        }
        if (invDel(inv, INK).failure) {
            invAdd(inv, FEATHER)
            return
        }
        invAdd(inv, PEN)
        objbox(PEN, "You dip the phoenix feather into the dye.")
    }

    private suspend fun ProtectedAccess.writeProgram() {
        if (golem.stage(player) < STAGE_GOLEM_UNCONVINCED) {
            mes("You don't know what to write.")
            return
        }
        if (invDel(inv, PAPYRUS).failure) {
            return
        }
        invAdd(inv, PROGRAM)
        objbox(PROGRAM, "You write on the papyrus:<br>YOUR TASK IS DONE")
    }

    private fun ProtectedAccess.emptyDye(slot: Int) {
        if (invDel(inv, INK, slot = slot).failure) {
            return
        }
        invAdd(inv, VIAL)
        mes("You empty the dye out of the vial.")
    }

    private companion object {
        const val GRIND_SEQ = "seq.human_herbing_grind"
    }
}
