package org.rsmod.content.quest.area.gnomestronghold.grandtree.npcs

import dev.openrune.types.MesAnimType
import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTree
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.BARK_SAMPLE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.DACONIA_ROCK
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GNOME_GUARD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.INVASION_PLANS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.NARNODE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.NARNODE_HEAD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_CHARLIE_QUESTIONED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_DEMON_SLAIN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ESCAPE_BY_GLIDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_GLOUGH_WARNED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_SCROLL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_TWIGS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KEY_HINTED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KING_DOUBTS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ON_KARAMJA
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_PRISONER_REPORTED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_SEARCHING_ROOTS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRANSLATED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRAPDOOR_OPEN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TRANSLATION_BOOK
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TWIGS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeRoots
import org.rsmod.content.quest.area.gnomestronghold.grandtree.fadeTeleport
import org.rsmod.content.quest.area.gnomestronghold.grandtree.freeTileNear
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs.NarnodeMonkeyMadness
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Narnode Shareen. The same npc type stands on the Grand Tree's ground floor and beside the
 * ladder in the roots below it, so one dialogue serves both: the only difference is that the
 * surface King leads the player down the trapdoor before the foundations conversation.
 */
class KingNarnode
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val monkeyMadness: NarnodeMonkeyMadness,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NARNODE) { startDialogue(it.npc) { narnode(it.npc) } }
    }

    private suspend fun Dialogue.narnode(npc: Npc) {
        val inTunnel = npc.coords.z >= GrandTree.TUNNEL_MIN_Z
        when (grandTree.stage(player)) {
            0 -> notStarted(inTunnel)
            STAGE_STARTED -> awaitingHazelmere()
            STAGE_HAS_SCROLL -> bringBackNews()
            STAGE_TRANSLATED -> {
                chatNpc(quiz, "Hello Traveller, did you speak to Glough?")
                chatPlayer(neutral, "Not yet.")
                chatNpc(neutral, "OK. He lives just in front of the Grand Tree. Let me know when you've talked to him.")
            }
            STAGE_GLOUGH_WARNED -> reportingToKing()
            STAGE_PRISONER_REPORTED -> {
                chatNpc(neutral, "Hello Traveller. If you wish to talk to the prisoner go to the top of the tree, you'll find him there.")
                chatPlayer(happy, "Thanks.")
            }
            STAGE_CHARLIE_QUESTIONED -> if (grandTree.kingToldOfCharlie.get(player)) concernedAboutGlough() else reportCharlie()
            STAGE_ESCAPE_BY_GLIDER -> chatNpc(worried, "Glough is looking for you! Leave on the glider now before it's too late!")
            STAGE_ON_KARAMJA -> fullAlert()
            STAGE_HAS_LUMBER_ORDER -> if (player.inv.contains(LUMBER_ORDER)) showLumberOrder() else fullAlert()
            STAGE_KING_DOUBTS -> revoltWarning()
            STAGE_KEY_HINTED -> if (player.inv.contains(INVASION_PLANS)) showInvasionPlans() else revoltWarning()
            STAGE_HAS_TWIGS -> adviceToLeave()
            STAGE_TRAPDOOR_OPEN -> notRealistic()
            STAGE_DEMON_SLAIN -> endOfTunnel(npc)
            STAGE_SEARCHING_ROOTS -> if (player.inv.contains(DACONIA_ROCK)) daconiaDelivered() else stillSearching()
            else -> with(monkeyMadness) { talk() }
        }
    }

    /** The King's lines while the conversation happens away from the npc it started with. */
    private suspend fun Dialogue.king(mood: MesAnimType, text: String) {
        chatNpcSpecific(GrandTree.KING_NAME, NARNODE_HEAD, mood, text)
    }

    private suspend fun Dialogue.notStarted(inTunnel: Boolean) {
        chatNpc(happy, "Welcome Traveller. I am King Narnode. It's nice to see an outsider.")
        chatPlayer(happy, "Hi! It seems to be a busy settlement.")
        chatNpc(worried, "For now.")
        when (choice2("You seem worried, what's up?", 1, "I'll be off now.", 2)) {
            1 -> {
                chatPlayer(quiz, "You seem worried, what's up?")
                chatNpc(neutral, "Traveller, can I speak to you in strictest confidence?")
                chatPlayer(neutral, "Of course sire.")
                if (player.inv.freeSpace() < 2) {
                    mesbox("You need at least two free inventory spaces before following the King.")
                    return
                }
                if (!inTunnel) {
                    chatNpc(neutral, "Not here, follow me.")
                    chatNpc(neutral, "Down here.")
                    access.descend()
                }
                foundations()
            }
            2 -> {
                chatPlayer(neutral, "I'll be off now.")
                chatNpc(happy, "Enjoy your stay with us. There are many things to see in my kingdom.")
            }
        }
    }

    private suspend fun ProtectedAccess.descend() {
        mesbox("You follow King Narnode down the trapdoor.")
        fadeTeleport(GrandTree.TUNNEL_LANDING)
        npcFind(GrandTree.TUNNEL_NARNODE, NARNODE, TUNNEL_SEARCH_RADIUS, HuntVis.Off, search)?.facePlayer(player)
    }

    private suspend fun Dialogue.foundations() {
        chatPlayer(quiz, "So what is this place?")
        king(happy, "These, my friend, are the foundations of the stronghold.")
        chatPlayer(neutral, "They look like roots to me.")
        king(happy, "Not just any roots Traveller! These were created by gnome mages eons ago, since then they have grown to become a mighty stronghold!")
        chatPlayer(quiz, "Impressive. What exactly is the problem?")
        king(worried, "In the last two months our tree guardians have reported continuing deterioration of the Grand Tree's health. I've never seen this before! It could be the end for us all!")
        chatPlayer(quiz, "You mean the tree is ill?")
        king(sad, "In effect yes. Would you be willing to help us discover what is happening to the tree?")
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT."
            )
        }
        when (choice2("Yes.", 1, "No.", 2, title = "Start The Grand Tree quest?")) {
            1 -> startQuest()
            2 -> {
                chatPlayer(sad, "I'm sorry, I don't want to get involved.")
                king(neutral, "I understand Traveller. Please keep this to yourself.")
                chatPlayer(neutral, "Of course.")
            }
        }
        king(neutral, "I'll show you the way back up.")
        king(neutral, "Up here.")
        access.ascend()
    }

    private suspend fun Dialogue.startQuest() {
        chatPlayer(happy, "I'd be happy to help!")
        grandTree.advanceTo(access, STAGE_STARTED)
        king(happy, "Thank Guthix for your arrival!")
        king(neutral, "The first task is to find out what's killing the tree.")
        chatPlayer(quiz, "Do you have an idea?")
        king(neutral, "My top tree guardian, Glough, believes it's human sabotage. I'm not so sure! The only way to know for sure is to talk to Hazelmere.")
        chatPlayer(quiz, "Who's Hazelmere?")
        king(neutral, "Hazelmere is one of the mages that created the Grand Tree! He is the only one that has survived from that time. Take this bark sample to him, he will be able to help!")
        objbox(BARK_SAMPLE, "The king shows you a sample of bark.")
        king(neutral, "The mage only talks in the old tongue, you'll need this.")
        objbox(TRANSLATION_BOOK, "The king shows you a translation book.")
        chatPlayer(quiz, "What is it?")
        king(neutral, "It's a translation book, you'll need it to translate what Hazelmere says. Do that carefully! His words are our only hope! You'll find his dwellings high upon a towering hill, on an island east of Yanille.")
        access.invAddOrDrop(objRepo, BARK_SAMPLE)
        access.invAddOrDrop(objRepo, TRANSLATION_BOOK)
        mesbox("The king hands you the bark sample and the translation book.")
    }

    private suspend fun ProtectedAccess.ascend() {
        mesbox("You follow King Narnode back up the trapdoor.")
        fadeTeleport(GrandTree.SURFACE_LANDING)
    }

    private suspend fun Dialogue.awaitingHazelmere() {
        chatNpc(quiz, "Traveller, any word from Hazelmere?")
        chatPlayer(neutral, "Not yet.")
        if (!player.inv.contains(BARK_SAMPLE)) {
            chatPlayer(sad, "I've lost the bark sample.")
            chatNpc(neutral, "Here's another sample, hang on to it this time!")
            access.invAddOrDrop(objRepo, BARK_SAMPLE)
            mesbox("The King gives you another bark sample.")
        }
        if (!player.inv.contains(TRANSLATION_BOOK)) {
            chatPlayer(sad, "I've lost the translation book.")
            chatNpc(neutral, "Don't worry, I have more.")
            access.invAddOrDrop(objRepo, TRANSLATION_BOOK)
            mesbox("The King gives you another translation book.")
        }
        chatNpc(neutral, "Hazelmere lives on an island just south of the Khazard Fight Arena. Give him the sample and translate his reply.")
        chatNpc(worried, "I just hope he can help us in our hour of need!")
    }

    private suspend fun Dialogue.bringBackNews() {
        chatPlayer(happy, "Hello again, your highness.")
        chatNpc(quiz, "Hello Traveller, did you speak to Hazelmere?")
        chatPlayer(happy, "Yes! I managed to find him.")
        chatNpc(quiz, "Do you understand what he said?")
        when (choice2("I think so!", 1, "No, I need to go back.", 2)) {
            1 -> translate()
            2 -> {
                chatPlayer(neutral, "No, I need to go back.")
                if (!player.inv.contains(BARK_SAMPLE)) {
                    chatPlayer(sad, "I've lost the bark sample.")
                    chatNpc(neutral, "Here's another sample, hang on to it this time!")
                    access.invAddOrDrop(objRepo, BARK_SAMPLE)
                }
                chatNpc(worried, "Time is of the essence Traveller!")
            }
        }
    }

    /**
     * The five-part translation. The right option of the last three menus only appears while
     * every earlier choice was right; a slip replaces it with a decoy, as the real quest does.
     */
    private suspend fun Dialogue.translate() {
        chatPlayer(happy, "I think so!")
        chatNpc(quiz, "So what did he say?")
        var correct = true

        val first =
            choice5(
                "King Narnode must be stopped, he is a madman!", 1,
                "Praise be to the great Zamorak!", 2,
                "Do you have any bread? I do like bread.", 3,
                "The time has come to attack!", 4,
                "None of the above.", 5,
            )
        if (first != 5) {
            chatPlayer(neutral, FIRST_LINES[first - 1])
            correct = false
        }

        val second =
            choice5(
                "The tree is fine, you have nothing to fear.", 1,
                "You must come and see me!", 2,
                "The tree needs watering as there has been drought.", 3,
                "Grave danger lies ahead, only the bravest will linger.", 4,
                "None of the above.", 5,
            )
        if (second != 5) {
            chatPlayer(neutral, SECOND_LINES[second - 1])
            correct = false
        }

        val thirdKey = if (correct) "A man came to me with the King's seal." else "Time passes us by."
        val third =
            choice5(
                "Time is of the essence! We must move quickly.", 1,
                thirdKey, 2,
                "There is no need for haste, just send a runner.", 3,
                "You must act now, or we will all die!", 4,
                "None of the above.", 5,
            )
        when (third) {
            1 -> chatPlayer(neutral, "Time is of the essence! We must move quickly.")
            2 -> chatPlayer(neutral, thirdKey)
            3 -> chatPlayer(neutral, "There is no need for haste.")
            4 -> chatPlayer(neutral, "You must act now or we will all die!")
        }
        if (third != 2) {
            correct = false
        }

        val fourthKey = if (correct) "I gave the man Daconia rocks." else "Only Adamantite is any use."
        val fourth =
            choice5(
                fourthKey, 1,
                "You must use force!", 2,
                "Use a bucket of milk from a scared cow.", 3,
                "Take this banana to him, he will understand.", 4,
                "None of the above.", 5,
            )
        when (fourth) {
            1 -> chatPlayer(neutral, fourthKey)
            2 -> chatPlayer(neutral, "You must use force!")
            3 -> chatPlayer(neutral, "Use a bucket of milk from a sacred cow.")
            4 -> chatPlayer(neutral, "Take this banana to him, he will understand.")
        }
        if (fourth != 1) {
            correct = false
        }

        val fifthKey = if (correct) "And Daconia rocks will kill the tree!" else "The tree will die in five days!"
        val fifth =
            choice5(
                "All with be fine on the third night.", 1,
                "You must wait till the second night.", 2,
                "Nothing will help us now!", 3,
                fifthKey, 4,
                "None of the above.", 5,
            )
        when (fifth) {
            1 -> chatPlayer(neutral, "All with be fine on the third night.")
            2 -> chatPlayer(neutral, "You must wait till the second night.")
            3 -> chatPlayer(neutral, "Nothing will help us now!")
            4 -> chatPlayer(neutral, fifthKey)
        }
        if (fifth != 4) {
            correct = false
        }

        if (!correct) {
            chatNpc(confused, "Wait a minute! That doesn't sound like Hazelmere! Are you sure you translated correctly?")
            chatPlayer(confused, "Erm...I think so.")
            chatNpc(neutral, "I'm sorry Traveller but this is no good. The translation must be perfect or the information's no use. Please come back when you know exactly what Hazelmere said.")
            return
        }
        chatNpc(happy, "Of course! I should've known! Someone must've forged my royal seal. Hazelmere thought I sent him for the Daconia stones!")
        chatPlayer(quiz, "What are Daconia stones?")
        chatNpc(neutral, "Hazelmere created the Daconia stones. They are a safety measure, in case the tree grew out of control. They're the only thing that can kill the tree.")
        chatNpc(worried, "This is terrible! The stones must be recovered!")
        chatPlayer(quiz, "Can I help?")
        chatNpc(neutral, "First I must warn the tree guardians. Please, could you tell the chief tree guardian Glough. He lives in a tree house just in front of the Grand Tree.")
        chatNpc(neutral, "If he's not there he will be at his girlfriend Anita's place. Meet me back here once you've told him.")
        chatPlayer(happy, "OK! I'll be back soon.")
        grandTree.advanceTo(access, STAGE_TRANSLATED)
    }

    private suspend fun Dialogue.reportingToKing() {
        chatPlayer(quiz, "Hello, your highness. Have you any news on the Daconia stones?")
        chatNpc(happy, "It's OK Traveller, thanks to Glough! He found a human sneaking around! He had three Daconia rocks on him!")
        chatPlayer(confused, "Wow! That was quick!")
        chatNpc(neutral, "Yes Glough really knows what he's doing. The human has been detained until we know who else is involved.")
        chatNpc(worried, "Maybe Glough was right, maybe humans are invading!")
        chatPlayer(neutral, "I doubt it, can I speak to the prisoner?")
        chatNpc(neutral, "Certainly. He's on the top level of the tree. Be careful, it's a long way down!")
        grandTree.advanceTo(access, STAGE_PRISONER_REPORTED)
    }

    private suspend fun Dialogue.reportCharlie() {
        chatNpc(quiz, "Hello Traveller. So, did you speak to the culprit?")
        chatPlayer(neutral, "Yes I did and something's not right!")
        chatNpc(quiz, "What do you mean?")
        chatPlayer(neutral, "The prisoner said he was paid by Glough to get the Daconia stones!")
        chatNpc(angry, "That's absurd! He's just trying to save himself!")
        chatNpc(neutral, "Since Glough's wife died he's been a little strange. He would never wrongly imprison someone though! Now that the culprit is locked up we can relax. It's sad but I think Glough was right.")
        chatNpc(worried, "Humans are planning to invade and wipe out the tree gnomes!")
        chatPlayer(quiz, "But why?")
        chatNpc(neutral, "Who knows? You may have to leave soon Traveller! I trust you but the local gnomes are getting paranoid.")
        grandTree.kingToldOfCharlie.set(player, true)
    }

    private suspend fun Dialogue.concernedAboutGlough() {
        chatPlayer(worried, "Your highness! I'm concerned about Glough!")
        chatNpc(quiz, "Why? Don't worry about him now the culprit has been caught. I'm sure Glough's resentment of humans will pass with time.")
        chatPlayer(neutral, "I'm not so sure.")
        chatNpc(neutral, "If you're really concerned speak to him.")
    }

    private suspend fun Dialogue.fullAlert() {
        chatPlayer(neutral, "King Narnode, I need to talk!")
        chatNpc(worried, "Traveller, what are you doing here? The stronghold has been put on full alert! It's not safe for you here!")
    }

    private suspend fun Dialogue.showLumberOrder() {
        chatPlayer(neutral, "King Narnode, I need to talk!")
        chatNpc(worried, "Traveller, what are you doing here? The stronghold has been put on full alert! It's not safe for you here!")
        chatPlayer(neutral, "Your highness, I believe Glough is killing the trees in order to make a mass fleet of warships!")
        chatNpc(angry, "That's an absurd accusation!")
        chatPlayer(neutral, "His hatred for humanity is stronger than you know!")
        chatNpc(angry, "That's enough Traveller, you sound as paranoid as him! Traveller please leave! It's bad enough having one human locked up.")
        grandTree.advanceTo(access, STAGE_KING_DOUBTS)
    }

    private suspend fun Dialogue.revoltWarning() {
        chatPlayer(happy, "Hello, your highness.")
        chatNpc(worried, "Please Traveller, if the gnomes see me talking to you they'll revolt against me.")
        chatPlayer(confused, "That's crazy!")
        chatNpc(worried, "Glough's scared the whole town, he expects the humans to attack any day. He's even begun to recruit hundreds of gnome soldiers.")
        chatPlayer(angry, "Don't you understand he's creating his own army?!")
        chatNpc(worried, "Please Traveller, leave before it's too late!")
    }

    private suspend fun Dialogue.showInvasionPlans() {
        chatPlayer(quiz, "Hi, your highness, did you think about what I said?")
        chatNpc(neutral, "Look, if you're right about Glough I would have him arrested but there's no reason for me to think he's lying.")
        chatPlayer(neutral, "Look, I found this at Glough's home!")
        if (player.inv.freeSpace() < TWIGS.size - 1) {
            chatNpc(neutral, "Hmm. Looks like you can't carry anymore. Put 4 things down and come back to me.")
            return
        }
        if (access.invDel(access.inv, INVASION_PLANS).failure) {
            return
        }
        mesbox("You give the King the invasion plans.")
        chatNpc(worried, "If these are to be believed then this is terrible!")
        chatNpc(neutral, "But it's not proof, any one could have made these. Traveller, I understand your concern, I had guards search Glough's house but they found nothing suspicious, just these odd twigs.")
        chatNpc(neutral, "On the other hand, if Glough's right about the humans we will need an army of gnomes to protect ourselves. So I've decided to allow Glough to raise a mighty gnome army.")
        chatNpc(worried, "The Grand Tree's still slowly dying, if it is human sabotage we must respond!")
        access.giveTwigs()
        grandTree.advanceTo(access, STAGE_HAS_TWIGS)
    }

    private suspend fun ProtectedAccess.giveTwigs() {
        for (twig in TWIGS) {
            invAddOrDrop(objRepo, twig)
        }
        grandTree.clearPillars(player)
        mesbox("The King has given you some twigs lashed together.")
    }

    private suspend fun Dialogue.adviceToLeave() {
        chatNpc(worried, "Please Traveller, take my advice and leave!")
        if (grandTree.hasAnyTwigs(player)) {
            return
        }
        chatPlayer(sad, "I've lost those twigs you gave me.")
        chatNpc(neutral, "Here take these, I don't see how it will help you though.")
        if (player.inv.freeSpace() < TWIGS.size) {
            chatNpc(neutral, "Hmm. Looks like you can't carry anymore. Put 4 things down and come back to me.")
            return
        }
        access.giveTwigs()
    }

    private suspend fun Dialogue.notRealistic() {
        chatPlayer(neutral, "Your highness, it's true about Glough I tell you! He's planning to take over Gielinor!")
        chatNpc(neutral, "I'm sorry Traveller but it's just not realistic. How could Glough, even with a gnome army, take over?")
        chatPlayer(neutral, "He plans to make a fleet of warships from the Grand Tree's wood!")
        chatNpc(angry, "That's enough Traveller! I've no time for make believe. The tree's still dying. I must get to the truth of this!")
    }

    /** A guard runs in, checks the passage and comes back with Glough's hoard. */
    private suspend fun Dialogue.endOfTunnel(npc: Npc) {
        chatNpc(worried, "Traveller you're wounded! What happened?")
        chatPlayer(neutral, "It's Glough! He set a demon on me!")
        chatNpc(confused, "What?! Glough?! With a demon?!")
        chatPlayer(neutral, "Glough has a store of Daconia rocks further up the passage! He's been accessing the roots from a secret passage at his home.")
        chatNpc(angry, "Never! Not Glough! He's a good gnome at heart!")
        chatNpc(neutral, "Guard!")
        val guard = access.summonGuard(npc)
        try {
            guard(neutral, "Sire!")
            chatNpc(neutral, "Go and check out that passage!")
            guard(worried, "We found Glough hiding under a horde of Daconia rocks!")
            chatPlayer(neutral, "That's what I've been trying to tell you! Glough's been fooling you!")
            chatNpc(sad, "I..I don't know what to say! How could I have been so blind?!")
            chatNpc(neutral, "Guard! Call off the military training!")
            chatNpc(neutral, "The humans are not attacking!")
            guard(neutral, "Yes sir!")
        } finally {
            npcRepo.del(guard, Int.MAX_VALUE)
        }
        chatNpc(sad, "You have my full apologies Traveller! And my gratitude!")
        chatNpc(worried, "A reward will have to wait though, the tree is still dying! The guards are clearing Glough's rock supply now but there must be more Daconia hidden somewhere in the roots! Help us search, we have little time!")
        grandTree.rockRoot.set(player, Random.nextInt(GrandTreeRoots.ROOTS.size))
        grandTree.advanceTo(access, STAGE_SEARCHING_ROOTS)
    }

    private fun ProtectedAccess.summonGuard(king: Npc): Npc {
        val tile =
            freeTileNear(
                listOf(
                    king.coords.translateX(1),
                    king.coords.translateZ(-1),
                    king.coords.translateX(-1),
                    king.coords.translateZ(1),
                )
            )
        val guard = Npc(GNOME_GUARD, tile)
        guard.mode = NpcMode.None
        npcRepo.add(guard, GUARD_LINGER_TICKS)
        guard.facePlayer(player)
        return guard
    }

    private suspend fun Dialogue.guard(mood: MesAnimType, text: String) {
        chatNpcSpecific(GrandTree.GUARD_NAME, GNOME_GUARD, mood, text)
    }

    private suspend fun Dialogue.stillSearching() {
        chatNpc(quiz, "Traveller, have you managed to find the Daconia?")
        chatPlayer(sad, "No sign of it so far.")
        chatNpc(worried, "The tree will still die if we don't find it! It could be anywhere!")
        chatPlayer(happy, "Don't worry your highness! We'll find it!")
    }

    private suspend fun Dialogue.daconiaDelivered() {
        chatNpc(quiz, "Traveller, have you managed to find the Daconia?")
        chatPlayer(quiz, "Is this it?")
        chatNpc(happy, "Yes! Excellent, well done!")
        if (access.invDel(access.inv, DACONIA_ROCK).failure) {
            return
        }
        mesbox("You give the King the Daconia rock.")
        chatNpc(happy, "It's incredible, the tree's health is improving already! I don't know what to say, we owe you so much!")
        chatNpc(sad, "To think Glough had me fooled all along!")
        chatPlayer(happy, "All that matters now is that humans and gnomes can live together in peace!")
        chatNpc(happy, "I'll drink to that!")
        chatNpc(happy, "From now on I vow to make this stronghold a welcoming place for all! I'll grant you access to all our facilities.")
        chatPlayer(happy, "Thanks!")
        chatPlayer(confused, "I think!")
        chatNpc(happy, "It should make your stay here easier. You can use the spirit tree to transport yourself, as well as the gnome glider. I also give you access to our mine.")
        chatPlayer(quiz, "Mine?")
        chatNpc(neutral, "Very few know of the secret mine under the Grand Tree. If you push on the roots just to my north they will separate and let you pass.")
        chatPlayer(confused, "Strange!")
        chatNpc(happy, "That's magic trees for you!")
        chatNpc(happy, "All the best Traveller and thanks again!")
        chatPlayer(happy, "You too, your highness!")
        grandTree.advanceTo(access, STAGE_COMPLETE)
    }

    private companion object {
        const val TUNNEL_SEARCH_RADIUS = 4
        const val GUARD_LINGER_TICKS = 100

        val FIRST_LINES =
            listOf(
                "King Narnode must be stopped, he is a madman!",
                "Praise be to the great Zamorak!",
                "Do you have any bread? I do like bread.",
                "The time has come to attack!",
            )

        val SECOND_LINES =
            listOf(
                "The tree is fine, you have nothing to fear.",
                "You must come and see me!",
                "The tree needs watering as there has been drought.",
                "Grave danger lies ahead, only the bravest will linger.",
            )
    }
}
