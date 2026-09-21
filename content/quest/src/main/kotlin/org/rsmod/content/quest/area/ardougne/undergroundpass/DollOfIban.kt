package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ASHES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOVE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN_BOOK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SHADOW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL_READY
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The four things of Iban's the doll needs before it will hold him. */
enum class Ingredient(val varbit: String, val label: String, val obj: String?) {
    ASHES_OF_IBAN("varbit.upass_ashes_on_doll", "his ashes", ASHES),
    BLOOD_OF_IBAN("varbit.upass_venom_on_doll", "his blood", null),
    SHADOW_OF_IBAN("varbit.upass_shadow_on_doll", "his shadow", SHADOW),
    DOVE_OF_IBAN("varbit.upass_dove_on_doll", "his dove", DOVE),
}

/**
 * The Doll of Iban, and the four things that have to go into it.
 *
 * Kardia made it years ago and never used it, which is the only reason Iban is still standing.
 * Each ingredient is taken where it is found and goes straight into the doll, so there is nothing
 * to carry but the doll itself; searching it says what is still missing. With all four in, the
 * doll is Iban in every way that matters to the Well of the Damned.
 */
@Singleton
class DollOfIban
@Inject
constructor(private val quest: UndergroundPassQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(DOLL) { searchDoll() }
        onOpHeld1(IBAN_BOOK) { readHistory() }
    }

    private suspend fun ProtectedAccess.searchDoll() {
        val missing = Ingredient.entries.filter { player.vars[it.varbit] == 0 }
        if (missing.isEmpty()) {
            mesbox(
                "The doll is heavy now, and warm, and it is breathing. It is as much Iban as the " +
                    "thing sitting in the temple is.",
            )
            return
        }
        val have = Ingredient.entries.filter { player.vars[it.varbit] == 1 }
        val lines = buildString {
            append("You turn the doll over in your hands.")
            if (have.isNotEmpty()) {
                append("<br><br>Inside it already: ")
                append(have.joinToString(", ") { it.label })
                append('.')
            }
            append("<br><br>Still wanting: ")
            append(missing.joinToString(", ") { it.label })
            append('.')
        }
        mesbox(lines)
    }

    private suspend fun ProtectedAccess.readHistory() {
        player.readIbanBook = 1
        mesbox(
            "<col=8B0000>The History of Iban</col><br><br>Iban was the son of Zamorak by a mortal " +
                "woman, and his father gave him this hill to keep. Nothing that lives can kill " +
                "him, for he is not wholly alive.",
        )
        mesbox(
            "<col=8B0000>The History of Iban</col><br><br>What is his may still be turned against " +
                "him: his ashes, his blood, his shadow and his dove. Put all four in his likeness " +
                "and cast it into the Well of the Damned beneath his own throne.",
        )
    }

    /**
     * Puts [ingredient] into the doll. The last one to go in finishes it, which is the step the
     * quest log and the temple both wait on.
     */
    suspend fun ProtectedAccess.addIngredient(ingredient: Ingredient) {
        if (player.vars[ingredient.varbit] == 1) {
            return
        }
        UndergroundPassQuest.setVarBit(player, ingredient.varbit, 1)
        mesbox("The doll takes ${ingredient.label} into itself.")
        if (Ingredient.entries.any { player.vars[it.varbit] == 0 }) {
            return
        }
        quest.advanceTo(this, STAGE_DOLL_READY)
        mesbox(
            "That is all four. The doll is warm, and when you hold it still you can feel " +
                "something in it that is not your own pulse.",
        )
    }

    /** True when the player is carrying the doll, which every ingredient needs. */
    fun ProtectedAccess.holdingDoll(): Boolean = invContains(inv, DOLL)
}
