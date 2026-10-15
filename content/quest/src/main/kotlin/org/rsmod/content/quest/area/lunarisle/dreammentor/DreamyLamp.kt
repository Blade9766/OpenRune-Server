package org.rsmod.content.quest.area.lunarisle.dreammentor

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.DREAMY_LAMP
import org.rsmod.content.quest.manager.chooseLampSkill
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The dreamy lamp from Dream Mentor: one wish of [LAMP_XP] experience in Strength, Defence,
 * Ranged, Magic or Hitpoints. The lamp is taken before the experience is given and
 * `varbit.dream_lampused` is set with it, so the wish can't be granted twice.
 */
class DreamyLamp : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(DREAMY_LAMP) { rub() }
    }

    private suspend fun ProtectedAccess.rub() {
        if (player.dreamLampUsed) {
            invDel(inv, DREAMY_LAMP)
            mes("The lamp's magic has already been spent, and it crumbles away.")
            return
        }
        val stat = chooseLampSkill("Choose the stat you wish to be advanced!", stats = LAMP_STATS) ?: return
        if (player.dreamLampUsed || invDel(inv, DREAMY_LAMP).failure) {
            return
        }
        player.dreamLampUsed = true
        statAdvance(stat, LAMP_XP)
        mes("Your wish has been granted!")
    }

    companion object {
        const val LAMP_XP = 15_000.0

        val LAMP_STATS = listOf("stat.strength", "stat.defence", "stat.ranged", "stat.magic", "stat.hitpoints")
    }
}
