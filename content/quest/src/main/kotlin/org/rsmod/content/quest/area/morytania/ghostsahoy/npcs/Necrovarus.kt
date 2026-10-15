package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.NECROVARUS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PETITION
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_REFUSED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_RELEASED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.hasGhostspeak
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Necrovarus, in the temple of the Ectofuntus. He refuses the player's plea, burns the approved
 * petition (dropping the bone key to his robing room in his rage), and can only be made to free
 * the ghosts by someone wearing the enchanted ghostspeak amulet. The amulet holds one command:
 * frivolous orders leave it charged, the release spends it and it reverts to an ordinary
 * ghostspeak amulet.
 *
 * The dropped key is visible only to the player who angered him; anyone else picking it up later
 * gains nothing, since the temple door checks the finder's own petition.
 */
class Necrovarus
@Inject
constructor(private val ahoy: GhostsAhoyQuest, private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NECROVARUS) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(angry, "Woooo wooo WOOOOO!") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        val stage = ahoy.stage(player)
        startDialogue(npc) {
            when {
                stage == STAGE_ENCHANTED && player.worn.contains(GHOSTSPEAK_ENCHANTED) -> command(npc)
                stage >= STAGE_RELEASED -> afterwards()
                stage >= STAGE_REFUSED && carriesApprovedPetition() -> presentPetition(npc)
                stage >= STAGE_REFUSED && ahoy.isPetitionPresented(player) && needsKey() -> dropAnotherKey(npc)
                stage >= STAGE_STARTED -> plead(stage)
                else -> chatNpc(angry, "What are you doing in my temple, mortal? Get out!")
            }
        }
    }

    private suspend fun Dialogue.plead(stage: Int) {
        chatPlayer(neutral, "I've come to ask you to let the ghosts of this town pass on.")
        chatNpc(angry, "Pass on? You dare? I built the Ectofuntus to give my people eternity, and you would have me throw it away?")
        chatPlayer(neutral, "Most of them don't want eternity. They want to rest.")
        chatNpc(verymad, "Enough! Speak another word and I will burn the flesh from your bones! Leave my temple!")
        if (stage == STAGE_STARTED) {
            ahoy.advanceTo(access, STAGE_REFUSED)
        }
    }

    private fun Dialogue.carriesApprovedPetition(): Boolean =
        player.inv.contains(PETITION) && ahoy.isPetitionApproved(player)

    private fun Dialogue.needsKey(): Boolean = !ahoy.isTempleUnlocked(player) && !player.owns(BONE_KEY) && ahoy.stage(player) == STAGE_GATHER

    private suspend fun Dialogue.presentPetition(npc: Npc) {
        chatPlayer(neutral, "The townsfolk have signed a petition asking you to let them pass on.")
        if (access.invDel(access.inv, PETITION).failure) {
            return
        }
        ahoy.setPetitionPresented(player)
        chatNpc(verymad, "A petition?! My own people plot against me with scraps of paper?")
        mesbox("The petition crumbles to ash in your hand.")
        chatNpc(verymad, "This is how I answer petitions! Now get out of my sight!")
        dropKey(npc)
    }

    private suspend fun Dialogue.dropAnotherKey(npc: Npc) {
        chatPlayer(neutral, "About the townsfolk's petition...")
        chatNpc(verymad, "You again! Have I not made myself clear?")
        dropKey(npc)
    }

    private suspend fun Dialogue.dropKey(npc: Npc) {
        if (needsKey()) {
            objRepo.add(BONE_KEY, npc.coords, KEY_DURATION, receiver = player, reveal = KEY_DURATION + 1)
            mesbox("Necrovarus flings something to the floor in his fury. It is a key carved from bone.")
        }
    }

    private suspend fun Dialogue.command(npc: Npc) {
        chatNpc(angry, "You again. What do you want now?")
        val order =
            menu(
                "Let any ghost who wishes it pass on to the next world." to Order.RELEASE,
                "Tell me a joke." to Order.JOKE,
                "Do a chicken impression." to Order.CHICKEN,
                "Nothing, I'm leaving." to Order.NOTHING,
            )
        when (order) {
            Order.RELEASE -> release()
            Order.JOKE -> {
                chatPlayer(happy, "I command you to tell me a joke!")
                chatNpc(neutral, "Why did the skeleton refuse to fight?")
                chatPlayer(quiz, "I don't know. Why?")
                chatNpc(neutral, "He didn't have the guts.")
                chatNpc(angry, "I have not told a joke in three hundred years. Do not ask me again.")
                mesbox("The amulet still hums with power. That command did not use up the enchantment.")
            }
            Order.CHICKEN -> {
                chatPlayer(happy, "I command you to do a chicken impression!")
                chatNpc(neutral, "Bawk. Bawk bawk. Bwaaaaak!")
                mesbox("Necrovarus flaps his spectral arms and struts about the temple, glaring at you.")
                mesbox("The amulet still hums with power. That command did not use up the enchantment.")
            }
            Order.NOTHING -> chatPlayer(neutral, "Nothing, I'm leaving.")
        }
    }

    /** Spends the amulet's single command, swapping it back to an ordinary amulet where it is worn. */
    private suspend fun Dialogue.release() {
        chatPlayer(neutral, "I command you to let any ghost who wishes it pass on to the next world.")
        val slot = player.worn.indexOfFirst { it?.id == ENCHANTED_ID }
        if (slot < 0) {
            return
        }
        val swapped = access.invReplaceSlot(player.worn, slot, 1, plainAmulet())
        if (swapped.failure) {
            return
        }
        ahoy.advanceTo(access, STAGE_RELEASED)
        chatNpc(verymad, "I... cannot refuse. Very well. Any ghost that wishes to leave this world may now do so.")
        mesbox("The enchantment fades from your amulet. It is an ordinary ghostspeak amulet once more.")
        chatNpc(angry, "I should burn you where you stand for this, mortal. Leave, before the amulet's protection fades entirely.")
    }

    private suspend fun Dialogue.afterwards() {
        chatNpc(angry, "You have taken everything from me. Get out of my temple and never return.")
    }

    private fun plainAmulet() =
        checkNotNull(ServerCacheManager.getItem(PLAIN_ID)) { GHOSTSPEAK }

    private enum class Order {
        RELEASE,
        JOKE,
        CHICKEN,
        NOTHING,
    }

    private companion object {
        const val KEY_DURATION = 200
        val ENCHANTED_ID: Int by lazy { GHOSTSPEAK_ENCHANTED.asRSCM() }
        val PLAIN_ID: Int by lazy { GHOSTSPEAK.asRSCM() }
    }
}
