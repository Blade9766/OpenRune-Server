package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.interfaces.bank.tryOpenBank
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.BANK_TELLER
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BANK_OPEN
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_CLEARED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_CELLAR_SUGGESTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_FURNACE_LIT
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_GADDERANKS_DEAD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_HELP_OFFERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_REPAIRS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STORE_STOCKED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The people of Burgh de Rott. They all share one conversation that follows the town's recovery
 * stage by stage; only what each says about their own job differs, and Cornelius, the would-be
 * banker, has his part in recruiting himself. Florin's gate duty is in [BurghGate]; Aurel's store
 * is in [GeneralStore].
 */
@Singleton
class BurghCitizens @Inject constructor(private val iaom: InAidOfTheMyrequeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (citizen in CITIZENS.keys - BurghGate.FLORIN) {
            onOpNpc1(citizen) { startDialogue(it.npc) { talk(it.npc) } }
        }
        onOpNpc1(CORNELIUS) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc1(CORNELIUS_BANKER) { startDialogue(it.npc) { banker() } }
        for (child in CHILDREN) {
            onOpNpc1(child) { startDialogue(it.npc) { chatNpc(shifty, "Sorry. I shouldn't talk to strangers.") } }
        }
        onOpNpc1(MARIUS) { mesbox("The man is too ill to talk.") }
        onOpLoc1(MARIUS_BED) { mesbox("The man is too ill to talk.") }
    }

    /** The shared conversation for [npc], who may also be Florin or Aurel once the gate is open. */
    suspend fun Dialogue.talk(npc: Npc?) {
        val stage = iaom.effectiveStage(player)
        when {
            stage < STAGE_CELLAR_SUGGESTED -> newcomer(npc)
            stage < STAGE_CELLAR_CLEARED -> clearingCellar(npc)
            stage < STAGE_HELP_OFFERED -> cellarDone(npc)
            stage < STAGE_STORE_STOCKED -> helpingAurel(npc)
            stage < STAGE_BANK_OPEN -> fixingBank(npc)
            stage < STAGE_FURNACE_LIT -> fixingFurnace(npc)
            stage < STAGE_GADDERANKS_DEAD -> titheDays(npc)
            else -> settled(npc)
        }
    }

    private suspend fun Dialogue.newcomer(npc: Npc?) {
        chatNpc(neutral, "Hey there. How's it going?")
        chatPlayer(neutral, "Not so bad.")
        while (true) {
            when (
                choice5(
                    "How come you have no food?", 1,
                    "What is this place?", 2,
                    "What do you do here?", 3,
                    "Are there any 'out of the way' places here?", 4,
                    "Okay, thanks.", 5,
                )
            ) {
                1 -> noFood()
                2 -> {
                    chatPlayer(quiz, "What is this place?")
                    chatNpc(neutral, "This is our town! We live here. It's called Burgh de Rott. It ain't much to look at, but it's home.")
                }
                3 -> job(npc)
                4 -> {
                    hideaway()
                    return
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.noFood() {
        chatPlayer(quiz, "How come you have no food?")
        when (access.random.of(0, 2)) {
            0 -> chatNpc(laugh, "Well obviously we just don't get out to the shops like we used to!")
            1 -> {
                chatNpc(sad, "We can't leave the village and there's precious little food in here. Just rats, and they're not very appetising.")
                chatNpc(sad, "There's nowhere to store food either. The whole town is falling to bits.")
            }
            else -> {
                chatNpc(worried, "We daren't leave town in case the vampyres spot us and make us pay a blood tithe.")
                chatNpc(sad, "We're all so weak, we'd probably die if they took any more blood from us.")
            }
        }
    }

    private suspend fun Dialogue.hideaway() {
        chatPlayer(quiz, "Are there any 'out of the way' places here?")
        chatNpc(confused, "How do you mean, 'out of the way'?")
        chatPlayer(neutral, "Somewhere a couple of friends and I could hole up for a while without the vampyres noticing.")
        chatNpc(neutral, "Ah, right, I see what you mean. Hmm, let's think now...")
        chatNpc(neutral, "Well, there's the inn. The cellar would have been useful, but the trapdoor down to it is buried under rubble from a fallen wall.")
        chatNpc(neutral, "The cellar's probably full of rubble too. But you might be able to do something with it.")
        chatPlayer(happy, "That could work. Thanks.")
        chatNpc(neutral, "Listen, if you do decide to join us here, we'll expect you to pull your weight. Help fix things up a bit, you know.")
        if (iaom.stage(player) in InAidOfTheMyrequeQuest.STAGE_ADMITTED until STAGE_CELLAR_SUGGESTED) {
            iaom.advanceTo(access, STAGE_CELLAR_SUGGESTED)
        }
    }

    private suspend fun Dialogue.clearingCellar(npc: Npc?) {
        chatNpc(neutral, "Hey there. So you're clearing out the cellar at the inn? How's it going?")
        chatPlayer(neutral, "Not so bad, but it's a lot of hard work to be honest.")
        chatNpc(neutral, "Oh well, I'm sure you'll manage.")
        if (choice2("What do you do here?", true, "Okay, thanks.", false)) {
            job(npc)
        } else {
            chatPlayer(neutral, "Okay, thanks.")
        }
    }

    private suspend fun Dialogue.cellarDone(npc: Npc?) {
        chatPlayer(happy, "Hey there, I'm finished with my project at the inn.")
        chatNpc(neutral, "Oh, that's good to know.")
        while (true) {
            when (
                choice4(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "I'd like to help fix up the town.", 3,
                    "I'd best be off.", 4,
                )
            ) {
                1 -> job(npc)
                2 -> barelyATown()
                3 -> {
                    chatPlayer(happy, "I'd like to help fix up the town.")
                    chatNpc(happy, "Oh, that's very kind of you!")
                    chatNpc(neutral, "Perhaps you could start by fixing up the general store. Go and have a chat with Aurel if you want the details.")
                    chatPlayer(neutral, "Hmm, sounds interesting.")
                    iaom.advanceTo(access, STAGE_HELP_OFFERED)
                    return
                }
                else -> {
                    chatPlayer(neutral, "I'd best be off.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.barelyATown() {
        chatPlayer(quiz, "What do you think of your town?")
        chatNpc(neutral, "It's barely a town. Not exactly a breeding ground for high society, is it?")
        chatNpc(neutral, "At least we can hide from the vampyres here. Hopefully we can get things going soon. Some of the trappings of normal life, you know.")
    }

    private suspend fun Dialogue.helpingAurel(npc: Npc?) {
        val repaired = iaom.isStoreRepaired(player)
        if (repaired) {
            chatNpc(happy, "Hi there. You've done a good job on that general store.")
            chatPlayer(neutral, "Yeah, but I've only fixed the roof and the walls.")
        } else {
            chatPlayer(neutral, "Hello, I wonder if I could ask a few questions?")
            chatNpc(neutral, "Sure, go ahead.")
        }
        while (true) {
            when (
                choice4(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "What should I do now?", 3,
                    "Okay, thanks.", 4,
                )
            ) {
                1 -> job(npc)
                2 -> {
                    chatPlayer(quiz, "What do you think of your town?")
                    if (repaired) {
                        chatNpc(neutral, "It's coming together. The store looks much nicer now it's been fixed up. It would be better with some stock in it though.")
                    } else {
                        chatNpc(neutral, "It's barely a town. Not exactly a breeding ground for high society, is it?")
                    }
                }
                3 -> {
                    chatPlayer(quiz, "What should I do now?")
                    if (iaom.stage(player) >= InAidOfTheMyrequeQuest.STAGE_CRATE_GIVEN) {
                        chatNpc(neutral, "I thought you were helping Aurel stock his store?")
                    } else if (iaom.stage(player) >= STAGE_STORE_REPAIRS) {
                        chatNpc(confused, "I thought you were helping Aurel with something? Fixing up his store, perhaps?")
                        chatPlayer(confused, "Oh, was I?")
                        chatNpc(neutral, "I'd go and have a chat with him if I were you.")
                    } else {
                        chatNpc(neutral, "Go and have a chat with Aurel about his store.")
                    }
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.fixingBank(npc: Npc?) {
        val booth = player.bankBooth == 1
        val wall = player.bankWall == 1
        if (booth && wall) {
            chatNpc(happy, "Hey, it's you! You're doing a great job with the town! It's really getting better!")
            chatPlayer(happy, "I'm happy to help!")
        } else {
            chatPlayer(neutral, "Hello.")
        }
        while (true) {
            when (
                choice4(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "What should I do now?", 3,
                    "Okay, thanks.", 4,
                )
            ) {
                1 -> job(npc)
                2 -> {
                    chatPlayer(quiz, "What do you think of your town?")
                    chatNpc(happy, "It's a lot better now we've got a general store. You're doing a great job! What a hero!")
                }
                3 -> {
                    chatPlayer(quiz, "What should I do now?")
                    when {
                        !booth -> chatNpc(neutral, "It would be good if you could fix the bank up. I'd start with the bank booth.")
                        !wall -> chatNpc(neutral, "You need to fix up the bank. If the booth's done, you should move on to the wall.")
                        npc?.isType(CORNELIUS) == true -> {
                            recruitCornelius()
                            return
                        }
                        else -> chatNpc(neutral, "Well, the bank's all fixed now, but it isn't going to serve people on its own. You'll have to recruit someone!")
                    }
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.recruitCornelius() {
        chatNpc(neutral, "Well, the bank's all fixed now, but it isn't going to serve people on its own. You'll have to recruit someone!")
        chatPlayer(quiz, "Do you fancy the job?")
        chatNpc(shocked, "Me? Why, I suppose I could... Yes, in fact, I'm sure I could!")
        chatNpc(happy, "I'd best go and get changed...")
        VarPlayerIntMapSetter.set(player, BANK_TELLER, 1)
        iaom.advanceTo(access, STAGE_BANK_OPEN)
        mesbox("Cornelius changes into something more suitable and takes his place behind the bank booth.")
    }

    private suspend fun Dialogue.fixingFurnace(npc: Npc?) {
        chatNpc(happy, "Hello there. Thank you so much for all your work so far. We really appreciate it.")
        chatPlayer(happy, "Thanks.")
        while (true) {
            when (
                choice5(
                    "What do you do here?", 1,
                    "What do you think of your town?", 2,
                    "Why does everyone stay in this town?", 3,
                    "What should I do now?", 4,
                    "Okay, thanks.", 5,
                )
            ) {
                1 -> job(npc)
                2 -> {
                    chatPlayer(quiz, "What do you think of your town?")
                    chatNpc(happy, "Oh, it's brilliant, but we could really do with somewhere to melt down the local ores. Maybe you could fix up the furnace?")
                }
                3 -> {
                    chatPlayer(quiz, "Why does everyone stay in this town?")
                    chatNpc(worried, "It's safer here than anywhere else in Morytania. We could try escaping across the Salve, but the ghasts in Mort Myre would eat us alive!")
                }
                4 -> {
                    chatPlayer(quiz, "What should I do now?")
                    chatNpc(neutral, "You've done a lot already and we're all very grateful. Still, I overheard someone saying how great it'd be to have the old furnace working again.")
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.titheDays(npc: Npc?) {
        val inStore = npc?.isType(AUREL) == true
        val where = if (inStore) "They've set up a blood tithing station right here in my store!" else "They've set up a blood tithing station in the general store!"
        chatNpc(angry, "Well done, big shot! The smoke from the furnace has brought Gadderanks and his juvinates. $where")
        while (true) {
            when (
                choice4(
                    "Who's Gadderanks?", 1,
                    "What's a juvinate?", 2,
                    "What should I do now?", 3,
                    "Okay, thanks.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Who's Gadderanks?")
                    chatNpc(angry, "Some human lackey of the vampyres. He collects blood tithes from the villages around here, with juvinates to guard him because he's too weak to defend himself.")
                }
                2 -> {
                    chatPlayer(quiz, "What's a juvinate?")
                    chatNpc(worried, "A kind of vampyre. Stronger than a juvenile, not as bad as a full-blown vyre. Still, I've never seen anyone get the better of one.")
                }
                3 -> {
                    chatPlayer(quiz, "What should I do now?")
                    chatNpc(worried, "If I were you, I'd get out of town. With Gadderanks here, I doubt many people will stay.")
                    chatPlayer(angry, "Maybe I can stop him.")
                    val place = if (inStore) "He's right here" else "He's set up in the general store"
                    chatNpc(laugh, "Ha! In your dreams. $place if you want to give it a go. It's your funeral though!")
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.settled(npc: Npc?) {
        chatNpc(happy, "Hello there! Thanks to you this place is starting to feel like a real town.")
        if (choice2("What do you do here?", true, "Okay, thanks.", false)) {
            job(npc)
        } else {
            chatPlayer(neutral, "Okay, thanks.")
        }
    }

    private suspend fun Dialogue.job(npc: Npc?) {
        chatPlayer(quiz, "What do you do here?")
        val type = npc?.let { n -> (CITIZENS.keys + CORNELIUS).firstOrNull { n.isType(it) } }
        val name = type?.let { CITIZENS[it]?.first } ?: "Cornelius"
        when (type?.let { CITIZENS[it]?.second }) {
            Job.Odd ->
                when (access.random.of(0, 2)) {
                    0 -> chatNpc(neutral, "My name's $name. I do a bit of this, a bit of that. Just try to survive, really. There's not much else we can do.")
                    1 -> chatNpc(neutral, "Oh, my name's $name. I just help out around here, you know, gathering food and so on.")
                    else -> chatNpc(neutral, "Not that it makes much difference, but my name's $name. Like everyone else here, I just get through each day as it comes.")
                }
            Job.RatCatcher ->
                when (access.random.of(0, 2)) {
                    0 -> chatNpc(neutral, "Oh, hi. I'm $name. I hunt rats, mostly. This one tastes awful, but it's the only food we've got!")
                    1 -> chatNpc(neutral, "Huh? I'm $name. Scavenging for food is what I mostly do. Now and then I come across a rat, and that's a good day. Like today!")
                    else -> chatNpc(laugh, "Oh, hello. $name's the name, and I keep the village clear of rats. I love my work so much I bring it home with me... on a stick!")
                }
            Job.Warming -> chatNpc(neutral, "I'm $name. I'm just trying to stay warm.")
            Job.Leader -> chatNpc(neutral, "I'm $name, the village leader.")
            Job.Farmer -> chatNpc(neutral, "I'm $name. I'm a farmer. Not that there's much to grow around here.")
            Job.Nurse -> chatNpc(worried, "I'm $name. I'm looking after my husband, who isn't feeling too well!")
            null -> {
                val banking = player.vars[BANK_TELLER] == 1
                chatNpc(neutral, if (banking) "I'm Cornelius. I run the bank now." else "I'm Cornelius. I don't really do anything here.")
            }
        }
    }

    private suspend fun Dialogue.banker() {
        chatNpc(happy, "Good day. How may I help you?")
        if (!iaom.isBankOpen(player)) {
            chatNpc(confused, "Actually, I'm not quite sure what I'm doing back here yet.")
            return
        }
        when (choice2("I'd like to access my bank account, please.", 1, "Never mind.", 2)) {
            1 -> access.tryOpenBank()
            else -> chatPlayer(neutral, "Never mind.")
        }
    }

    enum class Job {
        Odd,
        RatCatcher,
        Warming,
        Leader,
        Farmer,
        Nurse,
    }

    companion object {
        const val CORNELIUS = "npc.burgh_potential_bank_teller"
        const val CORNELIUS_BANKER = "npc.burgh_bank_teller"
        const val MARIUS = "npc.burgh_bed_man"
        const val MARIUS_BED = "loc.burgh_bed_man"
        const val AUREL = "npc.burgh_general_store_owner"

        /** Each citizen's name and what they say about their job. */
        val CITIZENS: Map<String, Pair<String, Job>> =
            linkedMapOf(
                "npc.burgh_villager_0" to ("Sorin" to Job.Odd),
                "npc.burgh_vilager_1" to ("Luscion" to Job.Odd),
                "npc.burgh_vilager_2" to ("Sergiu" to Job.Odd),
                "npc.burgh_vilager_3" to ("Radu" to Job.Odd),
                "npc.burgh_vilager_4" to ("Grigore" to Job.Odd),
                "npc.burgh_vilager_5" to ("Ileana" to Job.Odd),
                "npc.burgh_vilager_6" to ("Valeria" to Job.Odd),
                "npc.burgh_vilager_7" to ("Emilia" to Job.Odd),
                "npc.burgh_vilager_8" to ("Florin" to Job.Odd),
                "npc.burgh_vilager_rat_2" to ("Razvan" to Job.RatCatcher),
                "npc.burgh_vilager_rat_3" to ("Luminata" to Job.RatCatcher),
                "npc.burgh_vilager_sit1" to ("Calin" to Job.Warming),
                "npc.burgh_vilager_sit2" to ("Mihail" to Job.Warming),
                "npc.burgh_vilager_sit3" to ("Nicoleta" to Job.Warming),
                "npc.burgh_vilager_sit4" to ("Simona" to Job.Warming),
                "npc.burgh_vilager_leader" to ("Elisabeta" to Job.Leader),
                "npc.burgh_farmer" to ("Teodor" to Job.Farmer),
                "npc.burgh_bed_man_wife" to ("Gabriela" to Job.Nurse),
            )

        val CHILDREN =
            listOf(
                "npc.burgh_villager_child1",
                "npc.burgh_villager_child2",
                "npc.burgh_villager_child3",
                "npc.burgh_villager_child4",
            )
    }
}
