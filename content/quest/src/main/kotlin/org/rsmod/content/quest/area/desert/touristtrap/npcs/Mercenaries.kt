package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CAPTAIN
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.COINS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.MERCENARIES
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.METAL_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_APPROACHED_CAPTAIN
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ENTERED_CAMP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_KILLED_CAPTAIN
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.touristtrap.ttAlZabaDebunked
import org.rsmod.content.quest.area.desert.touristtrap.ttAskedAlZaba
import org.rsmod.content.quest.area.desert.touristtrap.ttBet
import org.rsmod.content.quest.area.desert.touristtrap.ttCaptainDuel
import org.rsmod.content.quest.area.desert.touristtrap.ttEvictions
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Mercenary Captain, who keeps the key to the Desert Mining Camp gate on his belt, and has
 * his men do all his fighting until he is shamed into a duel.
 */
class MercenaryCaptain
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CAPTAIN) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc3(CAPTAIN) {
            mesbox(
                "You watch the Mercenary Captain for some time. He has a large metal key " +
                    "attached to his belt. You notice that he usually gets his men to do his " +
                    "dirty work.",
            )
        }
        onPlayerQueueWithArgs<Npc>(ATTACK_WARNING_QUEUE) { attackWarning(it.args) }
    }

    private suspend fun Dialogue.talk(captain: Npc) {
        val stage = quest.stage(player)
        val wantsKey = stage >= STAGE_KILLED_CAPTAIN && METAL_KEY !in player.inv
        if (stage == 0 || (stage >= STAGE_KILLED_CAPTAIN && !wantsKey) || quest.isComplete(player)) {
            chatNpc(angry, "Move along now... we've had enough of your sort!")
            return
        }
        mesbox("You approach the Mercenary Captain.")
        quest.advanceFrom(access, STAGE_STARTED, STAGE_APPROACHED_CAPTAIN)
        val informed = player.ttAlZabaDebunked
        val option =
            if (informed) {
                choice4(
                    "Wow! A real captain!",
                    1,
                    "You there!",
                    2,
                    "Hey ugly!",
                    3,
                    "I've some information for you about Al Zaba Bhasim",
                    4,
                )
            } else {
                choice3("Wow! A real captain!", 1, "You there!", 2, "Hey ugly!", 3)
            }
        when (option) {
            1 -> realCaptain(captain)
            2 -> youThere(captain)
            3 -> heyUgly(captain)
            else -> alZabaIsAFake(captain)
        }
    }

    private suspend fun Dialogue.realCaptain(captain: Npc) {
        chatPlayer(happy, "Wow! A real captain!")
        chatNpc(neutral, "Be off effendi, you are not wanted around here.")
        val rude =
            choice2(
                "That's rude, I ought to teach you some manners.",
                true,
                "I'd love to work for a tough guy like you!",
                false,
            )
        if (!rude) {
            toughGuy(captain)
            return
        }
        chatPlayer(angry, "That's rude, I ought to teach you some manners.")
        chatNpc(
            neutral,
            "Oh yes! How might you do that? You seem little more than a gutter dweller. How " +
                "could you teach me manners?",
        )
        val fist =
            choice2(
                "With my right fist and a good deal of force.",
                true,
                "Err, sorry. I thought I was talking to someone else.",
                false,
            )
        if (fist) {
            chatPlayer(angry, "With my right fist and a good deal of force.")
            chatNpc(
                happy,
                "Oh yes, ready your weapon then! I'm sure you won't mind if my men join in? Har, " +
                    "har, har! Guards kill this gutter dwelling slime.",
            )
        } else {
            chatPlayer(worried, "Err, sorry. I thought I was talking to someone else.")
            chatNpc(
                neutral,
                "Well, Effendi, you do need to be careful of what you say to people. Or they may " +
                    "take it the wrong way. Thankfully, I'm very understanding. I'll just let my " +
                    "guards deal with you.",
            )
            chatNpc(neutral, "Guards, teach this desert weed some manners.")
        }
        guardsDealWithIt()
    }

    private suspend fun Dialogue.toughGuy(captain: Npc) {
        chatPlayer(happy, "I'd love to work for a tough guy like you!")
        chatNpc(neutral, "Hmmm, oh yes, what can you do?")
        val mine = choice2("I can mine!", true, "Can't I do something for a strong Captain like you?", false)
        if (mine) {
            chatPlayer(happy, "I can mine!")
            chatNpc(
                laugh,
                "Ha, ha, ha! You come to a mining camp and offer us your mining skills! Thanks " +
                    "effendi, but we have all the miners we'll ever need.",
            )
            chatNpc(angry, "Now be off with you, before we reduce you to a bloody mess on the sand.")
            val strong =
                choice2(
                    "Can't I do something for a strong Captain like you?",
                    true,
                    "You don't scare me!",
                    false,
                )
            if (!strong) {
                youDontScareMe()
                return
            }
        }
        strongCaptain(captain)
    }

    private suspend fun Dialogue.strongCaptain(captain: Npc) {
        chatPlayer(happy, "Can't I do something for a strong Captain like you?")
        mesbox("The Captain ponders a moment and then looks at you critically.")
        chatNpc(neutral, "You could bring me the head of Al Zaba Bhasim.")
        chatNpc(
            neutral,
            "He is the leader of the notorious desert bandits, they plague us daily. You should " +
                "find them west of here. You should have no problem in finishing them all off. " +
                "Do this for me and maybe I will consider helping you.",
        )
        player.ttAskedAlZaba = true
        val accept = choice2("Consider it done.", true, "Sorry Sir, I don't think I can do that.", false)
        if (accept) {
            chatPlayer(happy, "Consider it done.")
            chatNpc(
                angry,
                "Good... run along then. You stand around flapping your tongue chatting like an " +
                    "insane camel.",
            )
            return
        }
        chatPlayer(sad, "Sorry Sir, I don't think I can do that.")
        chatNpc(
            neutral,
            "Hmm, well yes. I did consider you might not be right for the job. Be off with you " +
                "then before I turn my men loose on you.",
        )
        val goad =
            choice2(
                "It's a funny captain who can't fight his own battles!",
                true,
                "Okay, I'll be moving along then.",
                false,
            )
        if (!goad) {
            chatPlayer(neutral, "Okay, I'll be moving along then.")
            chatNpc(
                happy,
                "Effendi, I think you'll find that is the wisest decision you have made today.",
            )
            return
        }
        chatPlayer(quiz, "It's a funny captain who can't fight his own battles!")
        mesbox(
            "The men around you fall silent and the Captain silently fumes. All eyes turn to the " +
                "Captain...",
        )
        chatNpc(angry, "Very well, if you're challenging me, let's get on with it!")
        startDuel(captain)
    }

    private suspend fun Dialogue.youThere(captain: Npc) {
        chatPlayer(angry, "You there!")
        chatNpc(
            angry,
            "How dare you talk to me like that! Explain your business quickly... or my guards " +
                "will slay you where you stand.",
        )
        mesbox("Some guards close in around you.")
        val lost = choice2("I'm lost, can you help me?", true, "What are you guarding?", false)
        if (lost) {
            chatPlayer(worried, "I'm lost, can you help me?")
            mesbox("The captain smiles broadly and with a sickening voice says...")
            chatNpc(
                happy,
                "We are not a charity effendi. Be off with you before I have your head removed " +
                    "from your body.",
            )
            val guarding = choice2("What are you guarding?", true, "You don't scare me!", false)
            if (!guarding) {
                youDontScareMe()
                return
            }
        }
        whatAreYouGuarding()
    }

    private suspend fun Dialogue.whatAreYouGuarding() {
        chatPlayer(quiz, "What are you guarding?")
        chatNpc(happy, "Effendi...")
        chatNpc(happy, "For just one second, imagine that it's none of your business!")
        chatNpc(angry, "Also imagine having your limbs pulled from your body one at a time.")
        chatNpc(quiz, "Now, what was the question again?")
        val sand =
            choice2(
                "Do you have sand in your ears? I said. 'What are you guarding?'",
                true,
                "You don't scare me!",
                false,
            )
        if (!sand) {
            youDontScareMe()
            return
        }
        chatPlayer(angry, "Do you have sand in your ears? I said. 'What are you guarding?'")
        chatNpc(
            verymad,
            "Why... you ignorant, rude and eternally damned infidel, <col=000080>-- The Captain " +
                "is very angry at what you just said. --</col> Guards, kill this infidel!",
        )
        guardsDealWithIt()
    }

    private suspend fun Dialogue.youDontScareMe() {
        chatPlayer(angry, "You don't scare me!")
        chatNpc(neutral, "Well, perhaps I can try a little harder. Guards, kill this infidel.")
        guardsDealWithIt()
    }

    private suspend fun Dialogue.heyUgly(captain: Npc) {
        chatPlayer(laugh, "Hey ugly!")
        val pronoun = if (access.isBodyTypeB()) "her" else "him"
        chatNpc(verymad, "I will not tolerate such insults.. Guards, kill $pronoun.")
        mesbox("The captain marches away in disgust leaving his guards to tackle you.")
        captain.resetMode()
        guardsDealWithIt()
    }

    private suspend fun Dialogue.alZabaIsAFake(captain: Npc) {
        chatPlayer(neutral, "I've some information for you about Al Zaba Bhasim")
        chatNpc(quiz, "Oh yes and what might that be?")
        chatPlayer(
            happy,
            "Al Zaba Bhasim is a figment of your diseased imagination. Go look for him yourself " +
                "and waste your own time if you think he still exists.",
        )
        chatNpc(
            verymad,
            "Why, I've never been so insulted in all my days. Prepare to defend yourself wretch, " +
                "I'll run you through myself!",
        )
        startDuel(captain)
    }

    private suspend fun Dialogue.startDuel(captain: Npc) {
        mesbox("The guards gather around to watch the fight.")
        access.ifClose()
        player.ttCaptainDuel = true
        captain.opPlayer2(player, aiInteractions)
    }

    /**
     * The captain's men never actually kill anyone who insults him: the first time they put on a
     * show and whisper a warning, then they get rougher, and the fourth time the player is carted
     * off into the desert. The count carries on from there, one trip into the desert each time.
     */
    private suspend fun Dialogue.guardsDealWithIt() {
        val guard = with(security) { mercenaryNear(access.coords, GUARD_REACH) }
        val times = player.ttEvictions
        player.ttEvictions = (times + 1).coerceAtMost(MAX_EVICTIONS)
        when (times) {
            0 -> {
                chatNpcSpecific("Guard", MERCENARY_HEAD, angry, "Prepare to die effendi!")
                mesbox("A guard approaches you and pretends to start hitting you.")
                guard?.anim(PUNCH_SEQ)
                chatNpcSpecific("Mercenary", MERCENARY_HEAD, angry, "Take that you infidel!")
                mesbox("The guard leans closer to you and says in a low voice.")
                chatNpcSpecific(
                    "Mercenary",
                    MERCENARY_HEAD,
                    neutral,
                    "We're sick of having to kill every lunatic that comes along and insults the " +
                        "captain, it makes such a mess. Thankfully, he's a bit decrepit so he " +
                        "doesn't notice so please, buzz off and don't come here again.",
                )
            }
            1 -> {
                mesbox("The guard approaches you again and gives you a sharp kick.")
                access.ifClose()
                guard?.anim(KICK_SEQ)
                access.queueHit(player, delay = 0, type = HitType.Typeless, damage = 1)
                player.say("Ow!")
            }
            2 -> {
                chatNpcSpecific("Mercenary", MERCENARY_HEAD, angry, "Prepare to die effendi!")
                mesbox("The guard leans close and whispers")
                chatNpcSpecific(
                    "Mercenary",
                    MERCENARY_HEAD,
                    angry,
                    "Are you mad effendi? This is your last chance! Leave now and never come back " +
                        "or I'll introduce you to my friend.",
                )
                objbox(SCIMITAR, "The guard half draws his fearsome looking scimitar.")
                chatNpcSpecific(
                    "Mercenary",
                    MERCENARY_HEAD,
                    happy,
                    "And we'll be pleased to clean the mess up after you've been dispatched.",
                )
            }
            else -> {
                access.ifClose()
                with(security) { access.cartedOff(guard) }
            }
        }
    }

    private suspend fun ProtectedAccess.attackWarning(captain: Npc) {
        startDialogue(captain) {
            mesbox("This mercenary Captain looks very fierce. Are you sure you want to attack him?")
            val attack =
                choice2(
                    "Yes, I can take him on!",
                    true,
                    "Er, no thanks, I've had second thoughts about it.",
                    false,
                )
            if (!attack) {
                mesbox("You decide not to fight the Mercenary Captain.")
                return@startDialogue
            }
            access.ifClose()
            captain.say(CAPTAIN_ORDERS.random())
            val guard = security.mercenaryNear(access.coords, GUARD_REACH)
            with(security) {
                access.roughUp(guard, hits = 4)
                access.dumpInDesert(guard)
            }
        }
    }

    companion object {
        const val ATTACK_WARNING_QUEUE = "queue.touristtrap_attack_warning"

        const val MERCENARY_HEAD = "npc.tourtrap_qip_desert_mining_merc_2"
        const val SCIMITAR = "obj.bronze_scimitar"
        const val PUNCH_SEQ = "seq.human_unarmedpunch"
        const val KICK_SEQ = "seq.human_unarmedkick"

        const val GUARD_REACH = 7
        const val MAX_EVICTIONS = 3

        val CAPTAIN_ORDERS =
            listOf(
                "Destroy this low life desert rat guards!",
                "Guards! Attack that Desert Jackal!",
                "Kill that intruder guards!",
                "Men! Kill this filthy vermin.",
                "Kill this madman guards!",
            )
    }
}

