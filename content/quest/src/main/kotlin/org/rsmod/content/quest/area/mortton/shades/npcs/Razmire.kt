package org.rsmod.content.quest.area.mortton.shades.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.mortton.shades.SerumCures
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.LOAR_REMAINS
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.RAZMIRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.RAZMIRE_AFFLICTED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.RAZMIRE_PERM_BIT
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.RAZMIRE_TEMP_BIT
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_207
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_208
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SHADES_NEEDED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_ALL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_CREMATED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_GAVE_RAZMIRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_KILL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_TOLD_OF_TEMPLE
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Razmire Keelgan, the shopkeeper of Mort'ton. Afflicted he only mumbles; a dose of serum clears
 * his head long enough to talk, and once Serum 208 has cured him for a player he comes round
 * whenever that player speaks to him. His two stores stay shut until the five shades are slain.
 */
class Razmire
@Inject
constructor(
    private val shades: ShadesOfMorttonQuest,
    private val serums: SerumCures,
    private val shops: Shops,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(RAZMIRE_AFFLICTED) { talkAfflicted(it.npc) }
        onOpNpc1(RAZMIRE) { startDialogue(it.npc) { talk(it.npc, justCured = false) } }
        onOpNpc3(RAZMIRE) { openStore(it.npc, general = true) }
        onOpNpc4(RAZMIRE) { openStore(it.npc, general = false) }
        for (serum in SERUM_207 + SERUM_208) {
            onOpNpcU(RAZMIRE_AFFLICTED, serum) { useSerum(it.npc, serum) }
            onOpNpcU(RAZMIRE, serum) { useSerum(it.npc, serum) }
        }
        onOpNpcU(RAZMIRE_AFFLICTED) { mes("Razmire just stares blankly at you.") }
    }

    private suspend fun ProtectedAccess.talkAfflicted(npc: Npc) {
        if (shades.isCured(player, RAZMIRE_PERM_BIT)) {
            serums.cure(npc, RAZMIRE)
            startDialogue(npc) { talk(npc, justCured = false) }
            return
        }
        shades.setCured(player, RAZMIRE_TEMP_BIT, false)
        startDialogue(npc) { chatNpc(confused, "Mmmmm... yes, lovely... shop... Uuurgh.") }
    }

    private suspend fun ProtectedAccess.useSerum(npc: Npc, serum: String) {
        val permanent = serums.spendDose(this, serum)
        anim(SerumCures.POUR_SEQ)
        serums.cure(npc, RAZMIRE)
        shades.setCured(player, RAZMIRE_TEMP_BIT, true)
        if (permanent) {
            shades.setCured(player, RAZMIRE_PERM_BIT, true)
        }
        delay(1)
        startDialogue(npc) {
            if (permanent) {
                chatNpc(
                    happy,
                    "I feel... wonderful! Clear-headed, and I think this time it'll last! You " +
                        "have my eternal thanks, my friend.",
                )
            }
            talk(npc, justCured = true)
        }
    }

    private suspend fun Dialogue.talk(npc: Npc, justCured: Boolean) {
        val stage = shades.stage(player)
        if (justCured) {
            chatNpc(
                happy,
                "Ah, excellent, you've made your own serum and it seems to work well! Many " +
                    "thanks for using the Serum on me! But beware, I may start to change back soon!",
            )
        } else {
            chatNpc(happy, "Ah, it's you, how's it going?")
        }
        when {
            shades.isComplete(player) || stage >= STAGE_GAVE_RAZMIRE -> storeMenu(npc)
            stage == STAGE_ALL_SHADES -> collectRemains(npc)
            stage >= STAGE_KILL_SHADES -> {
                chatNpc(quiz, "Hey, have you killed the five shades yet?")
                chatPlayer(sad, "No not yet!")
                chatNpc(
                    neutral,
                    "Come back then when you have. And remember to bring me the remains, " +
                        "Ulsquire seems to think we might be able to send these poor souls to a " +
                        "better place.",
                )
            }
            else -> introduction()
        }
    }

    private suspend fun Dialogue.introduction() {
        while (true) {
            when (
                menu(
                    "Who are you?" to 1,
                    "What is this place?" to 2,
                    "What's going on around here?" to 3,
                    "What are all these shadowy creatures?" to 4,
                    "Is there anything worth doing around here?" to 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "My name is Razmire Keelgan, I run a couple of shops in Mort'ton but " +
                            "don't get much custom these days.",
                    )
                    chatPlayer(quiz, "Which stores do you run?")
                    chatNpc(
                        neutral,
                        "I run a general store with a few useful items, and I have a building " +
                            "supplies store, I'm hoping that when things get better down here, " +
                            "people will want to buy items to repair their buildings.",
                    )
                    when (
                        menu(
                            "Can I see your general store please" to 1,
                            "Can I see your build supplies store please" to 1,
                            "I have other questions for you." to 2,
                        )
                    ) {
                        1 -> {
                            chatNpc(
                                neutral,
                                "Well to be honest, all of my stock is packed away right now " +
                                    "seeing as the town is all overrun with these Shades. I tell " +
                                    "you what. You put an end to five Shades for me and I'll open " +
                                    "the store for you!",
                            )
                            chatNpc(
                                neutral,
                                "I have details on that dilapidated temple to the North. These " +
                                    "shades have a purpose - they jealously guard their burial " +
                                    "treasure! If you can do me some favours, I could help you " +
                                    "to locate it!",
                            )
                            if (offerTask()) return
                        }
                    }
                }
                2 -> {
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(
                        neutral,
                        "This place is called Mort'ton, it used to be a quiet town with a " +
                            "specialism in death. That is to say that many burial tombs were " +
                            "created here in the naturally formed caves under this very village.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What's going on around here?")
                    chatNpc(
                        neutral,
                        "Well, the people of the village, my usual customers... are all " +
                            "affected by the emanations from some place to the North East. " +
                            "Ulsquire can give you the name.",
                    )
                    chatNpc(
                        neutral,
                        "Needless to say Herbi was working on a serum to help us and you " +
                            "stumbled across the recipe! I for one am grateful to you, but " +
                            "there's a lot left to do before Mort'ton is returned to it's " +
                            "former glory.",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "What are all these shadowy creatures?")
                    chatNpc(
                        angry,
                        "Those disgusting entities are the filth that have ruined my life. " +
                            "They're the restless spirits of the dead who jealously guard their " +
                            "burial treasure yet feed on the life force of the living.",
                    )
                    chatNpc(
                        neutral,
                        "I've heard that they jealously guard their tomb treasure and that " +
                            "it's worth plundering. I need you to kill five shades and bring me " +
                            "their remains so that I can conduct some experiments. Will you do it?",
                    )
                    if (offerTask()) return
                }
                5 -> {
                    chatPlayer(quiz, "Is there anything worth doing around here?")
                    chatNpc(
                        worried,
                        "Life is a daily struggle with those Shades around here. They attack " +
                            "when you least expect it...it's terrifying! I tell you what, if you " +
                            "kill five of those Shades I'll see if I can help you out! Is it a deal?",
                    )
                    if (offerTask()) return
                }
            }
        }
    }

    /** Returns true once the conversation should end. */
    private suspend fun Dialogue.offerTask(): Boolean {
        val choice =
            menu(
                "Yes, I'll dispatch those dark and evil creatures." to 1,
                "Sorry, not right now, I need to do something else first." to 2,
                "I have another question." to 3,
                title = "Kill five Shades?",
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Yes, I'll dispatch those dark and evil creatures.")
                chatNpc(
                    happy,
                    "Great, that's what I wanted to hear. When you've done it, bring back all " +
                        "the remains, I want to inspect them.",
                )
                shades.advanceTo(access, STAGE_KILL_SHADES)
                return true
            }
            2 -> {
                chatPlayer(neutral, "Sorry, not right now, I need to do something else first.")
                chatNpc(neutral, "Ok, fair enough...")
                return true
            }
        }
        return false
    }

    private suspend fun Dialogue.collectRemains(npc: Npc) {
        chatNpc(quiz, "Hey, have you killed the five shades yet?")
        chatPlayer(happy, "Yes I have actually!")
        if (access.inv.count(LOAR_REMAINS) < SHADES_NEEDED) {
            chatNpc(
                neutral,
                "Hmm, yes, but you don't have the five shade remains I asked of you! Come back " +
                    "when you have them.",
            )
            return
        }
        access.invDel(access.inv, LOAR_REMAINS, REMAINS_TAKEN)
        objbox(LOAR_REMAINS, "Razmire takes two Shade remains.")
        shades.advanceTo(access, STAGE_GAVE_RAZMIRE)
        chatNpc(
            happy,
            "Great, I'll experiment on these! I think that Ulsquire might be keen to see some " +
                "shade remains, if you take some over to him, he can possibly give you some " +
                "extra information about them.",
        )
        chatNpc(
            happy,
            "Now that you've slayed some of those nasty shades, I feel motivated to help you " +
                "out, I can trade with you now if you like.",
        )
        storeChoices(npc)
    }

    private suspend fun Dialogue.storeMenu(npc: Npc) {
        val stage = shades.stage(player)
        when {
            shades.isComplete(player) || stage >= STAGE_CREMATED -> {
                chatPlayer(
                    quiz,
                    "Hey, I've laid a Shade to rest, can you tell me where the Shade lair is now?",
                )
                chatNpc(
                    happy,
                    "Yeah, great job on putting that Shade to rest. The entrance to the Shade " +
                        "Lair is due North of my shop. Good hunting in the lair my friend!",
                )
                storeChoices(npc)
            }
            stage in STAGE_TOLD_OF_TEMPLE until STAGE_SACRED_OIL -> templeMenu(npc)
            else -> {
                chatNpc(neutral, "Hello again...what can I do for you now?")
                storeChoices(npc)
            }
        }
    }

    private suspend fun Dialogue.storeChoices(npc: Npc) {
        while (true) {
            when (
                menu(
                    "Can I see the general store please?" to 1,
                    "Can I see the building store please?" to 2,
                    "I have another question." to 3,
                    "Ok, thanks." to 4,
                )
            ) {
                1 -> {
                    showStore(npc, general = true)
                    return
                }
                2 -> {
                    showStore(npc, general = false)
                    return
                }
                3 -> if (questions(npc)) return
                else -> {
                    chatPlayer(neutral, "Ok, thanks")
                    return
                }
            }
        }
    }

    /** Returns true once a store has been opened or the player is done. */
    private suspend fun Dialogue.questions(npc: Npc): Boolean {
        while (true) {
            when (
                menu(
                    "What do you know about the shades?" to 1,
                    "What can you tell me about that temple?" to 2,
                    "Do you know how I could make this serum permanent?" to 3,
                    "Can you open a store for me?" to 4,
                    "Ok, thanks" to 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What do you know about the shades?")
                    chatNpc(
                        neutral,
                        "Ulsqire says they're the incarnation of the dead who were interred in " +
                            "the tombs under Mort'ton. If you can find a way to put these " +
                            "spirits to rest, I'll show you a way into their lair!",
                    )
                    chatNpc(
                        happy,
                        "Mind you...there must be thousands of them buried under there! Just " +
                            "imagine all the treasure they must have buried under there!",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "What can you tell me about that temple?")
                    chatNpc(
                        neutral,
                        "It's ancient and has been desecrated since before I was born. I've " +
                            "always felt that it was quite a nice place to sit and have a " +
                            "picnic, quite a nice view out of the swamps of Mort Myre.",
                    )
                    chatNpc(
                        neutral,
                        "I told Ulsquire that if he can afford to go half's with me on the " +
                            "building materials, I'd help him to try and fix it up one day. I've " +
                            "always wondered what it looked like back in pagan times.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Do you know how I could make this serum permanent?")
                    chatNpc(
                        neutral,
                        "I'd say not my friend. That clever Flax chap was always " +
                            "experimentatin', I guess he was a scholarly type. He always " +
                            "dismissed Ulsquires learned speculations about the temple, I'd say " +
                            "that was a bit short sighted myself.",
                    )
                    chatPlayer(confused, "What's that supposed to mean?")
                    chatNpc(
                        neutral,
                        "Well, you made the serum yourself so I guess you'll experimentate with " +
                            "it. I'd say that the temple might be able to change the serum in " +
                            "some way. Well, yes, I guess it sounds superstitious, but what's to " +
                            "be lost?",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "Can you open a store for me?")
                    chatNpc(happy, "Sure, which store do you wanna see?")
                    when (
                        menu(
                            "Can I see the general store please?" to 1,
                            "Can I see the building store please?" to 2,
                            "Ok, thanks." to 3,
                        )
                    ) {
                        1 -> showStore(npc, general = true)
                        2 -> showStore(npc, general = false)
                    }
                    return true
                }
                else -> return false
            }
        }
    }

    private suspend fun Dialogue.templeMenu(npc: Npc) {
        chatPlayer(happy, "Hello there, I've started repairing the temple.")
        chatNpc(happy, "That's great, carry on the good work.")
        while (true) {
            when (
                menu(
                    "What should I do when the temple is built?" to 1,
                    "The Shades keep knocking the temple down!" to 2,
                    "I keep running out of building materials!" to 3,
                    "Can you open a store for me?" to 4,
                    "Ok, thanks." to 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What should I do when the temple is built?")
                    chatNpc(
                        confused,
                        "Well, you could always have a nice picnic... oh.. but then there's the " +
                            "problem with the Shades... hmmm, not nice... not nice at all! Well, " +
                            "I'll be honest, I don't really know! But I bet Ulsquire will know.",
                    )
                }
                2 -> {
                    chatPlayer(worried, "The Shades keep knocking the temple down!")
                    chatNpc(
                        neutral,
                        "Interesting...perhaps they have something against the temple? Perhaps " +
                            "it represents something bad to them? Hmm, interesting stuff, but " +
                            "you'd best speak to Ulsquire about that sort of thing... do you need " +
                            "any supplies?",
                    )
                }
                3 -> {
                    chatPlayer(sad, "I keep running out of building materials!")
                    chatNpc(
                        happy,
                        "Oh really... that's interesting... I have just the thing for you!",
                    )
                    showStore(npc, general = false)
                    return
                }
                4 -> {
                    chatPlayer(quiz, "Can you open a store for me?")
                    chatNpc(happy, "Sure, which store do you wanna see?")
                    when (
                        menu(
                            "Can I see the general store please?" to 1,
                            "Can I see the building store please?" to 2,
                            "Ok, thanks." to 3,
                        )
                    ) {
                        1 -> showStore(npc, general = true)
                        2 -> showStore(npc, general = false)
                    }
                    return
                }
                else -> return
            }
        }
    }

    private suspend fun Dialogue.showStore(npc: Npc, general: Boolean) {
        access.openStore(npc, general)
    }

    private suspend fun ProtectedAccess.openStore(npc: Npc, general: Boolean) {
        if (!shades.isComplete(player) && shades.stage(player) < STAGE_GAVE_RAZMIRE) {
            startDialogue(npc) {
                chatNpc(
                    neutral,
                    "Well to be honest, all of my stock is packed away right now seeing as the " +
                        "town is all overrun with these Shades. You put an end to five Shades " +
                        "for me and I'll open the store for you!",
                )
            }
            return
        }
        if (general) {
            shops.open(player, npc, GENERAL_STORE_TITLE, GENERAL_STORE)
        } else {
            shops.open(player, npc, BUILDERS_STORE_TITLE, BUILDERS_STORE)
        }
    }

    private companion object {
        const val REMAINS_TAKEN = 2
        const val GENERAL_STORE = "inv.razmiregeneralstore"
        const val BUILDERS_STORE = "inv.razmirebuildingstore"
        const val GENERAL_STORE_TITLE = "Razmire General Store."
        const val BUILDERS_STORE_TITLE = "Razmire Builders Merchants."
    }
}
