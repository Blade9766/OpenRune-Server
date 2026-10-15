package org.rsmod.content.quest.area.tirannwn.mourningsend.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BELLOWS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BIG_CHOMPY
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BROKEN_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRUNCHIES
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FEATHER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_AGREED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_ASKED_AMMO
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_FREED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_SLIPPED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LEATHER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MAGIC_LOGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_DEVICE_FIXED
import org.rsmod.content.quest.area.tirannwn.mourningsend.gnomeState
import org.rsmod.content.quest.area.tirannwn.mourningsend.ownsAnywhere
import org.rsmod.content.quest.area.tirannwn.mourningsend.swap
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnome inventor the mourners keep on a rack, who alone can fix their dye device.
 *
 * His progress is the cache's `varbit.mourning_gnome`, which the rack (occupied at 0-6, empty from
 * 7) and the freed gnome (shown from 7) are multis on, so each player frees their own gnome. He
 * lets slip what he can't stand (1), gives in once tickled with a feather while toad crunchies
 * are dangled before him (2), and on release takes the leather, magic logs, broken device and one
 * lot of crunchies and hands back the fixed device in one transaction (7). Crunchies given before
 * the tickling are simply eaten. After that he explains the ammunition (8), and he will fix
 * another broken device for the same materials at any time.
 */
