package org.rsmod.content.quest.area.desert.shadowofthestorm

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DENATH_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.IN_UZER
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_APPEAR
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_CIRCLE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_SUMMON
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_CHASE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIRST_RITUAL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_SECOND_RITUAL
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two summonings.
 *
 * A demonic sigil is chanted over, one word at a time, while its owner stands on their point of
 * the pentagram. The words themselves are fixed; only their order matters, and the order is what
 * the quest turns on. Denath dictates the incantation backwards and calls it a summoning, so the
 * first ritual unravels him instead - Denath was Agrith-Naar wearing a man. The second ritual uses
 * the order written in the tome and brings the demon back in his own shape, where Silverlight can
 * reach him.
 *
 * Both scenes run inside the player's copy of the throne room, so the casters can be moved around
 * without anyone else seeing it.
 */
@Singleton
class Ritual
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val throneRoom: DemonThroneRoom,
    private val worldRepo: WorldRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(SIGIL) { chant() }
    }

    private suspend fun ProtectedAccess.chant() {
        val stage = sots.stage(player)
        val ritual =
            when (stage) {
                STAGE_FIRST_RITUAL -> Which.First
                STAGE_SECOND_RITUAL -> Which.Second
                else -> null
            }
        if (ritual == null || !throneRoom.inside(player)) {
            mes("You mutter over the sigil. Nothing answers.")
            return
        }
        val point = throneRoom.at(player, ritual.playerPoint)
        if (player.coords != point) {
            mes("Nothing happens. You are not standing on your point of the circle.")
            return
        }
        if (!Incantation.assigned(player)) {
            mes("You do not know an incantation to chant.")
            return
        }
        val expected =
            when (ritual) {
                Which.First -> Incantation.denathOrder(player)
                Which.Second -> Incantation.tomeOrder(player)
            }
        val spoken = speakIncantation()
        if (spoken != expected) {
            failedChant(ritual)
            return
        }
        when (ritual) {
            Which.First -> unsummoning()
            Which.Second -> summoning()
        }
    }

    /** Walks the player through the five words, one option list per word. */
    private suspend fun ProtectedAccess.speakIncantation(): List<Int> {
        val spoken = ArrayList<Int>(Incantation.LENGTH)
        startDialogue {
            for (step in 0 until Incantation.LENGTH) {
                val pick =
                    choice5(
                        Incantation.WORDS[0], 0,
                        Incantation.WORDS[1], 1,
                        Incantation.WORDS[2], 2,
                        Incantation.WORDS[3], 3,
                        Incantation.WORDS[4], 4,
                        title = "Word ${step + 1} of the incantation",
                    )
                spoken += pick
                say(Incantation.WORDS[pick] + if (step == Incantation.LENGTH - 1) "!" else "...")
                delay(1)
            }
        }
        return spoken
    }

    private suspend fun ProtectedAccess.failedChant(ritual: Which) {
        soundSynth(SPELL_FAIL_SOUND)
        val speaker = ritual.prompter()
        val chider = throneRoom.findNpc(player, speaker)
        if (chider != null) {
            chider.say(ritual.rebuke)
        } else {
            mesbox(ritual.rebuke)
        }
        mesbox("The words fall apart. You will have to start the incantation again.")
    }

    /**
     * The chant Denath taught runs the incantation backwards, and a summoning read backwards is a
     * banishment. There is no demon in the circle to banish, so it takes the only demon present:
     * Denath himself, who has been Agrith-Naar in a borrowed shape the whole time.
     */
    private suspend fun ProtectedAccess.unsummoning() {
        beginCutscene()
        camMoveTo(
            throneRoom.at(player, ThroneRoom.CAMERA_FROM),
            height = ThroneRoom.CAMERA_HEIGHT,
            rate = ThroneRoom.CAMERA_RATE,
            rate2 = ThroneRoom.CAMERA_RATE,
        )
        camLookAt(
            throneRoom.at(player, ThroneRoom.CAMERA_AT),
            height = ThroneRoom.LOOK_HEIGHT,
            rate = ThroneRoom.CAMERA_RATE,
            rate2 = ThroneRoom.CAMERA_RATE,
        )
        delay(1)

        val denath = throneRoom.findNpc(player, DENATH_SIGIL)
        soundSynth(SOUND_CIRCLE)
        spotanimCircle(VORTEX_SPOTANIM)
        delay(3)
        denath?.say("Yes! Yes, it comes! Hold the words, all of you!")
        delay(3)
        denath?.say("No. No, that is not... those words are the wrong way round!")
        soundSynth(SOUND_APPEAR)
        delay(3)
        denath?.say("What have you DONE?")
        denath?.anim(BANISH_SEQ)
        delay(2)
        if (denath != null) {
            denath.spotanim(VORTEX_SPOTANIM)
            delay(2)
            throneRoom.despawn(denath)
        }
        // Whatever Denath was, his sigil was real metal, and it is one of the four the second
        // circle needs. It stays on the floor of the room rather than being handed over.
        objRepo.add(SIGIL, throneRoom.at(player, ThroneRoom.CIRCLE_NORTH), SIGIL_DURATION, player)
        soundSynth(SPELL_FAIL_SOUND)
        mesbox(
            "Denath comes apart like smoke pulled down a chimney. For one moment there is " +
                "something else standing in his clothes - horned, and far too tall for the room.",
        )
        mesbox("Then the circle is empty, and the others are already running.")
        mesbox(
            "Denath's sigil is lying where he stood. Pick it up before you leave - you will need " +
                "four of them, and Jennifer's mould only makes one at a time.",
        )

        sots.advanceTo(this, STAGE_CHASE)
        player.daveConvinced = IN_UZER
        scatterCult()
        throneRoom.failPortal(player)
        endCutscene()
        mes("The portal behind you is guttering. You should get out while it still holds.")
    }

    /** Sends every caster and onlooker south towards the portal, then clears the room. */
    private suspend fun ProtectedAccess.scatterCult() {
        val fleeing = throneRoom.occupants(player)
        for (npc in fleeing) {
            npc.say(FLEEING_CRIES.random())
            npc.walk(throneRoom.at(player, ThroneRoom.ARRIVAL))
        }
        delay(4)
        for (npc in fleeing) {
            throneRoom.despawn(npc)
        }
    }

    /** The tome's order, spoken by five casters at once: Agrith-Naar in his own shape. */
    private suspend fun ProtectedAccess.summoning() {
        beginCutscene()
        camMoveTo(
            throneRoom.at(player, ThroneRoom.CAMERA_FROM),
            height = ThroneRoom.CAMERA_HEIGHT,
            rate = ThroneRoom.CAMERA_RATE,
            rate2 = ThroneRoom.CAMERA_RATE,
        )
        camLookAt(
            throneRoom.at(player, ThroneRoom.CAMERA_AT),
            height = ThroneRoom.LOOK_HEIGHT,
            rate = ThroneRoom.CAMERA_RATE,
            rate2 = ThroneRoom.CAMERA_RATE,
        )
        delay(1)

        val casters = throneRoom.occupants(player)
        for (caster in casters) {
            caster.faceSquare(throneRoom.at(player, ThroneRoom.CIRCLE_CENTRE))
        }
        soundSynth(SOUND_CIRCLE)
        spotanimCircle(VORTEX_SPOTANIM)
        delay(3)
        mes("The marked floor turns over like water coming to the boil.")
        soundSynth(SOUND_SUMMON)
        delay(3)

        sots.advanceTo(this, STAGE_FIGHT)
        val demon = throneRoom.spawnDemon(player)
        demon?.anim(APPEAR_SEQ)
        soundSynth(SOUND_APPEAR)
        delay(4)
        demon?.say("Who dares call me back to this dust heap?")
        delay(3)
        demon?.say("You. The one who unmade me. I will eat your name.")
        delay(2)
        endCutscene()
        mesbox(
            "Agrith-Naar cannot leave the circle, and the circle cannot hold him for long. " +
                "Wound him however you like, but the blow that kills him must come from " +
                "<col=7f0000>Silverlight</col>.",
        )
        demon?.facePlayer(player)
    }

    /** Lights up the five marked tiles of the pentagram at once. */
    private fun ProtectedAccess.spotanimCircle(spotanim: String) {
        for (tile in CIRCLE_POINTS) {
            spotanimAt(throneRoom.at(player, tile), spotanim)
        }
        spotanimAt(throneRoom.at(player, ThroneRoom.CIRCLE_CENTRE), spotanim)
    }

    private fun ProtectedAccess.spotanimAt(coords: CoordGrid, spotanim: String) {
        spotanimMap(worldRepo, spotanim, coords)
    }

    /** Which of the two summonings the player is standing in. */
    private enum class Which(val playerPoint: CoordGrid, val rebuke: String) {
        First(
            ThroneRoom.CIRCLE_NORTH_WEST,
            "That is not what I told you! Say the words in the order I gave them!",
        ),
        Second(
            ThroneRoom.CIRCLE_NORTH,
            "Those are not the words from the book. Start again, and take your time.",
        ),
        ;

        fun prompter(): String =
            when (this) {
                First -> DENATH_SIGIL
                Second -> ShadowOfTheStormQuest.BADDEN_SIGIL
            }
    }

    private companion object {
        const val VORTEX_SPOTANIM = "spotanim.demon_spawn_portal"
        const val BANISH_SEQ = "seq.qip_ds_delrith_banished"
        const val APPEAR_SEQ = "seq.demon_update_appear"
        const val SPELL_FAIL_SOUND = "synth.spellfail"

        /** Long enough that the player can finish the scene before the sigil despawns. */
        const val SIGIL_DURATION = 2000

        val CIRCLE_POINTS =
            listOf(
                ThroneRoom.CIRCLE_NORTH,
                ThroneRoom.CIRCLE_NORTH_EAST,
                ThroneRoom.CIRCLE_SOUTH_EAST,
                ThroneRoom.CIRCLE_SOUTH_WEST,
                ThroneRoom.CIRCLE_NORTH_WEST,
            )

        val FLEEING_CRIES =
            listOf(
                "Run! Just run!",
                "He was one of them! He was one of THEM!",
                "Get to the portal!",
                "I never wanted any of this!",
            )
    }
}