/**
 * The mercenaries at the camp gate: bribable for gossip, willing to take a bet on the captain's
 * duel, and far too many to fight.
 */
class Mercenaries
@Inject
constructor(private val quest: TouristTrapQuest, private val security: MiningCampSecurity) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (mercenary in MERCENARIES) {
            onOpNpc1(mercenary) { startDialogue(it.npc) { talk(it.npc) } }
            onApNpc2(mercenary) { apRange(-1) }
            onOpNpc2(mercenary) { attackWarning(it.npc) }
        }
    }

    private suspend fun Dialogue.talk(mercenary: Npc) {
        val stage = quest.stage(player)
        if (stage >= STAGE_ENTERED_CAMP) {
            chatNpc(angry, "Move along now, we've had enough of your sort!")
            return
        }
        if (stage == STAGE_KILLED_CAPTAIN) {
            collectBet()
            return
        }
        chatNpc(angry, "Yeah, what do you want?")
        val started = stage >= STAGE_STARTED
        val option =
            if (started) {
                choice3(
                    "What is this place?",
                    1,
                    "What are you guarding?",
                    2,
                    "I'm looking for a woman called Ana, have you seen her?",
                    3,
                )
            } else {
                choice2("What is this place?", 1, "What are you guarding?", 2)
            }
        when (option) {
            1 -> {
                chatPlayer(quiz, "What is this place?")
                chatNpc(angry, "It's none of your business now get lost.")
                if (choice2("Perhaps five gold coins will make it my business?", true, "Okay, thanks.", false)) {
                    bribe(mercenary, "Perhaps five gold coins will make it my business?", withAna = false)
                } else {
                    okayThanks()
                }
            }
            2 -> {
                chatPlayer(quiz, "What are you guarding?")
                chatNpc(angry, "Get lost before I chop off your head!")
                if (choice2("Okay, thanks.", false, "Perhaps these five gold coins will sweeten your mood?", true)) {
                    bribe(mercenary, "Perhaps five gold coins will sweeten your mood?", withAna = started)
                } else {
                    okayThanks()
                }
            }
            else -> {
                chatPlayer(quiz, "I'm looking for a woman called Ana, have you seen her?")
                chatNpc(angry, "No, now get lost!")
                if (choice2("Perhaps five gold coins will help you remember?", true, "Okay, thanks.", false)) {
                    bribe(mercenary, "Perhaps five gold coins will help you remember?", withAna = true)
                } else {
                    okayThanks()
                }
            }
        }
    }

    private suspend fun Dialogue.bribe(mercenary: Npc, offer: String, withAna: Boolean) {
        chatPlayer(shifty, offer)
        if (access.inv.count(COINS) < BRIBE) {
            chatNpc(
                angry,
                "Don't try to fool me, you don't have five gold coins! Before you try to bribe " +
                    "someone, make sure you have the money effendi!",
            )
            with(security) { access.dumpInDesert(mercenary) }
            return
        }
        access.invDel(access.inv, COINS, BRIBE)
        chatNpc(happy, "Well, it certainly will help!")
        objbox(COINS, "-- The guard takes the five gold coins. --")
        chatNpc(happy, "Now then, what did you want to know?")
        val option =
            if (withAna) {
                choice3(
                    "What is this place?",
                    1,
                    "What are you guarding?",
                    2,
                    "I'm looking for a woman called Ana, have you seen her?",
                    3,
                )
            } else {
                choice2("What is this place?", 1, "What are you guarding?", 2)
            }
        when (option) {
            1 -> whatIsThisPlace()
            2 -> whatAreYouGuarding()
            else -> haveYouSeenAna()
        }
    }

    private suspend fun Dialogue.haveYouSeenAna() {
        chatPlayer(quiz, "I'm looking for a woman called Ana, have you seen her?")
        chatNpc(
            quiz,
            "Hmm, well, we get a lot of people in here. But not many women though... Saw one " +
                "come in last week....",
        )
        chatNpc(quiz, "But I don't know if it's the woman you're looking for?")
        if (choice2("What is this place?", true, "What are you guarding?", false)) {
            whatIsThisPlace()
        } else {
            whatAreYouGuarding()
        }
    }

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(
            happy,
            "It's just a mining camp. Prisoners are sent here from Al Kharid. They serve out " +
                "their sentence by mining. Most prisoners will end their days here, surrounded " +
                "by desert.",
        )
        chatPlayer(laugh, "So you could almost say that they got their ... 'Just Desserts'")
        chatNpc(bored, "You could say that... <col=000080>-- There is an awkward pause --</col>")
        chatNpc(bored, "But it wouldn't be very funny.")
        chatPlayer(
            confused,
            "When they talk about 'the silence of the desert', this must be what they mean.",
        )
        if (!choice2("Can I take a look around the place?", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        chatPlayer(quiz, "Can I take a look around the place?")
        chatNpc(
            bored,
            "Not really. The Captain won't let you in the compound. He's the only one who has " +
                "the key to the gate. And if you talk to him, he'll probably just order us to " +
                "kill you. Unless...",
        )
        if (choice2("Does the Captain order you to kill a lot of people?", true, "Unless what?", false)) {
            captainOrders()
            return
        }
        chatPlayer(quiz, "Unless what?")
        chatNpc(
            bored,
            "Unless he has a use for you. He's been trying to track down someone called 'Al " +
                "Zaba Bhasim'. You could offer to catch him and that might put you in his good " +
                "books?",
        )
        if (!choice2("Where would I find this Al Zaba Bhasim?", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        chatPlayer(quiz, "Where would I find this Al Zaba Bhasim?")
        chatNpc(
            bored,
            "Well, he could be anywhere. He's a nomadic desert dweller. However, they say that " +
                "he's frequently to be found to the west in the hospitality of the tenti's.",
        )
        if (!choice2("The Tenti's, who are they?", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        chatPlayer(quiz, "The Tenti's, who are they?")
        chatNpc(
            bored,
            "Well, we're not really sure what their proper name is. But they live in tents so we " +
                "call them the 'Tenti's'.",
        )
        if (!choice2("Okay, thanks.", false, "Is Al Zaba Bhasim very tough?", true)) {
            okayThanks()
            return
        }
        chatPlayer(quiz, "Is Al Zaba Bhasim very tough?")
        chatNpc(
            bored,
            "Well, I'm not sure, but by all accounts, he is a slippery fellow. The Captain has " +
                "been trying to capture him for years. A bit of a waste of time if you ask me. " +
                "Anyway, I have to get going, I do have work to do.",
        )
    }

    private suspend fun Dialogue.whatAreYouGuarding() {
        chatPlayer(quiz, "What are you guarding?")
        chatNpc(
            bored,
            "Well, if you have to know, we're making sure that no prisoners get out. " +
                "<col=000080>-- The guard gives you a disapproving look. --</col> And to make " +
                "sure that unauthorised people don't get in.",
        )
        chatNpc(
            worried,
            "<col=000080>-- The guard looks around nervously. --</col> You'd better go soon " +
                "before the Captain orders us to kill you.",
        )
        if (choice2("Does the Captain order you to kill a lot of people?", true, "Okay, thanks.", false)) {
            captainOrders()
        } else {
            okayThanks()
        }
    }

    private suspend fun Dialogue.captainOrders() {
        chatPlayer(quiz, "Does the Captain order you to kill a lot of people?")
        chatNpc(
            bored,
            "<col=000080>-- The guard snorts. --</col> *Snort* Just about anyone who talks to him.",
        )
        chatNpc(
            bored,
            "Unless he has a use for you, he'll probably just order us to kill you. And it's " +
                "such a horrible job cleaning up the mess afterwards.",
        )
        if (!choice2("Not to mention the senseless waste of human life.", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        chatPlayer(sad, "Not to mention the senseless waste of human life.")
        chatNpc(confused, "Huh? Them's your words, not mine.")
        if (!choice2("It doesn't sound as if you respect your Captain much.", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        chatPlayer(quiz, "It doesn't sound as if you respect your Captain much.")
        mesbox("-- The guard looks around conspiratorially. --")
        chatNpc(
            shifty,
            "Well, to be honest. We think he's not exactly as brave as he makes out. But we have " +
                "to follow his orders. If someone called him a coward, or managed to trick him " +
                "into a one-on-one duel many of us bet that he'd be beaten.",
        )
        chatPlayer(quiz, "And how could I trick him into a one-on-one duel?")
        chatNpc(
            shifty,
            "Like all cowards, he likes to be made to feel important. If anyone insults him " +
                "outright, he just gets us to do his dirty work. However, if he thinks he's " +
                "better than you, if you compliment him, he may feel that he can defeat you.",
        )
        chatNpc(
            shifty,
            "And if he initiated a duel, all the men agreed that they wouldn't intervene. We " +
                "think he'd be slaughtered in double quick time.",
        )
        if (!choice2("Can I have a bet on that?", true, "Okay, thanks.", false)) {
            okayThanks()
            return
        }
        placeBet()
    }

    private suspend fun Dialogue.placeBet() {
        chatPlayer(quiz, "Can I have a bet on that?")
        if (player.ttBet > 0) {
            chatNpc(
                bored,
                "Sorry, we've already taken your bet, wouldn't want any cheating now. Anyway, I " +
                    "have to get back to work. See ya around...",
            )
            return
        }
        chatNpc(
            bored,
            "Well, if you think you stand a chance, sure. But remember, if he gives us an order, " +
                "we have to obey.",
        )
        val stake =
            choice5(
                "I'll bet 5 gold that I win.",
                5,
                "I'll bet 10 gold that I win.",
                10,
                "I'll bet 15 gold that I win.",
                15,
                "I'll bet 20 gold that I win.",
                20,
                "Okay, thanks.",
                0,
            )
        if (stake == 0) {
            okayThanks()
            return
        }
        chatPlayer(happy, "I'll bet $stake gold that I win.")
        if (access.inv.count(COINS) < stake) {
            chatNpc(angry, "Huh! It looks like you're betting with money you don't have.")
            return
        }
        chatNpc(happy, "Great, I'll take that bet.")
        if (access.invDel(access.inv, COINS, stake).failure) {
            return
        }
        player.ttBet = stake / BRIBE
        objbox(COINS, "You hand over $stake gold coins.")
        val payout = BET_PAYOUTS.getValue(stake)
        chatNpc(
            happy,
            "Ok, if you win, you'll get $payout gold back. Anyway, I have to get going, I do have " +
                "work to do. <col=000080>-- The guard walks off. --</col>",
        )
    }

    /** The mercenaries pay out, less a "cleaning fee" worked out on the spot in their favour. */
    private suspend fun Dialogue.collectBet() {
        val bet = player.ttBet
        if (bet !in 1..4) {
            chatNpc(angry, "Move along now, we've had enough of your sort!")
            return
        }
        chatPlayer(happy, "Hey, I've come to collect my bet!")
        chatNpc(bored, "Well, I guess congratulations are in order.")
        chatPlayer(happy, "Thanks!")
        chatNpc(bored, "And we'll only charge the paltry sum of... erm...")
        chatNpc(
            confused,
            "<col=000080>The guards starts to do some mental calculations. You can see his brow " +
                "furrow and he starts to sweat profusely.</col>",
        )
        val stake = bet * BRIBE
        val winnings = BET_WINNINGS.getValue(stake)
        val fee = if (stake == BRIBE) "Five" else stake.toString()
        val pieces = if (winnings == 1) "piece" else "pieces"
        chatNpc(
            laugh,
            "$fee gold for cleaning up the mess. You have won $winnings Gold $pieces! Well " +
                "done..! Ha, ha, ha ha! <col=000080>-- The guards walk off chuckling to " +
                "themselves. --</col>",
        )
        player.ttBet = BET_SETTLED
        access.invAdd(access.inv, COINS, winnings)
    }

    private suspend fun Dialogue.okayThanks() {
        chatPlayer(neutral, "Okay, thanks.")
        chatNpc(bored, "Yeah, whatever!")
    }

    private suspend fun ProtectedAccess.attackWarning(mercenary: Npc) {
        startDialogue(mercenary) {
            mesbox("This mercenary looks very fierce. Are you sure you want to attack him?")
            val attack =
                choice2(
                    "Yes, I can take him on!",
                    true,
                    "Er, no thanks, I've had second thoughts about it.",
                    false,
                )
            if (!attack) {
                mesbox("You decide not to fight the Mercenary.")
                return@startDialogue
            }
            access.ifClose()
            with(security) {
                access.roughUp(mercenary, hits = 4)
                access.dumpInDesert(mercenary)
            }
        }
    }

    private companion object {
        const val BRIBE = 5
        const val BET_SETTLED = 5

        val BET_PAYOUTS = mapOf(5 to 6, 10 to 12, 15 to 19, 20 to 30)
        val BET_WINNINGS = mapOf(5 to 1, 10 to 2, 15 to 4, 20 to 10)
    }
}

/**
 * Nobody attacks the Mercenary Captain in front of his men; the attempt is turned into his
 * warning. Once he has been goaded into a duel he is fair game.
 */
class MercenaryCaptainAttackHook : NpcAttackValidateHook {
    private val captainId by lazy { CAPTAIN.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != captainId || player.ttCaptainDuel) {
            return NpcAttackValidateResult.Pass
        }
        player.queue(MercenaryCaptain.ATTACK_WARNING_QUEUE, 1, npc)
        return NpcAttackValidateResult.Deny("")
    }
}

/**
 * Killing the captain in his duel hands the player the gate key straight from his belt; the
 * ordinary drop table only drops his bones.
 */
class MercenaryCaptainKillHook
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val objRepo: ObjRepository,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {
    private val captainId by lazy { CAPTAIN.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != captainId) {
            return
        }
        val hero = context.hero
        hero.ttCaptainDuel = false
        val launched = launcher.launch(hero) { captainDefeated() }
        if (!launched) {
            hero.mes("You kill the captain!")
            if (quest.stage(hero) >= STAGE_APPROACHED_CAPTAIN && METAL_KEY !in hero.inv) {
                hero.invAddOrDrop(objRepo, METAL_KEY)
                hero.mes("The mercenary captain drops a metal key on the floor.")
            }
        }
    }

    private suspend fun ProtectedAccess.captainDefeated() {
        mes("You kill the captain!")
        if (quest.stage(player) < STAGE_APPROACHED_CAPTAIN || METAL_KEY in inv) {
            return
        }
        quest.advanceFrom(this, STAGE_APPROACHED_CAPTAIN, STAGE_KILLED_CAPTAIN)
        if (METAL_KEY in bank) {
            mes("After killing the captain, you remember that you stored a mining gate key in your")
            mes("bank.")
            return
        }
        if (invAdd(inv, METAL_KEY).success) {
            objbox(
                METAL_KEY,
                "The mercenary captain drops a metal key on the floor. You quickly grab the key " +
                    "and add it to your inventory.",
            )
            return
        }
        invAddOrDrop(objRepo, METAL_KEY)
        objbox(
            METAL_KEY,
            "The mercenary captain drops a metal key on the floor, you try to add it to your " +
                "inventory, but it won't fit in. You drop the key on the floor.",
        )
    }
}
