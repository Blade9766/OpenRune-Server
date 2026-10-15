package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dye handling for the quest: mixing two primary dyes into a secondary one, and dyeing goblin
 * mail. Any dye can go on any goblin mail, whatever colour it already is, so a mistake can be
 * painted over rather than costing a whole set.
 */
class GoblinDyes : PluginScript() {

    override fun ScriptContext.startup() {
        for ((pair, result) in MIXES) {
            onOpHeldU(pair.first, pair.second) { mix(pair.first, pair.second, result) }
        }
        for ((dye, colour) in DYE_COLOURS) {
            for (mail in ALL_MAIL) {
                if (mail == colour.mail) {
                    continue
                }
                onOpHeldU(dye, mail) { dyeMail(dye, mail, colour) }
            }
        }
    }

    private fun ProtectedAccess.mix(first: String, second: String, result: String) {
        if (invDel(inv, first).failure || invDel(inv, second).failure) {
            return
        }
        invAdd(inv, result)
        val name = result.substringAfter("obj.").removeSuffix("dye")
        mes("You mix the two dyes together and make some $name dye.")
    }

    private fun ProtectedAccess.dyeMail(dye: String, mail: String, colour: MailColour) {
        if (invDel(inv, dye).failure || invDel(inv, mail).failure) {
            return
        }
        invAdd(inv, colour.mail)
        mes("You dye the goblin mail ${colour.name}.")
    }

    private data class MailColour(val name: String, val mail: String)

    private companion object {
        const val RED_DYE = "obj.reddye"
        const val YELLOW_DYE = "obj.yellowdye"
        const val BLUE_DYE = "obj.bluedye"
        const val ORANGE_DYE = "obj.orangedye"
        const val GREEN_DYE = "obj.greendye"
        const val PURPLE_DYE = "obj.purpledye"

        /** Primary dye pairs and what they make. */
        val MIXES =
            mapOf(
                (RED_DYE to YELLOW_DYE) to ORANGE_DYE,
                (RED_DYE to BLUE_DYE) to PURPLE_DYE,
                (BLUE_DYE to YELLOW_DYE) to GREEN_DYE,
            )

        val DYE_COLOURS =
            mapOf(
                RED_DYE to MailColour("red", "obj.goblin_armour_red"),
                YELLOW_DYE to MailColour("yellow", "obj.goblin_armour_yellow"),
                BLUE_DYE to MailColour("blue", "obj.goblin_armour_darkblue"),
                ORANGE_DYE to MailColour("orange", "obj.goblin_armour_orange"),
                GREEN_DYE to MailColour("green", "obj.goblin_armour_green"),
                PURPLE_DYE to MailColour("purple", "obj.goblin_armour_purple"),
            )

        val ALL_MAIL = listOf(GoblinDiplomacyQuest.GOBLIN_MAIL) + DYE_COLOURS.values.map { it.mail }
    }
}
