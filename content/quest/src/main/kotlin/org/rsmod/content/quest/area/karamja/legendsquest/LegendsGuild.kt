package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.MACHETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.REQUIRED_QUEST_POINTS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RETURNED
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Legends' Guild grounds north of Ardougne: the guards who vet would-be members at the
 * mithril gate, the gate itself, the doors of the main hall and the cupboard in Radimus Erkle's
 * study that keeps the machetes.
 *
 * Before the quest the guards stop everyone at the gate and let through only those eligible to
 * start it. Once it is under way they nod questers through, and they salute members.
 */
class LegendsGuild
@Inject
constructor(
    private val legends: LegendsQuest,
    private val support: LegendsSupport,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) {
                mes("You approach a nearby guard...")
                delay(1)
                startDialogue(it.npc) { guardGreeting() }
            }
        }
        onOpLoc1(GATE_LEFT) { gate(it.vis, it.type) }
        onOpLoc1(GATE_RIGHT) { gate(it.vis, it.type) }
        onOpLoc2(GATE_LEFT) { mes("These gates seem to be made of pure mithril!") }
        onOpLoc2(GATE_RIGHT) { mes("These gates seem to be made of pure mithril!") }
        onOpLoc1(HALL_DOOR_LEFT) { hallDoor(it.vis, it.type) }
        onOpLoc1(HALL_DOOR_RIGHT) { hallDoor(it.vis, it.type) }
        onOpLoc1(CUPBOARD) { openCupboard(it.vis) }
        onOpLoc2(CUPBOARD_OPEN) { searchCupboard() }
        onOpLoc3(CUPBOARD_OPEN) { shutCupboard(it.vis) }
    }

    private suspend fun ProtectedAccess.gate(gate: BoundLocInfo, type: ObjectServerType) {
        val inside = coords.z >= LegendsCoords.GATE_INSIDE.z
        val guard = support.findNpc(coords, GUARDS[0], GUARD_REACH) ?: support.findNpc(coords, GUARDS[1], GUARD_REACH)
        if (inside) {
            with(passages) { walkThrough(gate, type) }
            if (guard != null && legends.stage(player) == 0) {
                guard.say("Good day ${if (isBodyTypeA()) "Sir" else "Madam"}!")
            }
            return
        }
        when {
            legends.isComplete(player) -> {
                guard?.say("! ! ! Attention ! ! !")
                mes("The guards salute you as you walk past.")
                with(passages) { walkThrough(gate, type) }
            }
            legends.isStarted(player) -> {
                mes("A guard nods at you as you walk past.")
                guard?.say("Hope the quest is going well ${if (isBodyTypeA()) "Sir" else "Madam"}!")
                with(passages) { walkThrough(gate, type) }
            }
            else -> {
                arriveDelay()
                mes("A nearby guard approaches you...")
                delay(1)
                if (guard == null) {
                    mesbox("The gate is locked. A guard will have to let you in.")
                    return
                }
                startDialogue(guard) { guardGreeting() }
            }
        }
    }

    private suspend fun Dialogue.guardGreeting() {
        val sir = if (access.isBodyTypeA()) "sir" else "ma'am"
        when {
            legends.isComplete(player) -> {
                npc?.say("! ! ! Attention ! ! !")
                chatNpc(neutral, "! ! ! Attention ! ! !")
                npc?.say("Legends Guild Member Approaching!")
                chatNpc(neutral, "Legends Guild Member Approaching!")
                chatNpc(
                    neutral,
                    "Welcome ${if (access.isBodyTypeA()) "Sir" else "Madam"}! I hope you enjoy " +
                        "your time in the Legends' Guild.",
                )
            }
            legends.isStarted(player) -> {
                access.mes("A guard nods at you as you walk past.")
                npc?.say("I hope the quest is going well ${if (access.isBodyTypeA()) "Sir" else "Madam"}!")
            }
            else -> {
                chatNpc(neutral, "Yes $sir, how can I help you?")
                guardMenu()
            }
        }
    }

    private suspend fun Dialogue.guardMenu() {
        when (
            choice4(
                "What is this place?", 1,
                "How do I get in here?", 2,
                "Can I speak to someone in charge?", 3,
                "It's ok thanks.", 4,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> howDoIGetIn()
            3 -> someoneInCharge()
            else -> {
                chatPlayer(neutral, "It's ok thanks.")
                chatNpc(neutral, "Very well ${sir()}!")
            }
        }
    }

    private fun Dialogue.sir(): String = if (access.isBodyTypeA()) "sir" else "ma'am"

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(neutral, "What is this place?")
        chatNpc(neutral, "This is the Legends Guild, ${sir()}!")
        chatNpc(neutral, "Legendary RuneScape citizens are invited on a quest in order to become members of the guild.")
        if (choice2("Can I go on the quest?", 1, "What kind of quest is it?", 2) == 1) {
            canIGo()
        } else {
            whatKindOfQuest()
        }
    }

    private suspend fun Dialogue.howDoIGetIn() {
        chatPlayer(quiz, "How do I get in here?")
        chatNpc(neutral, "Well ${sir()}, you'll need to be a legendary citizen of RuneScape.")
        chatNpc(
            neutral,
            "If you want to use the Legends Hall, you'll be invited to complete a quest. Once you " +
                "have completed that quest, you'll be a fully fledged member of the Guild.",
        )
        when (
            choice3(
                "What is this place?", 1,
                "Can I speak to someone in charge?", 2,
                "Can I go on the quest?", 3,
            )
        ) {
            1 -> whatIsThisPlace()
            2 -> someoneInCharge()
            else -> canIGo()
        }
    }

    private suspend fun Dialogue.someoneInCharge() {
        chatPlayer(neutral, "Can I speak to someone in charge?")
        chatNpc(
            neutral,
            "Well ${sir()}, Radimus Erkle is the Grand Vizier of the Legends Guild. He's a very " +
                "busy man. And he'll only talk to those people eligible for the quest.",
        )
        if (choice2("Can I go on the quest?", 1, "What kind of quest is it?", 2) == 1) {
            canIGo()
        } else {
            whatKindOfQuest()
        }
    }

    private suspend fun Dialogue.whatKindOfQuest() {
        chatPlayer(neutral, "What kind of quest is it?")
        chatNpc(
            neutral,
            "Well, to be honest ${sir()}, I'm not really sure. You'll need to talk to Grand " +
                "Vizier Erkle to find that out.",
        )
        if (choice2("Can I go on the quest?", 1, "Thanks for your help.", 2) == 1) {
            canIGo()
            return
        }
        chatPlayer(neutral, "Thanks for your help.")
        chatNpc(neutral, "You're welcome...")
        access.mes("The Guard marches off on patrol again.")
    }

    private suspend fun Dialogue.canIGo() {
        chatPlayer(neutral, "Can I go on the quest?")
        mesbox("The guard gets out a scroll of paper and starts looking through it.")
        val enoughPoints = legends.questPoints(player) >= REQUIRED_QUEST_POINTS
        if (legends.hasRequiredQuests(player)) {
            if (!enoughPoints) {
                chatNpc(
                    neutral,
                    "Well, you've completed the required quests. However, you also need to have " +
                        "107 Quest points. Sorry to disappoint you, but you need ${missingPoints()}. " +
                        "They don't call it the Legends Guild for nothing you know!",
                )
                return
            }
            chatNpc(
                neutral,
                "Well, it looks as if you are eligible for the quest. Grand Vizier Erkle will give " +
                    "you the details about the quest. You can go and talk to him about it if you like?",
            )
            when (
                choice3(
                    "Who is Grand Vizier Erkle?", 1,
                    "Yes, I'd like to talk to Grand Vizier Erkle.", 2,
                    "Some other time perhaps.", 3,
                )
            ) {
                1 -> whoIsErkle()
                2 -> letIn()
                else -> chatPlayer(neutral, "Some other time perhaps?")
            }
            return
        }
        if (enoughPoints) {
            chatNpc(sad, "I'm very sorry but you need to complete more quests before you can go on this quest.")
        } else {
            chatNpc(
                sad,
                "I'm very sorry but you need to complete more quests before you can go on this " +
                    "quest. Additionally you need to gain ${missingPoints()}. They don't call it " +
                    "the Legends Guild for nothing you know!",
            )
        }
        if (choice2("Which quests do I need to complete?", 1, "Ok thanks.", 2) == 1) {
            whichQuests()
        } else {
            chatPlayer(neutral, "Ok, thanks.")
            chatNpc(neutral, "That's no problem... Best of luck if you intend to become a member!")
        }
    }

    private fun Dialogue.missingPoints(): String {
        val needed = REQUIRED_QUEST_POINTS - legends.questPoints(player)
        return if (needed == 1) "one more quest point" else "$needed more quest points"
    }

    private suspend fun Dialogue.whichQuests() {
        chatPlayer(neutral, "Which quests do I need to complete?")
        val missing = legends.missingQuests(player)
        if (missing.size == 1) {
            chatNpc(neutral, "Just one: ${missing.single()}.")
        } else {
            val list = missing.dropLast(1).joinToString(", ") + " and " + missing.last()
            chatNpc(neutral, "Well, you'll need to complete the following quests: $list.")
        }
        if (legends.questPoints(player) < REQUIRED_QUEST_POINTS) {
            chatNpc(neutral, "You also need a total of 107 quest points.")
        } else {
            chatNpc(neutral, "You already have enough quest points.")
        }
        chatNpc(
            neutral,
            "They don't call it the Legends Guild for nothing you know! Best of luck if you " +
                "intend to become a member!",
        )
    }

    private suspend fun Dialogue.whoIsErkle() {
        chatPlayer(neutral, "Who is Grand Vizier Erkle?")
        chatNpc(
            neutral,
            "He is the head of the Legends Guild. His full name is Radimus Erkle. Would you like " +
                "to talk to him about the quest?",
        )
        if (choice2("Yes, I'd like to talk to Grand Vizier Erkle.", 1, "Some other time perhaps.", 2) == 1) {
            letIn()
        } else {
            chatPlayer(neutral, "Some other time perhaps?")
        }
    }

    private suspend fun Dialogue.letIn() {
        chatPlayer(neutral, "Yes, I'd like to talk to Grand Vizier Erkle.")
        chatNpc(neutral, "Ok, very well... You need to go into the building on the left, he's in his study.")
        mesbox("The guard unlocks the gate and pushes it open for you.")
        chatNpc(neutral, "Good Luck!")
        access.ifClose()
        with(access) { walkInThroughGate() }
    }

    private suspend fun ProtectedAccess.walkInThroughGate() {
        if (coords.z >= LegendsCoords.GATE_INSIDE.z) {
            return
        }
        if (coords != LegendsCoords.GATE_OUTSIDE) {
            playerWalk(LegendsCoords.GATE_OUTSIDE)
        }
        val gateCoords = LegendsCoords.GATE_WEST.translateX(1)
        val type = ServerCacheManager.getObject(GATE_RIGHT.asRSCM(RSCMType.LOC)) ?: return
        val info = locRepo.findExact(gateCoords, type) ?: return
        with(passages) { walkThrough(BoundLocInfo(info, type), type) }
    }

    private suspend fun ProtectedAccess.hallDoor(door: BoundLocInfo, type: ObjectServerType) {
        val leaving = coords.z > door.coords.z
        if (leaving) {
            with(passages) { walkThrough(door, type) }
            return
        }
        if (legends.stage(player) >= STAGE_RETURNED) {
            mes("You approach the Legends Guild main doors.")
            mes("You push the huge Legends Guild doors open.")
            with(passages) { walkThrough(door, type) }
            return
        }
        arriveDelay()
        mesbox("You need to complete the Legends Guild Quest before you can enter the Legends Guild.")
    }

    private suspend fun ProtectedAccess.openCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(CUPBOARD_OPEN_SOUND)
        locRepo.change(cupboard, CUPBOARD_OPEN, CUPBOARD_OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.shutCupboard(cupboard: BoundLocInfo) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(CUPBOARD_CLOSE_SOUND)
        locRepo.change(cupboard, CUPBOARD, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.searchCupboard() {
        arriveDelay()
        val radimus = support.findNpc(coords, RADIMUS, RADIMUS_REACH) ?: run {
            mes("You search the cupboard but find nothing of interest.")
            return
        }
        if (legends.isComplete(player)) {
            radimusSays(radimus, "Hey, I think it's about time you started buying your own machetes. I'm sure there must be some jungle native down in Karamja who can sell you one.")
            return
        }
        when {
            carries(MACHETE) ->
                radimusSays(radimus, "Hey, you've already got one machete, you don't need two do you?")
            bank.count(MACHETE) > 0 ->
                radimusSays(radimus, "Hey, I hear that you have enough machetes in your bank to start your own store. Please don't take my generosity for granted.")
            else -> {
                if (!invAdd(inv, MACHETE).success) {
                    mes("You don't have enough room in your inventory for the machete.")
                    return
                }
                objbox(MACHETE, "You find a machete in the cupboard.")
                mes("You find a machete.")
            }
        }
    }

    private suspend fun ProtectedAccess.radimusSays(radimus: Npc, text: String) {
        startDialogue(radimus) { chatNpc(neutral, text) }
    }

    private companion object {
        val GUARDS = listOf("npc.legends_guild_guard1", "npc.legends_guild_guard2")
        const val RADIMUS = "npc.radimus_erkle_hut"
        const val GUARD_REACH = 14
        const val RADIMUS_REACH = 5

        const val GATE_LEFT = "loc.legendsguildgatel"
        const val GATE_RIGHT = "loc.legendsguildgater"
        const val HALL_DOOR_LEFT = "loc.legendsguilddoorl"
        const val HALL_DOOR_RIGHT = "loc.legendsguilddoorr"
        const val CUPBOARD = "loc.legends_cupboard"
        const val CUPBOARD_OPEN = "loc.legends_cupboardopen"
        const val CUPBOARD_OPEN_TICKS = 300

        const val OPEN_SEQ = "seq.human_pickuptable"
        const val CUPBOARD_OPEN_SOUND = "synth.cupboard_open"
        const val CUPBOARD_CLOSE_SOUND = "synth.cupboard_close"
    }
}
