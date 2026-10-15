package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_AWAKE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SITTING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STANDING
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Guidance System (`interface.dream_cyrisus`): Cyrisus' health, spirit and armament bars with
 * their markers, a chathead showing how he looks, and an explanation of each stat when its name is
 * clicked. The interface has no scripts of its own, so the server slides each bar into place.
 */
class CyrisusStatus @Inject constructor(private val dreamMentor: DreamMentorQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        for (stat in Stat.entries) {
            onIfModalButton(stat.label) { explain(stat) }
        }
        onIfModalButton(CLOSE) { ifClose() }
    }

    fun ProtectedAccess.openStatus() {
        ifOpenMainModal(INTERFACE)
        for (stat in Stat.entries) {
            val percent = stat.value(this).coerceIn(0, CyrisusRecovery.FULL)
            player.ifSetPosition(stat.bar, BAR_EMPTY_X + percent * BAR_WIDTH / CyrisusRecovery.FULL, stat.barY)
            ifSetText(stat.text, "$percent%")
            ifSetEvents(stat.label, -1..-1, IfEvent.Op1)
        }
        val stage = dreamMentor.stage(player)
        val face =
            when {
                stage < STAGE_AWAKE -> FACE_UNCONSCIOUS
                stage < STAGE_SITTING -> FACE_VERY_ILL
                stage < STAGE_STANDING -> FACE_ILL
                else -> FACE_NORMAL
            }
        for (layer in FACES) {
            ifSetHide(layer, layer != face)
        }
        ifSetText(INFO_TITLE, "--- Stat ---")
        ifSetText(INFO, "Click on the name of a stat to find out more about it.")
        ifSetEvents(CLOSE, -1..-1, IfEvent.Op1)
    }

    private fun ProtectedAccess.explain(stat: Stat) {
        ifSetText(INFO_TITLE, "--- ${stat.title} ---")
        ifSetText(INFO, stat.info)
    }

    private enum class Stat(
        val title: String,
        val label: String,
        val bar: String,
        val barY: Int,
        val text: String,
        val info: String,
        val value: (ProtectedAccess) -> Int,
    ) {
        Health(
            "Health",
            "component.dream_cyrisus:dream_health_name",
            "component.dream_cyrisus:dream_health_bar",
            6,
            "component.dream_cyrisus:dream_health_text",
            "His body is starved and battered. Use food on him to build up his strength, but " +
                "he won't eat the same kind of food twice in a row.",
            { it.player.dreamHealth },
        ),
        Spirit(
            "Spirit",
            "component.dream_cyrisus:dream_spirit_name",
            "component.dream_cyrisus:dream_spirit_bar",
            8,
            "component.dream_cyrisus:dream_spirit_text",
            "He has all but given up. Talk to him and pick the most encouraging things to say to " +
                "lift his spirits.",
            { it.player.dreamSpirit },
        ),
        Armament(
            "Armament",
            "component.dream_cyrisus:dream_armament_name",
            "component.dream_cyrisus:dream_armament_bar",
            6,
            "component.dream_cyrisus:dream_armament_text",
            "He has nothing to protect himself with. He will need a full set of armour and a " +
                "weapon that suit him before he leaves the cave.",
            { it.player.dreamArmament },
        ),
    }

    companion object {
        const val INTERFACE = "interface.dream_cyrisus"
        const val CLOSE = "component.dream_cyrisus:cyrisusclose"
        const val INFO_TITLE = "component.dream_cyrisus:info"
        const val INFO = "component.dream_cyrisus:dream_info_box"

        /** The bars are 200px rectangles slid left out of their 210px layers: x = -195 shows 0%. */
        const val BAR_EMPTY_X = -195
        const val BAR_WIDTH = 200

        const val FACE_UNCONSCIOUS = "component.dream_cyrisus:cyrisus_chathead_unconcious"
        const val FACE_VERY_ILL = "component.dream_cyrisus:cyrisus_chathead_very_ill"
        const val FACE_ILL = "component.dream_cyrisus:cyrisus_chathead_ill"
        const val FACE_NORMAL = "component.dream_cyrisus:cyrisus_chathead_normal"
        val FACES = listOf(FACE_UNCONSCIOUS, FACE_VERY_ILL, FACE_ILL, FACE_NORMAL)
    }
}
