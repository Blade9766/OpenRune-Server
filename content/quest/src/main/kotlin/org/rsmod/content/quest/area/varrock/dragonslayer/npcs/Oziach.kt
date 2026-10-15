package org.rsmod.content.quest.area.varrock.dragonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.ELVARGS_HEAD
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_ELVARG_SLAIN
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_OZIACH
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Oziach, the maker of rune platemail, in his hut west of Edgeville. He sets the task, takes
 * Elvarg's head at the end, and only trades with heroes who have finished the quest.
 */
class Oziach
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val shops: Shops,
) : PluginScript() {

    private val quest
        get() = dragonSlayer.quest

    override fun ScriptContext.startup() {
        onOpNpc1(OZIACH) { startDialogue(it.npc) { oziach(it.npc) } }
        onOpNpc3(OZIACH) { trade(it.npc) }
    }

    private suspend fun ProtectedAccess.trade(npc: Npc) {
        if (quest.isQuestCompleted(player)) {
            shops.open(player, npc, SHOP_TITLE, SHOP_INV)
            return
        }
        startDialogue(npc) {
            chatNpc(angry, "I'm not letting just anyone wear my rune platemail. It's only for heroes. So, leave me alone.")
        }
    }

    private suspend fun Dialogue.oziach(npc: Npc) {
        when (dragonSlayer.stage(player)) {
            0 -> greeting(canSetTask = false, npc)
            STAGE_STARTED -> greeting(canSetTask = true, npc)
            in STAGE_OZIACH until STAGE_ELVARG_SLAIN -> {
                chatNpc(quiz, "Have ye slayed that dragon yet?")
                chatPlayer(sad, "Um... no.")
                chatNpc(angry, "Be off with ye, then.")
            }
            STAGE_ELVARG_SLAIN -> dragonSlain()
            else -> afterQuest(npc)
        }
    }

    private suspend fun Dialogue.greeting(canSetTask: Boolean, npc: Npc) {
        chatNpc(happy, "Aye, 'tis a fair day, my friend.")
        when (
            choice3(
                "Can you sell me a rune platebody?", 1,
                "I'm not your friend.", 2,
                "Yes, it's a very nice day.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Can you sell me a rune platebody?")
                chatNpc(quiz, "So, how does thee know I 'ave some?")
                when (
                    choice2(
                        "The Guildmaster of the Champions' Guild told me.", 1,
                        "I am a master detective.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "The Guildmaster of the Champions' Guild told me.")
                        chatNpc(bored, "Yes, I suppose he would, wouldn't he? He's always sending you fancy-pants 'heroes' up to bother me, telling me I'll give them a quest or sommat like that.")
                    }
                    2 -> {
                        chatPlayer(happy, "I am a master detective.")
                        chatNpc(bored, "Never 'eard of yer.")
                        chatNpc(neutral, "Well, however you found out about it...")
                    }
                }
                chatNpc(angry, "I'm not going to let just anyone wear my rune platemail. It's only for heroes. So, leave me alone.")
                when (
                    choice2(
                        "I thought you were going to give me a quest.", 1,
                        "That's a pity, I'm not a hero.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "I thought you were going to give me a quest.")
                        if (canSetTask) {
                            setTask()
                        } else {
                            chatNpc(bored, "Did ye now? Then go and bother the Guildmaster of the Champions' Guild first. He's the one who sends heroes my way, not the other way round.")
                        }
                    }
                    2 -> {
                        chatPlayer(sad, "That's a pity, I'm not a hero.")
                        chatNpc(laugh, "Aye, I ken tell!")
                    }
                }
            }
            2 -> {
                chatPlayer(angry, "I'm not your friend.")
                chatNpc(angry, "I'd rather be friends with an ogre.")
            }
            3 -> {
                chatPlayer(happy, "Yes, it's a very nice day.")
                chatNpc(happy, "Aye. Mind the wind off the cliffs, though.")
            }
        }
    }

    private suspend fun Dialogue.setTask() {
        chatNpc(bored, "*Sigh*")
        chatNpc(neutral, "All right, I'll give ye a quest. I'll let ye wear my rune platemail if ye...")
        chatNpc(angry, "Slay the dragon of Crandor!")
        when (
            choice2(
                "A dragon, that sounds like fun.", 1,
                "I may be a champion, but I don't think I'm up to dragon-killing yet.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "A dragon, that sounds like fun!")
                chatNpc(laugh, "Hah, yes, you are a typical reckless adventurer, aren't you? Now go kill the dragon and get out of my face.")
                chatPlayer(quiz, "But how can I defeat the dragon?")
                chatNpc(bored, "Go talk to the Guildmaster in the Champions' Guild. He'll help ye out if yer so keen on doing a quest. I'm not going to be holding any adventurer's hand.")
                dragonSlayer.setStage(access, STAGE_OZIACH)
            }
            2 -> {
                chatPlayer(sad, "I may be a champion, but I don't think I'm up to dragon-killing yet.")
                chatNpc(bored, "Yes, I can understand that. Yer a coward.")
                // Oziach has named the task all the same; the Guildmaster fills in the rest.
                dragonSlayer.setStage(access, STAGE_OZIACH)
            }
        }
    }

    private suspend fun Dialogue.dragonSlain() {
        chatNpc(quiz, "Have ye slayed that dragon yet?")
        chatPlayer(happy, "Yes! Elvarg is dead.")
        if (player.inv.contains(ELVARGS_HEAD)) {
            chatNpc(shocked, "Ye have? Let me see that head, then.")
            access.invDel(access.inv, ELVARGS_HEAD, 1)
            objbox(ELVARGS_HEAD, "You hand Oziach the dragon's head.")
            chatNpc(happy, "Aye, that's the beast all right. I never thought I'd see the day. Ye've earned the right to wear my rune platemail, hero.")
        } else {
            chatNpc(quiz, "Slayed it, have ye? Then where's the proof?")
            chatPlayer(neutral, "I left the head behind, but I'm telling you the truth. The dragon is dead.")
            chatNpc(neutral, "Hmm. Yer not the type to lie about a thing like that, and I've heard tell the skies over Crandor are quiet at last. Very well.")
            chatNpc(happy, "Ye've earned the right to wear my rune platemail, hero.")
        }
        dragonSlayer.setStage(access, STAGE_COMPLETE)
    }

    private suspend fun Dialogue.afterQuest(npc: Npc) {
        chatNpc(happy, "Aye, 'tis a fair day, my friend. Or should I say, my hero?")
        when (
            choice2(
                "Can you sell me a rune platebody?", 1,
                "Yes, it's a very nice day.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Can you sell me a rune platebody?")
                chatNpc(happy, "For the slayer of Elvarg? Of course. Have a look at what I've got.")
                shops.open(player, npc, SHOP_TITLE, SHOP_INV)
            }
            2 -> {
                chatPlayer(happy, "Yes, it's a very nice day.")
                chatNpc(happy, "Aye, and no dragons in the sky to spoil it.")
            }
        }
    }

    private companion object {
        const val OZIACH = "npc.oziach"
        const val SHOP_TITLE = "Oziach's Armour."
        const val SHOP_INV = "inv.runeplateshop"
    }
}
