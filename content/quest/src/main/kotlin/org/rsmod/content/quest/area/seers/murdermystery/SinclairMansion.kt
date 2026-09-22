package org.rsmod.content.quest.area.seers.murdermystery

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.DAGGER
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.DUSTED_DAGGER
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.FLYPAPER
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.KILLERS_PRINT
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_CLAIM_HEARD
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_DISPROVED
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.PUNGENT_POT
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.UNKNOWN_PRINT
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Sinclair Mansion crime scene: the study's dagger, pungent pot and smashed window, the
 * family's barrels of silverware, the flour and flypaper used to lift fingerprints, the places the
 * family claim to have used their poison, the guard dog's gate, and the house's drawers and
 * bookcases.
 */
class SinclairMansion
@Inject
constructor(
    private val murder: MurderMysteryQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val search: NpcSearch,
    private val random: GameRandom,
) : PluginScript() {

    private val daggerType by lazy { objType(DAGGER) }
    private val pungentPotType by lazy { objType(PUNGENT_POT) }

    override fun ScriptContext.startup() {
        onOpObj3(daggerType) { takeDagger(it.obj) }
        onOpObj3(pungentPotType) { takePungentPot(it.obj) }
        onOpLoc2(WINDOW) { investigateWindow() }

        for (suspect in Suspect.entries) {
            onOpLoc2(suspect.barrel) { searchBarrel(suspect) }
            onOpLoc2(suspect.poisonLoc) { investigatePoison(suspect) }
        }

        onOpLoc2(FLOUR_BARREL) { takeFlour() }
        onOpLocU(FLOUR_BARREL, POT) { takeFlour() }
        onOpLocU(FLOUR_BARREL, PUNGENT_POT) {
            mesbox("You probably shouldn't use evidence from a crime scene to keep flour in...")
        }
        onOpLoc2(SACKS) { investigateSacks() }

        for (proof in DUSTABLE) {
            onOpHeldU(POT_OF_FLOUR, proof.clean) { dust(proof) }
            onOpHeldU(FLYPAPER, proof.dusted) { liftPrint(proof) }
        }
        for (suspect in Suspect.entries) {
            onOpHeldU(UNKNOWN_PRINT, suspect.print) { comparePrints(suspect) }
        }

        onOpLoc1(DOG_GATE_LEFT) { investigateGate() }
        onOpLoc1(DOG_GATE_RIGHT) { investigateGate() }
        onAiTimer(GUARD_DOG) { bark(npc) }

        for ((closed, open) in DRAWERS) {
            onOpLoc1(closed) { openDrawers(it.loc, open) }
            onOpLoc1(open) { searchDrawers() }
            onOpLoc2(open) { shutDrawers(it.loc, closed) }
        }
        for (bookcase in BOOKCASES) {
            onOpLoc1(bookcase) { searchBookcase() }
        }
    }

    /* The study */

    private suspend fun ProtectedAccess.takeDagger(obj: Obj) {
        if (murder.isComplete(player)) {
            mesbox("I cannot take the flimsy dagger. The Guards will need it as evidence.")
            return
        }
        if (DAGGER in inv || DUSTED_DAGGER in inv) {
            mes("I already have the murder weapon.")
            return
        }
        if (bank.count(DAGGER) > 0 || bank.count(DUSTED_DAGGER) > 0) {
            mes("I already have the murder weapon in my bank.")
            return
        }
        if (!pickUp(obj, DAGGER)) {
            return
        }
        mesbox("This knife doesn't seem sturdy enough to have killed Lord Sinclair.")
    }

    private suspend fun ProtectedAccess.takePungentPot(obj: Obj) {
        if (murder.isComplete(player)) {
            mesbox("I cannot take the poisoned pot. The Guards will need it for evidence.")
            return
        }
        if (PUNGENT_POT in inv) {
            mes("I already have the poisoned pot.")
            return
        }
        if (bank.count(PUNGENT_POT) > 0) {
            mes("I already have the poisoned pot in my bank.")
            return
        }
        if (!pickUp(obj, PUNGENT_POT)) {
            return
        }
        mesbox("It seems like Lord Sinclair was drinking from this before he died.")
    }

    /** The evidence stays where it lies for every other investigator; each one takes a copy. */
    private suspend fun ProtectedAccess.pickUp(obj: Obj, type: String): Boolean {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return false
        }
        if (coords != obj.coords) {
            delay(1)
            anim(PICKUP_TABLE_SEQ)
        }
        soundSynth(PICKUP_SOUND)
        invAdd(inv, type)
        return true
    }

    private suspend fun ProtectedAccess.investigateWindow() {
        arriveDelay()
        if (murder.isComplete(player)) {
            mes("A slightly broken window. Could be easily shattered.")
            return
        }
        if (!murder.isInvestigating(player)) {
            mes("You need the guards' permission to do that.")
            return
        }
        val killer = player.murderer ?: return
        mesbox("Some thread seems to have been caught on a loose nail on the window.")
        if (murder.ownedCount(this, killer.thread) > 0) {
            mesbox("You have already taken the thread.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        if (player.murderFoundThread) {
            invAdd(inv, killer.thread)
            mesbox("Lucky for you there's some thread left. You should be less careless in future.")
            return
        }
        player.murderFoundThread = true
        invAdd(inv, killer.thread)
        mes("You take the thread.")
    }

    /* The family's belongings */

    private suspend fun ProtectedAccess.searchBarrel(suspect: Suspect) {
        arriveDelay()
        if (!murder.isInvestigating(player)) {
            mes("I need the guards' permission to do that.")
            return
        }
        val owned =
            murder.ownedCount(this, suspect.silverItem) + murder.ownedCount(this, suspect.dustedItem)
        val item = ServerCacheManager.getItem(suspect.silverItem.asRSCM(RSCMType.OBJ))
        val itemName = item?.name?.lowercase() ?: "silverware"
        if (owned > 0) {
            mes("I already have ${suspect.displayName}'s $itemName.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        soundSynth(PICKUP_SOUND)
        invAdd(inv, suspect.silverItem)
        objbox(
            suspect.silverItem,
            "There's something shiny hidden at the bottom. You take ${suspect.displayName}'s " +
                "$itemName.",
        )
    }

    /* The places the family say they used their poison */

    private suspend fun ProtectedAccess.investigatePoison(suspect: Suspect) {
        arriveDelay()
        if (!murder.isInvestigating(player)) {
            if (murder.isComplete(player)) {
                mes(suspect.idleExamine())
            } else {
                mes("I need the guards' permission to do that.")
            }
            return
        }
        if (player.murderPoisonProgress < POISON_CLAIM_HEARD) {
            mes(suspect.idleExamine())
            return
        }
        if (player.murderSuspect != suspect.id) {
            mesbox(suspect.poisonedDescription())
            return
        }
        when (suspect) {
            Suspect.Anna -> {
                player.murderPoisonProgress = POISON_DISPROVED
                mesbox(
                    "The compost is teeming with maggots. Somebody should really do something " +
                        "about it. It's certainly clear nobody's used poison here.",
                )
            }
            Suspect.Bob -> {
                player.murderPoisonProgress = POISON_DISPROVED
                soundSynth(BEES_SOUND)
                mesbox(
                    "The beehive buzzes with activity. These bees definitely don't seem " +
                        "poisoned at all.",
                )
            }
            Suspect.Carol -> {
                player.murderPoisonProgress = POISON_DISPROVED
                mesbox(
                    "The drain is totally blocked. It really stinks. No, it REALLY smells bad. " +
                        "It's certainly clear nobody's cleaned it recently.",
                )
            }
            Suspect.David -> {
                player.murderPoisonProgress = POISON_DISPROVED
                mesbox(
                    "There is a spiders' nest here. You estimate there must be at least a few " +
                        "hundred spiders ready to hatch. It's certainly clear nobody's used " +
                        "poison here.",
                )
            }
            Suspect.Elizabeth -> {
                soundSynth(MOSQUITO_SOUND)
                mesbox(
                    "The fountain is swarming with mosquitos. There's a nest of them underneath " +
                        "the fountain.",
                )
                startDialogue { chatPlayer(angry, "I hate mosquitos, they're so annoying!") }
                player.murderPoisonProgress = POISON_DISPROVED
                mesbox("It's certainly clear nobody's used poison here.")
            }
            Suspect.Frank -> {
                player.murderPoisonProgress = POISON_DISPROVED
                mesbox(
                    "It looks like the Sinclair family crest but it is very dirty. You can " +
                        "barely make it out under all of the grime. It's certainly clear " +
                        "nobody's cleaned it recently.",
                )
            }
        }
    }

    private fun Suspect.idleExamine(): String =
        when (this) {
            Suspect.Anna -> "It's a heap of compost."
            Suspect.Bob -> "It's a very old beehive."
            Suspect.Carol -> "It's the drains from the kitchen."
            Suspect.David -> "It looks like a spiders' nest of some kind..."
            Suspect.Elizabeth -> "A fountain with large numbers of insects around the base."
            Suspect.Frank -> "The Sinclair Family Crest is hung up here."
        }

    private fun Suspect.poisonedDescription(): String =
        when (this) {
            Suspect.Anna -> "There is a faint smell of poison behind the smell of the compost."
            Suspect.Bob -> "The hive is empty. There are a few dead bees and a faint smell of poison."
            Suspect.Carol ->
                "The drain seems to have been recently cleaned. You can still smell the faint " +
                    "aroma of poison."
            Suspect.David ->
                "A faint smell of poison and a few dead spiders is all that remains of the " +
                    "spiders nest."
            Suspect.Elizabeth ->
                "There are a lot of dead mosquitos around the base of the fountain. A faint " +
                    "smell of poison is in the air, but the water seems clean."
            Suspect.Frank ->
                "The Sinclair family crest. It's shiny and freshly polished and has a slight " +
                    "smell of poison."
        }

    /* Flour, flypaper and fingerprints */

    private suspend fun ProtectedAccess.takeFlour() {
        arriveDelay()
        if (!murder.isInvestigating(player)) {
            mes("I need the guards' permission to do that.")
            return
        }
        if (POT !in inv) {
            mes("A barrel full of finely sifted flour.")
            mes("You need something to put the flour in.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        soundSynth(FLOUR_SOUND)
        invReplace(inv, POT, 1, POT_OF_FLOUR)
        mes("You take some flour from the barrel.")
        mes("There's still plenty of flour left.")
    }

    private suspend fun ProtectedAccess.investigateSacks() {
        arriveDelay()
        if (!murder.isInvestigating(player)) {
            mes("I need the guards' permission to do that.")
            return
        }
        mesbox("There's some flypaper in there. Should I take it?")
        val take =
            choice2("Yes, it might be useful.", true, "No, I don't see any need for it.", false)
        if (!take) {
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        invAdd(inv, FLYPAPER)
        mesbox("You take a piece of fly paper. There is still plenty of fly paper left.")
    }

    private fun ProtectedAccess.dust(proof: Dustable) {
        if (invDel(inv, proof.clean).failure) {
            return
        }
        invAdd(inv, proof.dusted)
        invReplace(inv, POT_OF_FLOUR, 1, POT)
        soundSynth(SPRINKLE_SOUND)
        mes(proof.dustMessage)
        mes(proof.dustedMessage)
    }

    private fun ProtectedAccess.liftPrint(proof: Dustable) {
        if (invDel(inv, proof.dusted).failure) {
            return
        }
        invAdd(inv, proof.clean)
        invReplace(inv, FLYPAPER, 1, proof.print)
        soundSynth(FLYPAPER_SOUND)
        mes(proof.liftMessage)
        mes(proof.liftedMessage)
    }

    private suspend fun ProtectedAccess.comparePrints(suspect: Suspect) {
        if (player.murderSuspect == suspect.id) {
            invReplace(inv, UNKNOWN_PRINT, 1, KILLERS_PRINT)
            player.murderFoundPrints = true
            mes("The finger prints are an exact match to ${suspect.displayName}'s.")
            return
        }
        mesbox(
            "They don't seem to be the same. I guess that clears ${suspect.displayName} of the " +
                "crime. You destroy the useless fingerprint.",
        )
        invDel(inv, suspect.print)
    }

    /* The guard dog */

    private suspend fun ProtectedAccess.investigateGate() {
        arriveDelay()
        if (!murder.isInvestigating(player)) {
            mes("I need the guard's permission to do that.")
            return
        }
        npcFind(coords, GUARD_DOG, DOG_SEARCH_RANGE, HuntVis.Off, search)?.let { bark(it) }
        mesbox(
            "As you approach the gate the Guard Dog starts barking loudly at you. There is no way " +
                "an intruder could have committed the murder. It must have been someone the dog " +
                "knew to get past it quietly.",
        )
    }

    private fun bark(dog: Npc) {
        dog.anim(DOG_BARK_SEQ)
        dog.say("BARK")
        worldRepo.soundArea(dog.coords, BARK_SOUND, radius = BARK_RADIUS)
        dog.aiTimer(random.of(1, BARK_MAX_TICKS))
    }

    /* The house's furniture */

    private suspend fun ProtectedAccess.openDrawers(drawers: BoundLocInfo, open: String) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(DRAWER_OPEN_SOUND)
        locRepo.change(drawers, open, FURNITURE_TICKS)
    }

    private suspend fun ProtectedAccess.shutDrawers(drawers: BoundLocInfo, closed: String) {
        arriveDelay()
        anim(CLOSE_SEQ)
        soundSynth(DRAWER_CLOSE_SOUND)
        locRepo.change(drawers, closed, FURNITURE_TICKS)
    }

    private suspend fun ProtectedAccess.searchDrawers() {
        arriveDelay()
        anim(PICKUP_TABLE_SEQ)
        mes("You search the drawers but find nothing of interest.")
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        arriveDelay()
        spam("You search the books...")
        delay(2)
        mes(
            random.pick(
                "None of them look very interesting.",
                "You don't find anything that you'd ever want to read.",
                "You find nothing to interest you.",
            ),
        )
    }

    private class Dustable(
        val clean: String,
        val dusted: String,
        val print: String,
        val dustMessage: String,
        val dustedMessage: String,
        val liftMessage: String,
        val liftedMessage: String,
    )

    private companion object {
        const val WINDOW = "loc.murderwindow"
        const val FLOUR_BARREL = "loc.flourbarrel"
        const val SACKS = "loc.murdersacks"
        const val DOG_GATE_LEFT = "loc.murderdoggatel"
        const val DOG_GATE_RIGHT = "loc.murderdoggater"
        const val GUARD_DOG = "npc.murder_mystery_guarddog"

        const val POT = "obj.pot_empty"
        const val POT_OF_FLOUR = "obj.pot_flour"

        const val PICKUP_TABLE_SEQ = "seq.human_pickuptable"
        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_closecupboard"
        const val DOG_BARK_SEQ = "seq.dog_update_small_dog_attack"

        const val PICKUP_SOUND = "synth.pick2"
        const val FLOUR_SOUND = "synth.fill_grain"
        const val SPRINKLE_SOUND = "synth.ernest_sprinkle"
        const val FLYPAPER_SOUND = "synth.paper_move"
        const val BARK_SOUND = "synth.barklike"
        const val BEES_SOUND = "synth.insect_swarm"
        const val MOSQUITO_SOUND = "synth.mosquitos_1"
        const val DRAWER_OPEN_SOUND = "synth.drawer_open"
        const val DRAWER_CLOSE_SOUND = "synth.drawer_close"

        const val DOG_SEARCH_RANGE = 8
        const val BARK_RADIUS = 10
        const val BARK_MAX_TICKS = 40
        const val FURNITURE_TICKS = 500

        val DUSTABLE: List<Dustable> =
            Suspect.entries.map { suspect ->
                val noun = suspect.silverItem.nounFor()
                Dustable(
                    clean = suspect.silverItem,
                    dusted = suspect.dustedItem,
                    print = suspect.print,
                    dustMessage = "You sprinkle the flour on ${suspect.displayName}'s $noun.",
                    dustedMessage = "The $noun is now coated with a thin layer of flour.",
                    liftMessage = "You use the flypaper on the flour covered $noun.",
                    liftedMessage =
                        "You have a clean impression of ${suspect.displayName}'s finger prints.",
                )
            } +
                Dustable(
                    clean = DAGGER,
                    dusted = DUSTED_DAGGER,
                    print = UNKNOWN_PRINT,
                    dustMessage = "You sprinkle a small amount of flour on the murderweapon.",
                    dustedMessage = "The murderweapon is now coated with a thin layer of flour.",
                    liftMessage = "You use the flypaper on the floury dagger.",
                    liftedMessage = "You have a clean impression of the murderer's finger prints.",
                )

        val DRAWERS =
            listOf(
                "loc.kr_sin_drawers1" to "loc.kr_sin_drawers1open",
                "loc.kr_sin_drawers2" to "loc.kr_sin_drawers2open",
                "loc.kr_sin_drawers3" to "loc.kr_sin_drawers3open",
                "loc.kr_sin_drawers_left" to "loc.kr_sin_drawers_left_open",
                "loc.kr_sin_drawers_right" to "loc.kr_sin_drawers_right_open",
                "loc.kr_sin_drawers_up" to "loc.kr_sin_drawers_up_open",
                "loc.kr_sin_drawers_down" to "loc.kr_sin_drawers_down_open",
            )

        val BOOKCASES =
            listOf(
                "loc.kr_sin_bookcase1a",
                "loc.kr_sin_bookcase2c",
                "loc.kr_sin_bookcase2d",
                "loc.kr_sin_bookcase3a",
                "loc.kr_sin_bookcase3b",
                "loc.kr_sin_bookcase4a",
                "loc.kr_sin_bookcase4b",
            )

        fun String.nounFor(): String =
            when (this) {
                "obj.murdernecklace" -> "necklace"
                "obj.murdercup" -> "cup"
                "obj.murderbottle" -> "bottle"
                "obj.murderbook" -> "book"
                "obj.murderneedle" -> "needle"
                else -> "pot"
            }

        fun objType(obj: String) =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $obj")
    }
}
