package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DISCIPLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBANS_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_CAVEIN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_COLLAPSE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_IBAN_LIGHTNING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_IBAN_CLAW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL_READY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_IBAN_DEAD
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ZAMORAK_BOTTOM
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ZAMORAK_TOP
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Iban's temple and the end of him.
 *
 * His disciples keep the doors, and they will not open for anything that is not dressed as one of
 * them: a full set of Zamorak monk robes, which the disciples themselves wear. Inside, Iban cannot
 * be fought - he throws anyone who comes near him across the floor - and the only thing that can
 * be done with him is done at the Well of the Damned under his own throne.
 *
 * While the player is in the temple a timer runs. Iban keeps hold of them with his claws, so the
 * walk to the well is a fight against being knocked off it rather than a fight against him.
 */
@Singleton
class IbanTemple
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val doll: DollOfIban,
    private val npcRepo: NpcRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (door in TEMPLE_DOORS) {
            onOpLoc1(door) { enterTemple() }
        }
        for (monk in listOf(DISCIPLE, UndergroundPassQuest.visibleTwin(DISCIPLE))) {
            onOpNpc1(monk) { startDialogue(it.npc) { disciple() } }
        }
        for (iban in listOf(IBAN, UndergroundPassQuest.visibleTwin(IBAN))) {
            onOpNpc1(iban) { approachIban() }
        }
        onOpLoc1(WELL_OF_THE_DAMNED) { lookIntoWell() }
        onOpLocU(WELL_OF_THE_DAMNED, DOLL) { castDollIn() }
        onOpLoc1(THRONE) { mesbox("The throne is warm, and there is nobody sitting in it.") }
        onPlayerSoftTimer(TEMPLE_TIMER) { templeTick(player) }
    }

    private suspend fun org.rsmod.api.player.dialogue.Dialogue.disciple() {
        chatNpc(angry, "Kneel or get out. You have no business in this place.")
        chatPlayer(neutral, "I'm here to see Iban.")
        chatNpc(laugh, "Everyone is, sooner or later.")
    }

    private suspend fun ProtectedAccess.enterTemple() {
        arriveDelay()
        if (!wearingRobes()) {
            mesbox(
                "The doors do not move, and the disciples inside have stopped what they were " +
                    "doing to look at you. They are all dressed the same, and you are not.",
            )
            return
        }
        if (quest.stage(player) < STAGE_DOLL_READY) {
            mesbox(
                "The disciples take you for one of their own and stand aside. There is no point " +
                    "going in until the doll is finished.",
            )
            return
        }
        soundSynth(DOOR_SOUND)
        mes("The disciples take you for one of their own and let you through.")
        delay(2)
        telejump(UpassCoords.TEMPLE_ENTRY)
        player.templeEntered = true
        UndergroundPassQuest.setVarBit(player, "varbit.upass_seen_temple", 1)
        softTimer(TEMPLE_TIMER, 1)
        mesbox(
            "Iban is at the far end of the hall and he has already seen you. Between you and him " +
                "is a well cut into the floor.",
        )
    }

    private suspend fun ProtectedAccess.approachIban() {
        mesbox(
            "There is no fighting him. Whatever is standing at the end of the hall stopped being " +
                "a man a long time ago; the well is the only thing here that can touch him.",
        )
    }

    private suspend fun ProtectedAccess.lookIntoWell() {
        arriveDelay()
        mesbox(
            "The well goes down further than the temple is tall, and the sound coming up out of " +
                "it is a great many voices, all of them Iban's.",
        )
    }

    private suspend fun ProtectedAccess.castDollIn() {
        arriveDelay()
        if (quest.stage(player) >= STAGE_IBAN_DEAD) {
            mes("There is nothing left down there to answer.")
            return
        }
        if (quest.stage(player) < STAGE_DOLL_READY) {
            mesbox("The doll is not finished. Throwing it in now would only warn him.")
            return
        }
        if (invDel(inv, DOLL).failure) {
            return
        }
        faceSquare(UpassCoords.WELL_OF_THE_DAMNED)
        anim(THROW_SEQ)
        soundSynth(SOUND_IBAN_LIGHTNING)
        delay(2)
        mesbox("The doll goes into the well, and the well takes it.")
        destroyTemple()
    }

    /**
     * The temple comes down on Iban with the doll. It takes the disciples and the whole upper
     * cavern with it, so the player is put out on the other side of the hill.
     */
    private suspend fun ProtectedAccess.destroyTemple() {
        clearSoftTimer(TEMPLE_TIMER)
        camModeClose()
        hideEntityOps()
        try {
            spotanim(IBAN_DEATH_SPOT)
            soundSynth(SOUND_IBAN_LIGHTNING)
            delay(2)
            startDialogue {
                chatNpcSpecific("Iban", IBAN_HEAD, angry, "What have you done? What have you DONE?")
            }
            ifClose()
            soundSynth(SOUND_COLLAPSE)
            mesbox("Iban comes apart where he stands, and the temple starts to come apart with him.")
            delay(2)
            soundSynth(SOUND_CAVEIN)
            takeInstantHit(HitType.Typeless, random.of(COLLAPSE_MIN, COLLAPSE_MAX))
            mesbox("The roof gives way. You run.")
            delay(2)
            invAddOrDrop(objRepo, IBANS_STAFF)
            quest.advanceTo(this, STAGE_IBAN_DEAD)
            telejump(UpassCoords.TEMPLE_ESCAPE_LANDING, TeleportType.Exempt)
            delay(1)
            objbox(
                IBANS_STAFF,
                "You come to on the far side of the hill with a staff in your hand that was not " +
                    "there before. Koftik is sitting a little way off, and he knows you.",
            )
        } finally {
            camReset()
            camModeReset()
            showEntityOps()
        }
    }

    /**
     * Every cycle in the temple Iban reaches for whoever is in it: a spike of red that stuns,
     * hurts, and throws them back towards the doors.
     */
    private fun templeTick(player: Player) {
        if (!player.templeEntered || !inTemple(player)) {
            player.clearSoftTimer(TEMPLE_TIMER)
            return
        }
        if (quest.stage(player) >= STAGE_IBAN_DEAD) {
            player.clearSoftTimer(TEMPLE_TIMER)
            return
        }
        if (random.of(CLAW_ONE_IN) != 0) {
            return
        }
        val atTheWell = player.coords.chebyshevDistance(UpassCoords.WELL_OF_THE_DAMNED) <= WELL_REACH
        launcher.launch(player) { clawed(knockBack = !atTheWell) }
    }

    private suspend fun ProtectedAccess.clawed(knockBack: Boolean) {
        spotanim(SPOT_IBAN_CLAW)
        soundSynth(SOUND_IBAN_LIGHTNING)
        takeInstantHit(HitType.Typeless, random.of(CLAW_MIN, CLAW_MAX))
        mes("Iban reaches for you across the hall.")
        if (!knockBack) {
            return
        }
        delay(1)
        telejump(UpassCoords.TEMPLE_ENTRY, TeleportType.Exempt)
    }

    private fun inTemple(player: Player): Boolean {
        val coords = player.coords
        return coords.level == TEMPLE_LEVEL &&
            coords.x in TEMPLE_MIN_X..TEMPLE_MAX_X &&
            coords.z in TEMPLE_MIN_Z..TEMPLE_MAX_Z
    }

    private fun ProtectedAccess.wearingRobes(): Boolean =
        player.worn.contains(ZAMORAK_TOP) && player.worn.contains(ZAMORAK_BOTTOM)

    private companion object {
        val TEMPLE_DOORS =
            arrayOf("loc.upass_templedoor_closed_left", "loc.upass_templedoor_closed_right")
        const val WELL_OF_THE_DAMNED = "loc.cave_temple_altar"
        const val THRONE = "loc.iban_temple_throne"
        const val IBAN_HEAD = "npc.iban_vis"

        const val TEMPLE_TIMER = "timer.upass_temple"
        const val DOOR_SOUND = "synth.stone_door"
        const val THROW_SEQ = "seq.human_throw_arrow1"
        const val IBAN_DEATH_SPOT = "spotanim.upass_claw"

        const val CLAW_ONE_IN = 10
        const val WELL_REACH = 2
        const val CLAW_MIN = 5
        const val CLAW_MAX = 8
        const val COLLAPSE_MIN = 3
        const val COLLAPSE_MAX = 10

        const val TEMPLE_LEVEL = 1
        const val TEMPLE_MIN_X = 2126
        const val TEMPLE_MAX_X = 2145
        const val TEMPLE_MIN_Z = 4637
        const val TEMPLE_MAX_Z = 4658
    }
}
