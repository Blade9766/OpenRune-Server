package org.rsmod.content.quest.area.lumbridge.losttribe.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.WaterMill
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.ZanikFollower
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.CELLAR_ARRIVAL
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.KAZGAR_ARRIVAL
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.MINING_HELMET
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.MISTAG
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_CONTACT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_EMOTES_LEARNT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SILVERWARE_MISSING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TREATY
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.TREATY
import org.rsmod.content.quest.area.lumbridge.losttribe.TreatySigning
import org.rsmod.content.quest.area.lumbridge.losttribe.lostTribeBroochReturned
import org.rsmod.content.quest.area.lumbridge.losttribe.lostTribeMistagDenied
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mistag, foreman of the Dorgeshuun mines. Before first contact he panics at the sight of a
 * surface-dweller; the goblin bow (unlocked by the generals) calms him, the goblin salute does not.
 * He later receives the Duke's treaty, which plays [TreatySigning], buys iron and silver ore, trades
 * his lost brooch for a mining helmet and guides players back to Kazgar by the castle cellar.
 */
class Mistag
@Inject
constructor(
    private val lostTribe: LostTribeQuest,
    private val signing: TreatySigning,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val launcher: ProtectedAccessLauncher,
    private val dttd: DeathToTheDorgeshuunQuest,
    private val zanik: ZanikFollower,
) : PluginScript() {

    private val mistagIds by lazy { MISTAG_TYPES.map { it.asRSCM(RSCMType.NPC) }.toSet() }
    private val bowSeqs by lazy { BOW_SEQS.map { it.asRSCM(RSCMType.SEQ) }.toSet() }
    private val saluteSeqs by lazy { SALUTE_SEQS.map { it.asRSCM(RSCMType.SEQ) }.toSet() }

    override fun ScriptContext.startup() {
        onOpNpc1(MISTAG) { talk(it.npc) }
        onOpNpc3(MISTAG) { lostTribe.run { guideThroughTunnels("Mistag", KAZGAR_ARRIVAL) } }
        onOpNpc4(MISTAG) { lostTribe.run { guideThroughTunnels("Mistag", WaterMill.MILLSIDE_LANDING) } }
        val mistag = ServerCacheManager.getNpc(MISTAG.asRSCM(RSCMType.NPC)) ?: error("Missing $MISTAG")
        val brooch = ServerCacheManager.getItem(BROOCH.asRSCM(RSCMType.OBJ)) ?: error("Missing $BROOCH")
        onOpNpcU(mistag, brooch) { startDialogue(it.npc) { returnBrooch() } }
        onEvent<PlayEmote> { emotePlayed(player, seq.id) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val stage = lostTribe.stage(player)
        if (stage == STAGE_TREATY && TREATY in player.inv) {
            deliverTreaty(npc)
            return
        }
        startDialogue(npc) { mistag() }
    }

    private suspend fun Dialogue.mistag() {
        val stage = lostTribe.stage(player)
        if (stage < STAGE_CONTACT) {
            panic()
            return
        }
        val complete = lostTribe.isComplete(player)
        val offerFavour = complete && dttd.stage(player) == 0 && dttd.canStart(player)
        when {
            offerFavour ->
                chatNpc(
                    happy,
                    "It is good to see you again! The Dorgeshuun Council would like to ask a favour of you, if you " +
                        "are interested?",
                )
            zanik.isFollowing(player) -> {
                chatNpc(
                    happy,
                    "Hello Zanik, hello ${player.displayName}. How is your exploration of the surface going?",
                )
                chatNpcSpecific(
                    "Zanik",
                    DeathToTheDorgeshuunQuest.ZANIK_CHATHEAD,
                    angry,
                    "I've hardly SEEN the surface yet! ${player.displayName} just dragged me back down here!",
                )
            }
            else -> chatNpc(quiz, "Hello, friend?")
        }
        val options = buildList {
            if (offerFavour) {
                add("What is this favour?" to Topic.Favour)
            }
            if (complete) {
                add("Can I sell you some ore?" to Topic.SellOre)
            } else if (stage == STAGE_SILVERWARE_MISSING) {
                add("Some silverware has gone missing from the castle cellar..." to Topic.Innocent)
                add("Where is the silverware you stole from the castle cellar?" to Topic.Accuse)
            } else {
                add("May I mine the rocks here?" to Topic.Mine)
            }
            add("Why do the Dorgeshuun live underground?" to Topic.Underground)
            add("What happened to your arm?" to Topic.Arm)
            add("Can you show me the way out of the mines?" to Topic.WayOut)
        }
        when (menu(options)) {
            Topic.Favour -> favour()
            Topic.SellOre -> sellOre()
            Topic.Innocent -> {
                chatPlayer(worried, "Some silverware has gone missing from the castle, and the Duke thinks you stole it!")
                chatNpc(shocked, "Oh no! We would never do such a thing!")
                chatNpc(sad, "I assure you we took nothing from the cellar. You must convince the Duke we are innocent!")
            }
            Topic.Accuse -> accuse()
            Topic.Mine -> {
                chatPlayer(quiz, "May I mine the rocks here?")
                chatNpc(
                    neutral,
                    "No offence, but we will not let you mine here until there is peace between the humans " +
                        "and the Dorgeshuun.",
                )
                player.lostTribeMistagDenied = true
            }
            Topic.Underground -> underground()
            Topic.Arm -> {
                chatPlayer(quiz, "What happened to your arm?")
                chatNpc(sad, "I lost it in a mining accident a few years ago.")
                chatNpc(
                    neutral,
                    "This area is very unstable. That's why we put markers on the wall showing the safest path.",
                )
            }
            Topic.WayOut -> {
                chatPlayer(quiz, "Can you show me the way out of the mines?")
                if (complete) {
                    chatNpc(happy, "Certainly. Come back soon!")
                } else {
                    chatNpc(worried, "Certainly. Please hurry, you must avert the war!")
                }
                val dest = if (stage >= STAGE_SILVERWARE_MISSING) KAZGAR_ARRIVAL else CELLAR_ARRIVAL
                lostTribe.run { access.guideThroughTunnels("Mistag", dest) }
            }
        }
    }

    private suspend fun Dialogue.panic() {
        chatNpc(shocked, "Who...who are you? How did you get in here?")
        chatNpc(shocked, "Help! A surface-dweller this deep in our mines? We will all be destroyed!")
    }

    private suspend fun Dialogue.accuse() {
        chatPlayer(angry, "Where's the silverware you stole from the castle cellar?")
        chatNpc(shocked, "What? We didn't take anything!")
        chatPlayer(
            angry,
            "You expect me to believe that? Perhaps Sigmund was right: you're just thieving little goblins!",
        )
        chatNpc(
            confused,
            "Why would we want to steal silver? As you see we have much silver already in our mines!",
        )
        chatPlayer(neutral, "Well, the Duke says he will attack you unless the silverware is returned.")
        chatNpc(
            sad,
            "The last thing we want is war! If we had anything of yours, we would return it at once. But we don't!",
        )
    }

    private suspend fun Dialogue.underground() {
        chatPlayer(quiz, "Why do the Dorgeshuun live underground?")
        chatNpc(
            neutral,
            "Our ancient legends say that goblins were created by an evil god in order to fight in a huge " +
                "war. This god sent the Dorgeshuun tribe to fight a battle where all would die.",
        )
        chatNpc(
            neutral,
            "But our ancestors escaped by hiding in a deep hole in the ground where our god could not find " +
                "us. Eventually an earthquake sealed the hole and they were forever safe.",
        )
        when (
            choice2(
                "The war is over now, so you can return to the surface.",
                1,
                "What was the name of your god?",
                2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "The war is over now, so you can return to the surface.")
                chatNpc(
                    worried,
                    "Even if the gods are no longer at war, I find it hard to believe that a tribe of peaceful " +
                        "goblins would be safe above. I have heard of humans coming down into the caves to " +
                        "slaughter our people!",
                )
                chatNpc(
                    neutral,
                    "But even if we could return, we would not want to. We are adapted to living underground, " +
                        "and we have built a home for ourselves that we do not want to leave.",
                )
            }
            2 -> {
                chatPlayer(quiz, "What was the name of your god?")
                chatNpc(
                    neutral,
                    "Our ancestors did not speak his name, thinking that to do so would attract his attention. " +
                        "His name has been unspoken for so long that it is now entirely forgotten.",
                )
                chatNpc(
                    neutral,
                    "That is for the best. We have survived perfectly well without gods, and we do not want a " +
                        "return to the old ways.",
                )
            }
        }
    }

    private suspend fun Dialogue.sellOre() {
        chatPlayer(quiz, "Can I sell you some ore?")
        val iron = player.inv.count(IRON_ORE)
        val silver = player.inv.count(SILVER_ORE)
        if (iron == 0 && silver == 0) {
            chatNpc(
                happy,
                "Certainly. I will buy any iron or silver ore you mine. Speak to me again when you have some.",
            )
            return
        }
        val price = iron * IRON_PRICE_TENTHS / 10 + silver * SILVER_PRICE
        chatNpc(happy, "I will give you $price gold pieces for the ore you are carrying.")
        if (choice2("Okay", true, "No", false)) {
            chatPlayer(happy, "Okay.")
            val sold =
                (iron == 0 || access.invDel(access.inv, IRON_ORE, iron).success) &&
                    (silver == 0 || access.invDel(access.inv, SILVER_ORE, silver).success)
            if (sold) {
                access.invAddOrDrop(objRepo, COINS, price)
            }
        } else {
            chatPlayer(neutral, "No.")
        }
    }

    private suspend fun Dialogue.returnBrooch() {
        chatPlayer(quiz, "Is this your brooch?")
        if (!lostTribe.isComplete(player)) {
            chatNpc(
                worried,
                "We have more important things to think about than a brooch right now. We must prevent a war!",
            )
            return
        }
        if (player.lostTribeBroochReturned) {
            chatNpc(neutral, "You returned my brooch earlier.")
            return
        }
        if (access.invDel(access.inv, BROOCH).failure) {
            return
        }
        player.lostTribeBroochReturned = true
        chatNpc(happy, "Yes! I thought I'd lost it. Thank you.")
        chatNpc(happy, "Have one of these helmets. It will be useful if you want to work in the mine.")
        access.invAddOrDrop(objRepo, MINING_HELMET)
        objbox(MINING_HELMET, "Mistag gives you a mining helmet.")
    }

    private suspend fun ProtectedAccess.deliverTreaty(npc: Npc) {
        startDialogue(npc) {
            chatNpc(quiz, "Hello, friend?")
            chatPlayer(happy, "I have a peace treaty from the Duke of Lumbridge.")
            chatNpc(shocked, "A peace treaty? Then you will not invade?")
            chatPlayer(
                neutral,
                "No. As long as you stick to the terms of this treaty there will be no conflict. The Duke of " +
                    "Lumbridge wants to meet your ruler to sign it.",
            )
            chatNpc(happy, "I will summon Ur-tag, our headman, at once.")
        }
        if (invDel(inv, TREATY).failure) {
            return
        }
        with(signing) { play() }
        lostTribe.complete(this)
    }

    /** Mistag reacts to the goblin greetings performed in front of him. */
    private fun emotePlayed(player: Player, seq: Int) {
        val bow = seq in bowSeqs
        val salute = seq in saluteSeqs
        if (!bow && !salute) {
            return
        }
        val mistag = nearbyMistag(player) ?: return
        val stage = lostTribe.stage(player)
        when {
            bow && stage == STAGE_EMOTES_LEARNT -> launcher.launch(player) { firstContact(mistag) }
            bow && stage >= STAGE_CONTACT -> mistag.anim(MISTAG_BOW_SEQ)
            salute && stage < STAGE_CONTACT -> launcher.launch(player) { warDance(mistag) }
        }
    }

    private suspend fun ProtectedAccess.firstContact(npc: Npc) {
        delay(BOW_TICKS)
        npc.anim(MISTAG_BOW_SEQ)
        startDialogue(npc) {
            chatNpc(shocked, "A human knows ancient greeting?")
            chatNpc(happy, "Perhaps you are friend after all!")
            chatNpc(happy, "Greetings, friend. I am sorry I panicked when I saw you.")
            chatNpc(
                sad,
                "Our legends tell of the surface as a place of horror and violence, where the gods forced us " +
                    "to fight in terrible battles.",
            )
            chatNpc(
                worried,
                "When I saw a surface-dweller appear I was afraid it was a return to the old days!",
            )
            chatPlayer(quiz, "Did you break in to the castle cellar?")
            chatNpc(
                sad,
                "It was an accident. We were following a seam of iron and suddenly we found ourselves in a room!",
            )
            chatNpc(
                sad,
                "We blocked up our tunnel behind us and ran back here. Then we did what cave goblins always do " +
                    "when there is a problem: we hid and hoped it would go away.",
            )
            chatNpc(
                worried,
                "We meant no harm! Please tell the ruler of the above- people that we want to make peace.",
            )
            lostTribe.advanceTo(access, STAGE_CONTACT)
        }
    }

    private suspend fun ProtectedAccess.warDance(npc: Npc) {
        delay(BOW_TICKS)
        startDialogue(npc) { chatNpc(shocked, "Eeek! The war-dance!") }
    }

    private fun nearbyMistag(player: Player): Npc? =
        npcRepo.findAll(ZoneKey.from(player.coords), 1).firstOrNull {
            it.type.id in mistagIds && it.coords.chebyshevDistance(player.coords) <= GREET_RANGE
        }

    private suspend fun Dialogue.favour() {
        chatPlayer(quiz, "What is this favour?")
        chatNpc(
            neutral,
            "Surface-dwellers have been visiting the mines for some time now, but no Dorgeshuun has yet visited " +
                "the surface.",
        )
        chatNpc(
            worried,
            "We are curious about the surface, and we are also worried about the dangers it poses. We know that " +
                "Lumbridge is friendly but we are worried that the HAM group may be plotting against us.",
        )
        chatNpc(neutral, "We are planning to send an agent to the surface and we would like you to act as a guide.")
        if (!choice2("Yes.", true, "No.", false, title = "Start the Death to the Dorgeshuun quest?")) {
            chatPlayer(neutral, "I'm too busy.")
            chatNpc(
                sad,
                "Oh dear! Our agent is ready, but the Council will not let an expedition go ahead without a guide. " +
                    "If you reconsider, please let me know!",
            )
            return
        }
        chatPlayer(happy, "I'll act as a guide.")
        dttd.advanceTo(access, DeathToTheDorgeshuunQuest.STAGE_STARTED)
        zanik.returnToCellarIfDue(player)
        chatNpc(happy, "Thank you!")
        chatNpc(
            neutral,
            "In order to get into the HAM base undetected you will both need to go in disguise. You should get two " +
                "full sets of HAM robes. Once you have them, our agent will meet you in the cellar of Lumbridge castle.",
        )
    }

    private enum class Topic {
        Favour,
        SellOre,
        Innocent,
        Accuse,
        Mine,
        Underground,
        Arm,
        WayOut,
    }

    private companion object {
        const val IRON_ORE = "obj.iron_ore"
        const val SILVER_ORE = "obj.silver_ore"
        const val COINS = "obj.coins"

        /** 80% of the ores' store values: 13.6 coins for iron (in tenths) and 60 for silver. */
        const val IRON_PRICE_TENTHS = 136
        const val SILVER_PRICE = 60

        const val MISTAG_BOW_SEQ = "seq.cave_goblin_bow"
        const val GREET_RANGE = 6
        const val BOW_TICKS = 2

        val MISTAG_TYPES =
            listOf(
                MISTAG,
                "npc.lost_tribe_mistag_1op",
                "npc.lost_tribe_mistag_2ops",
                "npc.lost_tribe_mistag_3ops",
            )
        val BOW_SEQS = listOf("seq.human_cave_goblin_bow", "seq.human_cave_goblin_bow_loop")
        val SALUTE_SEQS = listOf("seq.human_cave_goblin_dance", "seq.human_cave_goblin_dance_loop")
    }
}
