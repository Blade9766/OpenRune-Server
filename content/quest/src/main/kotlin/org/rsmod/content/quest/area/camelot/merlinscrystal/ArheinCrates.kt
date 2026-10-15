package org.rsmod.content.quest.area.camelot.merlinscrystal

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BUCKET
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BUCKET_CRATE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CATHERBY_ARRIVAL
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CATHERBY_CRATE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.CRATE_INTERIOR
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.KEEP_ARRIVAL
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.KEEP_CRATE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.PICK_UP_SEQ
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.PUT_DOWN_SOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_LANCELOT
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Arhein's shipping crates: the only way in and out of Keep Le Faye.
 *
 * The voyage is played out in the walled crate on the shipping map rather than on a boat, so the
 * player hears the loading, the crossing and the unloading from inside the box and never sees
 * anything else. They can sit there as long as they like; climbing out puts them down beside
 * whichever dock the crate was last set down on, which is also where a player who logs out
 * mid-voyage is put when they come back.
 */
@Singleton
class ArheinCrates
@Inject
constructor(
    private val quest: MerlinsCrystalQuest,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc2(CATHERBY_CRATE) { boardAtCatherby() }
        onOpLoc2(KEEP_CRATE) { boardAtKeep() }
        onOpLoc2(BUCKET_CRATE) { takeBucket() }
        onPlayerSoftQueue(EVICT_QUEUE) { stowaway(player) }
        onPlayerLogin {
            if (player.merlinInCrate || player.coords == CRATE_INTERIOR) {
                player.softQueue(EVICT_QUEUE, 1)
            }
        }
    }

    private suspend fun ProtectedAccess.boardAtCatherby() {
        if (quest.stage(player) < STAGE_SPOKEN_LANCELOT) {
            mes("You have no reason to do that...")
            return
        }
        player.merlinCrateAtKeep = false
        if (!climbIn()) {
            return
        }
        sail(OUTBOUND)
        player.merlinCrateAtKeep = true
        waitToClimbOut()
    }

    private suspend fun ProtectedAccess.boardAtKeep() {
        player.merlinCrateAtKeep = true
        if (!climbIn()) {
            return
        }
        sail(RETURN)
        player.merlinCrateAtKeep = false
        waitToClimbOut()
    }

    private suspend fun ProtectedAccess.climbIn(): Boolean {
        arriveDelay()
        mesbox("The crate is empty. It's just about big enough to hide inside.")
        val hide =
            choice2("Yes.", true, "No.", false, title = "Would you like to hide inside the crate?")
        if (!hide) {
            return false
        }
        anim(PICK_UP_SEQ)
        mesbox("You climb inside the crate and wait.")
        player.merlinInCrate = true
        telejump(CRATE_INTERIOR)
        return true
    }

    private suspend fun ProtectedAccess.sail(voyage: List<String>) {
        for (line in voyage) {
            mesbox(line)
        }
        soundSynth(PUT_DOWN_SOUND)
    }

    private suspend fun ProtectedAccess.waitToClimbOut() {
        while (true) {
            val out =
                choice2(
                    "Yes.",
                    true,
                    "No.",
                    false,
                    title = "Would you like to get back out of the crate?",
                )
            if (out) {
                break
            }
            mesbox("You wait.")
            mesbox("And wait...")
            mesbox("And wait...")
        }
        mesbox("You climb out of the crate.")
        climbOut()
    }

    private fun ProtectedAccess.climbOut() {
        val dest = if (player.merlinCrateAtKeep) KEEP_ARRIVAL else CATHERBY_ARRIVAL
        player.merlinInCrate = false
        telejump(dest)
    }

    /** Puts a player who logged out mid-voyage down on the dock the crate was bound for. */
    private fun stowaway(player: Player) {
        if (!player.merlinInCrate && player.coords != CRATE_INTERIOR) {
            return
        }
        launcher.launch(player) {
            mes("You climb out of the crate.")
            climbOut()
        }
    }

    private suspend fun ProtectedAccess.takeBucket() {
        arriveDelay()
        mesbox("There are buckets in the crate. Would you like a bucket?")
        val take = choice2("Yes.", true, "No.", false)
        if (!take) {
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to hold the bucket.")
            return
        }
        invAdd(inv, BUCKET)
        mesbox("You take a bucket.")
    }

    private companion object {
        const val EVICT_QUEUE = "queue.merlin_crate_evict"

        val OUTBOUND =
            listOf(
                "You wait.",
                "And wait...",
                "And wait...",
                "You hear voices outside the crate.<br>Is this your crate, Arhein?",
                "Yeah, I think so. Pack it aboard soon as you can. I'm on a tight schedule for deliveries!",
                "You feel the crate being lifted.<br>Oof. Wow, this is pretty heavy!<br>I never knew candles weighed so much!",
                "Quit your whining, and stow it in the hold.",
                "You feel the crate being put down inside the ship.",
                "You wait...",
                "And wait...",
                "Casting off!",
                "You feel the ship start to move.",
                "Feels like you're now out at sea.",
                "The ship comes to a stop.",
                "Unload Mordred's deliveries onto the jetty.<br>Aye-aye, cap'n!",
                "You feel the crate being lifted.",
                "You can hear someone mumbling outside the crate.<br>...stupid Arhein... making me... candles... never weigh THIS much... hurts...",
                "You feel the crate being put down.",
            )

        val RETURN =
            listOf(
                "You wait.",
                "And wait...",
                "And wait...",
                "You hear voices outside the crate.<br>Are these the crates for pick up?",
                "Yeah, I think so. Mordred wants them out of the way for his next shipment.",
                "You feel the crate being lifted.<br>Oof. Wow, this is pretty heavy!<br>What is she shipping back?",
                "Quit your whining, and stow it in the hold.",
                "You feel the crate being put down inside the ship.",
                "You wait...",
                "And wait...",
                "Casting off!",
                "You feel the ship start to move.",
                "Feels like you're now out at sea.",
                "The ship comes to a stop.",
                "Unload the crates to the pier.<br>Aye-aye, cap'n!",
                "You feel the crate being lifted.",
                "You feel the crate being put down.",
            )
    }
}
