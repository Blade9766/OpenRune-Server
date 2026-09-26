package org.rsmod.content.quest.area.lumbridge.losttribe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.CHEST_KEY
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.HAM_ROBES_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.SIGMUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.SIGMUND_HAM
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.SILVERWARE
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_ASKING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_EMOTES_LEARNT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_PERMISSION
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SHOWN_BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SILVERWARE_MISSING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TUNNEL_DUG
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_WITNESS_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.THIEVING_REQ
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Witness
import org.rsmod.content.quest.area.lumbridge.losttribe.lostTribeHam
import org.rsmod.content.quest.area.lumbridge.losttribe.lostTribeSigmundAccused
import org.rsmod.content.quest.area.lumbridge.losttribe.lostTribeWitness
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sigmund, the Duke's adviser and secret H.A.M. member. He starts The Lost Tribe beside the Duke
 * on the castle's first floor, carries the key to his chest, and after he is dismissed sulks in
 * the H.A.M. hideout. Both spawns are multinpcs on the quest stage, so the ops are bound to the
 * base names.
 */
class LumbridgeSigmund
@Inject
constructor(private val lostTribe: LostTribeQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SIGMUND) { startDialogue(it.npc) { sigmund() } }
        onOpNpc3(SIGMUND) { pickpocket(it.npc) }
        onOpNpc1(SIGMUND_HAM) {
            startDialogue(it.npc) {
                chatNpc(
                    angry,
                    "...And I would have gotten away with it too, if it wasn't for that pesky adventurer!",
                )
            }
        }
    }

    private suspend fun Dialogue.sigmund() {
        chatNpc(neutral, "Can I help you?")
        val stage = lostTribe.stage(player)
        val witness = lostTribe.witness(player).displayName
        val quest: Pair<String, Topic>? =
            when (stage) {
                0 -> "Do you have any quests for me?" to Topic.Start
                STAGE_ASKING,
                STAGE_PERMISSION,
                STAGE_SHOWN_BROOCH -> "What was I doing again?" to Topic.Reminder
                STAGE_WITNESS_FOUND -> "$witness says he saw something in the cellar." to Topic.Witness
                STAGE_TUNNEL_DUG -> "I dug through the rubble..." to Topic.Tunnel
                STAGE_READ_BOOK -> "I found out about the symbol..." to Topic.Symbol
                STAGE_EMOTES_LEARNT -> "I spoke to the generals in the Goblin Village..." to Topic.Generals
                STAGE_SILVERWARE_MISSING -> "Tell me about the silverware that was stolen." to Topic.Silverware
                else -> null
            }
        val options = buildList {
            quest?.let(::add)
            if (stage == STAGE_SILVERWARE_MISSING && player.lostTribeHam >= HAM_ROBES_FOUND) {
                add("You're a H.A.M. member, aren't you?" to Topic.Ham)
            }
            add("Who are you?" to Topic.WhoAreYou)
        }
        when (menu(options)) {
            Topic.Start -> offerQuest()
            Topic.Reminder -> reminder(stage)
            Topic.Witness -> {
                chatPlayer(neutral, "$witness says he saw something in the cellar. Like a goblin with big eyes.")
                chatNpc(angry, "A goblin? This validates my suspicion! We should tell the Duke!")
            }
            Topic.Tunnel -> {
                chatPlayer(happy, "I dug through the rubble in the cellar and found a tunnel!")
                chatNpc(angry, "See, it was goblins! You should tell the Duke.")
            }
            Topic.Symbol -> {
                chatPlayer(
                    happy,
                    "I found out about the symbol. It was the symbol of one of the ancient goblin tribes!",
                )
                chatNpc(angry, "At last, proof that goblins are behind this! You should tell the Duke at once!")
            }
            Topic.Generals -> {
                chatPlayer(
                    neutral,
                    "I spoke to the goblin generals in the Goblin Village. They told me about an ancient " +
                        "goblin tribe that went to live underground.",
                )
                chatNpc(angry, "I don't see how there can be any more doubt. You should tell the Duke at once!")
            }
            Topic.Silverware -> silverware()
            Topic.Ham -> {
                chatPlayer(angry, "You're a H.A.M member, aren't you?")
                chatNpc(shocked, "What?")
                chatNpc(angry, "Well, so what if I am? It's not a crime.")
                player.lostTribeSigmundAccused = true
            }
            Topic.WhoAreYou -> whoAreYou()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(quiz, "Do you have any quests for me?")
        if (!QuestRequirements.hasCompleted(player, LostTribeQuest.RUNE_MYSTERIES)) {
            chatNpc(
                neutral,
                "I hear the Duke has a task for an adventurer. Otherwise, if you want to make yourself " +
                    "useful, there are always evil monsters to slay.",
            )
            chatPlayer(neutral, "Okay, I might just do that.")
            return
        }
        chatNpc(
            worried,
            "There was recently some damage to the castle cellar. Part of the wall has collapsed.",
        )
        chatNpc(
            angry,
            "The Duke insists that it was an earthquake, but I think some kind of monsters are to blame.",
        )
        if (!lostTribe.canStart(player)) {
            mesbox("You need to have completed Goblin Diplomacy and Rune Mysteries to start this quest.")
            return
        }
        if (!startQuestPrompt(lostTribe.quest)) {
            chatPlayer(neutral, "Sorry, I'm not interested.")
            return
        }
        chatPlayer(quiz, "So what do you need me to do?")
        player.lostTribeWitness = access.random.of(0, Witness.entries.size - 1)
        lostTribe.advanceTo(access, STAGE_ASKING)
        chatNpc(neutral, "You should ask people around the town about it. Maybe someone saw something.")
    }

    private suspend fun Dialogue.reminder(stage: Int) {
        chatPlayer(quiz, "What was I doing again?")
        when (stage) {
            STAGE_ASKING ->
                chatNpc(
                    neutral,
                    "I asked you to talk to people around Lumbridge to see if any of them know what " +
                        "happened in the cellar.",
                )
            STAGE_PERMISSION ->
                chatNpc(
                    angry,
                    "The Duke asked you to investigate the damage in the cellar. I hope you can find the " +
                        "monsters that are behind this!",
                )
            else ->
                chatNpc(
                    angry,
                    "The Duke wants you to find out where that brooch came from. It was stolen by goblins, " +
                        "mark my words.",
                )
        }
    }

    private suspend fun Dialogue.silverware() {
        chatPlayer(quiz, "Tell me about the silverware that was stolen.")
        chatNpc(
            angry,
            "The priceless Lumbridge Silverware! It comes out only on special occasions. It was kept in " +
                "storage in the cellar, but now the goblins have stolen it.",
        )
        chatPlayer(quiz, "So why did no one notice earlier?")
        chatNpc(
            angry,
            "That doesn't matter. You'll never find it, because the goblins took it. So you can't stop the war!",
        )
        if (SILVERWARE in player.inv) {
            chatPlayer(angry, "Actually I have found it! You put it in the H.A.M. cave!")
            chatNpc(shocked, "I... I... You're lying! I don't know what you're talking about.")
        }
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(neutral, "I'm the Duke's advisor.")
        chatPlayer(quiz, "Can you give me any advice then?")
        chatNpc(
            neutral,
            "I only advise the Duke. But if you want to make yourself useful, there are evil goblins to slay " +
                "on the other side of the river.",
        )
    }

    private suspend fun ProtectedAccess.pickpocket(npc: Npc) {
        if (player.thievingLvl < THIEVING_REQ) {
            mes("You need to be at least level $THIEVING_REQ Thieving to pick Sigmund's pocket.")
            return
        }
        val wanted =
            lostTribe.stage(player) == STAGE_SILVERWARE_MISSING &&
                player.lostTribeHam < HAM_ROBES_FOUND &&
                !lostTribe.ownsItem(this, CHEST_KEY)
        arriveDelay()
        faceEntitySquare(npc)
        anim(STEAL_SEQ)
        spam("You attempt to pick Sigmund's pocket.")
        delay(2)
        if (random.of(0, 255) >= successChance()) {
            mes("You fail to pick Sigmund's pocket.")
            npc.facePlayer(player)
            npc.say("What do you think you're doing?!")
            spotanim(STUN_SPOTANIM, height = STUN_SPOTANIM_HEIGHT)
            soundSynth(STUN_SOUND)
            takeInstantHit(HitType.Typeless, random.of(1, 2))
            return
        }
        soundSynth(STEAL_SOUND)
        if (!wanted) {
            mes("You find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, CHEST_KEY)
        mes("You steal a little key.")
    }

    private fun ProtectedAccess.successChance(): Int =
        (BASE_CHANCE + (player.thievingLvl - THIEVING_REQ) * CHANCE_PER_LEVEL).coerceAtMost(MAX_CHANCE)

    private enum class Topic {
        Start,
        Reminder,
        Witness,
        Tunnel,
        Symbol,
        Generals,
        Silverware,
        Ham,
        WhoAreYou,
    }

    private companion object {
        const val STEAL_SEQ = "seq.human_pickpocket"
        const val STEAL_SOUND = "synth.pick"
        const val STUN_SPOTANIM = "spotanim.stunned_thieving"
        const val STUN_SOUND = "synth.thieving_stunned"
        const val STUN_SPOTANIM_HEIGHT = 100
        const val BASE_CHANCE = 150
        const val CHANCE_PER_LEVEL = 4
        const val MAX_CHANCE = 245
    }
}
