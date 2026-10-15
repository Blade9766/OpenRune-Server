package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.BUNKWICKET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.DENTURES
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ENCHANTED_BAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.GOLD_BAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_SKULL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MOULD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SIGIL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_ALLIANCE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_MONKEY
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.TALISMAN
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.UNSTRUNG_AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.WAYMOTTIN
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK_AMULET_EXPLAINED
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK_BAR_GIVEN
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK_GREEGREE_MADE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK_MET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ZOOKNOCK_NOT_MET
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Zooknock, the 10th squad's mage, at the far end of the tunnel under Ape Atoll, with the sappers
 * Waymottin and Bunkwicket for company. He enchants the gold bar for the M'speak amulet and turns
 * a monkey talisman and a set of monkey bones into a greegree.
 */
class Zooknock @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ZOOKNOCK) { startDialogue(it.npc) { talk() } }
        onOpNpcU(ZOOKNOCK) { useItem(it.npc, it.objType) }
        onOpNpc1(WAYMOTTIN) { startDialogue(it.npc) { waymottin() } }
        onOpNpc1(BUNKWICKET) { startDialogue(it.npc) { chatNpc(angry, "Can't you see I'm busy? There's half an island on top of us and it wants to come down.") } }
    }

    private suspend fun Dialogue.talk() {
        when {
            monkeyMadness.stage(player) < MonkeyMadnessQuest.STAGE_APE_ATOLL -> chatNpc(neutral, "Hm? How did you get down here?")
            monkeyMadness.stage(player) == STAGE_COMPLETE -> {
                chatNpc(happy, "Ah, the human who became a monkey! Sit, sit. Or don't; the ceiling drips.")
                anotherGreegree()
            }
            !monkeyMadness.metGarkor.get(player) -> chatNpc(neutral, "You should speak to the Sergeant before you speak to me. He is south of the palace, between it and the white house.")
            else -> when (monkeyMadness.zooknockStage.get(player)) {
                ZOOKNOCK_NOT_MET -> thePlan()
                ZOOKNOCK_MET -> amuletReminder()
                ZOOKNOCK_BAR_GIVEN -> {
                    chatNpc(neutral, "Take the enchanted bar somewhere holy to the monkeys and pour it into the mould there. The temple in the north-east of the town has a trapdoor, and beneath it a wall of flame that never goes out.")
                    chatNpc(neutral, "Once it is cast you will need a ball of wool to string it.")
                    if (!player.inv.contains(ENCHANTED_BAR) && !player.inv.contains(UNSTRUNG_AMULET) && !player.inv.contains(AMULET)) {
                        chatPlayer(sad, "I've lost the enchanted bar.")
                        chatNpc(neutral, "Then bring me another gold bar and the dentures and I will enchant it again. Keep hold of the mould!")
                        monkeyMadness.zooknockStage.set(player, ZOOKNOCK_MET)
                        monkeyMadness.syncVars(player)
                    }
                }
                ZOOKNOCK_AMULET_EXPLAINED -> talismanTalk()
                else -> anotherGreegree()
            }
        }
    }

    private suspend fun Dialogue.thePlan() {
        chatNpc(neutral, "So Garkor found himself a human. Good. Come closer, and mind the stalagmites.")
        chatPlayer(quiz, "Garkor says you want to turn me into a monkey.")
        chatNpc(neutral, "I do. Two spells, two objects. The first lets you speak and understand the monkey tongue; the second changes your shape. Neither will hold unless it is bound into something that already belongs to the monkeys.")
        chatNpc(neutral, "For the first I need a gold bar, a mould for a monkey amulet, and a set of magical monkey dentures. The mould and the dentures are in the crates of the warehouse in Marim, and in the caves beneath it.")
        chatNpc(neutral, "Bring me all three and I will enchant the bar. You must then cast it yourself, somewhere the monkeys hold sacred, and string it.")
        chatPlayer(quiz, "And the second spell?")
        chatNpc(neutral, "One thing at a time. Fetch what I asked for.")
        monkeyMadness.zooknockStage.set(player, ZOOKNOCK_MET)
        monkeyMadness.syncVars(player)
    }

    private suspend fun Dialogue.amuletReminder() {
        chatNpc(neutral, "A gold bar, the m'amulet mould and the monkey dentures. Hand them to me when you have all three.")
        if (player.inv.contains(GOLD_BAR) && player.inv.contains(MOULD) && player.inv.contains(DENTURES)) {
            chatPlayer(neutral, "I have them all here.")
            npc?.let { access.enchant(it) }
        }
    }

    private suspend fun ProtectedAccess.useItem(npc: Npc, obj: ItemServerType) {
        val name = obj.internalName
        if (monkeyMadness.zooknockStage.get(player) == ZOOKNOCK_NOT_MET) {
            startDialogue(npc) { chatNpc(neutral, "Let us talk before you start handing me things.") }
            return
        }
        when (name) {
            GOLD_BAR, MOULD, DENTURES -> {
                if (monkeyMadness.zooknockStage.get(player) != ZOOKNOCK_MET) {
                    startDialogue(npc) { chatNpc(neutral, "I have already enchanted a bar for you. Do not give me the mould; you will need it.") }
                    return
                }
                if (player.inv.contains(GOLD_BAR) && player.inv.contains(MOULD) && player.inv.contains(DENTURES)) {
                    startDialogue(npc) { access.enchant(npc) }
                } else {
                    startDialogue(npc) { chatNpc(neutral, "I need the gold bar, the mould and the dentures together. Bring me all three.") }
                }
            }
            TALISMAN -> startDialogue(npc) { talismanUse() }
            else -> {
                val greegree = BONES_TO_GREEGREE[name]
                if (greegree != null) {
                    startDialogue(npc) { talismanUse() }
                } else {
                    startDialogue(npc) { chatNpc(neutral, "I have no use for that.") }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.enchant(npc: Npc) {
        invDel(player.inv, GOLD_BAR)
        invDel(player.inv, DENTURES)
        anim(CAST_SEQ)
        spotanim(CAST_SPOTANIM)
        soundSynth(MonkeyMadness.SOUND_TELEPORT)
        delay(2)
        invAdd(player.inv, ENCHANTED_BAR)
        monkeyMadness.zooknockStage.set(player, ZOOKNOCK_BAR_GIVEN)
        monkeyMadness.syncVars(player)
        startDialogue(npc) {
            objbox(ENCHANTED_BAR, "Zooknock mutters over the dentures and the gold bar begins to glow.")
            chatNpc(neutral, "The magic of the dentures is in the bar now. Keep the mould; you will pour the bar into it yourself.")
            chatNpc(neutral, "It must be cast in a place the monkeys hold sacred. There is a temple in the north-east of Marim with a trapdoor, and under it a wall of flame that never goes out. That will do.")
            chatNpc(neutral, "Then string it with a ball of wool and wear it, and every monkey on this island will think you one of them when you speak.")
        }
    }

    private suspend fun Dialogue.talismanTalk() {
        chatNpc(neutral, "You have the amulet? Good. Now for the second spell: the greegree.")
        chatNpc(neutral, "I need a real monkey talisman, the kind the monkeys make for their children, and the bones of the monkey you wish to become. Karamjan monkey bones will make you an ordinary monkey, which is what we want.")
        chatNpc(neutral, "Hand me both together and I will bind them.")
        if (player.inv.contains(TALISMAN) && bonesHeld() != null) {
            chatPlayer(neutral, "I have a talisman and some bones here.")
            talismanUse()
        }
    }

    private suspend fun Dialogue.talismanUse() {
        if (monkeyMadness.zooknockStage.get(player) < ZOOKNOCK_AMULET_EXPLAINED) {
            chatNpc(neutral, "The amulet first. I cannot bind a greegree for someone who cannot even talk to the monkeys.")
            return
        }
        val bones = bonesHeld()
        if (!player.inv.contains(TALISMAN) || bones == null) {
            chatNpc(neutral, "I need a monkey talisman and a set of monkey bones together.")
            return
        }
        val greegree = BONES_TO_GREEGREE.getValue(bones)
        access.invDel(player.inv, TALISMAN)
        access.invDel(player.inv, bones)
        access.anim(CAST_SEQ)
        access.spotanim(CAST_SPOTANIM)
        access.soundSynth(MonkeyMadness.SOUND_TELEPORT)
        delay(2)
        access.invAdd(player.inv, greegree)
        objbox(greegree, "Zooknock binds the bones to the talisman. The result is a greegree.")
        if (monkeyMadness.zooknockStage.get(player) < ZOOKNOCK_GREEGREE_MADE) {
            monkeyMadness.zooknockStage.set(player, ZOOKNOCK_GREEGREE_MADE)
            monkeyMadness.advanceTo(access, STAGE_MONKEY)
            monkeyMadness.syncVars(player)
            chatNpc(neutral, "Hold it in your hand on the island and you will take the shape of the monkey whose bones went into it. Take it off and you are yourself again.")
            chatNpc(neutral, "Now go and find Garkor. Show him what you have become.")
        } else {
            chatNpc(neutral, "Another for your collection. Use it well.")
        }
    }

    private suspend fun Dialogue.anotherGreegree() {
        chatNpc(neutral, "Bring me a monkey talisman and the bones of the monkey you want to become, and I will bind you another greegree.")
        if (player.inv.contains(TALISMAN) && bonesHeld() != null) {
            talismanUse()
        }
    }

    private fun Dialogue.bonesHeld(): String? = BONES_TO_GREEGREE.keys.firstOrNull { player.inv.contains(it) }

    private suspend fun Dialogue.waymottin() {
        if (monkeyMadness.stage(player) >= STAGE_ALLIANCE && monkeyMadness.sigilGiven.get(player) && !player.inv.contains(SIGIL) && !player.worn.contains(SIGIL)) {
            chatPlayer(neutral, "Garkor says you keep the spare sigils. I've lost mine.")
            if (player.inv.freeSpace() < 1) {
                chatNpc(neutral, "Make room in your pack first.")
                return
            }
            access.invAdd(player.inv, SIGIL)
            objbox(SIGIL, "Waymottin hands you a spare 10th squad sigil.")
            chatNpc(neutral, "Try not to lose this one. They don't grow on trees, whatever the Sergeant thinks.")
            return
        }
        chatNpc(angry, "Can't you see I'm busy? Talk to Zooknock if you want something.")
    }

    companion object {
        const val CAST_SEQ = "seq.gnome_cast_teleport"
        const val CAST_SPOTANIM = "spotanim.teleport_casting"

        val BONES_TO_GREEGREE =
            mapOf(
                "obj.mm_normal_monkey_bones" to "obj.mm_monkey_greegree_for_normal_monkey",
                "obj.mm_small_ninja_monkey_bones" to "obj.mm_monkey_greegree_for_small_ninja_monkey",
                "obj.mm_medium_ninja_monkey_bones" to "obj.mm_monkey_greegree_for_medium_ninja_monkey",
                "obj.mm_normal_gorilla_monkey_bones" to "obj.mm_monkey_greegree_for_normal_gorilla",
                "obj.mm_bearded_gorilla_monkey_bones" to "obj.mm_monkey_greegree_for_bearded_gorilla",
                MONKEY_SKULL to "obj.mm_monkey_greegree_for_ancient_monkey_skull",
                "obj.mm_small_zombie_monkey_bones" to "obj.mm_monkey_greegree_for_small_zombie_monkey",
                "obj.mm_large_zombie_monkey_bones" to "obj.mm_monkey_greegree_for_large_zombie_monkey",
            )
    }
}
