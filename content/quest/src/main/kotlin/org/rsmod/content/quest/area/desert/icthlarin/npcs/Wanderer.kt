package org.rsmod.content.quest.area.desert.icthlarin.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.front
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.other.pets.cats.CatForm
import org.rsmod.content.quest.area.desert.icthlarin.CanopicJar
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinCats
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.FULL_WATERSKIN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.KLENTER_HAUNT_CYCLES
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.KLENTER_HAUNT_TIMER
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_GAVE_SUPPLIES
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_WOKE_IN_SOPHANEM
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.desert.icthlarin.SophanemCoords
import org.rsmod.content.quest.area.desert.icthlarin.ilhJar
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Wanderer, camped west of the Agility Pyramid: Amascut in disguise. She cannot abide the
 * player's cat, trades the secret of the tunnel into Sophanem for a full waterskin and a
 * tinderbox, and hypnotises the player into robbing Klenter's tomb.
 *
 * She is `npc.ics_little_multi_wanderer`, shown for stages 0-2, so her ops are bound on the multi.
 */
class Wanderer
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val cats: IcthlarinCats,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WANDERER) { startDialogue(it.npc) { talk() } }
        onOpNpcU(WANDERER) {
            val obj = it.objType.id
            if (obj != FULL_WATERSKIN.asRSCM(RSCMType.OBJ) && obj != TINDERBOX.asRSCM(RSCMType.OBJ)) {
                mes("Nothing interesting happens.")
                return@onOpNpcU
            }
            startDialogue(it.npc) { talk() }
        }
        onOpLoc1(TENT_FLAP) {
            startDialogue {
                chatPlayer(
                    confused,
                    "Yuck! It smells like there's dead cats in there or something. I'm not going in.",
                )
            }
        }
    }

    private suspend fun Dialogue.talk() {
        when (quest.stage(player)) {
            0 -> firstMeeting()
            STAGE_STARTED, STAGE_GAVE_SUPPLIES -> askForSupplies()
            else -> return
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        val cat = cats.followerPet(player)
        chatPlayer(happy, "Good day, wanderer.")
        chatNpc(neutral, "Hello to you too, adventurer.")
        if (cat == null) {
            chatNpc(neutral, "Now leave me be. I have a long journey ahead of me.")
            return
        }
        catHisses(cat)
        chatNpc(shocked, "Aghhh get that cat away from me. Quickly please.")
        val curious = choice2("Why? What's your problem with it?", true, "Sorry, I'll leave you alone.", false)
        if (!curious) {
            chatPlayer(neutral, "Sorry, I'll leave you alone.")
            return
        }
        chatPlayer(quiz, "Why? What's your problem with it?")
        chatNpc(worried, "Just take it away please. Take it away. If you do, I'll tell you of the secret passageway.")
        chatPlayer(quiz, "What secret passage?")
        chatNpc(worried, "Get the cat away from me first and then I'll tell you.")
        val pickedUp = with(cats) { access.pickUpFollower() }
        if (!pickedUp) {
            mesbox("You don't have enough room in your inventory to pick up your cat.")
            return
        }
        mesbox("You pick up your cat.")
        chatPlayer(quiz, "Okay, now where is this passage?")
        chatNpc(
            neutral,
            "It's by that group of rocks just to the north east, but you won't be able to enter. " +
                "Well, not yet anyway...",
        )
        chatPlayer(quiz, "Where does it lead to?")
        chatNpc(neutral, "To Sophanem, the city of the dead.")
        chatPlayer(quiz, "Sophanem?")
        chatNpc(neutral, "The second city of the Menaphites, full of giant monuments to their departed.")
        chatNpc(
            neutral,
            "The place is locked down at the moment, so the passage is the only way in. Apparently, " +
                "they've been hit by a set of plagues. That pathetic new High Priest of Icthlarin is " +
                "probably to blame.",
        )
        chatPlayer(quiz, "Interesting... Tell me about this Icthlarin.")
        chatNpc(angry, "No!")
        chatPlayer(neutral, "Oh. Well fair enough then.")
        chatPlayer(quiz, "So why do you say I won't be able to enter the passage?")
        chatNpc(
            neutral,
            "First bring me some supplies and then I'll tell you. I'm running low and I'm about to " +
                "embark on a long journey.",
        )
        chatPlayer(quiz, "What do you need?")
        chatNpc(
            neutral,
            "Get me a full waterskin and a tinderbox. I seem to have lost my own and these desert " +
                "nights are very cold.",
        )
        if (!quest.canStart(player)) {
            mesbox("You need to have completed Gertrude's Cat to start Icthlarin's Little Helper.")
            return
        }
        if (!choice2("Yes.", true, "No.", false, title = "Start the Icthlarin's Little Helper quest?")) {
            chatPlayer(neutral, "Sorry, I've got other things to do.")
            chatNpc(angry, "Fool! Only I can give you the knowledge on the secret passageway!")
            return
        }
        quest.advanceTo(access, STAGE_STARTED)
        if (!hasSupplies()) {
            chatPlayer(neutral, "Okay, I'll get you your supplies.")
            return
        }
        chatPlayer(happy, "Okay. I actually have those on me already.")
        hypnotise()
    }

    private suspend fun Dialogue.askForSupplies() {
        val cat = cats.followerPet(player)
        if (cat != null) {
            catHisses(cat)
            chatNpc(shocked, "Get that cat away from me!")
            return
        }
        chatNpc(neutral, "You again. Do you have my supplies?")
        if (!hasSupplies()) {
            chatPlayer(quiz, "What did you need again?")
            chatNpc(neutral, "Bring me a full waterskin and a tinderbox.")
            return
        }
        chatPlayer(happy, "Yes. I have them here.")
        hypnotise()
    }

    private suspend fun Dialogue.catHisses(cat: CatForm) {
        cats.follower(player)?.say("Hiss!")
        access.soundSynth(CAT_HISS)
        chatNpcSpecific("Cat", cat.npc, angry, "Hiss!")
    }

    private fun Dialogue.hasSupplies(): Boolean = FULL_WATERSKIN in player.inv && TINDERBOX in player.inv

    /**
     * "Look into my eyes..." The player hands over the supplies, loses the next stretch of their
     * memory, and comes to outside Klenter's pyramid holding one of his canopic jars.
     */
    private suspend fun Dialogue.hypnotise() {
        chatNpc(neutral, "Good... Now, look into my eyes...")
        chatNpc(neutral, "Look deeply into them...")
        chatNpc(neutral, "Don't blink...")
        with(access) {
            if (invDel(inv, FULL_WATERSKIN).failure || invDel(inv, TINDERBOX).failure) {
                return
            }
            ifClose()
            soundSynth(HYPNOTISE)
            fadeToBlack()
            val jar = chooseJar()
            player.ilhJar = jar.multi
            invAdd(inv, jar.obj)
            quest.advanceTo(this, STAGE_GAVE_SUPPLIES)
            telejump(SophanemCoords.PYRAMID_DOORSTEP, TeleportType.Exempt)
            delay(2)
            anim(GET_UP_SEQ)
            fadeFromBlack()
            closeFadeOverlay()
        }
        wakeUp()
    }

    private suspend fun Dialogue.wakeUp() {
        mesbox("You slowly pick yourself off the ground. Your head is pounding, and an irrational sense of guilt nags you.")
        chatPlayer(sad, "Ah, my poor head. What's going on?")
        chatPlayer(angry, "How did I get here? That wanderer... This is her doing!")
        quest.advanceTo(access, STAGE_WOKE_IN_SOPHANEM)
        access.softTimer(KLENTER_HAUNT_TIMER, KLENTER_HAUNT_CYCLES)
        access.soundSynth(SPECTRE_APPEAR)
        mesbox("You notice a ghostly figure making pained gestures in your direction.")
        if (player.front?.id in GHOSTSPEAK_AMULETS) {
            chatNpcSpecific("Klenter", KLENTER, angry, "You foul thief, return what is mine!")
            chatPlayer(confused, "What?")
            chatNpcSpecific("Klenter", KLENTER, angry, "You heard me! Restore what you have stolen from my tomb!")
            chatPlayer(worried, "But...")
        } else {
            chatNpcSpecific("Klenter", KLENTER, angry, "Wooo wooo wooooo!")
            mesbox(
                "The spirit tries to converse with you, but all you can understand is his anger, " +
                    "which is directed toward you.",
            )
        }
    }

    /**
     * The jar is chosen by the player's strongest discipline when they are hypnotised: melee
     * fighters take Het's jar, rangers Apmeken's and mages Scabaras's, so that the apparition that
     * later guards it is one they are poorly matched against. The choice is likely, not certain.
     */
    private fun ProtectedAccess.chooseJar(): CanopicJar {
        val melee = maxOf(statBase(ATTACK), statBase(STRENGTH))
        val ranged = statBase(RANGED)
        val magic = statBase(MAGIC)
        val likely =
            when {
                ranged > melee && ranged >= magic -> CanopicJar.Apmeken
                magic > melee && magic > ranged -> CanopicJar.Scabaras
                else -> CanopicJar.Het
            }
        if (random.of(100) < LIKELY_JAR_PERCENT) {
            return likely
        }
        val others = QUEST_JARS.filter { it != likely }
        return others[random.of(others.size)]
    }

    private companion object {
        const val WANDERER = "npc.ics_little_multi_wanderer"
        const val KLENTER = "npc.ics_little_spectre_vis"
        const val TENT_FLAP = "loc.icthalarins_tent_door"

        const val CAT_HISS = "synth.cat_hiss"
        const val HYPNOTISE = "synth.hypnotise"
        const val SPECTRE_APPEAR = "synth.ics_spectre_appear"
        const val GET_UP_SEQ = "seq.human_getup"

        const val ATTACK = "stat.attack"
        const val STRENGTH = "stat.strength"
        const val RANGED = "stat.ranged"
        const val MAGIC = "stat.magic"

        const val LIKELY_JAR_PERCENT = 70

        /** Crondis' jar has not been handed out since the 2022 rework of the quest. */
        val QUEST_JARS = listOf(CanopicJar.Het, CanopicJar.Apmeken, CanopicJar.Scabaras)

        val GHOSTSPEAK_AMULETS: Set<Int> by lazy {
            setOf("obj.amulet_of_ghostspeak", "obj.amulet_of_ghostspeak_enchanted")
                .map { it.asRSCM(RSCMType.OBJ) }
                .toSet()
        }
    }
}
