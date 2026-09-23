package org.rsmod.content.quest.area.varrock.romeojuliet

import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The cadava potion's own ops. Drinking it is allowed only inside the Apothecary's shop, where he
 * can revive the player once the swooning is over.
 */
class CadavaPotion : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(CADAVA_POTION) { lookAt() }
        onOpHeld2(CADAVA_POTION) { drink() }
    }

    private suspend fun ProtectedAccess.lookAt() {
        objbox(
            CADAVA_POTION,
            "It's very colourful, but you remember Father Lawrence's warning. Probably best not " +
                "to drink it.",
        )
    }

    private suspend fun ProtectedAccess.drink() {
        if (!inApothecaryShop()) {
            objbox(
                CADAVA_POTION,
                "You daren't drink this outside the Apothecary's shop. At least there he might be " +
                    "able to help you if something goes wrong.",
            )
            return
        }
        val drink =
            choice2(
                "Yes, I'll drink it all down.",
                true,
                "No, I'm having second thoughts now.",
                false,
                title = "Are you sure you wish to drink this Cadava Potion?",
            )
        if (!drink) {
            mes("You decide against drinking the potion.")
            return
        }
        invDel(inv, CADAVA_POTION)
        objbox(CADAVA_POTION, "Ignoring Father Lawrence's warnings, you drink the whole potion.")
        anim(DRINK_SEQ)
        spotanim(DRINK_SPOTANIM)
        soundSynth(DRINK_SOUND)
        delay(DRINK_TICKS)
        camShake(CamShakeAxis.PAN_LEFT_RIGHT, random = 0, amplitude = SWAY_AMPLITUDE, rate = SWAY_RATE)
        say("Urk!")
        mes("Oh dear... you are nearly dead.")
        anim(COLLAPSE_SEQ)
        delay(COLLAPSE_TICKS)
        fadeToBlack()
        camShakeResetAll()
        resetAnim()
        mesbox("Some time later.....")
        fadeFromBlack()
        closeFadeOverlay()
        startDialogue {
            chatNpcSpecific(
                "Apothecary",
                APOTHECARY,
                shocked,
                "Blimey... you've been out cold for hours! Are you alright? Customers kept " +
                    "tripping over you on the way in.",
            )
        }
    }

    private fun ProtectedAccess.inApothecaryShop(): Boolean {
        val coords = player.coords
        return coords.level == 0 && coords.x in SHOP_X && coords.z in SHOP_Z
    }

    private companion object {
        const val DRINK_SEQ = "seq.human_drink_from_vial_cadava"
        const val DRINK_SPOTANIM = "spotanim.human_drink_from_vial_cadava_spotanim"
        const val DRINK_SOUND = "synth.drink"
        const val COLLAPSE_SEQ = "seq.human_death"
        const val DRINK_TICKS = 3
        const val COLLAPSE_TICKS = 3
        const val SWAY_AMPLITUDE = 12
        const val SWAY_RATE = 2

        val SHOP_X = 3192..3198
        val SHOP_Z = 3402..3406
    }
}
