package org.rsmod.content.quest.area.varrock.familycrest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.JOHNATHON
import org.rsmod.content.quest.area.varrock.familycrest.johnathonAsked
import org.rsmod.content.quest.area.varrock.familycrest.johnathonCured
import org.rsmod.content.quest.area.varrock.familycrest.johnathonDone
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The youngest Fitzharmon, upstairs in the Jolly Boar Inn. A poison spider bit him on his way
 * back from Edgeville Dungeon and he will not hold a conversation until it is cured; the demon
 * Chronozon took his piece of the crest while he lay there.
 */
class Johnathon @Inject constructor(private val familyCrest: FamilyCrestQuest) : PluginScript() {

    /** Each dose of a poison cure mapped to what is left after Johnathon drinks one. */
    private val antipoisons: Map<String, String> by lazy {
        buildMap {
            for (doses in POISON_CURES) {
                for ((index, dose) in doses.withIndex()) {
                    put(dose, doses.getOrElse(index + 1) { EMPTY_VIAL })
                }
            }
        }
    }

    override fun ScriptContext.startup() {
        onOpNpc1(JOHNATHON) { startDialogue(it.npc) { johnathon() } }
        onOpNpcU(JOHNATHON) { useOnJohnathon(it.npc, it.objType.internalName, it.invSlot) }
    }

    private suspend fun Dialogue.johnathon() {
        when {
            !familyCrest.isStarted(player) -> chatNpc(sad, "Leave me be...")
            player.johnathonDone -> chatNpc(happy, "That demon owed me. Thanks for collecting.")
            !player.johnathonCured -> poisoned()
            !player.johnathonAsked -> aboutChronozon()
            else -> chronozonReminder()
        }
    }

    private suspend fun Dialogue.poisoned() {
        chatNpc(sad, "Urgh... my leg...")
        chatPlayer(quiz, "Are you Johnathon Fitzharmon?")
        chatNpc(
            sad,
            "I am, and I'm dying. A spider got me down in the Edgeville dungeon and the poison " +
                "hasn't let up since.",
        )
        chatPlayer(quiz, "Can I do anything?")
        chatNpc(
            sad,
            "Antipoison. Anything that cures poison. Bring me some and I'll talk about whatever " +
                "you like.",
        )
    }

    private suspend fun ProtectedAccess.useOnJohnathon(npc: Npc, obj: String, slot: Int) {
        if (!familyCrest.isStarted(player)) {
            mes("Nothing interesting happens.")
            return
        }
        val remainder = antipoisons[obj]
        if (remainder == null) {
            mes("Johnathon has no use for that.")
            return
        }
        if (player.johnathonCured) {
            mes("Johnathon's poisoning is already cured.")
            return
        }
        if (invDel(inv, obj, slot = slot).failure) {
            return
        }
        invAdd(inv, remainder)
        player.johnathonCured = true
        familyCrest.syncStage(this)
        startDialogue(npc) { cured() }
    }

    private suspend fun Dialogue.cured() {
        mesbox("Johnathon drinks the potion in one.")
        chatNpc(happy, "Oh, that's better. That's much better.")
        aboutChronozon()
    }

    private suspend fun Dialogue.aboutChronozon() {
        chatPlayer(neutral, "Your father wants the family crest put back together.")
        chatNpc(
            worried,
            "Then he'll want a word with a demon. I had my third with me in the dungeon when " +
                "Chronozon came out of the dark and took it off me.",
        )
        chatPlayer(quiz, "Chronozon?")
        chatNpc(
            worried,
            "A blood demon, down past the poison spiders by the earth obelisk. He is not killed " +
                "the way other things are killed.",
        )
        chatNpc(
            neutral,
            "He is bound to all four elements at once. Strike him with wind, water, earth and " +
                "fire - the blast spells, nothing weaker - and only then will he stay down.",
        )
        player.johnathonAsked = true
        familyCrest.syncStage(access)
    }

    private suspend fun Dialogue.chronozonReminder() {
        chatNpc(quiz, "Have you dealt with the demon?")
        chatPlayer(sad, "Not yet.")
        chatNpc(
            neutral,
            "Wind blast, water blast, earth blast and fire blast. All four have to land on him, " +
                "or he simply knits himself back together.",
        )
    }

    private companion object {
        const val EMPTY_VIAL = "obj.vial_empty"

        /** Every poison cure Johnathon accepts, highest dose first within each family. */
        val POISON_CURES =
            listOf(
                listOf(
                    "obj.4doseantipoison",
                    "obj.3doseantipoison",
                    "obj.2doseantipoison",
                    "obj.1doseantipoison",
                ),
                listOf(
                    "obj.4dose2antipoison",
                    "obj.3dose2antipoison",
                    "obj.2dose2antipoison",
                    "obj.1dose2antipoison",
                ),
                listOf("obj.antidote+4", "obj.antidote+3", "obj.antidote+2", "obj.antidote+1"),
                listOf("obj.antidote++4", "obj.antidote++3", "obj.antidote++2", "obj.antidote++1"),
                listOf(
                    "obj.sanfew_salve_4_dose",
                    "obj.sanfew_salve_3_dose",
                    "obj.sanfew_salve_2_dose",
                    "obj.sanfew_salve_1_dose",
                ),
                listOf("obj.brutal_2doseantipoison", "obj.brutal_1doseantipoison"),
                listOf("obj.brutal_2dose2antipoison", "obj.brutal_1dose2antipoison"),
                listOf("obj.brutal_antidote+2", "obj.brutal_antidote+1"),
            )
    }
}
