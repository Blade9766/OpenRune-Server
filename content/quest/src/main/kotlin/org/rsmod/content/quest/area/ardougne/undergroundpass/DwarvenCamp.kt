package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BUCKET
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DWARF_BREW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.GAUNTLETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KAMEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KLANK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.NILOOF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BIGFIRE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DWARVES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.TINDERBOX
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Niloof, Klank and Kamen: three dwarves who came down the Well of Voyage years ago, found it
 * corrupted behind them, and have been living in Iban's cellar ever since.
 *
 * Niloof knows the only thing about Iban that matters - that the witch Kardia keeps something of
 * his - Klank hands over the gauntlets that make the blessed spiders survivable, and Kamen runs
 * what is left of the camp's kitchen.
 *
 * Their brew is strong enough to burn, which is what Iban's tomb wants.
 */
@Singleton
class DwarvenCamp
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val random: GameRandom,
    private val doll: DollOfIban,
) : PluginScript() {

    private val burntTombType by lazy {
        ServerCacheManager.getObject(BURNT_TOMB.asRSCM(RSCMType.LOC)) ?: error("Missing $BURNT_TOMB")
    }

    override fun ScriptContext.startup() {
        onOpNpc1(NILOOF) { startDialogue(it.npc) { niloof() } }
        onOpNpc1(KLANK) { startDialogue(it.npc) { klank() } }
        onOpNpc1(KAMEN) { startDialogue(it.npc) { kamen() } }

        onOpLoc1(BREW_BARREL) { searchBarrel() }
        onOpLocU(BREW_BARREL, BUCKET) { fillBucket() }
        for (tomb in TOMBS) {
            onOpLoc1(tomb) { searchTomb() }
            onOpLocU(tomb, DWARF_BREW) { pourBrew(tomb) }
            onOpLocU(tomb, TINDERBOX) { lightTomb(tomb) }
        }
    }

    /* Dialogue */

    private suspend fun Dialogue.niloof() {
        if (quest.isComplete(player)) {
            chatNpc(happy, "You did for him, then. We felt it come down. We'll be going home soon.")
            return
        }
        chatNpc(quiz, "And what would you be? Not one of his, by the look of you.")
        chatPlayer(neutral, "I'm here to kill Iban.")
        chatNpc(laugh, "Kill him! Oh, that's a good one. Half of Ardougne's chivalry is lying in his hallway.")
        chatNpc(neutral, "We came down the Well of Voyage after him, my brothers and I, and it shut behind us. It's corrupted. There's no going back up it while he's alive.")
        chatPlayer(quiz, "So how do I kill him?")
        chatNpc(neutral, "You don't. Not with a sword. There's a witch lives out over the bridges, Kardia. Mad as a bag of frogs and she keeps something of his in her house.")
        chatNpc(neutral, "A doll. Made in his likeness. She'll not part with it and she'll not tell you why, so don't ask her - get her out of the house and take it.")
        if (!player.niloofTold) {
            player.niloofTold = true
            quest.advanceTo(access, STAGE_DWARVES)
        }
        chatPlayer(neutral, "Thank you, Niloof.")
        chatNpc(neutral, "Talk to Klank before you go out there. Those spiders will have the hands off you.")
    }

    private suspend fun Dialogue.klank() {
        if (player.gauntletsGiven && !access.invContains(access.inv, GAUNTLETS) &&
            !access.player.worn.contains(GAUNTLETS)
        ) {
            chatNpc(neutral, "Lost them, did you? Here, take another pair. I've time on my hands.")
            if (access.invAdd(access.inv, GAUNTLETS).success) {
                access.mes("Klank hands you another pair of gauntlets.")
            }
            return
        }
        if (player.gauntletsGiven) {
            chatNpc(happy, "Keep them on out there. The spiders aren't the worst of it.")
            return
        }
        chatNpc(quiz, "You're going out over the bridges, are you?")
        chatPlayer(neutral, "I am.")
        chatNpc(neutral, "Then you'll want these. Blessed spiders, out that way - they don't bite so much as burn, and bare hands won't do.")
        if (access.invAdd(access.inv, GAUNTLETS).failure) {
            chatNpc(neutral, "You've nowhere to put them. Come back when you have.")
            return
        }
        player.klankTold = true
        player.gauntletsGiven = true
        access.objbox(GAUNTLETS, "Klank gives you his gauntlets.")
        chatNpc(neutral, "Don't take them off for the half-soulless in the north, either. They've a touch on them.")
    }

    private suspend fun Dialogue.kamen() {
        chatNpc(happy, "Hungry? Everyone's hungry down here.")
        when (
            choice3(
                "What's on offer?", 1,
                "How did you end up here?", 2,
                "Nothing, thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What's on offer?")
                if (player.dwarfFood == 1) {
                    chatNpc(neutral, "You've had your share today. Come back tomorrow, if there is one.")
                    return
                }
                chatNpc(neutral, "Stew, and a barrel of brew that would strip paint. Seventy-five coins for the stew.")
                if (access.invDel(access.inv, COINS, STEW_PRICE).failure) {
                    chatNpc(neutral, "You haven't got seventy-five coins.")
                    return
                }
                if (access.invAdd(access.inv, "obj.stew").failure) {
                    access.invAdd(access.inv, COINS, STEW_PRICE)
                    chatNpc(neutral, "You've nowhere to put it.")
                    return
                }
                UndergroundPassQuest.setVarBit(player, "varbit.upass_dwarf_food", 1)
                player.kamenTold = true
                access.mes("Kamen hands you a bowl of stew.")
            }
            2 -> {
                chatPlayer(quiz, "How did you end up here?")
                chatNpc(sad, "Same as everyone. Came down a hole and found there wasn't a way back up it.")
                chatNpc(neutral, "Help yourself to the brew barrel if you've a bucket. It's no good to us in that quantity.")
                player.kamenTold = true
            }
            3 -> chatPlayer(neutral, "Nothing, thanks.")
        }
    }

    /* The brew, and what it is for */

    private suspend fun ProtectedAccess.searchBarrel() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (invContains(inv, BUCKET)) {
            fillBucket()
            return
        }
        mesbox(
            "The barrel is most of the way full of something the dwarves call brew. The fumes " +
                "coming off it would take the eyebrows off a man. It wants a bucket.",
        )
    }

    private suspend fun ProtectedAccess.fillBucket() {
        arriveDelay()
        if (invDel(inv, BUCKET).failure) {
            mes("I need an empty bucket for that.")
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(FILL_SOUND)
        delay(2)
        invAdd(inv, DWARF_BREW)
        mes("You fill the bucket from the barrel.")
    }

    private suspend fun ProtectedAccess.searchTomb() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (player.ashesOnDoll == 1) {
            mes("There is nothing left in the tomb but soot.")
            return
        }
        if (player.brewOnTomb == 1) {
            mes("The tomb is soaked through with dwarf brew. It only wants a spark.")
            return
        }
        mesbox(
            "<col=8B0000>Here lies Iban, son of Zamorak, who could not be killed by any hand.</col>" +
                "<br><br>The slab has been lifted before. There are bones under it, and they are " +
                "not dust yet.",
        )
    }

    private suspend fun ProtectedAccess.pourBrew(tomb: String) {
        arriveDelay()
        if (player.ashesOnDoll == 1) {
            mes("There is nothing left in there to burn.")
            return
        }
        if (invDel(inv, DWARF_BREW).failure) {
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(POUR_SOUND)
        delay(2)
        invAdd(inv, BUCKET)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_brew_tomb", 1)
        mes("You empty the bucket over the bones. The fumes come up off them in a haze.")
    }

    private suspend fun ProtectedAccess.lightTomb(tomb: String) {
        arriveDelay()
        if (player.ashesOnDoll == 1) {
            mes("There is nothing left in there to burn.")
            return
        }
        if (player.brewOnTomb == 0) {
            mes("Old bones will not take a flame on their own.")
            return
        }
        if (!invContains(inv, DOLL)) {
            mes("There is no point burning them without something to catch the ashes in.")
            return
        }
        anim(SEQ_SEARCH)
        delay(1)
        soundSynth(SOUND_BIGFIRE)
        for (coords in TOMB_COORDS) {
            locRepo.findExact(coords, LocShape.CentrepieceStraight)?.let {
                locRepo.change(it, burntTombType, Int.MAX_VALUE)
            }
        }
        delay(2)
        mesbox(
            "The brew goes up with a thump that knocks you back a step, and everything under the " +
                "slab with it.",
        )
        with(doll) { addIngredient(Ingredient.ASHES_OF_IBAN) }
    }

    private companion object {
        const val BREW_BARREL = "loc.upassdwarfbrewbarrel"
        val TOMBS = arrayOf("loc.ibantomb_left", "loc.ibantomb_right")
        const val COINS = "obj.coins"
        const val STEW_PRICE = 75
        const val FILL_SOUND = "synth.liquid"
        const val POUR_SOUND = "synth.vial_pour"
        const val BURNT_TOMB = "loc.ibantomb_burnt_left"

        val TOMB_COORDS = listOf(UpassCoords.IBAN_TOMB_LEFT, UpassCoords.IBAN_TOMB_RIGHT)
    }
}
