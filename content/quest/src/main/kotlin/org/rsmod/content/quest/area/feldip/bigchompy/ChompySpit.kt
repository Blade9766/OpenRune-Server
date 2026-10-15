package org.rsmod.content.quest.area.feldip.bigchompy

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CABBAGE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COOKED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COOKING_REQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COOK_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.DOOGLE_LEAVES
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.EQUA_LEAVES
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_CABBAGE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_DOOGLE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_ONION
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_UNSET
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.ONION
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.POTATO
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.RAW_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.ROAST_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.RUINED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SEASONED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SPIT_ROAST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SPIT_ROAST_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_COOKED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_KILLED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_TOLD_TO_COOK
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TABLE_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOMATO
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ogre spit-roast north of Rantz.
 *
 * The spit is one multiloc driven by `varbit.ogre_spit_roaster`, so each player sees their own
 * bird turning on it. During the quest the roast only succeeds when all three seasonings the ogre
 * family named are in the inventory; afterwards a plain chompy can be cooked on it like any other
 * food.
 */
class ChompySpit
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU(SPIT_ROAST, RAW_CHOMPY) { cook(it.vis) }
        for (seasoning in SEASONINGS) {
            onOpHeldU(RAW_CHOMPY, seasoning) { seasoningHint() }
        }
    }

    private suspend fun ProtectedAccess.seasoningHint() {
        mesbox(
            "You can only add ingredients when you're cooking a chompy bird. Ensure you have the " +
                "seasonings you want to use in your inventory when cooking the chompy bird and " +
                "you will use them automatically when cooking.",
        )
    }

    private suspend fun ProtectedAccess.cook(spit: BoundLocInfo) {
        arriveDelay()
        if (player.cookingLvl < COOKING_REQ) {
            mes("You need a cooking level of $COOKING_REQ to use the spit roast.")
            return
        }
        val stage = quest.stage(player)
        if (stage < STAGE_TOLD_TO_COOK) {
            if (stage >= STAGE_KILLED_CHOMPY) {
                mes("Rantz hasn't told you what his family want with their chompy yet.")
            } else {
                mes("Perhaps you'd better get permission before you use this?")
            }
            return
        }
        if (stage == STAGE_TOLD_TO_COOK) {
            cookSeasoned(spit)
            return
        }
        if (stage == STAGE_COOKED && invTotal(inv, SEASONED_CHOMPY) == 0) {
            cookSeasoned(spit)
            return
        }
        cookPlain(spit)
    }

    private suspend fun ProtectedAccess.cookSeasoned(spit: BoundLocInfo) {
        val wanted = seasoningsWanted() ?: return
        val missing = wanted.filter { invTotal(inv, it.obj) == 0 }
        if (missing.isNotEmpty()) {
            mesbox("You don't have all the ingredients yet to cook the chompy bird for the ogre family.")
            val (rantz, bugs, fycie) = wanted
            mesbox(
                "Remember what the Ogre family wanted? Rantz wanted ${rantz.label}. Fycie wanted " +
                    "${fycie.label}. Bugs wanted ${bugs.label}.",
            )
            return
        }
        if (!placeOnSpit(spit)) {
            return
        }
        val cooked = statRandom("stat.cooking", BURN_LOW, BURN_HIGH, invisibleBoost = 0)
        for (seasoning in wanted) {
            invDel(inv, seasoning.obj, 1)
        }
        if (!cooked) {
            burn(spit)
            return
        }
        mes("You add the other ingredients and cook the food.")
        turnSpit(spit, SPIT_COOKED)
        delay(2)
        anim(TABLE_SEQ)
        mes("Eventually the chompy is cooked.")
        invAdd(inv, SEASONED_CHOMPY)
        statAdvance("stat.cooking", SEASONED_XP)
        quest.advanceTo(this, STAGE_COOKED)
        delay(1)
        val (rantz, bugs, fycie) = wanted
        objbox(
            SEASONED_CHOMPY,
            OBJBOX_ZOOM,
            "You use the ${rantz.label}, ${bugs.label} and the ${fycie.label} with the chompy " +
                "bird to make a seasoned chompy.",
        )
        clearSpit()
    }

    private suspend fun ProtectedAccess.cookPlain(spit: BoundLocInfo) {
        if (!placeOnSpit(spit)) {
            return
        }
        if (!statRandom("stat.cooking", BURN_LOW, BURN_HIGH, invisibleBoost = 0)) {
            burn(spit)
            return
        }
        mes("You start to cook the chompy... it takes some time.")
        turnSpit(spit, SPIT_COOKED)
        delay(2)
        anim(TABLE_SEQ)
        mes("Eventually the chompy is cooked.")
        invAdd(inv, COOKED_CHOMPY)
        statAdvance("stat.cooking", PLAIN_XP)
        clearSpit()
    }

    private suspend fun ProtectedAccess.placeOnSpit(spit: BoundLocInfo): Boolean {
        if (invDel(inv, RAW_CHOMPY).failure) {
            return false
        }
        mes("You carefully place the chompy bird on the spit-roast.")
        turnSpit(spit, SPIT_RAW)
        anim(COOK_SEQ)
        soundSynth(SPIT_ROAST_SOUND)
        delay(2)
        return true
    }

    private suspend fun ProtectedAccess.burn(spit: BoundLocInfo) {
        mes("You accidentally burn the chompy.")
        turnSpit(spit, SPIT_RUINED)
        delay(2)
        anim(TABLE_SEQ)
        invAdd(inv, RUINED_CHOMPY)
        delay(1)
        clearSpit()
    }

    private fun ProtectedAccess.turnSpit(spit: BoundLocInfo, state: Int) {
        VarPlayerIntMapSetter.set(player, SPIT_VARBIT, state)
        locAnim(worldRepo, spit, ROAST_SEQ)
        anim(COOK_SEQ)
    }

    private fun ProtectedAccess.clearSpit() {
        VarPlayerIntMapSetter.set(player, SPIT_VARBIT, SPIT_EMPTY)
    }

    /** Rantz's, Bugs' and Fycie's seasonings, in that order, or null if one is still unknown. */
    private suspend fun ProtectedAccess.seasoningsWanted(): List<Seasoning>? {
        if (player.bugsFlavour == FLAVOUR_UNSET) {
            mesbox("You need to find what Bugs wants on the chompy bird before you can cook it.")
            return null
        }
        if (player.fycieFlavour == FLAVOUR_UNSET) {
            mesbox("You need to find what Fycie wants with her chompy bird before you can cook it.")
            return null
        }
        return listOf(
            if (player.rantzFlavour == FLAVOUR_ONION) Seasoning(ONION, "onion") else Seasoning(POTATO, "potato"),
            if (player.bugsFlavour == FLAVOUR_CABBAGE) {
                Seasoning(CABBAGE, "cabbage")
            } else {
                Seasoning(EQUA_LEAVES, "equa leaves")
            },
            if (player.fycieFlavour == FLAVOUR_DOOGLE) {
                Seasoning(DOOGLE_LEAVES, "doogle leaves")
            } else {
                Seasoning(TOMATO, "tomato")
            },
        )
    }

    private data class Seasoning(val obj: String, val label: String)

    private companion object {
        const val SPIT_VARBIT = "varbit.ogre_spit_roaster"

        const val SPIT_EMPTY = 0
        const val SPIT_RAW = 1
        const val SPIT_COOKED = 2
        const val SPIT_RUINED = 3

        /** The cooking roll the spit uses; low and high chances out of 255. */
        const val BURN_LOW = 200
        const val BURN_HIGH = 255

        const val SEASONED_XP = 142.0
        const val PLAIN_XP = 140.0

        const val OBJBOX_ZOOM = 250

        val SEASONINGS = listOf(ONION, POTATO, CABBAGE, TOMATO, EQUA_LEAVES, DOOGLE_LEAVES)
    }
}
