package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The north gate of Burgh de Rott, the only way in. Florin turns strangers away; once he has, a
 * piece of food left in the open chest outside convinces him, and from then on the gate opens for
 * that player only. Anyone may always leave.
 */
class BurghGate
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val citizens: BurghCitizens,
    private val doors: PaterdomusDoors,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (gate in listOf(GATE_LEFT, GATE_RIGHT)) {
            onOpLoc1(gate) { useGate() }
        }
        onOpNpc1(FLORIN) { startDialogue(it.npc) { florin() } }
        onApNpc1(FLORIN) {
            if (isWithinApRange(it.npc, TALK_RANGE)) {
                startDialogue(it.npc) { florin() }
            }
        }
        onOpLoc2(CHEST) { mesbox("The chest contains some empty packages. It looks like they were used to hold food.") }
        onOpLocU(CHEST) { placeInChest(it.objType.id, it.invSlot) }
        onOpLoc1(TABLE) { mesbox("A rickety table, sticky from old card games and past meals.") }
        onOpLocU(TABLE) {
            if (isFood(it.objType.id)) {
                mesbox("If you placed food here, it would just get ruined. You decide against it.")
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun ProtectedAccess.useGate() {
        arriveDelay()
        val inside = player.coords.z < BurghCoords.GATE_Z
        if (!inside && !iaom.isAdmitted(player)) {
            mesbox("The gate has been locked. Clearly the townspeople don't want visitors.")
            florinShouts()
            return
        }
        val dest = if (inside) BurghCoords.GATE_OUTSIDE else BurghCoords.GATE_INSIDE
        doors.walkThrough(
            this,
            listOf(BurghCoords.GATE_LEFT to GATE_LEFT, BurghCoords.GATE_RIGHT to GATE_RIGHT),
            dest,
            GATE_OPEN_SOUND,
            GATE_CLOSE_SOUND,
        )
    }

    private fun ProtectedAccess.florinShouts() {
        val florin = npcRepo.nearby(BurghCoords.GATE_INSIDE, FLORIN, FLORIN_RANGE) ?: return
        florin.say("Get out of it! You're not having our food or our blood!")
        florin.facePlayer(player)
        florin.anim(THROW_SEQ)
    }

    private suspend fun Dialogue.florin() {
        val stage = iaom.stage(player)
        when {
            iaom.isAdmitted(player) && stage == STAGE_ADMITTED -> {
                chatNpc(
                    happy,
                    "Hey, what did you put in that chest? A nice cooked snail? Some well-aged cheese " +
                        "on fresh bread? Ohhh, I bet it's dead tasty...",
                )
                with(citizens) { talk(npc) }
            }
            iaom.isAdmitted(player) -> with(citizens) { talk(npc) }
            else -> keepOut()
        }
    }

    private suspend fun Dialogue.keepOut() {
        chatNpc(angry, "Get out of it! You ain't coming in here for our blood or our food!")
        chatPlayer(confused, "What on earth makes you think I want your food or your blood?")
        chatNpc(angry, "Don't you sweet talk me. I know you want something! But we've got nothing! Nothing!")
        chatNpc(angry, "What blood we've got, we're keeping! You can tell that stinking Vanstrom to go suck a cow's udder!")
        chatNpc(angry, "And food? We've barely got enough to feed ourselves, so you ain't having none!")
        if (iaom.stage(player) >= STAGE_STARTED) {
            iaom.markFlorinRefused(player)
        }
        while (true) {
            val topic =
                choice4(
                    "What is this place?",
                    Topic.Place,
                    "Why would I want your blood?",
                    Topic.Blood,
                    "Why would I want your food?",
                    Topic.Food,
                    "Okay, goodbye.",
                    Topic.Bye,
                )
            when (topic) {
                Topic.Place -> {
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(angry, "This is our town! We live here. It's called Burgh de Rott, and you ain't coming in!")
                    chatPlayer(quiz, "Where did you all come from?")
                    chatNpc(neutral, "We escaped from the vampyres. They kept us penned in Meiyerditch to drink our blood! Yuck!")
                    chatPlayer(quiz, "What's Meiyerditch?")
                    chatNpc(angry, "As if you don't know! That dark city to the east, where people are kept like cattle to feed those greedy vampyres!")
                    chatPlayer(worried, "That sounds like an awful place! Let me in and I'll help you!")
                    chatNpc(laugh, "Oh yeah, of course! No way! You can't trick me like that!")
                }
                Topic.Blood -> {
                    chatPlayer(quiz, "Why would I want your blood?")
                    chatNpc(angry, "You're probably one of them stinking vampyres in disguise! You ain't coming in!")
                    chatPlayer(angry, "I don't want your blood!")
                    chatNpc(angry, "Don't care. Then you probably want our food, so you still ain't coming in!")
                }
                Topic.Food -> {
                    chatPlayer(quiz, "Why would I want your food?")
                    chatNpc(angry, "So you can eat it all, and we ain't gonna let you!")
                    chatPlayer(neutral, "But I don't want to eat your food. I'm healthy enough!")
                    chatNpc(angry, "Well, that's as maybe, but then you probably want our blood! You ain't having it and you ain't coming in!")
                }
                Topic.Bye -> {
                    chatPlayer(neutral, "Okay, goodbye.")
                    return
                }
            }
        }
    }

    private suspend fun ProtectedAccess.placeInChest(objId: Int, slot: Int) {
        arriveDelay()
        if (!isFood(objId)) {
            mes("Nothing interesting happens.")
            return
        }
        val stage = iaom.stage(player)
        if (iaom.isAdmitted(player)) {
            mes("The people of Burgh de Rott already trust you. You decide to keep your food.")
            return
        }
        if (stage < STAGE_STARTED || !iaom.isFlorinRefused(player)) {
            mes("You'd better find out who's guarding the gate before you leave anything here.")
            return
        }
        val obj = inv[slot] ?: return
        if (obj.id != objId) {
            return
        }
        invDel(inv, objSymbol(objId), count = 1, slot = slot)
        mesbox("You gingerly place the food into the chest.")
        iaom.advanceTo(this, STAGE_ADMITTED)
        startDialogue {
            chatNpcSpecific(FLORIN_NAME, FLORIN, happy, "Blimey, did you see that? They put some food in the chest for us! They're probably not after anything!")
        }
    }

    private enum class Topic {
        Place,
        Blood,
        Food,
        Bye,
    }

    internal companion object {
        const val GATE_LEFT = "loc.burgh_fencegate_l"
        const val GATE_RIGHT = "loc.burgh_fencegate_r"
        const val CHEST = "loc.burgh_quest_food_chest_open"
        const val TABLE = "loc.burgh_quest_food_table"
        const val FLORIN = "npc.burgh_vilager_8"
        const val FLORIN_NAME = "Florin"
        const val TALK_RANGE = 3
        const val FLORIN_RANGE = 6
        const val THROW_SEQ = "seq.human_throw"
        const val GATE_OPEN_SOUND = "synth.picketgate_open"
        const val GATE_CLOSE_SOUND = "synth.picketgate_close"
    }
}
