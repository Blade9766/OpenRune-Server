package org.rsmod.content.quest.area.mortton.shades.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortton.shades.SerumCures
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.LOAR_REMAINS
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.OLIVE_OIL_3
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_207
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_208
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_ALL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_CREMATED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_GAVE_RAZMIRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_GOT_OLIVE_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_KILL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_LOGS_ON_PYRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_PYRE_LOGS
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_SHOWN_ULSQUIRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_TOLD_OF_TEMPLE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ULSQUIRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ULSQUIRE_AFFLICTED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ULSQUIRE_PERM_BIT
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ULSQUIRE_TEMP_BIT
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ulsquire Shauncy, Mort'ton's teacher and priest. Cured by a dose of serum he explains the town's
 * plight, studies the shade remains, and leads the player to the temple of Flamtaer and the
 * cremation of a shade; telling him of a cremated shade finishes the quest.
 */
class Ulsquire
@Inject
constructor(private val shades: ShadesOfMorttonQuest, private val serums: SerumCures) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ULSQUIRE_AFFLICTED) { talkAfflicted(it.npc) }
        onOpNpc1(ULSQUIRE) { startDialogue(it.npc) { talk(justCured = false) } }
        for (serum in SERUM_207 + SERUM_208) {
            onOpNpcU(ULSQUIRE_AFFLICTED, serum) { useSerum(it.npc, serum) }
            onOpNpcU(ULSQUIRE, serum) { useSerum(it.npc, serum) }
        }
        onOpNpcU(ULSQUIRE, LOAR_REMAINS) { startDialogue(it.npc) { talk(justCured = false) } }
        onOpNpcU(ULSQUIRE_AFFLICTED) { mes("Ulsquire just stares blankly at you.") }
    }

    private suspend fun ProtectedAccess.talkAfflicted(npc: Npc) {
        if (shades.isCured(player, ULSQUIRE_PERM_BIT)) {
            serums.cure(npc, ULSQUIRE)
            startDialogue(npc) { talk(justCured = false) }
            return
        }
        shades.setCured(player, ULSQUIRE_TEMP_BIT, false)
        startDialogue(npc) { chatNpc(confused, "Hmmmm... the temple... the flame... Uuurrgh.") }
    }

    private suspend fun ProtectedAccess.useSerum(npc: Npc, serum: String) {
        val permanent = serums.spendDose(this, serum)
        anim(SerumCures.POUR_SEQ)
        serums.cure(npc, ULSQUIRE)
        shades.setCured(player, ULSQUIRE_TEMP_BIT, true)
        if (permanent) {
            shades.setCured(player, ULSQUIRE_PERM_BIT, true)
        }
        delay(1)
        startDialogue(npc) {
            if (permanent) {
                chatNpc(
                    happy,
                    "Praise be! The sacred flame has made the serum whole. I feel that my mind " +
                        "is my own again, for good this time!",
                )
            }
            talk(justCured = true)
        }
    }

    private suspend fun Dialogue.talk(justCured: Boolean) {
        val stage = shades.stage(player)
        if (justCured) {
            chatNpc(
                happy,
                "Ah, excellent, you've made your own serum and it seems to work well! Many " +
                    "thanks for using the Serum on me! But beware, I may start to change back soon!",
            )
        } else {
            chatNpc(happy, "Ah, hello again, what can I do for you now?")
        }
        when {
            shades.isComplete(player) -> remainsMenu()
            stage >= STAGE_CREMATED -> finish()
            stage >= STAGE_LOGS_ON_PYRE -> {
                chatPlayer(quiz, "I've placed the logs on the pyre. What do I do now?")
                chatNpc(
                    neutral,
                    "You need to place the remains of the shade on top of the logs and then set " +
                        "alight to it.",
                )
            }
            stage >= STAGE_PYRE_LOGS -> {
                chatPlayer(
                    happy,
                    "Hello there, I used the sacred oil on the logs and I made pyre logs! What " +
                        "should I do now?",
                )
                chatNpc(
                    neutral,
                    "Now you need to place the sacred logs on the pyre, then add the remains of " +
                        "a shade, then set fire to the pyre. This should release the Shade from " +
                        "it's eternal damnation and help it pass on to the other side.",
                )
            }
            stage >= STAGE_SACRED_OIL -> {
                chatPlayer(
                    happy,
                    "Hello there, I used the olive oil on the flame and it gave me sacred oil! " +
                        "What should I do now?",
                )
                chatNpc(
                    neutral,
                    "From what I've learned, the ancients used to use the sacred oil on logs in " +
                        "order to make pyre logs for cremation. You could try that!",
                )
            }
            stage >= STAGE_TOLD_OF_TEMPLE -> templeMenu()
            stage == STAGE_SHOWN_ULSQUIRE -> remainsMenu()
            stage == STAGE_GAVE_RAZMIRE -> showRemains()
            stage in STAGE_KILL_SHADES..STAGE_ALL_SHADES -> {
                chatPlayer(
                    neutral,
                    "How's it going? I talked to Razmire and he's asked me to slay five shades.",
                )
                chatNpc(
                    neutral,
                    "I can't say that I'm surprised, Razmire hates them, but it would be " +
                        "interesting to see the remains of one and investigate it somehow. I " +
                        "might find something useful!",
                )
                introduction(offerLeave = true)
            }
            else -> introduction(offerLeave = false)
        }
    }

    private suspend fun Dialogue.introduction(offerLeave: Boolean) {
        var asked = offerLeave
        while (true) {
            val options = buildList {
                add("Who are you?" to 1)
                add("What is this place?" to 2)
                add("What's going on around here?" to 3)
                add("What are all these shadowy creatures?" to 4)
                if (asked) add("Is there anyone else worth talking to around here?" to 5)
                if (offerLeave && size < 5) add("Ok, thanks" to 6)
            }
            asked = true
            when (menu(options)) {
                1 -> {
                    chatPlayer(quiz, "Who are you?")
                    chatNpc(
                        neutral,
                        "My name is Ulsquire Shauncy, I'm the local teacher and priest around " +
                            "here. You've really come at a time when Mort'ton is in a terrible " +
                            "state.",
                    )
                    chatNpc(
                        sad,
                        "The emanations from the rest of Morytania affect the people here " +
                            "terribly turning them into mindless creatures. And, as if that " +
                            "wasn't enough, we get picked off by the evil Shades that exist in " +
                            "this area.",
                    )
                    chatPlayer(
                        neutral,
                        "Wow, you guys seem to be really at the bottom of the food chain around here.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(
                        neutral,
                        "This sad and afflicted little town is called 'Mort'ton'. In it's heyday " +
                            "it was a busy and bustling market town with a speciality for " +
                            "interring the dead.",
                    )
                    chatNpc(
                        sad,
                        "The vile emanations from the Sanguinesti area started to spread and the " +
                            "town hasn't been the same since. The serum you made was the one " +
                            "that Herbi Flax was working on. Unfortunately the serum isn't permanent.",
                    )
                    chatPlayer(quiz, "Do you know how I could make the serum permanent?")
                    chatNpc(
                        neutral,
                        "I remember Herbi Flax talking about the serum. He said it worked on what " +
                            "he called the 'biological' effects of the affliction but that some " +
                            "other secondary effect was at work which he didn't quite understand.",
                    )
                    chatNpc(
                        neutral,
                        "He was interested in 'Flaemtaer', the ruined temple to the North. I " +
                            "found this odd. A man of science studying a structure dedicated to " +
                            "faith and the struggle of good verus evil, another concept he didn't " +
                            "understand.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What's going on around here?")
                    chatNpc(
                        neutral,
                        "The people are affected by the emanations from the rest of Morytania " +
                            "and the dead from the tombs have manifested into shadow creatures.",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "What are all these shadowy creatures?")
                    chatNpc(
                        neutral,
                        "They are the disturbed spirits of those interred in the earth beneath " +
                            "Mort'ton. Their eternal spirits have been poisoned by the close " +
                            "vicinity to the evil nature of the inhabitants of the Sanguinesti area.",
                    )
                }
                5 -> {
                    chatPlayer(quiz, "Is there anyone else worth talking to around here?")
                    chatNpc(
                        neutral,
                        "Well, there's my good friend Razmire Keelgan. He's a prominent shop " +
                            "owner in Mort'ton, at least he was before he was affected by the area.",
                    )
                    chatNpc(
                        neutral,
                        "I know he tried to gather the militia together to fight the shades. He " +
                            "might know something about the temple. Razmire lives in the " +
                            "building to the west just over there, go and talk to him.",
                    )
                    return
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.showRemains() {
        chatPlayer(neutral, "Razmire said to come and talk to you.")
        chatNpc(neutral, "Oh yes, well, that's very nice.")
        if (LOAR_REMAINS !in access.inv) {
            chatPlayer(
                neutral,
                "I just slayed five shades for Razmire...he said that you might be interested " +
                    "in seeing the shade remains. Unfortunately I don't have any with me at the " +
                    "moment...",
            )
            chatNpc(
                sad,
                "That is a shame, I would have liked to have seen the remains. Razmire was " +
                    "right. I'm keen to study them and perhaps see if I can work out a way to " +
                    "put their spirits to rest.",
            )
            return
        }
        chatPlayer(
            neutral,
            "I just slayed 5 shades for Razmire, he said you might be interested in seeing some " +
                "shade remains?",
        )
        chatNpc(happy, "Oh yes, that would be interesting!")
        access.invDel(access.inv, LOAR_REMAINS)
        shades.advanceTo(access, STAGE_SHOWN_ULSQUIRE)
        objbox(LOAR_REMAINS, "You show the shade remains to the priest. He wanders off to study the bones.")
    }

    private suspend fun Dialogue.remainsMenu() {
        while (true) {
            when (
                menu(
                    "What did you find out about the remains?" to 1,
                    "What can you tell me about that temple?" to 2,
                    "Do you know how I could make this serum permanent?" to 3,
                    "What should I do now?" to 4,
                    "Ok, thanks" to 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What did you find out about the remains.")
                    chatNpc(
                        neutral,
                        "The shade remains are made of a strange substance which I've not come " +
                            "across before. I believe we should put these tormented spirits to rest.",
                    )
                    chatPlayer(confused, "How can we do that? You can't even bury the remains!")
                    chatNpc(
                        neutral,
                        "Quite right...I was thinking that a holy cremation would do the trick, " +
                            "but the funeral pyres need specially prepared 'pyre logs' - normal " +
                            "logs that have been treated with 'sacred oil' to give it a nice holy " +
                            "touch.",
                    )
                    chatPlayer(quiz, "How would I make some sacred oil?")
                    chatNpc(
                        sad,
                        "You won't! It's a pagan secret lost a long time ago. Normally I would " +
                            "try to bless some commonly found vegetable oil, but I'm so weak from " +
                            "this illness that I know I just won't be able to do it.",
                    )
                    chatNpc(
                        laugh,
                        "Huh! I used to joke with Herbi Flax that if 'Flaemtaer temple' was " +
                            "still standing, we could practice some ancient pagan rituals and " +
                            "incite the sacred flame! Huh, I'm not surprised that he never took " +
                            "me seriously!",
                    )
                    shades.askedAboutRemains.set(player, true)
                    checkTold()
                }
                2 -> {
                    chatPlayer(quiz, "What can you tell me about that temple?")
                    chatNpc(
                        neutral,
                        "Hmm, interesting question. It is an ancient pagan temple referred to as " +
                            "Flaemtaer. I've studied it quite a lot, one day I hope to be able to " +
                            "rebuild it, who knows what ancient power it could unleash?",
                    )
                    chatPlayer(confused, "How can you rebuild a totally destroyed temple?")
                    chatNpc(
                        neutral,
                        "I couldn't do it alone, I'd need a few expert artisans to help with the " +
                            "construction. I'd also need a good supply of limestone bricks, swamp " +
                            "paste and timber beams. Thankfully Razmire stocks all these items. " +
                            "(Sigh)",
                    )
                    shades.askedAboutTemple.set(player, true)
                    checkTold()
                }
                3 -> {
                    chatPlayer(quiz, "Do you know how I could make this serum permanent?")
                    chatNpc(
                        neutral,
                        "The serum is a product of science. Herbi was a clever man to have made " +
                            "it, but he didn't integrate anything from the spiritual world into " +
                            "his work - and for all of his hard science there is still something " +
                            "missing.",
                    )
                    chatPlayer(neutral, "So, you don't know then!")
                    chatNpc(
                        neutral,
                        "I'm a man of faith...that's all I know. Sometimes I feel that we should " +
                            "experiment with the sacred and the holy, not just the crude matter we " +
                            "find around us everyday.",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "What should I do now?")
                    chatNpc(
                        neutral,
                        "That's up to you my friend! I once heard someone say that making your " +
                            "own decisions is the only real freedom you have. Exercise your " +
                            "freedom my friend, while you still can.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks")
                    return
                }
            }
        }
    }

    private fun Dialogue.checkTold() {
        if (shades.stage(player) != STAGE_SHOWN_ULSQUIRE) {
            return
        }
        if (shades.askedAboutRemains.get(player) && shades.askedAboutTemple.get(player)) {
            shades.advanceTo(access, STAGE_TOLD_OF_TEMPLE)
        }
    }

    private suspend fun Dialogue.templeMenu() {
        chatPlayer(happy, "Hello there, I've started repairing the temple.")
        chatNpc(
            happy,
            "That's great, carry on the good work. I'm sure that the temple is the key to " +
                "unlocking a better life for all of us in Mort'ton.",
        )
        while (true) {
            when (
                menu(
                    "What should I do when the temple is built?" to 1,
                    "The Shades keep knocking the temple down!" to 2,
                    "I keep running out of building materials!" to 3,
                    "Ok, thanks." to 4,
                )
            ) {
                1 -> whenBuilt()
                2 -> {
                    chatPlayer(worried, "The Shades keep knocking the temple down!")
                    chatNpc(
                        neutral,
                        "I'm not surprised. The temple represents holiness. They may feel that it " +
                            "has power over them in some way. My advice is to carry on and finish " +
                            "the temple if you can, and see what happens. You may need help though!",
                    )
                    chatPlayer(quiz, "How do you mean?")
                    chatNpc(
                        neutral,
                        "You may need help building the temple or defending yourself against the " +
                            "Shades. I'm sure if you ask around nicely you would be able to get " +
                            "some help.",
                    )
                    if (moreAboutShades()) return
                }
                3 -> {
                    chatPlayer(sad, "I keep running out of building materials!")
                    chatNpc(neutral, "I'd go and see Razmire, perhaps he has some items in stock?")
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whenBuilt() {
        chatPlayer(quiz, "What should I do when the temple is built?")
        chatNpc(
            neutral,
            "I've researched this much, it seems that the temple was dedicated to the worship of " +
                "elemental flame or fire. The sacred flame was said to be very holy and was used " +
                "somehow to sanctify oil for use in cremations.",
        )
        if (shades.stage(player) >= STAGE_GOT_OLIVE_OIL || shades.isComplete(player)) {
            chatNpc(
                neutral,
                "The pagans believed that sacred oil sanctified the cremation ceremony and thus " +
                    "helped the deceased pass onto the next life, and it helped the logs to burn! " +
                    "You can buy olive oil from Razmire, it's quite cheap.",
            )
            return
        }
        chatNpc(
            happy,
            "The pagans believed that sacred oil sanctified the cremation ceremony and thus " +
                "helped the deceased pass onto the next life, and it helped the logs to burn! In " +
                "case there's any truth in the story, have this!",
        )
        if (!access.invAdd(access.inv, OLIVE_OIL_3).success) {
            mesbox("You don't have enough room in your inventory for the olive oil.")
            return
        }
        objbox(OLIVE_OIL_3, "Ulsquire gives you some olive oil.")
        shades.advanceTo(access, STAGE_GOT_OLIVE_OIL)
        chatPlayer(happy, "Thanks!")
    }

    /** Returns true once the player is done talking. */
    private suspend fun Dialogue.moreAboutShades(): Boolean {
        while (true) {
            when (
                menu(
                    "Tell me more about the Shades." to 1,
                    "What should I do when the temple is built?" to 2,
                    "I keep running out of building materials!" to 3,
                    "Ok, thanks." to 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Tell me more about the Shades.")
                    chatNpc(
                        neutral,
                        "All undead hate mortal spirituality. It's a symbol of mortal hope in the " +
                            "face of death and the unknown. The temple represents the finality of " +
                            "true death where all ties to the mortal world are lost.",
                    )
                    chatNpc(
                        neutral,
                        "The Shades wish to forever possess the value of their tomb treasures and " +
                            "jealously guard them in death as they did in life. They are afraid " +
                            "of true death and so try to destroy anything which reminds them of it.",
                    )
                }
                2 -> whenBuilt()
                3 -> {
                    chatPlayer(sad, "I keep running out of building materials!")
                    chatNpc(neutral, "I'd go and see Razmire, perhaps he has some items in stock?")
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return true
                }
            }
        }
    }

    private suspend fun Dialogue.finish() {
        chatPlayer(happy, "I've put the Shade's spirit to rest!")
        chatNpc(happy, "Great! Well done my friend!")
        shades.quest.completeQuest(access)
    }
}
