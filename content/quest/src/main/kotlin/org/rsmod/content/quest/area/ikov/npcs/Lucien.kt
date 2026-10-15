package org.rsmod.content.quest.area.ikov.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.LUCIEN_HOUSE_NPC
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.LUCIEN_INN_NPC
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.PENDANT_OF_LUCIEN
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAFF_OF_ARMADYL
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_LUCIEN
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ikov.ikovSidedWithLucien
import org.rsmod.content.quest.area.ikov.wearingArmadylPendant
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lucien, who starts the quest in the Flying Horse Inn and finishes it at his house between
 * Edgeville and the Grand Exchange.
 *
 * Both of him are multi-npcs on `varbit.ikov_lucien_vis`, so the quest script alone decides which
 * one a player can see; this script only ever talks to whichever is in front of them. He is
 * killable at the house on the Armadyl path, and only the pendant the guardians gave makes the
 * blows land, so his death is handled here rather than by the ordinary death script.
 */
class Lucien
@Inject
constructor(
    private val quest: TempleOfIkovQuest,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val houseType: NpcServerType =
        ServerCacheManager.getNpc(LUCIEN_HOUSE_NPC.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $LUCIEN_HOUSE_NPC")

    override fun ScriptContext.startup() {
        onOpNpc1(LUCIEN_INN_NPC) { startDialogue(it.npc) { atTheInn() } }
        onOpNpc1(LUCIEN_HOUSE_NPC) { startDialogue(it.npc) { atTheHouse() } }
        onNpcQueue(houseType, "queue.death") { lucienDefeated() }
        // Lucien is a Mahjarrat: only the pendant the guardians gave lets a blow reach him.
        onModifyNpcHit(houseType) {
            val source = hit.sourceUid?.let { PlayerUid(it).resolve(playerList) }
            if (hit.isFromPlayer && source?.wearingArmadylPendant() != true) {
                hit.damage = 0
            }
        }
    }

    /* The Flying Horse Inn */

    private suspend fun Dialogue.atTheInn() {
        if (quest.isStarted(player)) {
            alreadySent()
            return
        }
        chatNpc(neutral, "I seek a hero to go on an important mission!")
        chatPlayer(quiz, "What sort of mission?")
        chatNpc(
            neutral,
            "There is a staff in the Temple of Ikov, south of the Ranging Guild. The Staff of " +
                "Armadyl. I want it, and I am willing to pay well for it.",
        )
        val choice =
            choice3(
                "Why can't you get it yourself?",
                1,
                "What's the reward?",
                2,
                "Oh no! Sounds far too dangerous!",
                3,
            )
        when (choice) {
            1 -> whyNotYourself()
            2 -> theReward()
            else -> {
                chatPlayer(worried, "Oh no! Sounds far too dangerous!")
                chatNpc(bored, "Then go and drink somewhere else.")
                return
            }
        }
        chatPlayer(quiz, "So will you tell me what is down there?")
        warnings()
        val accept = choice2("I'll do it.", true, "Find yourself another hero.", false)
        if (!accept) {
            chatPlayer(neutral, "Find yourself another hero.")
            chatNpc(angry, "Bah. Heroes. All mouth and no stomach.")
            return
        }
        chatPlayer(happy, "I'll do it.")
        handOverPendant()
    }

    private suspend fun Dialogue.whyNotYourself() {
        chatPlayer(quiz, "Why can't you get it yourself?")
        chatNpc(
            angry,
            "The staff is kept by an order of guardians who have no love for me. They have raised " +
                "barriers of fear across the tunnels that turn me back at the door.",
        )
        chatNpc(
            neutral,
            "You they have never seen. With a little help from me you will walk straight through.",
        )
    }

    private suspend fun Dialogue.theReward() {
        chatPlayer(quiz, "What's the reward?")
        chatNpc(
            happy,
            "Gold, and the training that comes of surviving the place. You will learn more about " +
                "a bow down there than in a year at the Ranging Guild.",
        )
    }

    private suspend fun Dialogue.warnings() {
        chatNpc(
            neutral,
            "The bridge over the lava will not hold a laden man. Go lightly, or do not go at all.",
        )
        chatNpc(
            neutral,
            "A Fire Warrior of Lesarkus guards the way north. Steel will not mark him. There are " +
                "arrows of ice in the chests along the icy path; nothing else will do.",
        )
        chatNpc(
            neutral,
            "And there is a witch by the lava called Winelda. She wants limpwurt roots. Twenty of " +
                "them, all at once, and she will throw you across.",
        )
    }

    private suspend fun Dialogue.handOverPendant() {
        chatNpc(
            happy,
            "Good. Take this pendant and wear it; it will carry you through the Chamber of Fear.",
        )
        chatNpc(
            neutral,
            "When you have the staff, bring it to my house. I have moved out by the Grand Exchange " +
                "these days. Do not keep me waiting.",
        )
        quest.advanceTo(access, STAGE_STARTED)
        access.invAdd(access.inv, PENDANT_OF_LUCIEN)
        objbox(PENDANT_OF_LUCIEN, "Lucien hands you a pendant on a worn leather cord.")
    }

    private suspend fun Dialogue.alreadySent() {
        chatNpc(bored, "You are still here? The staff will not walk out on its own.")
        offerReplacementPendant()
    }

    /* The house by the Grand Exchange */

    private suspend fun Dialogue.atTheHouse() {
        if (quest.isComplete(access.player)) {
            afterTheQuest()
            return
        }
        if (quest.stage(access.player) == STAGE_LUCIEN) {
            handInStaff()
            return
        }
        chatNpc(bored, "Yes? The staff is still in the temple, is it not?")
        offerReplacementPendant()
    }

    private suspend fun Dialogue.handInStaff() {
        chatNpc(quiz, "Do you have it?")
        val handing = choice2("Yes! Here it is.", true, "No, not yet.", false)
        if (!handing) {
            chatPlayer(neutral, "No, not yet.")
            chatNpc(angry, "Then why are you standing in my doorway?")
            return
        }
        chatPlayer(happy, "Yes! Here it is.")
        if (STAFF_OF_ARMADYL !in access.player.inv) {
            chatNpc(angry, "You are holding nothing at all. Do not waste my time.")
            return
        }
        access.invDel(access.inv, STAFF_OF_ARMADYL)
        chatNpc(
            happy,
            "At last. Do you feel it? Centuries of it, humming in the wood.",
        )
        chatNpc(
            happy,
            "They will all bow. Every one of them. And you - you shall have your reward, hero.",
        )
        chatPlayer(quiz, "You look different when you smile.")
        chatNpc(neutral, "Yes. I imagine I do. Now go.")
        access.player.ikovSidedWithLucien = true
        quest.advanceTo(access, STAGE_COMPLETE)
    }

    private suspend fun Dialogue.afterTheQuest() {
        if (access.player.ikovSidedWithLucien) {
            chatNpc(bored, "Yes?")
        } else {
            chatPlayer(shocked, "I thought I killed you?!")
            chatNpc(laugh, "Ha! Ha! Ha! You cannot kill me, human!")
        }
        offerReplacementPendant()
    }

    /** The pendant is the only way back into the temple, so Lucien always replaces a lost one. */
    private suspend fun Dialogue.offerReplacementPendant() {
        if (!quest.isStarted(access.player)) {
            return
        }
        if (access.player.inv.contains(PENDANT_OF_LUCIEN)) {
            return
        }
        if (access.player.wearingArmadylPendant()) {
            return
        }
        chatPlayer(sad, "I have lost the pendant you gave me.")
        chatNpc(bored, "Careless. Here is another. Try to keep hold of it.")
        access.invAdd(access.inv, PENDANT_OF_LUCIEN)
        objbox(PENDANT_OF_LUCIEN, "Lucien hands you another pendant.")
    }

    /* The fight at the house */

    private suspend fun StandardNpcAccess.lucienDefeated() {
        val killer = findHero(playerList)
        death.deathNoDrops(this)
        if (killer == null) {
            return
        }
        launcher.launch(killer) {
            mes("Lucien crumples, and the shape under the robe is nothing like a man.")
            if (quest.stage(player) == TempleOfIkovQuest.STAGE_ARMADYL) {
                quest.advanceTo(this, STAGE_COMPLETE)
            }
        }
    }
}
