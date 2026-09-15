package org.rsmod.content.quest.area.gnomestronghold.grandtree

import dev.openrune.types.MesAnimType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.FOREMAN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.SHIPYARD_GUARD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ON_KARAMJA
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Karamja shipyard east of Shilo Village: the gate worker who wants Glough's password, the
 * workers building his fleet, and the foreman on the docks who hands over the lumber order once
 * he is satisfied the player really knows Glough.
 */
class Shipyard
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val passages: GenericPassageScript,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { gate(it.loc, it.type) }
        onOpLoc1(GATE_RIGHT) { gate(it.loc, it.type) }
        onOpNpc1(SHIPYARD_GUARD) { startDialogue(it.npc) { gateWorker(null, null) } }
        onOpNpc1(WORKER_1) { startDialogue(it.npc) { worker() } }
        onOpNpc1(WORKER_2) { startDialogue(it.npc) { worker() } }
        onOpNpc1(FOREMAN) { startDialogue(it.npc) { foreman(it.npc) } }
    }

    private suspend fun ProtectedAccess.gate(loc: BoundLocInfo, type: ObjectServerType) {
        val outside = player.coords.x < GrandTree.SHIPYARD_GATE_X
        if (outside && !grandTree.shipyardAccess.get(player) && !grandTree.quest.isQuestCompleted(player)) {
            startDialogue { gateWorker(loc, type) }
            return
        }
        with(passages) { passage(loc, type, 0) }
    }

    private suspend fun Dialogue.workerLine(mood: MesAnimType, text: String) {
        chatNpcSpecific(WORKER_NAME, SHIPYARD_GUARD, mood, text)
    }

    /** The worker on the gate. When started from the gate itself he lets the player through. */
    private suspend fun Dialogue.gateWorker(gate: BoundLocInfo?, type: ObjectServerType?) {
        workerLine(angry, "Hey you! What are you up to?")
        chatPlayer(neutral, "I'm trying to open the gate!")
        workerLine(angry, "I can see that! Why?")
        when (
            choice3(
                "I'm from the Ministry of Health and Safety.", 1,
                "Glough sent me.", 2,
                "I'm just looking around.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'm from the Ministry of Health and Safety.")
                workerLine(neutral, "Never 'erd of 'em.")
                chatPlayer(angry, "You will respect my authority!")
                workerLine(angry, "Get out of here before I give you a beating!")
            }
            2 -> password(gate, type)
            3 -> lookingAround()
        }
    }

    private suspend fun Dialogue.password(gate: BoundLocInfo?, type: ObjectServerType?) {
        chatPlayer(neutral, "Glough sent me.")
        workerLine(quiz, "Hmm...really? What for?")
        chatPlayer(angry, "You're wasting my time! Take me to your superior!")
        workerLine(neutral, "OK. Password.")
        val first = choice3("Ka.", 1, "Ko.", 2, "Ke.", 3)
        chatPlayer(neutral, listOf("Ka.", "Ko.", "Ke.")[first - 1])
        val second = choice3("Lo.", 1, "Lu.", 2, "Le.", 3)
        chatPlayer(neutral, listOf("Lo.", "Lu.", "Le.")[second - 1])
        val third = choice3("Mon.", 1, "Min.", 2, "Men.", 3)
        chatPlayer(neutral, listOf("Mon.", "Min.", "Men.")[third - 1])
        if (first == 1 && second == 2 && third == 2) {
            workerLine(neutral, "Sorry to have kept you.")
            grandTree.shipyardAccess.set(player, true)
            if (gate != null && type != null) {
                mesbox("The worker lets you through the gate.")
                with(passages) { access.passage(gate, type, 0) }
            }
            return
        }
        workerLine(angry, "You have no idea!")
        access.attackedByGuard()
    }

    private fun ProtectedAccess.attackedByGuard() {
        val guard = npcFind(GrandTree.SHIPYARD_GUARD_POST, SHIPYARD_GUARD, GUARD_RADIUS, HuntVis.Off, search) ?: return
        guard.opPlayer2(player, aiInteractions)
    }

    private suspend fun Dialogue.lookingAround() {
        chatPlayer(neutral, "I'm just looking around.")
        workerLine(angry, "This ain't a museum! Leave now!")
        chatPlayer(angry, "I'll leave when I choose!")
        workerLine(neutral, "Well you're not on the list so you're not coming in. Go away.")
        chatPlayer(neutral, "Well I'll just stand here then until you let me in.")
        workerLine(neutral, "You do that!")
        chatPlayer(neutral, "I will!")
        workerLine(quiz, "Yeah?")
        chatPlayer(angry, "Yeah!")
        workerLine(neutral, "....")
        chatPlayer(neutral, "...")
        chatPlayer(quiz, "So are you going to let me in then?")
        workerLine(neutral, "No.")
        chatPlayer(neutral, "...")
        workerLine(neutral, "....")
        chatPlayer(quiz, "You bored yet?")
        workerLine(neutral, "No. I can stand here all day.")
        chatPlayer(neutral, "...")
        workerLine(neutral, "....")
        chatPlayer(sad, "Alright you win. I'll find another way in.")
        workerLine(neutral, "No you won't.")
        chatPlayer(angry, "Yes I will.")
        workerLine(neutral, "I'm not starting that again. Maybe if I ignore you you'll go away...")
    }

    private suspend fun Dialogue.worker() {
        when (WORKER_CHATS.random()) {
            0 -> {
                chatPlayer(happy, "Hello.")
                chatPlayer(neutral, "Quite a few ships you're building!")
                chatNpc(happy, "This is just the start! The completed fleet will be awesome!")
            }
            1 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "No time to talk we've a fleet to build!")
            }
            2 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "Hello matey!")
                chatPlayer(quiz, "How are you?")
                chatNpc(sad, "Tired!")
                chatPlayer(neutral, "You shouldn't work so hard!")
            }
            3 -> {
                chatPlayer(happy, "Hello.")
                chatPlayer(neutral, "Quite an impressive set up!")
                chatNpc(neutral, "It needs to be. There's no other way to build a fleet of this size!")
            }
            4 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(quiz, "Hello there. I haven't seen you before.")
                chatPlayer(happy, "I'm new!")
                chatNpc(neutral, "Well it's hard work, but the pay is good.")
            }
            5 -> {
                chatPlayer(happy, "Hello!")
                chatPlayer(quiz, "How are you?")
                chatNpc(angry, "Too busy to waste time gossiping!")
                chatPlayer(neutral, "Touchy!")
            }
            6 -> {
                chatPlayer(happy, "Hello.")
                chatPlayer(neutral, "Looks like hard work.")
                chatNpc(neutral, "I like to keep busy.")
            }
            7 -> {
                chatPlayer(happy, "Hello!")
                chatPlayer(quiz, "So where are you sailing?")
                chatNpc(confused, "What do you mean?")
                chatPlayer(happy, "Don't worry, just kidding!")
            }
            8 -> {
                chatPlayer(happy, "Hello!")
                chatNpc(quiz, "Hello there, are you too lazy to work as well?")
                chatPlayer(neutral, "Something like that.")
                chatNpc(happy, "I'm just sun bathing!")
            }
            9 -> {
                chatPlayer(happy, "Hello!")
                chatPlayer(quiz, "What are you building?")
                chatNpc(confused, "Are you serious?")
                chatPlayer(happy, "Of course not! You're obviously building a boat.")
            }
            10 -> {
                chatPlayer(happy, "Hello!")
                chatNpc(angry, "I'm getting tired of this!")
                chatPlayer(quiz, "What?")
                chatNpc(angry, "Breaking my back for pennies! It's just not on!")
            }
            11 -> {
                chatPlayer(happy, "Hello!")
                chatNpc(angry, "What do you want?")
                chatPlayer(angry, "Is that any way to talk to your new superior?")
                chatNpc(worried, "Oh, I'm sorry, I didn't realise!")
            }
            12 -> {
                chatPlayer(happy, "Hello!")
                chatNpc(sad, "Ouch!")
                chatPlayer(worried, "What's wrong?!")
                chatNpc(sad, "I cut my finger!")
                chatNpc(quiz, "Do you have a bandage?")
                chatPlayer(sad, "I'm afraid not.")
                chatNpc(neutral, "That's ok, I'll use my shirt.")
            }
            else -> {
                chatPlayer(happy, "Hello!")
                chatNpc(quiz, "Can I help you?")
                chatPlayer(neutral, "I'm just looking around.")
                chatNpc(neutral, "Well there's plenty of work to be done, so if you don't mind...")
                chatPlayer(neutral, "Of course. Sorry to have disturbed you.")
            }
        }
    }

    private suspend fun Dialogue.foreman(npc: Npc) {
        val stage = grandTree.stage(player)
        when {
            stage == STAGE_ON_KARAMJA && !player.inv.contains(LUMBER_ORDER) -> interrogation(npc)
            stage >= STAGE_ON_KARAMJA -> mesbox("The foreman is too busy to talk.")
            else -> {
                chatPlayer(quiz, "Hello, are you in charge?")
                chatNpc(neutral, "That's right, and you are...?")
                chatPlayer(neutral, "Just passing through.")
                chatNpc(angry, "Then pass through somewhere else. We've a fleet to build!")
            }
        }
    }

    private suspend fun Dialogue.interrogation(npc: Npc) {
        chatPlayer(quiz, "Hello, are you in charge?")
        chatNpc(neutral, "That's right, and you are...?")
        chatPlayer(neutral, "Glough sent me to check on how you are doing.")
        chatNpc(quiz, "Right. Glough sent a human?")
        chatPlayer(neutral, "His gnomes are busy.")
        chatNpc(neutral, "Hmm...in that case we'd better go to my office. Follow me.")
        mesbox("You follow the Foreman to his office.")
        chatNpc(quiz, "Tell me again why you're here.")
        chatPlayer(confused, "Er...Glough sent me?")
        chatNpc(quiz, "By the way how is Glough? Still with his wife?")
        when (
            choice3(
                "Yes, they're getting on great.", 1,
                "Always arguing as usual!", 2,
                "Sadly his wife is no longer with us!", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, they're getting on great.")
                imposter(npc, "Really? That's odd, considering she died last year. Die imposter!")
                return
            }
            2 -> {
                chatPlayer(neutral, "Always arguing as usual!")
                imposter(npc, "Really? That's odd, considering she died last year. Die imposter!")
                return
            }
            3 -> chatPlayer(sad, "Sadly his wife is no longer with us!")
        }
        chatNpc(neutral, "Right answer. I have to watch for imposters. What's Glough's favourite dish?")
        when (choice3("He loves tangled toads legs.", 1, "He loves worm holes.", 2, "He loves choc bombs.", 3)) {
            2 -> chatPlayer(neutral, "He loves worm holes.")
            else -> {
                chatPlayer(neutral, "He loves tangled toads legs.")
                imposter(npc, "Our survey said.... Bzzzzzz! Wrong answer!")
                return
            }
        }
        chatNpc(quiz, "OK. Just one more. What's the name of his new girlfriend?")
        when (choice3("Anita.", 1, "Alia.", 2, "Elena.", 3)) {
            1 -> chatPlayer(neutral, "Anita.")
            2 -> {
                chatPlayer(neutral, "Alia.")
                imposter(npc, "You almost had me fooled! Die imposter!")
                return
            }
            3 -> {
                chatPlayer(neutral, "Elena.")
                imposter(npc, "You almost had me fooled! Die imposter!")
                return
            }
        }
        chatNpc(happy, "Well, well, you do know Glough. Sorry for the interrogation but I'm sure you understand.")
        chatPlayer(neutral, "Of course, security is paramount.")
        chatNpc(neutral, "As you can see things are going well.")
        chatPlayer(neutral, "Indeed.")
        chatNpc(neutral, "When I was asked to build a fleet large enough to invade Port Sarim and carry 300 gnome troops I said: 'If anyone can, I can.'")
        chatPlayer(confused, "That's a lot of troops!")
        chatNpc(neutral, "True but if the gnomes are really going to take over Gielinor they'll need at least that.")
        chatPlayer(confused, "Take over?")
        chatNpc(neutral, "Of course, why else would Glough want 30 battleships? Between you and me I don't think he stands a chance.")
        chatPlayer(quiz, "No?")
        chatNpc(neutral, "I mean, for the kind of battleships Glough's ordered I'll need tons and tons of lumber! Still, if he says he can supply the wood I'm sure he can! Anyway, here's the order for the lumber.")
        access.invAddOrDrop(objRepo, LUMBER_ORDER)
        grandTree.advanceTo(access, STAGE_HAS_LUMBER_ORDER)
        objbox(LUMBER_ORDER, "The foreman has given you the lumber order.")
        chatPlayer(neutral, "OK. I'll head off and give this order to Glough.")
    }

    private suspend fun Dialogue.imposter(npc: Npc, line: String) {
        chatNpc(angry, line)
        npc.opPlayer2(player, aiInteractions)
    }

    private companion object {
        const val GATE_LEFT = "loc.grandtree_fencegate_l"
        const val GATE_RIGHT = "loc.grandtree_fencegate_r"
        const val WORKER_1 = "npc.shipyardworker1"
        const val WORKER_2 = "npc.shipyardworker2"
        const val WORKER_NAME = "Shipyard worker"
        const val GUARD_RADIUS = 8
        val WORKER_CHATS = 0 until 15
    }
}
