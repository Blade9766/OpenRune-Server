package org.rsmod.content.quest.area.camelot.merlinscrystal

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.KEEP_FRONT_DOORS
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.RENEGADE_KNIGHT
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The heavy front doors of Keep Le Faye. They are barred from the inside, so from the shore they
 * only ever answer back; a player who reached the keep through Arhein's crates can walk out of
 * them, which is what makes them worth having at all.
 */
class KeepLeFaye @Inject constructor(private val passages: GenericPassageScript) : PluginScript() {

    private val doorTypes = KEEP_FRONT_DOORS.associateWith(::locType)

    override fun ScriptContext.startup() {
        for ((door, type) in doorTypes) {
            onOpLoc1(door) { frontDoor(it.loc, type) }
            onOpLoc2(door) { knock(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.frontDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        if (isOutside(door)) {
            mesbox("The door is securely locked. You will have to find another way in.")
            return
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.knock(door: BoundLocInfo) {
        arriveDelay()
        if (!isOutside(door)) {
            startDialogue {
                chatPlayer(
                    confused,
                    "Uh... I don't think anyone outside will answer me if I knock on the door...",
                )
            }
            return
        }
        startDialogue {
            mesbox("You knock at the door. You hear a voice from inside...")
            chatNpcSpecific(KNIGHT_NAME, RENEGADE_KNIGHT, angry, "Yes? What do you want?")
            chatPlayer(confused, "Um....")
            val excuse =
                choice4(
                    "Pizza delivery!",
                    Excuse.Pizza,
                    "Have you ever thought about letting Saradomin into your life?",
                    Excuse.Saradomin,
                    "Can I interest you in some double glazing?",
                    Excuse.Glazing,
                    "Would you like to buy some lucky heather?",
                    Excuse.Heather,
                )
            when (excuse) {
                Excuse.Pizza -> {
                    chatPlayer(happy, "Pizza delivery!")
                    chatNpcSpecific(KNIGHT_NAME, RENEGADE_KNIGHT, angry, "We didn't order any pizza. Get lost!")
                }
                Excuse.Saradomin -> {
                    chatPlayer(
                        happy,
                        "Have you ever considered letting the glory of Saradomin into your life? " +
                            "I have some pamphlets you may be interested in reading and " +
                            "discussing with me.",
                    )
                    chatNpcSpecific(KNIGHT_NAME, RENEGADE_KNIGHT, angry, "No. Go away.")
                }
                Excuse.Glazing -> {
                    chatPlayer(
                        happy,
                        "Can I interest you in some double glazing? An old castle like this must " +
                            "get very draughty in the winter...",
                    )
                    chatNpcSpecific(
                        KNIGHT_NAME,
                        RENEGADE_KNIGHT,
                        angry,
                        "No. Get out of here before I run you through.",
                    )
                }
                Excuse.Heather -> {
                    chatPlayer(happy, "Would you like to buy some lucky heather?")
                    chatNpcSpecific(KNIGHT_NAME, RENEGADE_KNIGHT, angry, "No. Go away.")
                }
            }
            mesbox("It looks like you'll have to find another way in...")
        }
    }

    /** The keep is east of its front doors, so anyone west of them is still on the shore. */
    private fun ProtectedAccess.isOutside(door: BoundLocInfo): Boolean = coords.x < door.coords.x

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private enum class Excuse {
        Pizza,
        Saradomin,
        Glazing,
        Heather,
    }

    private companion object {
        const val KNIGHT_NAME = "Renegade Knight"
    }
}