class HideoutGnome @Inject constructor(private val mourning: MourningsEndQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (rack in RACKS) {
            onOpLoc1(rack) { startDialogue { onRack() } }
            onOpLoc2(rack) { release() }
            onOpLocU(rack) { useOnRack(it.objType.internalName) }
        }
        for (gnome in FREED_GNOMES) {
            onOpNpc1(gnome) { startDialogue(it.npc) { freed() } }
            onOpNpcU(gnome) { useOnFreed(it.objType.internalName) }
        }
    }

    private fun admitted(player: Player): Boolean =
        mourning.stage(player) >= STAGE_ADMITTED || mourning.isComplete(player)

    private suspend fun Dialogue.gnomeSays(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Gnome on a rack", GNOME_HEAD, mesanim, text)

    private suspend fun Dialogue.onRack() {
        if (!admitted(player)) {
            gnomeSays(angry, "Go away, I've nothing to say to the likes of you.")
            return
        }
        when (player.gnomeState) {
            0 -> bluff()
            GNOME_SLIPPED -> gnomeSays(angry, "I've got nothing to say to you!")
            else -> agreed()
        }
    }

    private suspend fun Dialogue.bluff() {
        chatPlayer(neutral, "Hello. Will you help me fix this... err... thing?")
        gnomeSays(angry, "I'm not fixing that for you. It's caused enough trouble already. Your friends have tried every torture in the book and I'm still not telling anyone anything.")
        chatPlayer(quiz, "Have they tried stretching your eyelids yet?")
        gnomeSays(neutral, "Yes. It didn't work.")
        chatPlayer(quiz, "Feeding you nail and prune stew?")
        gnomeSays(sad, "That's all I've eaten since I got here.")
        chatPlayer(quiz, "Setting fire to your nostril hair? Rabid rabbits nibbling your toes? A twisted arm?")
        gnomeSays(neutral, "Yes, yes and yes. Tried them all. I quite liked the toe nibbling.")
        chatPlayer(quiz, "Pulling out your wisdom teeth?")
        gnomeSays(laugh, "Gnomes aren't wise, so we don't get them. Face it, you'll never hurt me enough to make me talk. I played gnomeball as a lad; this is a walk in the park.")
        chatPlayer(neutral, "Alright, I get the picture. So what would work?")
        gnomeSays(laugh, "Ha! As if I'd be daft enough to tell you I've been craving toad crunchies, or that I can't stand having my feet tickled!")
        chatPlayer(confused, "Err... but you just told me?!?")
        gnomeSays(confused, "I did? What did I say?")
        val right =
            menu(
                "You said about going to the park and playing gnomeball." to false,
                "You said about toad crunchies and a gnome ball." to false,
                "You said about toad crunchies and being tickled." to true,
                "You said about being tickled and a gnome ball." to false,
                "You said about being tickled and going to the park." to false,
            )
        if (!right) {
            gnomeSays(laugh, "Did I now? You mourners are idiots. Leave me be.")
            return
        }
        chatPlayer(neutral, "You said about toad crunchies and being tickled.")
        MourningsEndQuest.setVarBit(player, "varbit.mourning_gnome", GNOME_SLIPPED)
        gnomeSays(worried, "Oops... I mean, erm... no, that must have been some other... err... gnome.")
        chatPlayer(quiz, "So will you help me if I bring you toad crunchies?")
        gnomeSays(angry, "You can't just buy my co-operation.")
        chatPlayer(neutral, "You'll tell me, sooner or later.")
    }

    private suspend fun Dialogue.agreed() {
        gnomeSays(quiz, "You got everything?")
        val leather = player.inv.contains(LEATHER)
        val logs = player.inv.contains(MAGIC_LOGS)
        when {
            !leather -> chatPlayer(neutral, "I still need some soft leather.")
            !logs -> chatPlayer(neutral, "I still need some magic logs.")
            else -> {
                chatPlayer(neutral, "I have it all here.")
                gnomeSays(neutral, "Then let me up off this rack and I'll get started. I can't do a thing while I'm tied up.")
                return
            }
        }
        gnomeSays(worried, "Please, no more tickling. I'll fix it as soon as you have everything.")
    }

    private suspend fun ProtectedAccess.useOnRack(obj: String) {
        if (!admitted(player)) {
            mes("Nothing interesting happens.")
            return
        }
        when {
            obj == FEATHER -> tickle()
            obj in CRUNCHIES -> eatCrunchies(obj)
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.tickle() {
        val state = player.gnomeState
        if (state == 0) {
            mes("You can't see why tickling him would help.")
            return
        }
        if (state >= GNOME_AGREED) {
            startDialogue {
                gnomeSays(angry, "Oi! There's no need for that, I've already agreed to help.")
                chatPlayer(laugh, "That's all well and good, but I'm enjoying it.")
            }
            anim(TICKLE_SEQ)
            startDialogue {
                mesbox("You tickle the gnome's feet...")
                gnomeSays(laugh, "Ho ho ho... no more... tee hee... I hate you!")
            }
            return
        }
        val crunchies = CRUNCHIES.firstOrNull { inv.contains(it) }
        if (crunchies == null) {
            startDialogue {
                chatPlayer(quiz, "Will you help me now, or would you like some more tickling?")
                gnomeSays(angry, "What do I get out of helping you? That device is the only thing keeping me out of the slave pens.")
            }
            return
        }
        anim(TICKLE_SEQ)
        startDialogue {
            mesbox("You dangle the toad crunchies above the gnome's nose, just out of reach, and hold the feather menacingly close to his feet.")
            chatPlayer(shifty, "Now will you help me, or shall I tickle your feet?")
            MourningsEndQuest.setVarBit(player, "varbit.mourning_gnome", GNOME_AGREED)
            gnomeSays(sad, "Alright... alright! I'm beaten! Bring me some soft leather and some magic logs and I'll see what I can do.")
            if (player.inv.contains(LEATHER) && player.inv.contains(MAGIC_LOGS)) {
                chatPlayer(happy, "I have all of that here.")
                gnomeSays(neutral, "Then let me up off this rack and I'll get started. I can't do a thing while I'm tied up.")
            } else {
                chatPlayer(neutral, "Very well, but you don't get the crunchies until I'm back.")
                gnomeSays(angry, "You beastly little blighter!")
            }
        }
    }

    private suspend fun ProtectedAccess.eatCrunchies(obj: String) {
        if (invDel(inv, obj, 1).failure) {
            return
        }
        startDialogue {
            mesbox("You dangle the toad crunchies above the gnome's nose.")
            mesbox("The gnome cranes his neck and gulps them down before you can pull them away.")
            gnomeSays(happy, "I told you, you can't buy my co-operation... but thanks for the snack.")
        }
    }

    private suspend fun ProtectedAccess.release() {
        arriveDelay()
        if (!admitted(player) || player.gnomeState < GNOME_AGREED) {
            startDialogue { gnomeSays(angry, "Let me go? You'd only strap me back on again.") }
            return
        }
        val crunchies = CRUNCHIES.firstOrNull { inv.contains(it) }
        val missing =
            listOfNotNull(
                "some soft leather".takeIf { !inv.contains(LEATHER) },
                "some magic logs".takeIf { !inv.contains(MAGIC_LOGS) },
                "the broken device".takeIf { !inv.contains(BROKEN_DEVICE) },
                "some toad crunchies".takeIf { crunchies == null },
            )
        if (missing.isNotEmpty()) {
            startDialogue { gnomeSays(neutral, "No use letting me up until you've got ${missing.joinToString(" and ")} for me.") }
            return
        }
        startDialogue {
            chatNpcSpecific("Mourner", GNOME_GUARD, angry, "Hey! You're meant to be getting information out of him, not making friends.")
            chatPlayer(neutral, "I need to let him up if he's going to fix this device.")
            chatNpcSpecific("Mourner", GNOME_GUARD, neutral, "Fine. But without the proper paperwork he stays in this room.")
        }
        fix(checkNotNull(crunchies))
    }

    private suspend fun ProtectedAccess.fix(crunchies: String) {
        val take = listOf(LEATHER to 1, MAGIC_LOGS to 1, BROKEN_DEVICE to 1, crunchies to 1)
        if (!swap(take, listOf(FIXED_DEVICE to 1))) {
            return
        }
        if (player.gnomeState < GNOME_FREED) {
            MourningsEndQuest.setVarBit(player, "varbit.mourning_gnome", GNOME_FREED)
        }
        mourning.advanceTo(this, STAGE_DEVICE_FIXED)
        startDialogue {
            mesbox("You release the gnome and hand him the magic logs, soft leather and the strange device along with some toad crunchies.")
            gnomeSays(happy, "Right, here you go. Now leave me to eat my crunchies in peace.")
            objbox(FIXED_DEVICE, "The gnome gives you a fixed device.")
        }
    }

    private suspend fun Dialogue.freed() {
        val crunchies = CRUNCHIES.firstOrNull { player.inv.contains(it) }
        val canRefix = player.inv.contains(BROKEN_DEVICE) && player.inv.contains(LEATHER) &&
            player.inv.contains(MAGIC_LOGS) && crunchies != null
        if (canRefix) {
            chatPlayer(neutral, "Could you fix another of these devices for me?")
            chatNpc(sad, "Again? Hand it over, along with the leather, the logs and my crunchies.")
            access.fix(checkNotNull(crunchies))
            return
        }
        chatNpc(quiz, "What are you after now?")
        if (player.gnomeState == GNOME_FREED) {
            ammunition()
            MourningsEndQuest.setVarBit(player, "varbit.mourning_gnome", GNOME_ASKED_AMMO)
            return
        }
        chatPlayer(quiz, "Can you remind me how to load this thing?")
        chatNpc(angry, "How forgetful are you? Fill some bellows with dye, take them to the Feldip Hills, find a toad and use the bellows on it.")
        chompyAside()
    }

    private suspend fun Dialogue.ammunition() {
        chatPlayer(quiz, "Where do I get dye parcels for this thing?")
        chatNpc(laugh, "You mourners really aren't very good at this, are you? Still, you brought crunchies. The parcels are toads.")
        chatPlayer(confused, "Toads?")
        chatNpc(neutral, "Get some bellows and fill them with dye, then use the bellows on a toad to fill it up.")
        chatPlayer(sad, "Poor toads.")
        chatNpc(neutral, "The dye doesn't hurt them. Being fired out of the device is what does them in.")
        chatPlayer(shocked, "Ewww... That's nasty.")
        chatNpc(angry, "Oh, and torturing gnomes is perfectly fine, is it? Hypocrite.")
        chatPlayer(neutral, "Fair point. Where would I find toads?")
        chatNpc(neutral, "There are loads of them in the Feldip Hills, especially near the ponds.")
        chompyAside()
    }

    private suspend fun Dialogue.chompyAside() {
        if (!QuestRequirements.hasCompleted(player, BIG_CHOMPY)) {
            chatNpc(neutral, "Now leave me in peace.")
            return
        }
        val where = if (player.inv.contains(BELLOWS)) "right here" else if (access.ownsAnywhere(BELLOWS)) "in my bank" else null
        if (where != null) {
            chatPlayer(happy, "I've used ogre bellows to catch chompy birds before. I've got some $where.")
            chatNpc(confused, "Catching chompy birds? You mourners are weird. Now leave me in peace.")
            return
        }
        chatNpc(neutral, "Now leave me in peace.")
    }

    private suspend fun ProtectedAccess.useOnFreed(obj: String) {
        if (obj in CRUNCHIES) {
            if (invDel(inv, obj, 1).failure) {
                return
            }
            startDialogue { chatNpcSpecific("Gnome", GNOME_HEAD, happy, "Thanks... Toads always hit the spot!") }
            return
        }
        startDialogue { chatNpcSpecific("Gnome", GNOME_HEAD, neutral, "I've no need for that, keep it.") }
    }

    companion object {
        const val GNOME_HEAD = "npc.mourner_hideout_gnome_head"
        const val GNOME_GUARD = "npc.mourner_hideout_gnome_guard_vis"
        val RACKS = listOf("loc.mourning_gnome_rack_occupied", "loc.mourning_gnome_rack")
        val FREED_GNOMES = listOf("npc.mourner_hideout_gnome", GNOME_HEAD)
        const val TICKLE_SEQ = "seq.mourning_player_tickle"
    }
}
