package org.rsmod.content.quest.area.paterdomus.priestinperil

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BLESSED_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BUCKET
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.MURKY_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_CELL_UNLOCKED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COFFIN_SEALED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The coffin the Zamorakians dug up and left beside Drezel's cell. Pouring the water Drezel
 * blessed over it seals the vampyre inside: the player tips the bucket out, the coffin flares
 * with holy light and settles.
 */
class VampyreCoffin
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val world: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(COFFIN) { open() }
        onOpLocU(COFFIN, BLESSED_WATER) { pourBlessedWater(it.loc) }
        onOpLocU(COFFIN, MURKY_WATER) {
            say("This water doesn't look particularly holy to me... I think I'd better check with Drezel first.")
        }
        onOpLocU(COFFIN, BUCKET_OF_WATER) {
            say("I don't think pouring normal water on the coffin is going to help...")
        }
    }

    private suspend fun ProtectedAccess.say(line: String) {
        arriveDelay()
        startDialogue { chatPlayer(confused, line) }
    }

    private suspend fun ProtectedAccess.open() {
        arriveDelay()
        if (priestInPeril.stage(player) >= STAGE_COFFIN_SEALED) {
            startDialogue { chatPlayer(neutral, "The vampyre should be dealt with. I'd still best not risk it though.") }
            return
        }
        startDialogue {
            chatPlayer(
                worried,
                "It sounds like there's something alive inside it. I don't think it would be a very " +
                    "good idea to open it...",
            )
        }
    }

    private suspend fun ProtectedAccess.pourBlessedWater(coffin: BoundLocInfo) {
        arriveDelay()
        if (priestInPeril.stage(player) != STAGE_CELL_UNLOCKED) {
            startDialogue { chatPlayer(neutral, "The vampyre should be dealt with. I'd still best not risk it though.") }
            return
        }
        faceLoc(coffin)
        anim(POUR_SEQ)
        soundSynth(POUR_SOUND)
        invReplace(inv, BLESSED_WATER, 1, BUCKET)
        delay(POUR_TICKS)
        spotanimMap(world, HOLY_LIGHT_SPOTANIM, coffin.coords)
        locAnim(world, coffin, NEUTRALISED_SEQ)
        priestInPeril.advanceTo(this, STAGE_COFFIN_SEALED)
        objbox(BLESSED_WATER, "You pour the blessed water over the coffin...")
    }

    private companion object {
        const val COFFIN = "loc.priestperil_coffin_noanim"
        const val POUR_SEQ = "seq.throw_bucketofwater"
        const val NEUTRALISED_SEQ = "seq.priestperil_coffin_neutralised"
        const val HOLY_LIGHT_SPOTANIM = "spotanim.priestperil_coffin_spell"
        const val POUR_SOUND = "synth.holy_water_pour"

        /** `seq.throw_bucketofwater` runs for two cycles before the water lands. */
        const val POUR_TICKS = 2
    }
}
