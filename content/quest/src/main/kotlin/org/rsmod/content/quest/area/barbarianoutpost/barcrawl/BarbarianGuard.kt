package org.rsmod.content.quest.area.barbarianoutpost.barcrawl

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.CARD
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.barbarianoutpost.barcrawl.BarcrawlQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two Barbarian guards at the gate on the east side of the Barbarian Outpost. They start the
 * barcrawl, take the finished card, teach the vial-smashing trick and keep the gate shut to anyone
 * who has not drunk like a barbarian. The gate leads to the agility course pipe, so players on the
 * course side can always leave.
 */
class BarbarianGuard
@Inject
constructor(
    private val barcrawl: BarcrawlQuest,
    private val passages: GenericPassageScript,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {
    private val quest
        get() = barcrawl.quest

    override fun ScriptContext.startup() {
        onOpNpc1(GUARD) { startDialogue(it.npc) { guard() } }
        onOpNpc3(GUARD) { toggleVials() }
        for (gate in GATES) {
            onOpLoc1(gate) { openGate(it.loc, it.type) }
        }
    }

    private suspend fun Dialogue.guard() {
        when {
            quest.isQuestCompleted(player) -> vialSmashing()
            quest.isQuestInProgress(player) -> progress()
            else -> firstMeeting()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(angry, "Oi, whaddya want?")
        val wantsIn =
            menu("I want to come through this gate." to true, "I want some money." to false)
        if (!wantsIn) {
            chatPlayer(happy, "I want some money.")
            chatNpc(angry, "Do I look like a bank to you?")
            return
        }
        chatPlayer(neutral, "I want to come through this gate.")
        chatNpc(neutral, "Barbarians only. Are you a barbarian? You don't look like one.")
        val claimsBarbarian =
            choice2(
                "Hmm, yep you've got me there.",
                false,
                "Looks can be deceiving, I am in fact a barbarian.",
                true,
            )
        if (!claimsBarbarian) {
            chatPlayer(sad, "Hmm, yep you've got me there.")
            return
        }
        chatPlayer(happy, "Looks can be deceiving, I am in fact a barbarian.")
        if (player.inv.isFull()) {
            chatNpc(
                neutral,
                "Are you, now? Free up some space so you can carry something, and we'll see how " +
                    "barbarian you really are.",
            )
            return
        }
        chatNpc(
            happy,
            "If you're a barbarian you need to be able to drink like one. We barbarians like a " +
                "good drink.",
        )
        chatNpc(
            laugh,
            "I have the perfect challenge for you... The Alfred Grimhand Barcrawl! First " +
                "completed by Alfred Grimhand.",
        )
        handOverCard()
        quest.setQuestStage(access, STAGE_STARTED)
        chatNpc(
            neutral,
            "Take that card to each of the bars named on it. The bartenders will know what it " +
                "means. We're kinda well known.",
        )
        chatNpc(
            neutral,
            "They'll give you their strongest drink and sign your card. When you've done all " +
                "that, we'll be happy to let you in.",
        )
    }

    private suspend fun Dialogue.progress() {
        chatNpc(neutral, "So, how's the Barcrawl coming along?")
        val carried = player.inv.contains(CARD)
        when {
            carried && barcrawl.allSigned(player) -> finish()
            carried -> {
                chatPlayer(neutral, "I haven't finished it yet.")
                chatNpc(laugh, "Well come back when you have, you lightweight.")
            }
            access.bank.contains(CARD) -> {
                chatPlayer(
                    happy,
                    "Not too bad. I've got my barcrawl card stored somewhere for safe-keeping.",
                )
                chatNpc(angry, "You need to take it with you when you're going on a barcrawl.")
            }
            else -> {
                chatPlayer(sad, "I've lost my barcrawl card...")
                chatNpc(angry, "What are you like? You're gonna have to start all over now.")
                handOverCard()
            }
        }
    }

    private suspend fun Dialogue.finish() {
        chatPlayer(drunk, "I tink I jusht 'bout done dem all... but I losht count...")
        if (access.invDel(access.inv, CARD).failure) {
            return
        }
        objbox(CARD, "You give the card to the barbarian.")
        quest.setQuestStage(access, STAGE_COMPLETE)
        chatNpc(
            happy,
            "Yep that seems fine, you can come in now. I never learned to read, but you look " +
                "like you've drunk plenty. Also, one more thing...",
        )
        chatNpc(
            neutral,
            "Since you drink like a barbarian, I can show you how to smash your vials when you " +
                "finish them. Do you want to do that?",
        )
        offerSmashing()
    }

    private suspend fun Dialogue.vialSmashing() {
        if (!smashesVials()) {
            chatNpc(
                happy,
                "'Ello friend. Do you want me to show you how to smash your vials when you " +
                    "finish drinking them?",
            )
            offerSmashing()
            return
        }
        chatNpc(
            happy,
            "'Ello friend. I see you're drinking like a barbarian - do you want to stop smashing " +
                "your vials when you finish them?",
        )
        val stop =
            choice2(
                "Yes please, I want to stop smashing my vials.",
                true,
                "No thank you, I like smashing them.",
                false,
            )
        if (!stop) {
            chatPlayer(happy, "No thank you, I like smashing them.")
            chatNpc(laugh, "That's a proper barbarian spirit, that is.")
            return
        }
        chatPlayer(neutral, "Yes please, I want to stop smashing my vials.")
        chatNpc(
            laugh,
            "You're a funny sort of barbarian! But okay, you will no longer smash your vials as " +
                "you drink your potions.",
        )
        setSmashing(false)
    }

    private suspend fun Dialogue.offerSmashing() {
        val smash =
            choice2(
                "Yes please, I want to smash my vials.",
                true,
                "No thank you, I'd rather keep my vials.",
                false,
            )
        if (!smash) {
            chatPlayer(neutral, "No thank you, I'd rather keep my vials.")
            return
        }
        chatPlayer(happy, "Yes please, I want to smash my vials.")
        chatNpc(
            happy,
            "It's all part of drinking like a barbarian! Okay, you will now smash your vials as " +
                "you drink your potions.",
        )
        setSmashing(true)
    }

    private suspend fun Dialogue.handOverCard() {
        barcrawl.clearSignatures(player)
        player.invAddOrDrop(objRepo, CARD)
        objbox(CARD, "The guard hands you a Barcrawl card.")
    }

    private fun ProtectedAccess.toggleVials() {
        if (!barcrawl.quest.isQuestCompleted(player)) {
            return
        }
        val smash = !smashesVials()
        setSmashing(smash)
        mes(if (smash) "Vial smashing is now turned on." else "Vial smashing is now turned off.")
    }

    private fun Dialogue.smashesVials(): Boolean = access.smashesVials()

    private fun Dialogue.setSmashing(smash: Boolean) = access.setSmashing(smash)

    private fun ProtectedAccess.smashesVials(): Boolean = vars[SMASH_VARBIT] == 1

    private fun ProtectedAccess.setSmashing(smash: Boolean) {
        VarPlayerIntMapSetter.set(player, SMASH_VARBIT, if (smash) 1 else 0)
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo, type: ObjectServerType) {
        val onCourseSide = coords.x > gate.coords.x
        if (onCourseSide || QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        val nearest = nearestGuard(gate)
        if (nearest == null) {
            mes("The gate is locked.")
            return
        }
        startDialogue(nearest) { guard() }
    }

    private fun nearestGuard(gate: BoundLocInfo): Npc? =
        npcRepo
            .findAll(ZoneKey.from(gate.coords), GUARD_ZONE_RADIUS)
            .filter { it.isType(GUARD) }
            .minByOrNull { it.coords.chebyshevDistance(gate.coords) }

    private companion object {
        const val GUARD = "npc.barbguard1"
        const val SMASH_VARBIT = "varbit.auto_smash_vials"
        const val GUARD_ZONE_RADIUS = 1

        val GATES = listOf("loc.barbariangatel", "loc.barbariangater")
    }
}
