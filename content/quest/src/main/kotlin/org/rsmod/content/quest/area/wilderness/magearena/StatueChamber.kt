package org.rsmod.content.quest.area.wilderness.magearena

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_CAPE
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_DEFEATED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The chamber of the gods beneath the bank cave: the two sparkling pools that link it to the
 * bank, the three statues that grant a god cape to anyone who chants at them, and the Chamber
 * guardian who hands out the matching staff (and sells more, at 80,000 coins each).
 */
class StatueChamber
@Inject
constructor(
    private val mageArena: MageArenaQuest,
    private val shops: Shops,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val quest
        get() = mageArena.quest

    override fun ScriptContext.startup() {
        onOpLoc1(BANK_POOL) { stepInto(it.loc, MageArenaCoords.POOL_TO_CHAMBER) }
        onOpLoc1(CHAMBER_POOL) { stepInto(it.loc, MageArenaCoords.POOL_TO_BANK) }
        for (god in God.entries) {
            onOpLoc1(god.statue) { pray(it.loc, god) }
        }
        onOpNpc1(GUARDIAN) { startDialogue(it.npc) { guardian(it.npc) } }
        onOpNpc3(GUARDIAN) { openStaffShop(it.npc) }
    }

    private suspend fun ProtectedAccess.stepInto(pool: BoundLocInfo, dest: CoordGrid) {
        arriveDelay()
        faceLoc(pool)
        if (mageArena.stage(player) < STAGE_DEFEATED) {
            mes("Patterns of light dance across the water, but the pool offers you nothing. Kolodion has not yet found you worthy.")
            return
        }
        soundSynth(POOL_SOUND)
        mesbox("You step into the pool of sparkling water. You feel energy rush through your veins.")
        telejump(collision.nearestFree(dest) ?: dest)
    }

    private suspend fun ProtectedAccess.pray(statue: BoundLocInfo, god: God) {
        arriveDelay()
        faceLoc(statue)
        if (mageArena.stage(player) < STAGE_DEFEATED) {
            mes("You feel nothing but cold stone.")
            return
        }
        anim(PRAY_ANIM)
        mesbox("You kneel and chant to ${god.displayName}...")
        if (inv.isFull()) {
            mesbox(
                "You kneel and chant to ${god.displayName}... ... but there is no response. You feel " +
                    "that making space in your inventory could help.",
            )
            return
        }
        invAdd(inv, god.cape)
        mesbox(
            "You kneel and chant to ${god.displayName}... You feel a rush of energy charge through your " +
                "veins. Suddenly a cape appears in your pack.",
        )
        if (mageArena.stage(player) == STAGE_DEFEATED) {
            quest.advanceQuestStage(this)
        }
    }

    private suspend fun Dialogue.guardian(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> afterMiniquest(npc)
            mageArena.stage(player) < STAGE_DEFEATED -> {
                chatNpc(neutral, "Sssshhh... the gods are talking. I can hear their whispers.")
            }
            mageArena.stage(player) == STAGE_DEFEATED -> firstVisit()
            else -> chosen()
        }
    }

    private suspend fun Dialogue.firstVisit() {
        chatPlayer(happy, "Hello my friend, Kolodion sent me down.")
        chatNpc(shifty, "Sssshhh... the gods are talking. I can hear their whispers.")
        chatNpc(quiz, "Can you hear them adventurer, they're calling you.")
        chatPlayer(confused, "Erm... ok!")
        chatNpc(neutral, "Go chant at the statue of the god you most wish to represent in this world, you will be rewarded.")
        chatNpc(neutral, "Once you are done, come back to me. I shall supply you with a mage staff ready for battle.")
    }

    private suspend fun Dialogue.chosen() {
        chatPlayer(happy, "Hi.")
        chatNpc(quiz, "Hello adventurer, have you made your choice?")
        chatPlayer(happy, "I have.")
        val god = player.carriedCapeGod()
        if (god == null) {
            chatNpc(
                neutral,
                "Good, good, I hope you have chosen well. Have you been rewarded with a cape from your chosen god?",
            )
            chatPlayer(sad, "I'm afraid I don't have it with me.")
            chatNpc(neutral, "Once you have your cape come back to me, and I shall supply you with a mage staff ready for battle.")
            return
        }
        chatNpc(
            happy,
            "Good, good, I hope you have chosen well. I will now present you with a magic staff. This, " +
                "along with the cape awarded to you by your chosen god, are all the weapons and armour " +
                "you will need here.",
        )
        if (access.inv.isFull()) {
            chatNpc(neutral, "You will need some room in your pack for it, though.")
            return
        }
        access.invAdd(access.inv, god.staff)
        mageArena.staffGiven.set(player, true)
        objbox(god.staff, "The guardian hands you an ornate magic staff.")
        if (mageArena.stage(player) == STAGE_CAPE) {
            quest.completeQuest(access)
        }
    }

    private suspend fun Dialogue.afterMiniquest(npc: Npc) {
        chatPlayer(happy, "Hello again.")
        chatNpc(quiz, "Hello adventurer, are you looking for another staff?")
        when (
            choice3(
                "What do you have to offer?", 1,
                "No thanks.", 2,
                "Tell me what you know about the charge spell.", 3,
            )
        ) {
            1 -> access.openStaffShop(npc)
            2 -> {
                chatPlayer(neutral, "No thanks.")
                chatNpc(neutral, "Well let me know if you need one.")
            }
            3 -> {
                chatPlayer(quiz, "Tell me what you know about the charge spell.")
                chatNpc(
                    neutral,
                    "We believe the spells are gifts from the gods. The charge spell draws even more " +
                        "power from the cosmos.",
                )
                chatNpc(
                    neutral,
                    "While wearing a matching cape and staff it will add 50% more damage to that already " +
                        "caused by battle mage spells for several minutes.",
                )
                chatPlayer(happy, "Good stuff.")
            }
        }
    }

    private fun ProtectedAccess.openStaffShop(npc: Npc) {
        player.openStaffShop(npc)
    }

    private fun Player.openStaffShop(npc: Npc) {
        shops.open(this, npc, "Mage Arena Staffs.", STAFF_SHOP)
    }

    private companion object {
        const val BANK_POOL = "loc.magearena_waterportal1"
        const val CHAMBER_POOL = "loc.magearena_waterportal2"
        const val GUARDIAN = "npc.magearena_guardian"
        const val STAFF_SHOP = "inv.magearena_staffshop"
        const val POOL_SOUND = "synth.magearena_pool_plop"
        const val PRAY_ANIM = "seq.human_pray"
    }
}
