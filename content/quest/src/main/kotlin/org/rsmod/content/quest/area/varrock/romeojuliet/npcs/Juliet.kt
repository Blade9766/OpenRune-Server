package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.varrock.romeojuliet.JulietScene
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.DRAUL
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.HEART_SPOTANIM
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.JULIET
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.JULIET_MULTI
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.PHILLIPA
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_HAS_MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_JULIET_CRYPT
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_MESSAGE_DELIVERED
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_FATHER
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Juliet, kept on the upper floor of her father's mansion west of Varrock. The spawn is the
 * multinpc `npc.juliet_multi_visible`, so ops are bound to that base name (and to plain
 * `npc.juliet` for cutscene and debug spawns).
 */
class Juliet
@Inject
constructor(
    private val quest: RomeoJulietQuest,
    private val scene: JulietScene,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(JULIET_MULTI, JULIET)) {
            onOpNpc1(type) { startDialogue(it.npc) { juliet(it.npc) } }
            onOpNpcU(type) { useOn(it.npc, it.objType.internalName) }
        }
    }

    private suspend fun Dialogue.juliet(npc: Npc) {
        when (quest.stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> writeMessage()
            STAGE_HAS_MESSAGE -> awaitingReply()
            STAGE_MESSAGE_DELIVERED -> {
                chatPlayer(
                    neutral,
                    "Hi Juliet. I gave Romeo your message... he's scared out of his wits now he " +
                        "knows your father wants him dead.",
                )
                chatNpc(
                    worried,
                    "Yes, my father is quite the hunter - you'll have seen the trophies on the " +
                        "walls. It would be awful to see Romeo's head up there with them!",
                )
                chatPlayer(neutral, "I know what you mean...")
                chatPlayer(neutral, "...his hair would clash horribly with the rest of the decor.")
                chatNpc(angry, "That is not what I meant at all!")
                chatPlayer(happy, "I know, I know, I'm only joking.")
                chatPlayer(
                    neutral,
                    "Anyway, don't worry, I'm on the case. I'm off to ask Father Lawrence for help.",
                )
                chatNpc(
                    happy,
                    "Oh, yes, I'm sure Father Lawrence will think of something. I hope you find " +
                        "him soon.",
                )
            }
            STAGE_SEEN_FATHER -> {
                chatPlayer(
                    happy,
                    "Hi Juliet. Father Lawrence has come up with a cunning plan, but first I need " +
                        "to find the Apothecary!",
                )
                chatNpc(
                    happy,
                    "Wonderful! I knew Father Lawrence would think of something. I've no idea " +
                        "where the Apothecary lives, though. Please hurry - my father's temper " +
                        "isn't improving.",
                )
            }
            STAGE_SEEN_APOTHECARY -> potion(npc)
            STAGE_JULIET_CRYPT -> {
                chatNpc(quiz, "Have you seen Romeo? He's meant to be collecting me!")
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(sad, "Romeo, oh Romeo, wherefore art thou, Romeo?")
        chatNpc(
            sad,
            "Hello, adventurer. Have you come across a young man called Romeo in your travels? " +
                "Skinny, a little wishy-washy, head full of poetry?",
        )
        chatPlayer(neutral, "I can't say I have.")
        chatNpc(sad, "He's usually moping about Varrock Square. If you see him, do tell me.")
    }

    private suspend fun Dialogue.writeMessage() {
        chatPlayer(
            happy,
            "Juliet, I've come from Romeo. He begged me to tell you that he still cares for you.",
        )
        chatNpc(happy, "Oh, how my heart soars to hear it! Please take this message to him, and quickly!")
        chatPlayer(neutral, "I hope it's good news... he was in quite a state when I left him.")
        chatNpc(
            sad,
            "He's often in a state, the poor sensitive thing. I'm afraid he won't take this very " +
                "well. But all is not lost.",
        )
        chatNpc(neutral, "It's all explained in the letter. Would you deliver it to him for me?")
        chatPlayer(happy, "Of course. I'll take it straight away.")
        chatNpc(happy, "Thank you so much! You may be our only hope.")
        access.invAddOrDrop(objRepo, MESSAGE)
        quest.setStage(access, STAGE_HAS_MESSAGE)
        objbox(MESSAGE, "Juliet gives you a message.")
    }

    private suspend fun Dialogue.awaitingReply() {
        chatPlayer(happy, "Hello, Juliet!")
        chatNpc(
            quiz,
            "Hello there. Have you delivered my message to Romeo yet? What news from my love?",
        )
        if (quest.owns(access, MESSAGE)) {
            chatPlayer(sad, "Oh, sorry, I haven't had the chance to deliver it yet!")
            chatNpc(sad, "Oh, what a shame. I've waited so patiently to hear from him.")
            return
        }
        chatPlayer(neutral, "Hmm, the funny thing about messages is how easily they go missing...")
        chatNpc(
            angry,
            "How could you lose it? It was terribly important... and it took me ages to write! " +
                "I did it in joined-up writing and everything!",
        )
        chatNpc(sad, "Please take this new one to him - and please don't lose it.")
        access.invAddOrDrop(objRepo, MESSAGE)
        objbox(MESSAGE, "Juliet gives you another message.")
    }

    private suspend fun Dialogue.potion(npc: Npc) {
        if (access.inv.count(CADAVA_POTION) == 0) {
            chatPlayer(happy, "Hi Juliet!")
            npc.spotanim(HEART_SPOTANIM)
            chatNpc(
                happy,
                "Hello, ${player.displayName}! How close am I to being with my true love, Romeo?",
            )
            chatPlayer(neutral, "Sorry, I still need to get a special potion made for you.")
            npc.spotanim(HEART_SPOTANIM)
            chatNpc(
                happy,
                "I hope it's not a love potion, or you'd be wasting your time. My love for " +
                    "Romeo grows stronger by the minute...",
            )
            chatPlayer(neutral, "That'll be because you're not with him...")
            npc.spotanim(HEART_SPOTANIM)
            chatNpc(happy, "Oh no! I long to be near my true love, Romeo!")
            chatPlayer(neutral, "Well, alright then... I'll get this potion as fast as I can!")
            chatNpc(happy, "Good luck to you. The end is near.")
            return
        }
        chatPlayer(
            happy,
            "Hi Juliet! I've got an interesting proposal for you, courtesy of Father Lawrence. " +
                "It might be the only way you'll ever get out of this house and be with Romeo.",
        )
        chatNpc(quiz, "Go on...")
        chatPlayer(neutral, "I have a cadava potion here. If you drink it, you'll appear to be dead!")
        chatNpc(neutral, "Yes...")
        chatPlayer(neutral, "And once you appear dead... your cold, lifeless body will be taken to the crypt!")
        chatNpc(worried, "Oooh, a cold, dark, creepy crypt...")
        chatNpc(neutral, "...sounds just lovely.")
        chatPlayer(happy, "Then Romeo can creep into the crypt and rescue you just as you wake up!")
        chatNpc(quiz, "...and this is the grand plan for getting me out of here?")
        chatPlayer(neutral, "To be fair, I can't take the credit... it was all Father Lawrence's idea...")
        chatNpc(neutral, "Alright... if that's the best we can do, hand over the potion!")
        access.invDel(access.inv, CADAVA_POTION)
        quest.setStage(access, STAGE_JULIET_CRYPT)
        objbox(CADAVA_POTION, "You pass the suspicious potion to Juliet.")
        chatNpc(happy, "Wonderful! I only hope Romeo remembers to come and fetch me from the crypt.")
        chatNpc(
            worried,
            "Please go to Romeo and make sure he understands. I adore his gormless, lovesick " +
                "ways, but he can be a bit dim, and I don't want to wake up in that crypt alone.",
        )
        val played = with(scene) { access.play() }
        if (!played) {
            narrateDeath()
        }
    }

    /** Told in dialogue when the balcony copy cannot be made, so the scene is never lost. */
    private suspend fun Dialogue.narrateDeath() {
        mesbox("Juliet drinks the potion and falls to the floor.")
        chatNpcSpecific("Phillipa", PHILLIPA, sad, "OH NO... JULIET HAS... DIED! Oooooh... (sob)...")
        chatNpcSpecific("Draul Leptoc", DRAUL, sad, "My daughter! My poor Juliet... what has happened to you?")
        chatNpcSpecific("Phillipa", PHILLIPA, sad, "We must prepare her body to be laid in the crypt...")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(angry, "I lay in that freezing crypt for hours waiting for Romeo.")
        chatNpc(angry, "And the useless oaf never came for me!")
        chatNpc(angry, "All I got out of it was a stomach ache. I've had it with men like him.")
        chatNpc(angry, "Now go away before I call my father!")
    }

    private suspend fun ProtectedAccess.useOn(npc: Npc, obj: String) {
        startDialogue(npc) {
            if (obj == CADAVA_POTION && quest.stage(player) == STAGE_SEEN_APOTHECARY) {
                potion(npc)
                return@startDialogue
            }
            if (obj == MESSAGE) {
                chatNpc(neutral, "That's my message for Romeo. Please take it to him!")
                return@startDialogue
            }
            chatNpc(neutral, "I don't think I need that, thank you.")
        }
    }
}
