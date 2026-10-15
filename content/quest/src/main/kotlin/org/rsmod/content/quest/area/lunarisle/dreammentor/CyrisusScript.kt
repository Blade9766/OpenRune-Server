package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.lunarisle.dreammentor.CyrisusRecovery.Feeding
import org.rsmod.content.quest.area.lunarisle.dreammentor.CyrisusSpirit.converse
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_ARMED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_AWAKE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_MORE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_WELL
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_GOT_CHEST
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_NEEDS_ARMOUR
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_RESTORED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SITTING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SPIRIT_LIFTED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STANDING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Talking to, inspecting and feeding Cyrisus. In the cave he goes from unconscious to sitting to
 * standing as his health and spirit reach each marker, then wants armour from his own bank and,
 * once dressed and fully recovered, confesses his fear of combat. The dressed forms are also the
 * ones his two town multinpcs show, so their ops are passed on to [DreamTown].
 */
class CyrisusScript
@Inject
constructor(
    private val dreamMentor: DreamMentorQuest,
    private val cave: CyrisusCave,
    private val status: CyrisusStatus,
    private val town: DreamTown,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(CyrisusCave.ENTRANCE) {
            with(cave) { crawl(into = it.loc.coords == CyrisusCave.MINE_ENTRANCE) }
        }
        for (form in CyrisusCave.CAVE_FORMS) {
            onOpNpc1(form) { talk(it.npc) }
            onOpNpc3(form) { inspect(it.npc) }
            onOpNpcU(form) { useOn(it.npc, it.objType) }
        }
        onPlayerSoftTimer(CyrisusCave.CAVE_TIMER) {
            if (CyrisusCave.inCave(player.coords)) {
                cave.refresh(player)
            } else {
                cave.despawn(player)
                player.clearSoftTimer(CyrisusCave.CAVE_TIMER)
            }
        }
        onPlayerLogin {
            CyrisusRecovery.forgetMeals(player)
            if (CyrisusCave.inCave(player.coords)) {
                cave.refresh(player)
                player.softTimer(CyrisusCave.CAVE_TIMER, CyrisusCave.CAVE_CHECK_TICKS)
            }
        }
        onPlayerLogout { cave.despawn(player) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (town.isTownCyrisus(npc)) {
            with(town) { talkInTown(npc) }
            return
        }
        if (cave.isSomeoneElses(player, npc)) {
            mes("He doesn't seem to notice you.")
            return
        }
        val stage = dreamMentor.stage(player)
        when {
            stage == 0 -> startQuest(npc)
            stage == STAGE_STARTED -> mesbox("He is still unconscious. You had better build up his health first.")
            stage == STAGE_SPIRIT_LIFTED || stage == STAGE_SITTING -> {
                startDialogue(npc) {
                    chatPlayer(neutral, "Can you hear me?")
                    chatNpc(sad, if (stage == STAGE_SITTING) "Yes... but I'm still in pain." else "Only just.")
                }
                mesbox("He looks hungry. Some more food would do him good.")
            }
            stage == STAGE_AWAKE || stage == STAGE_FED_MORE || stage == STAGE_FED_WELL ->
                startDialogue(npc) { lift(npc) }
            stage == STAGE_STANDING -> startDialogue(npc) { standingUp() }
            stage == STAGE_NEEDS_ARMOUR || stage == STAGE_GOT_CHEST || stage == STAGE_ARMED ->
                startDialogue(npc) { gearingUp(npc) }
            stage == STAGE_RESTORED ->
                startDialogue(npc) {
                    chatPlayer(quiz, "Ready?")
                    chatNpc(happy, "Ready! Go and talk to the Oneiromancer. I'll meet you there, quicker than you think.")
                }
            else -> Unit
        }
    }

    private suspend fun ProtectedAccess.inspect(npc: Npc) {
        if (town.isTownCyrisus(npc) || !cave.isSomeoneElses(player, npc) && dreamMentor.stage(player) > 0) {
            with(status) { openStatus() }
            return
        }
        talk(npc)
    }

    private suspend fun ProtectedAccess.startQuest(npc: Npc) {
        var accepted = false
        startDialogue(npc) {
            mesbox("This poor man looks like he has been down on his luck for a long time. Will you help him?")
            accepted = choice2("Yes.", true, "No.", false, title = "Start the Dream Mentor quest?")
            if (!accepted) {
                mesbox("You turn away, but you can't shake the feeling that you should have helped.")
                return@startDialogue
            }
            val missing = dreamMentor.missingRequirements(player)
            if (missing.isNotEmpty()) {
                accepted = false
                mesbox("You aren't ready to help him yet. You still need: ${missing.joinToString(", ")}.")
                return@startDialogue
            }
            mesbox("His clothes are in tatters and he is covered in cuts and bruises. He has been down here a long time.")
            mesbox("There's a pulse, and he's breathing, though only just.")
            player.dreamCombatType =
                CombatStyle.dominant(
                    access.statBase("stat.attack"),
                    access.statBase("stat.strength"),
                    access.statBase("stat.ranged"),
                    access.statBase("stat.magic"),
                ).value
            dreamMentor.advanceTo(access, STAGE_STARTED)
            mesbox(
                "The next screen shows how he is doing. Click on a stat's name to find out more. " +
                    "Each stat needs raising to its markers before he can move on.",
            )
        }
        if (accepted) {
            with(status) { openStatus() }
            mes("You can open this screen again by selecting 'Inspect' on the man.")
        }
    }

    /* Spirit */

    private suspend fun Dialogue.lift(npc: Npc) {
        chatPlayer(neutral, "Can you hear me?")
        chatNpc(sad, "Just about.")
        if (!CyrisusRecovery.wantsCompany(player, dreamMentor.stage(player))) {
            return
        }
        if (converse()) {
            CyrisusRecovery.cheer(player, dreamMentor.stage(player))
        }
        checkpoint(npc)
    }

    private suspend fun Dialogue.checkpoint(npc: Npc) {
        val stage = dreamMentor.stage(player)
        val next = CyrisusRecovery.nextStage(player, stage) ?: return
        when (next) {
            STAGE_AWAKE -> wakeUp(npc)
            STAGE_SPIRIT_LIFTED -> {
                dreamMentor.advanceTo(access, next)
                chatNpc(neutral, "I do feel a little better for talking. I'm so hungry, though.")
            }
            STAGE_FED_MORE -> {
                dreamMentor.advanceTo(access, next)
                mesbox("He takes the food gratefully. He's looking a little less grey.")
            }
            STAGE_SITTING -> {
                npc.anim(SIT_UP_SEQ)
                access.delay(TRANSITION_TICKS)
                dreamMentor.advanceTo(access, next)
                cave.refresh(player)
                chatNpc(happy, "Oof. I think I can sit up now. Thank you.")
                chatPlayer(happy, "Take it slowly. Some more food and you'll be on your feet.")
            }
            STAGE_FED_WELL -> {
                dreamMentor.advanceTo(access, next)
                chatNpc(happy, "That's much better. My strength is coming back.")
            }
            STAGE_STANDING -> {
                npc.anim(STAND_UP_SEQ)
                access.delay(TRANSITION_TICKS)
                dreamMentor.advanceTo(access, next)
                cave.refresh(player)
                standingUp()
            }
        }
    }

    private suspend fun Dialogue.wakeUp(npc: Npc) {
        npc.anim(WAKE_SEQ)
        access.delay(TRANSITION_TICKS)
        dreamMentor.advanceTo(access, STAGE_AWAKE)
        cave.refresh(player)
        mesbox("He swallows the food, and his eyes flicker open.")
        chatPlayer(worried, "Hello? Can you hear me?")
        chatNpc(sad, "Mmmph... it hurts...")
        chatPlayer(worried, "Stay still. You're going to be all right.")
        chatNpc(sad, "Help... me...")
        chatPlayer(neutral, "I will. Just rest for now.")
    }

    private suspend fun Dialogue.standingUp() {
        chatNpc(happy, "Thank you. I really thought I was going to die down here.")
        chatPlayer(happy, "You're getting better, but don't push yourself. You're not ready to walk out yet.")
        chatNpc(confused, "Where am I? And who are you?")
        chatPlayer(neutral, "I'm ${player.displayName}. You're in a cave under Lunar Isle.")
        chatNpc(neutral, "Lunar Isle... I'm Cyrisus. I remember now. I was exploring, and then...")
        chatNpc(shocked, "Suqahs! A whole pack of them! I ran and ran until I found this hole and crawled in.")
        chatPlayer(neutral, "You're safe now. Nothing followed you.")
        chatNpc(sad, "My head is pounding and my legs won't stop shaking. Maybe you should leave me. I'm not worth it.")
        chatPlayer(neutral, "Not a chance. I'm getting you out of here.")
        chatPlayer(quiz, "So what brought you to the island?")
        chatNpc(
            happy,
            "Exploring. I've wandered from the frozen Wilderness to the desert pyramids, lived " +
                "among the monkeys of Ape Atoll and eaten with the TzHaar under Karamja.",
        )
        chatPlayer(quiz, "With all that behind you, the suqahs must have been a surprise. You can fight, surely?")
        chatNpc(worried, "Er... well... can we talk about something else?")
        chatPlayer(neutral, "If you like. What now, then?")
        chatNpc(
            neutral,
            "I'm not ready to go outside yet, and I'd feel a lot safer with some armour on.",
        )
        chatPlayer(neutral, "I could lend you some of mine.")
        chatNpc(
            neutral,
            "No need. I keep plenty in my account at the bank on this island. A friend of mine " +
                "works there; tell him I sent you. You'll know him when you see him.",
        )
        dreamMentor.advanceTo(access, STAGE_NEEDS_ARMOUR)
        chatPlayer(bored, "That's not mysterious at all.")
    }

    /* Armour */

    private suspend fun Dialogue.gearingUp(npc: Npc) {
        val stage = dreamMentor.stage(player)
        val spirit = CyrisusRecovery.wantsCompany(player, stage)
        val topic =
            if (spirit) {
                choice2("Talk about the armament.", 1, "Try to raise his spirit.", 2, title = "What do you want to talk about?")
            } else {
                1
            }
        if (topic == 2) {
            lift(npc)
            restoredCheck()
            return
        }
        when (stage) {
            STAGE_NEEDS_ARMOUR -> {
                chatPlayer(quiz, "You still want some armour?")
                chatNpc(neutral, "Please. My friend at the bank on this island will sort you out.")
            }
            STAGE_GOT_CHEST -> fitArmour(npc)
            else -> {
                chatPlayer(happy, "Looking good in that kit!")
                chatNpc(happy, "I feel safer already. I'm still not quite myself, though.")
                restoredCheck()
            }
        }
    }

    private suspend fun Dialogue.fitArmour(npc: Npc) {
        if (CyrisusBank.CHEST !in player.inv) {
            chatPlayer(neutral, "Your friend at the bank is letting me choose some armour for you.")
            chatNpc(neutral, "Bring it to me in my chest, then. I can't wear what isn't here.")
            return
        }
        if (CyrisusChest.isEmpty(player)) {
            chatPlayer(neutral, "I've got your chest.")
            chatNpc(confused, "But there's nothing in it! Fill it from my account first.")
            return
        }
        chatPlayer(happy, "I've been to the bank and filled your chest. See what you think.")
        chatNpc(neutral, "All right, I'll try it on. No peeking!")
        chatPlayer(neutral, "I'll keep my eyes shut.")
        access.fadeToBlack()
        access.delay(FITTING_TICKS)
        access.fadeFromBlack()
        access.closeFadeOverlay()
        val fit = CyrisusChest.fit(player, CombatStyle.of(player))
        player.dreamArmament = CyrisusChest.armament(fit)
        if (fit.all { it == CyrisusChest.Fit.Right }) {
            if (access.invDel(access.inv, CyrisusBank.CHEST).failure) {
                return
            }
            CyrisusChest.clear(player)
            dreamMentor.advanceTo(access, STAGE_ARMED)
            cave.refresh(player)
            chatNpc(happy, "Perfect! I couldn't have picked a better set myself.")
            chatPlayer(happy, "Excellent!")
            restoredCheck()
            return
        }
        chatNpc(neutral, "I've put it all back in the chest. Some of it's all right, but it isn't the right combination.")
        chatPlayer(confused, "Any hints?")
        for ((slot, piece) in fit.withIndex()) {
            val name = CyrisusChest.SLOT_NAMES[slot]
            when (piece) {
                CyrisusChest.Fit.Right -> Unit
                CyrisusChest.Fit.Missing -> chatNpc(neutral, "I'll need some $name as well.")
                CyrisusChest.Fit.Wrong -> chatNpc(neutral, "The $name has to go. It's not right for me at all.")
            }
        }
    }

    /* Food */

    private suspend fun ProtectedAccess.useOn(npc: Npc, obj: ItemServerType) {
        if (town.isTownCyrisus(npc)) {
            mes("He doesn't need that now.")
            return
        }
        if (cave.isSomeoneElses(player, npc)) {
            mes("He doesn't seem to notice you.")
            return
        }
        val stage = dreamMentor.stage(player)
        if (stage == 0) {
            startQuest(npc)
            return
        }
        when (val feeding = CyrisusRecovery.feeding(player, stage, obj.internalName)) {
            Feeding.NotFood -> mes("You can only give him something to eat.")
            Feeding.Sweets -> mes("Sweets? They'll rot his teeth.")
            Feeding.NotHungry -> mes("You don't need to improve his health any more right now.")
            Feeding.Recent -> mesbox("He pushes the food away. He'd rather have something different.")
            is Feeding.Eats -> feed(npc, stage, feeding)
        }
    }

    private suspend fun ProtectedAccess.feed(npc: Npc, stage: Int, meal: Feeding.Eats) {
        val leftover = meal.leftover
        val consumed =
            if (leftover != null) {
                invReplace(inv, meal.obj, 1, leftover).success
            } else {
                invDel(inv, meal.obj).success
            }
        if (!consumed) {
            return
        }
        anim(FEED_SEQ)
        CyrisusRecovery.eat(player, stage, meal)
        val named = stage >= STAGE_STANDING
        val nextStage = CyrisusRecovery.nextStage(player, stage)
        if (nextStage == null && stage != STAGE_ARMED) {
            mesbox(if (named) "Cyrisus eats the food. He seems to enjoy it." else "You manage to feed him. He seems to like it.")
            return
        }
        startDialogue(npc) {
            if (nextStage != null) {
                checkpoint(npc)
            } else {
                mesbox("Cyrisus eats the food. He seems to enjoy it.")
                restoredCheck()
            }
        }
    }

    /* Fully restored */

    private suspend fun Dialogue.restoredCheck() {
        if (dreamMentor.stage(player) != STAGE_ARMED || !CyrisusRecovery.fullyRestored(player)) {
            return
        }
        chatNpc(happy, "That's it! I feel healthy, I feel positive, and I look ready for war!")
        chatPlayer(happy, "You certainly look the part.")
        chatPlayer(quiz, "So now will you tell me why the suqahs gave you so much trouble?")
        chatNpc(worried, "Ah. Well. The thing is... the thing I ought to explain... the problem, you see...")
        chatPlayer(neutral, "Just say it.")
        chatNpc(sad, "I'm afraid of combat.")
        chatPlayer(shocked, "What?")
        chatNpc(
            sad,
            "I know every technique and I own every kind of armour, but as soon as a monster comes " +
                "at me my legs turn to jelly and I run.",
        )
        chatNpc(
            neutral,
            "That's partly why I came here. I hoped the Moon Clan could send me into my dreams to " +
                "face my fear. You've been through their dream ceremony yourself, haven't you?",
        )
        chatPlayer(neutral, "I have. It was very strange, and very useful.")
        chatNpc(quiz, "Would you come with me to see the Oneiromancer? I'd feel braver with you there.")
        chatPlayer(happy, "Of course.")
        dreamMentor.advanceTo(access, STAGE_RESTORED)
        player.dreamCyrisusSpot = CyrisusSpot.oneiromancer(CombatStyle.of(player))
        chatNpc(happy, "Thank you! You go on ahead. I'll find my own way there; I'm not walking past those suqahs again!")
    }

    private companion object {
        const val WAKE_SEQ = "seq.dream_cyrisus_barely_conscious_transition"
        const val SIT_UP_SEQ = "seq.dream_cyrisus_sit_up_transition"
        const val STAND_UP_SEQ = "seq.dream_cyrisus_stand_up_transition"
        const val FEED_SEQ = "seq.dream_player_feed_cyrisus_lying"
        const val TRANSITION_TICKS = 2
        const val FITTING_TICKS = 3
    }
}
