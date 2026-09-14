package org.rsmod.content.quest.area.paterdomus.priestinperil

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BUCKET
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.DREZEL_MAUSOLEUM
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.GOLDEN_KEY
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.IRON_KEY
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.MURKY_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COFFIN_SEALED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_MEET_IN_MAUSOLEUM
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_ROALD_FURIOUS
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mausoleum beneath Paterdomus: the two locked gates, the Seven Priestly Warriors' monuments
 * around the well of the Salve, the holy barrier into Morytania and the trapdoor that leads back
 * down to it from the Morytania bank.
 *
 * Each monument holds a golden gift. The key monument swaps the hooded monk's golden key for
 * the iron key to Drezel's cell; the others swap their golden replica once for the ordinary tool,
 * until the vampyre is sealed and Drezel moves down here.
 */
class Mausoleum
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val doors: PaterdomusDoors,
) : PluginScript() {

    private class Monument(
        val loc: String,
        val item: String,
        val golden: String,
        val accepted: List<String>,
        val study: String,
    )

    override fun ScriptContext.startup() {
        onOpLoc1(WEST_GATE) { westGate(it.loc) }
        onOpLocU(WEST_GATE, GOLDEN_KEY) { westGate(it.loc) }
        onOpLoc1(EAST_GATE) { eastGate(it.loc) }
        onOpLoc1(WELL) { mesbox("You look down the well and see the water of the River Salve moving slowly along.") }
        onOpLocU(WELL, BUCKET) { fillBucket() }
        onOpLoc1(BARRIER) { passBarrier() }
        onOpLoc1(EAST_TRAPDOOR_OPEN) { climbToBarrier() }
        for (monument in MONUMENTS) {
            onOpLoc1(monument.loc) { study(monument) }
            onOpLoc2(monument.loc) { takeFrom(monument) }
            onOpLocU(monument.loc) { useOn(monument, it.objType.internalName) }
        }
    }

    private suspend fun ProtectedAccess.westGate(gate: BoundLocInfo) {
        arriveDelay()
        val north = coords.z >= gate.coords.z
        if (north && !priestInPeril.westGateUnlocked.get(player)) {
            if (priestInPeril.stage(player) < STAGE_ROALD_FURIOUS || !inv.contains(GOLDEN_KEY)) {
                mesbox("The gate is securely locked.")
                return
            }
            soundSynth(UNLOCK_SOUND)
            mes("You unlock the gate with the golden key.")
            priestInPeril.westGateUnlocked.set(player, true)
        }
        val dest = CoordGrid(gate.coords.x, if (north) gate.coords.z - 1 else gate.coords.z, gate.coords.level)
        doors.walkThrough(this, listOf(gate.coords to WEST_GATE), dest, GATE_OPEN_SOUND, GATE_CLOSE_SOUND)
    }

    private suspend fun ProtectedAccess.eastGate(gate: BoundLocInfo) {
        arriveDelay()
        val east = coords.x > gate.coords.x
        if (!east && priestInPeril.stage(player) < STAGE_MEET_IN_MAUSOLEUM) {
            mesbox("The gate is securely locked.")
            return
        }
        val dest = CoordGrid(if (east) gate.coords.x else gate.coords.x + 1, gate.coords.z, gate.coords.level)
        doors.walkThrough(this, listOf(gate.coords to EAST_GATE), dest, GATE_OPEN_SOUND, GATE_CLOSE_SOUND)
    }

    private suspend fun ProtectedAccess.fillBucket() {
        arriveDelay()
        anim(FILL_SEQ)
        soundSynth(FILL_SOUND)
        val water = if (priestInPeril.stage(player) < STAGE_COMPLETE) MURKY_WATER else BUCKET_OF_WATER
        invReplace(inv, BUCKET, 1, water)
        objbox(water, "You fill the bucket from the well.")
    }

    private suspend fun ProtectedAccess.passBarrier() {
        arriveDelay()
        val stage = priestInPeril.stage(player)
        when {
            priestInPeril.blessed.get(player) -> {
                delay(1)
                telejump(PaterdomusCoords.MORYTANIA_EXIT, TeleportType.Exempt)
            }
            stage >= STAGE_COMPLETE ->
                startDialogue {
                    chatNpcSpecific(DREZEL, DREZEL_MAUSOLEUM, neutral, "Wait! You'll need my blessing before you can pass through the barrier. Come and speak to me.")
                }
            stage >= STAGE_MEET_IN_MAUSOLEUM ->
                startDialogue {
                    chatNpcSpecific(DREZEL, DREZEL_MAUSOLEUM, angry, "Stop!")
                    chatPlayer(quiz, "Can't I go through there?")
                    chatNpcSpecific(
                        DREZEL,
                        DREZEL_MAUSOLEUM,
                        angry,
                        "No, you cannot! It is taking all of my willpower to hold that barrier in " +
                            "place. You must restore the sanctity of the Salve as soon as possible!",
                    )
                }
            else -> mes("The holy barrier will not let you pass.")
        }
    }

    private suspend fun ProtectedAccess.climbToBarrier() {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(PaterdomusCoords.BARRIER_NORTH, TeleportType.Exempt)
    }

    /* Monuments */

    private fun giftsTaken(stage: Int): Boolean = stage >= STAGE_COFFIN_SEALED

    private suspend fun ProtectedAccess.study(monument: Monument) {
        arriveDelay()
        if (giftsTaken(priestInPeril.stage(player))) {
            mesbox("A monument dedicated to the fallen.")
            return
        }
        mesbox(monument.study)
    }

    private suspend fun ProtectedAccess.takeFrom(monument: Monument) {
        arriveDelay()
        if (giftsTaken(priestInPeril.stage(player))) {
            mesbox("It would be wrong to dishonour this monument.")
            return
        }
        mes("You try to take the ${monument.item} from the monument but a holy power stops you!")
        val damage = HOLY_DAMAGE.coerceAtMost(player.hitpoints - 1)
        if (damage > 0) {
            queueHit(delay = 0, type = HitType.Typeless, damage = damage)
        }
    }

    private suspend fun ProtectedAccess.useOn(monument: Monument, obj: String) {
        arriveDelay()
        if (giftsTaken(priestInPeril.stage(player))) {
            mes("Nothing interesting happens.")
            return
        }
        if (monument.loc == KEY_MONUMENT) {
            useOnKeyMonument(obj)
            return
        }
        when (obj) {
            in monument.accepted -> swap(monument, obj)
            monument.golden ->
                startDialogue {
                    chatPlayer(happy, "You know... I think I'd rather keep the valuable solid gold ${monument.item}.")
                }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.swap(monument: Monument, obj: String) {
        val bit = 1 shl MONUMENTS.indexOf(monument)
        val swaps = priestInPeril.monumentSwaps.get(player)
        if (swaps and bit != 0) {
            mesbox("You have already taken the Golden ${monument.item}.")
            return
        }
        anim(SWAP_SEQ)
        invReplace(inv, obj, 1, monument.golden)
        priestInPeril.monumentSwaps.set(player, swaps or bit)
        doubleobjbox(obj, monument.golden, "You swap your ${monument.item} for the golden ${monument.item}.")
    }

    private suspend fun ProtectedAccess.useOnKeyMonument(obj: String) {
        when (obj) {
            GOLDEN_KEY -> {
                if (player.holdsAnywhere(IRON_KEY)) {
                    mes("You have already swapped the golden key for the iron key.")
                    return
                }
                anim(SWAP_SEQ)
                invReplace(inv, GOLDEN_KEY, 1, IRON_KEY)
                doubleobjbox(GOLDEN_KEY, IRON_KEY, "You swap the Golden key for the Iron key.")
            }
            IRON_KEY ->
                startDialogue {
                    chatPlayer(neutral, "I think this key is more useful to me right now than the golden key is.")
                }
            else -> mes("Nothing interesting happens.")
        }
    }

    private companion object {
        const val WEST_GATE = "loc.pip_underground_door1"
        const val EAST_GATE = "loc.pip_underground_door2"
        const val WELL = "loc.priestperil_well"
        const val BARRIER = "loc.pip_underground_wall_side_withportal"
        const val EAST_TRAPDOOR_OPEN = "loc.pipeastsidetrapdoor_open"
        const val KEY_MONUMENT = "loc.priestperil_grave_base5"
        const val DREZEL = "Drezel"

        const val FILL_SEQ = "seq.human_pickuptable"
        const val SWAP_SEQ = "seq.priestperil_gravemonument"
        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val FILL_SOUND = "synth.well_fill"
        const val UNLOCK_SOUND = "synth.unlock"
        const val GATE_OPEN_SOUND = "synth.picketgate_open"
        const val GATE_CLOSE_SOUND = "synth.picketgate_close"
        const val HOLY_DAMAGE = 1

        /* Placement per the wiki's monument infobox: each loc id carries its own gift. */
        val MONUMENTS =
            listOf(
                Monument(
                    "loc.priestperil_grave_base1",
                    "pot",
                    "obj.pippot_gold",
                    listOf("obj.pot_empty"),
                    "Saradomin is the vessel that keeps us safe from harm.",
                ),
                Monument(
                    "loc.priestperil_grave_base2",
                    "hammer",
                    "obj.piphammer_gold",
                    listOf("obj.hammer"),
                    "Saradomin is the hammer that crushes evil everywhere.",
                ),
                Monument(
                    "loc.priestperil_grave_base3",
                    "feather",
                    "obj.pipfeather_gold",
                    listOf("obj.feather"),
                    "Saradomin is the delicate touch that brushes us with love.",
                ),
                Monument(
                    "loc.priestperil_grave_base4",
                    "needle",
                    "obj.pipneedle_gold",
                    listOf("obj.needle"),
                    "Saradomin is the needle that binds our lives together.",
                ),
                Monument(
                    KEY_MONUMENT,
                    "key",
                    GOLDEN_KEY,
                    emptyList(),
                    "Saradomin is the key that unlocks the mysteries of life.",
                ),
                Monument(
                    "loc.priestperil_grave_base6",
                    "tinderbox",
                    "obj.piptinderbox_gold",
                    listOf("obj.tinderbox"),
                    "Saradomin is the spark that lights the fire in our hearts.",
                ),
                Monument(
                    "loc.priestperil_grave_base7",
                    "candle",
                    "obj.pipcandle_gold",
                    listOf("obj.unlit_candle", "obj.lit_candle"),
                    "Saradomin is the light that shines throughout our lives.",
                ),
            )
    }
}
