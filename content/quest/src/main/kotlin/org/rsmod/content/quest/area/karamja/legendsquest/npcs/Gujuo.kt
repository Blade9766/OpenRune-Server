package org.rsmod.content.quest.area.karamja.legendsquest.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsCoords
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWLS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BULLROARER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GILDED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWLS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GOLD_BOWL_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.SKETCH
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ACCEPTED_RESCUE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ASKED_HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FINAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FIRE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ENTERED_LOWER_DUNGEON
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_FOUND_ENTRANCE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GERMINATED_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GOT_BULLROARER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GOT_GILDED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_HEART_IN_RECESS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_POOL_DRIED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_REPLACED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RETURNED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SPOKE_UNGADULU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SUMMONED_NEZIKCHENED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SWUNG_BULLROARER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_TALKED_GUJUO_POOL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS_GERMINATED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsSupport
import org.rsmod.content.quest.area.karamja.legendsquest.legendsAskedWhere
import org.rsmod.content.quest.area.karamja.legendsquest.legendsAskedWho
import org.rsmod.content.quest.area.karamja.legendsquest.legendsCalledVacu
import org.rsmod.content.quest.area.karamja.legendsquest.legendsEnteredCavern
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gujuo of the Kharazi tribe. He is not spawned in the jungle: swinging the jungle forester's
 * bullroarer anywhere inside it draws him out of the trees to the player, and he disappears again
 * when the conversation ends. He sets the player the rescue of Ungadulu, tells them where the
 * pure water is and how to reach its source, blesses their golden bowl and finally hands over the
 * tribe's gilded totem pole.
 */
@Singleton
class Gujuo
@Inject
constructor(
    private val legends: LegendsQuest,
    private val support: LegendsSupport,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val mapClock: MapClock,
) : PluginScript() {

    private val swingCooldowns = HashMap<PlayerUid, Int>()

    override fun ScriptContext.startup() {
        onOpHeld1(BULLROARER) { swingBullroarer() }
        onOpNpc1(GUJUO) {
            startDialogue(it.npc) {
                chatPlayer(neutral, "Hello there.")
                greet()
            }
        }
        onOpNpcU(GUJUO) { startDialogue(it.npc) { shown(it.objType.internalName) } }
    }

    private suspend fun ProtectedAccess.swingBullroarer() {
        val readyAt = swingCooldowns[player.uid] ?: 0
        if (readyAt > this@Gujuo.mapClock.cycle) {
            mes("You're a bit too busy to do that at the moment.")
            return
        }
        swingCooldowns[player.uid] = this@Gujuo.mapClock.cycle + SWING_COOLDOWN
        mes("You start to swing the bullroarer above your head.")
        mes("You feel a bit silly at first, but soon it makes an interesting sound.")
        anim(SWING_SEQ)
        spotanim(SWING_SPOTANIM, height = 92)
        soundSynth(BULLROARER_SOUND)
        delay(3)
        for (npc in support.findNpcs(coords, NOTICE_RADIUS)) {
            if (npc.type.isType(FORESTER_MALE) || npc.type.isType(FORESTER_FEMALE)) {
                mes("Nothing much seems to happen, though.")
                startDialogue(npc) {
                    chatNpc(happy, "You might like to use that when you get into the Kharazi jungle, it might attract more natives...")
                }
                return
            }
            if (JUNGLE_BEASTS.any { npc.type.isType(it) } && random.of(3) == 0) {
                mes("A nearby ${npc.type.name} takes a dislike to the noise you're making.")
                npc.opPlayer2(player, aiInteractions)
                return
            }
        }
        if (!LegendsCoords.inKharazi(coords) || !legends.isStarted(player)) {
            mes("Nothing much seems to happen though.")
            return
        }
        legends.advanceFrom(this, STAGE_GOT_BULLROARER, STAGE_SWUNG_BULLROARER)
        mes("You see some movement in the trees...")
        delay(1)
        mes("...and a tall, dark, charismatic looking native approaches you.")
        val gujuo = support.findNpc(coords, GUJUO, SUMMON_RADIUS) ?: summon(coords) ?: return
        if (!lineOfWalk(gujuo.coords, coords)) {
            mes("It looks like Gujuo cannot get to you from here.")
            return
        }
        gujuo.facePlayer(player)
        val uid = player.uid
        gujuo.walk(CoordGrid(coords.x + 1, coords.z, coords.level)) {
            support.launchWhenFree(uid) {
                if (!gujuo.isSlotAssigned) {
                    return@launchWhenFree
                }
                mes("Gujuo approaches.")
                startDialogue(gujuo) { greet() }
            }
        }
    }

    private fun ProtectedAccess.summon(near: CoordGrid): Npc? {
        val type = ServerCacheManager.getNpc(GUJUO.asRSCM(RSCMType.NPC)) ?: return null
        val spot = mapFindSquareLineOfWalk(near, 1, SPAWN_RADIUS) ?: return null
        val gujuo = Npc(type, spot)
        npcRepo.add(gujuo, SPAWN_DURATION)
        return gujuo
    }

    /** Gujuo stepping out of the jungle towards a player, as he does for the replaced totem pole. */
    fun ProtectedAccess.approach() {
        val gujuo = summon(coords) ?: return
        gujuo.facePlayer(player)
        val uid = player.uid
        gujuo.walk(CoordGrid(coords.x + 1, coords.z, coords.level)) {
            support.launchWhenFree(uid) {
                if (gujuo.isSlotAssigned) {
                    mes("Gujuo approaches.")
                    startDialogue(gujuo) { greet() }
                }
            }
        }
    }

    private suspend fun Dialogue.leave() {
        when (access.random.of(6)) {
            0 -> chatNpc(neutral, "I must go and hunt now Bwana...")
            1 -> chatNpc(neutral, "I am tired Bwana, I must go and rest...")
            2 -> chatNpc(neutral, "The mosquitos bite me bwana! I must go for a swim...")
            3 -> chatNpc(neutral, "I must visit my people now...")
            4 -> chatNpc(neutral, "I have work to do Bwana, I may see you again...")
            else -> chatNpc(neutral, "I have to collect herbs now Bwana...")
        }
        vanish()
    }

    private fun Dialogue.vanish() {
        val gujuo = npc ?: return
        if (gujuo.isSlotAssigned) {
            npcRepo.del(gujuo, Int.MAX_VALUE)
        }
        access.mes("Gujuo disappears into the Kharazi Jungle as swiftly as he appeared...")
    }

    private suspend fun Dialogue.shown(obj: String) {
        if (obj !in GOLD_BOWLS) {
            chatNpc(neutral, "Sorry, but I don't need that Bwana.")
            return
        }
        if (legends.stage(player) < STAGE_ASKED_HOLY_WATER) {
            access.mes("Nothing interesting happens.")
            return
        }
        chatNpc(neutral, "Aha Bwana, well done, you have made the golden bowl. Would you like me to show you how to bless it.")
        if (choice2("Yes, I'd like to bless my gold bowl.", true, "No thanks, I'll wait.", false)) {
            chatPlayer(neutral, "Yes, I'd like to bless my gold bowl.")
            greet()
        } else {
            chatPlayer(neutral, "No thanks, I'll wait.")
            chatNpc(neutral, "Very well, let me know when you want to try.")
            howGoesUngadulu()
        }
    }

    private suspend fun Dialogue.greet() {
        val stage = legends.stage(player)
        if (stage >= STAGE_ASKED_HOLY_WATER && GOLD_BOWLS.any { access.inv.count(it) > 0 }) {
            chatNpc(neutral, "Greetings Bwana. Ah I see you have the golden bowl! Would you like me to show you how to bless it?")
            if (choice2("Yes, I'd like you to bless my gold bowl.", true, "No thanks, I need help with something else.", false)) {
                chatPlayer(happy, "Yes, I'd like you to bless my gold bowl.")
                blessBowl()
                return
            }
        }
        when {
            stage in STAGE_GOT_BULLROARER..STAGE_SWUNG_BULLROARER || stage < STAGE_GOT_BULLROARER -> {
                chatNpc(neutral, "Greetings Bwana... Why do you make such strange sounds and disturb the peace of the jungle?")
                if (choice2("I was hoping to attract the attention of a native.", true, "Sorry, it was a mistake?", false)) {
                    chatPlayer(neutral, "I was hoping to attract the attention of a native.")
                    chatNpc(neutral, "Well, it had the desired effect... I am Gujuo, proud member of the Kharazi tribe. What did you want to talk about Bwana?")
                } else {
                    chatPlayer(neutral, "Sorry, it was a mistake?")
                    chatNpc(neutral, "Very good Bwana... however, it begs the question... What are you doing in the Kharazi Jungle?")
                }
                friendlyOrLost()
            }
            stage in STAGE_ACCEPTED_RESCUE until STAGE_DEFEATED_NEZIKCHENED_FIRE -> howGoesUngadulu()
            stage == STAGE_DEFEATED_NEZIKCHENED_FIRE -> {
                chatNpc(neutral, "How goes your quest to release Ungadulu?")
                seedsMenu()
            }
            stage == STAGE_GERMINATED_SEEDS -> {
                chatNpc(neutral, "Congratulations on releasing Ungadulu! My people are very pleased... How goes the growing of the Yommi tree?")
                germinatedMenu()
            }
            stage == STAGE_POOL_DRIED -> {
                chatNpc(neutral, "I have visited Ungadulu in the caves, he is hard at work studying. He looks well! Have you grown the Yommi tree yet?")
                if (choice2("The water pool has dried up and I need more water.", true, "The Yommi tree died.", false)) {
                    poolDried()
                } else {
                    chatPlayer(neutral, "The Yommi tree died.")
                    chatNpc(neutral, "Well, it requires pure sacred water for it to grow. It is a very special tree...")
                    if (choice2("The sacred water pool has dried up and I need more water.", true, "Does the Yommi tree have to have pure water?", false)) {
                        poolDried()
                    } else {
                        mustHavePure()
                    }
                }
            }
            stage in STAGE_TALKED_GUJUO_POOL until STAGE_DEFEATED_NEZIKCHENED_WATER -> {
                chatNpc(neutral, "I have visited Ungadulu in the caves, he is hard at work studying. He looks well! How is your quest going Bwana?")
                if (stage == STAGE_TALKED_GUJUO_POOL) {
                    sourceMenu(foundCaves = false)
                } else if (stage in STAGE_ENTERED_LOWER_DUNGEON..STAGE_HEART_IN_RECESS) {
                    sourceMenu(foundCaves = true)
                } else {
                    thanks()
                }
            }
            stage in STAGE_DEFEATED_NEZIKCHENED_WATER until STAGE_DEFEATED_NEZIKCHENED_FINAL -> {
                chatNpc(happy, "Hello Bwana, I'm very pleased to see you again. Things seem much happier now in the Kharazi Jungle. I suspect that it is down to your good doings!")
                totemMenu()
            }
            stage == STAGE_DEFEATED_NEZIKCHENED_FINAL -> {
                chatNpc(happy, "Hello Bwana, I'm very pleased to see you again. Things seem much happier now in the Kharazi Jungle. I suspect that it is down to your good doings!")
                totemMenu()
            }
            stage == STAGE_REPLACED_TOTEM -> giveGift()
            stage == STAGE_GOT_GILDED_TOTEM && !legends.owns(access, GILDED_TOTEM) -> {
                chatNpc(neutral, "Good day Bwana, it's always good to see you.")
                friendMenu(lostGift = true)
            }
            stage == STAGE_GOT_GILDED_TOTEM -> {
                chatNpc(neutral, "Good day Bwana. The Kharazi Jungle is especially beautiful today isn't it? My village people pass on their thanks to you.")
                friendMenu(lostGift = false)
            }
            stage >= STAGE_RETURNED -> {
                chatNpc(neutral, "Good day Bwana. The jungle is especially beautiful today isn't it? My village people pass on their thanks to you.")
                friendMenu(lostGift = false)
            }
        }
    }

    private suspend fun Dialogue.giveGift() {
        chatNpc(
            neutral,
            "Greetings Bwana, we saw your fight with the Demon from some distance away. My people " +
                "are so pleased with your heroic efforts. Your strength and ability as a warrior are Legendary.",
        )
        objbox(GILDED_TOTEM, "Gujuo offers you an awe inspiring totem pole.")
        chatNpc(neutral, "Please accept this as a token of our appreciation.")
        access.invAddOrDrop(objRepo, GILDED_TOTEM)
        legends.setStage(access, STAGE_GOT_GILDED_TOTEM)
        chatNpc(neutral, "Please, now consider yourself a friend of my people. And visit us anytime.")
        if (takeAll(YOMMI_SEEDS_GERMINATED)) {
            chatNpc(neutral, "I'll take those germinated Yommi tree seeds to Ungadulu, I'm sure he'll appreciate them.")
        }
        if (takeAll(YOMMI_SEEDS)) {
            chatNpc(neutral, "I'll take those Yommi tree seeds to Ungadulu, I'm sure he'll appreciate them.")
        }
        leave()
    }

    private fun Dialogue.takeAll(obj: String): Boolean {
        var taken = false
        for (container in listOf(access.inv, access.bank)) {
            val count = container.count(obj)
            if (count > 0) {
                access.invDel(container, obj, count)
                taken = true
            }
        }
        return taken
    }

    private suspend fun Dialogue.friendMenu(lostGift: Boolean) {
        while (true) {
            val choice =
                if (lostGift) {
                    choice4(
                        "Do you have any news?", NEWS,
                        "Where are all your people?", PEOPLE,
                        "I've lost the tribal gift you gave me.", LOST_GIFT,
                        "Ok thanks for your help.", THANKS,
                    )
                } else {
                    choice3("Do you have any news?", NEWS, "Where are all your people?", PEOPLE, "Ok thanks for your help.", THANKS)
                }
            when (choice) {
                NEWS -> {
                    chatPlayer(neutral, "Do you have any news?")
                    chatNpc(neutral, "Just that everything is fine in the jungle with us. And that we are grateful to you for your help.")
                }
                PEOPLE -> {
                    chatPlayer(neutral, "Where are all your people?")
                    chatNpc(neutral, "My people are all happy living in the jungle. They are still afraid of strangers and will not approach but they are around, none the less.")
                    chatNpc(neutral, "Your story has been woven into the fabric of our society. And we all sing your many praises Bwana.")
                }
                LOST_GIFT -> {
                    chatPlayer(neutral, "I've lost the tribal gift you gave me.")
                    chatNpc(neutral, "Well, that wasn't very nice of you. It took us a long time to make that Totem pole. Luckily, I made another one at the same time.")
                    if (!legends.owns(access, GILDED_TOTEM)) {
                        access.invAddOrDrop(objRepo, GILDED_TOTEM)
                        objbox(GILDED_TOTEM, "Gujuo hands over another totem pole.")
                    }
                    return
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.totemMenu() {
        while (true) {
            val hasTotem = access.inv.count(YOMMI_TOTEM) > 0
            val choice =
                if (hasTotem) {
                    choice4(
                        "I made the Totem pole, now what do I do with it?", MADE_TOTEM,
                        "I found the source of the spring and I got the water.", FOUND_SOURCE,
                        "I killed the demon again.", KILLED_DEMON,
                        "Ok thanks for your help.", THANKS,
                    )
                } else {
                    choice4(
                        "I found the source of the spring and I got the water.", FOUND_SOURCE,
                        "I killed the demon again.", KILLED_DEMON,
                        "How do I make the totem pole?", HOW_TOTEM,
                        "Ok thanks for your help.", THANKS,
                    )
                }
            when (choice) {
                MADE_TOTEM -> {
                    chatPlayer(neutral, "I made the Totem pole, now what do I do with it?")
                    chatNpc(neutral, "Well Bwana, it's a very interesting piece of work, not bad at all. Now we need to replace an existing corrupted totem pole with your newly constructed one. You should find those totems within the Kharazi Jungle.")
                }
                FOUND_SOURCE -> {
                    chatPlayer(neutral, "I found the source of the spring and I got the water.")
                    chatNpc(neutral, "Great Bwana, you are truly a brave warrior. Now you can try to grow the Yommi tree in earnest and make the totem pole.")
                }
                KILLED_DEMON -> {
                    chatPlayer(neutral, "I killed the demon again.")
                    chatNpc(neutral, "You are indeed very brave Bwana, we have noticed a difference in the Kharazi Jungle, the trees seem to sing again. And we have you to thank for it.")
                }
                HOW_TOTEM -> {
                    chatPlayer(neutral, "How do I make the totem pole?")
                    chatNpc(neutral, "You will need to grow the Yommi tree to full height. And then, before it rots, you must chop it down. Once you have felled the tree, you need to trim the branches.")
                    chatNpc(neutral, "And finally, you need to craft the totem pole out of the trunk. You'll need a very sharp, very tough axe to do all this. But once you have completed the totem pole,")
                    chatNpc(neutral, "You will need to use it to replace a totem pole that already exists, as they're all placed on sacred areas to my people.")
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.seedsMenu() {
        while (true) {
            when (
                choice3(
                    "Ungadulu is free, he was possessed by a demon and I killed it.", 1,
                    "I have the Yommi tree seeds.", 2,
                    "What do I do now?", 3,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "Ungadulu is free! He was possessed by a demon and I killed it.")
                    chatNpc(neutral, "You are indeed brave Bwana, a truly fearsome warrior to take on such an enemy! Well Done!")
                }
                2 -> {
                    chatPlayer(neutral, "I have the Yommi tree seeds.")
                    if (access.inv.count(YOMMI_SEEDS) == 0) {
                        chatNpc(neutral, "Hmmm, well I don't see them. You'd better go and ask Ungadulu for some more.")
                        return
                    }
                    chatNpc(neutral, "That's great, Bwana. Now you just need to germinate the seeds and then plant them in some fertile soil. I'm sure that Ungadulu has explained all this to you already.")
                }
                else -> {
                    chatPlayer(neutral, "What do I do now?")
                    chatNpc(neutral, "If you have the Yommi tree seeds, you will need to germinate them.")
                    chatNpc(neutral, "Place the seeds into pure water, and they will begin to sprout tiny shoots. You can then plant them in fertile soil.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.germinatedMenu() {
        while (true) {
            when (
                choice3(
                    "I have germinated the Yommi tree seeds.", 1,
                    "Where is the fertile soil?", 2,
                    "Ok thanks for your help.", 3,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "I have germinated the Yommi tree seeds.")
                    chatNpc(neutral, "Well done Bwana. With the blessings of the gods we will soon have our Totem Pole. Bwana, you now need to plant the seed in the fertile earth.")
                }
                2 -> {
                    chatPlayer(neutral, "Where is the fertile soil?")
                    chatNpc(neutral, "You should be able to find many places where the ground is fertile in the Kharazi Jungle. Planting the Yommi tree seeds in fertile soil gives it a good chance to grow.")
                    chatNpc(neutral, "My people are trying to grow the Yommi tree as well. But so far we have not met with any success. If you find a rotten tree or what looks like a rotten totem pole,")
                    chatNpc(neutral, "You'll need to remove it yourself to get to the fertile soil. It will take a very sharp, robust axe to do it.")
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.poolDried() {
        chatPlayer(neutral, "The water pool has dried up and I need more pure water.")
        chatNpc(neutral, "This is indeed a bad omen Bwana, that pool is sacred to us. I have seen it and it is full of filth, it is not natural. I suspect that some evil is at work here.")
        if (choice2("Does the Yommi tree have to have pure water?", true, "Where is the source of the spring of pure water?", false)) {
            mustHavePure()
        } else {
            source()
        }
    }

    private suspend fun Dialogue.mustHavePure() {
        chatPlayer(neutral, "Does the Yommi tree have to have pure water?")
        chatNpc(neutral, "Yes, it is a magical tree and can only survive on the water from the sacred pool. This is indeed a tragedy.")
        if (choice2("Where is the source of the spring of pure water?", true, "Ok thanks for your help.", false)) {
            source()
        } else {
            thanks()
        }
    }

    private suspend fun Dialogue.sourceMenu(foundCaves: Boolean) {
        val first = if (foundCaves) "I have found a way into the caves!" else "Where can I get more water for the Yommi tree?"
        when (
            choice5(
                first, 1,
                "Where is the source of the spring of pure water?", 2,
                "I searched the catacombs thoroughly but found nothing else.", 3,
                "If I went in search of the source, could you help me?", 4,
                "Ok thanks for your help.", 5,
            )
        ) {
            1 -> if (foundCaves) foundCaves() else moreWater()
            2 -> source()
            3 -> searched()
            4 -> searchSource()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.foundCaves() {
        chatPlayer(neutral, "I have found a way into the caves!")
        chatNpc(neutral, "That's great Bwana, good luck with your quest and take care!")
        while (true) {
            when (
                choice4(
                    "Do you know anything more about the caves?", 1,
                    "Who is Viyeldi?", 2,
                    "Where is the source of the spring of pure water?", 3,
                    "Ok thanks for your help.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "Do you know anything more about the caves?")
                    chatNpc(neutral, "I am sorry to say that I don't Bwana. You will need to explore that area, but use your wits, and you may be lucky.")
                }
                2 -> {
                    chatPlayer(neutral, "Who is Viyeldi?")
                    mesbox("Gujuo scratches his head for a moment.")
                    chatNpc(neutral, "Well, I have heard that name before, perhaps from the eldars.")
                    chatNpc(neutral, "Ah, yes, I think that is the name of the wizard who first went in search of the source. Be wary of him Bwana, he may try to trick you.")
                }
                3 -> {
                    source()
                    return
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.moreWater() {
        chatPlayer(neutral, "Where can I get more water for the Yommi tree?")
        chatNpc(neutral, "If the pool of sacred water has dried up, there may be a way to get to the source of the spring. But it is said to be very, very dangerous.")
        when (
            choice3(
                "Where is the source of the spring of pure water?", 1,
                "If I went in search of the source, could you help me?", 2,
                "Ok thanks for your help.", 3,
            )
        ) {
            1 -> source()
            2 -> searchSource()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.source() {
        chatPlayer(neutral, "Where is the source of the spring of pure water?")
        mesbox("Gujuo looks very uncomfortable...")
        chatNpc(neutral, "I am not sure but I have heard that deeper in the catacombs where you found Ungadulu, deep underground there is a terrible place guarded by the spirits of the undead.")
        chatNpc(neutral, "Since they died trying to find the source of the stream, they are cursed to guard it for all eternity. The first to seek the source was said to be a high level sorcerer")
        chatNpc(neutral, "He created a powerful spell in the caves. Now, all those who venture near are overcome by a supernatural fear. With all my heart Bwana, I would never go near such a place.")
        when (
            choice3(
                "Ok, I won't go...", 1,
                "If I went, could you help me?", 2,
                "I searched the catacombs thoroughly but found nothing else.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Ok, I won't go...")
                chatNpc(neutral, "I understand Bwana, it would be a waste of a perfectly good life. We will try to defeat the evil spirits in other ways. But I am not sure how we will do that.")
                if (choice2("If I went, could you help me?", true, "Ok thanks for your help.", false)) helpMe() else thanks()
            }
            2 -> helpMe()
            else -> searched()
        }
    }

    private suspend fun Dialogue.searched() {
        chatPlayer(neutral, "I searched the catacombs thoroughly but found nothing else.")
        chatNpc(neutral, "Perhaps the location has been hidden or buried under rubble? These stories were told to me as a child by the village elders. They were probably meant to frighten us away from the caves.")
        chatNpc(neutral, "It could all just be a myth! Perhaps there is another way to get to the source of the stream? But I am not sure where it is.")
        when (choice3("Ok, I won't go...", 1, "Where is the source of the spring of pure water?", 2, "If I went, could you help me?", 3)) {
            1 -> {
                chatPlayer(neutral, "Ok, I won't go...")
                chatNpc(neutral, "I understand Bwana, it would be a waste of a perfectly good life. We will try to defeat the evil spirits in other ways. But I am not sure how we will do that.")
            }
            2 -> source()
            else -> helpMe()
        }
    }

    private suspend fun Dialogue.helpMe() {
        chatPlayer(neutral, "If I went, could you help me?")
        chatNpc(neutral, "Well, if you are sure you want to go, I will assist as much as I can. You will need the bravery of the jungle lion, if you are to go into that forbidden place. I can give you the recipe for a potion to help with that.")
        chatNpc(neutral, "You will need to find two herbs, Snake weed and Ardrigal. Add them both to a vial of water, and you will walk with the bravery of the Kharazi lion.")
        legends.advanceFrom(access, STAGE_POOL_DRIED, STAGE_TALKED_GUJUO_POOL)
        potionMenu()
    }

    private suspend fun Dialogue.searchSource() {
        chatPlayer(neutral, "If I went in search of the source, could you help me?")
        chatNpc(neutral, "Well, if you are sure you want to go, I will assist as much as I can. You will need the bravery of the jungle lion, if you are to go into that forbidden place.")
        chatNpc(neutral, "I can give you the recipe for a potion to help with that. You will need to find two herbs, Snake weed and Ardrigal. Add them both to a vial of water, and you will walk with the bravery of the lion.")
        legends.advanceFrom(access, STAGE_POOL_DRIED, STAGE_TALKED_GUJUO_POOL)
        potionMenu()
    }

    private suspend fun Dialogue.potionMenu() {
        while (true) {
            when (
                choice5(
                    "Where can I find Snake weed?", 1,
                    "Where can I find Ardrigal?", 2,
                    "Where is the source of the spring of pure water?", 3,
                    "Will I need this potion? I feel brave enough as I am.", 4,
                    "Ok thanks for your help.", 5,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "Where can I find Snake weed?")
                    chatNpc(neutral, "Snake weed is usually found by swampy marshy areas. It is not very common and it may be quite difficult to find.")
                    chatNpc(neutral, "There is some marsh to the south of Tai Bwo Wannai village. The herb grows near jungle vines, so check all around very carefully.")
                }
                2 -> {
                    chatPlayer(neutral, "Where can I find Ardrigal?")
                    chatNpc(neutral, "Ardrigal is often found growing near to large groups of palms. Such a collection exists in the north.")
                    chatNpc(neutral, "If you head east out of Tai Bwo Wannai village you should come across them. The herb grows in the shade of the palm so check carefully.")
                }
                3 -> {
                    source()
                    return
                }
                4 -> {
                    chatPlayer(neutral, "Will I need this potion? I feel brave enough as I am.")
                    chatNpc(neutral, "I would urge you to take it, Bwana, I have heard that the caves are protected by a supernatural fear that renders even the bravest man to a trembling wreck.")
                    chatNpc(neutral, "You will need all your wits about you when dealing with the terrors that exist down there.")
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.friendlyOrLost() {
        if (choice2("I want to develop friendly relations with your people.", true, "I'm lost, can you show me the way out?", false)) {
            friendly()
        } else {
            lost()
        }
    }

    private suspend fun Dialogue.friendly() {
        chatPlayer(neutral, "I want to develop friendly relations with your people.")
        mesbox("Gujuo smiles and shakes your hand warmly.")
        chatNpc(neutral, "Very good Bwana... this is indeed a very pleasant gesture. However, my people are very distributed throughout the Kharazi Jungle.")
        if (!choice2("Can you get your people together?", true, "I'm lost, can you show me the way out?", false)) {
            lost()
            return
        }
        chatPlayer(neutral, "Can you get your people together?")
        chatNpc(neutral, "All of my people normally congregate around a totem pole. But ours has been polluted by an evil spirit. It has been transformed, and now our people are afraid to approach it.")
        chatNpc(neutral, "We tried to drive the evil spirit out of the totem pole, but it does not seem to work.")
        if (!choice2("What can we do instead then?", true, "I'm lost, can you show me the way out?", false)) {
            lost()
            return
        }
        chatPlayer(neutral, "What can we do instead then?")
        chatNpc(neutral, "We could try to make a new totem pole. However, we need to make it from the trunk of the sacred Yommi tree.")
        if (!choice2("How do we make the totem pole?", true, "I'm lost, can you show me the way out?", false)) {
            lost()
            return
        }
        chatPlayer(neutral, "How do we make a totem pole?")
        chatNpc(neutral, "First we need to plant a sacred Yommi tree. It is a magical tree of great power, however, our Shaman 'Ungadulu' is the only person with the seeds for this tree.")
        chatNpc(sad, "And I fear that it is impossible to get some seeds. He is being held against his will in some caves in the north western part of the Kharazi Jungle.")
        if (choice2("I will release Ungadulu...", true, "Oh well, sorry to hear about that.", false)) {
            releaseUngadulu()
            return
        }
        chatPlayer(neutral, "Oh well, sorry to hear about that.")
        mesbox("Gujuo's expression of sadness deepens...")
        chatNpc(sad, "Yes Bwana, perhaps we will become friends sometime in the future... But not today... Ungadulu has problably lost his mind anyway... It is most likely a lost cause...")
        when (choice3("I will release Ungadulu...", 1, "Ok thanks for your help.", 2, "What's in it for me if I release Ungadulu?", 3)) {
            1 -> releaseUngadulu()
            2 -> thanks()
            else -> {
                chatPlayer(neutral, "What's in it for me if I release Ungadulu?")
                chatNpc(neutral, "We would be very grateful and would be pleased to develop friendly relations with you. Do this thing for us all, my people would be very happy.")
                if (choice2("I will release Ungadulu...", true, "I'm lost, can you show me the way out?", false)) releaseUngadulu() else lost()
            }
        }
    }

    private suspend fun Dialogue.releaseUngadulu() {
        chatPlayer(neutral, "I will release Ungadulu...")
        chatNpc(neutral, "You make me very happy Bwana! In the north western part of the Kharazi Jungle area, near some great cliffs, you will find three rocks that form a roughly triangular shape.")
        chatNpc(neutral, "They are flanked by the palm which also forms the divine geometry. The stones are the entrance, search them well. That's where you'll find Ungadulu. If you can free him he will entrust you with some sacred Yommi tree seeds.")
        legends.raiseTo(access, STAGE_ACCEPTED_RESCUE)
        leave()
    }

    private suspend fun Dialogue.lost() {
        chatPlayer(neutral, "I'm lost, can you show me the way out?")
        chatNpc(neutral, "Yes Bwana. I can take you to the edge of the Kharazi Jungle. Would you like me to take you?")
        if (choice2("Yes please...", true, "No thanks...", false)) {
            chatPlayer(neutral, "Yes Please...")
            chatNpc(neutral, "Follow me...")
            mesbox("Gujuo takes you out of the jungle...")
            access.teleport(LegendsCoords.JUNGLE_EXIT)
            npc?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
            return
        }
        chatPlayer(neutral, "No thanks...")
        chatNpc(neutral, "As you wish... Again, Bwana, What is it that brings you to the Kharazi Jungle?")
        friendlyOrLost()
    }

    private suspend fun Dialogue.howGoesUngadulu() {
        chatNpc(neutral, "How goes your quest to release Ungadulu Bwana?")
        val stage = legends.stage(player)
        when (stage) {
            STAGE_ACCEPTED_RESCUE -> {
                if (choice2("I can't find the caves...", true, "Ok thanks for your help.", false)) {
                    chatPlayer(neutral, "I can't find the caves...")
                    chatNpc(neutral, "Well, they were still there the last time I checked... Go as far to the west in the Kharazi Jungle as you can, until you reach the sea, then walk eastwards with the barrier rocks to the north.")
                    chatNpc(neutral, "Walk past the covered pass and you'll find some rocks on slightly elevated ground. You'll find the caves there.")
                    leave()
                } else {
                    thanks()
                }
            }
            STAGE_FOUND_ENTRANCE -> {
                if (player.legendsEnteredCavern) {
                    if (choice2("Ungadulu is trapped in some magical flames!", true, "I'm not sure what to do.", false)) {
                        chatPlayer(neutral, "Ungadulu is trapped in some magical flames!")
                        chatNpc(neutral, "Well, maybe you can get his attention somehow? Did you examine the nature of the magical flames? From where did they come do you think?")
                        possessedMenu()
                    } else {
                        notSure()
                    }
                } else if (choice2("I've found the caves, but I don't know what to do.", true, "Ok thanks for your help.", false)) {
                    chatPlayer(neutral, "I've found the caves, but I don't know what to do.")
                    chatNpc(neutral, "Search the caves and try to talk to Ungadulu, there may be some clues to be had by searching all the items in the cave...")
                    leave()
                } else {
                    thanks()
                }
            }
            in STAGE_SPOKE_UNGADULU..STAGE_SUMMONED_NEZIKCHENED -> possessedMenu()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.possessedMenu() {
        while (true) {
            val choice =
                when {
                    player.legendsCalledVacu ->
                        choice5(
                            "Ungadulu looks a little strange.", STRANGE,
                            "I'm not sure what to do.", NOT_SURE,
                            "I need some pure water to douse some magic flames.", PURE_WATER,
                            "Ungadulu called me a 'Vacu'", VACU,
                            "Ok, thanks... Goodbye.", GOODBYE,
                        )
                    player.legendsAskedWhere ->
                        choice5(
                            "Ungadulu looks a little strange.", STRANGE,
                            "Sorry for bothering you.", SORRY,
                            "I'm not sure what to do.", NOT_SURE,
                            "I need some pure water to douse some magic flames.", PURE_WATER,
                            "Ok, thanks... Goodbye.", GOODBYE,
                        )
                    else ->
                        choice5(
                            "Ungadulu looks a little strange.", STRANGE,
                            "Sorry for bothering you.", SORRY,
                            "I'm not sure what to do.", NOT_SURE,
                            "Ungadulu mumbled something about 'pure' water?", MUMBLED,
                            "Ok, thanks... Goodbye.", GOODBYE,
                        )
                }
            when (choice) {
                STRANGE -> {
                    chatPlayer(neutral, "Ungadulu looks a little strange.")
                    chatNpc(neutral, "Be wary Bwana. There are many unknown spirits that reside in these dark areas. You may be tricked by an unknown force...")
                    if (choice2("What kind of unknown force?", true, "Ok, thanks... Goodbye.", false)) {
                        chatPlayer(neutral, "What kind of unknown force?")
                        chatNpc(neutral, "Strange spirits that our forefathers summoned for visions. They haunt the underworld and caves that exist in this area. Take not anything as it might first appear.")
                        if (choice2("How did they summon the spirits?", true, "Ok, thanks... Goodbye.", false)) {
                            chatPlayer(neutral, "How did they summon the spirits?")
                            chatNpc(neutral, "I am unlearned in such matters. But I am told of sacred patterns that are scored on the ground to bind the spirit and confine it... But that is all I know.")
                        } else {
                            goodbye()
                            return
                        }
                    } else {
                        goodbye()
                        return
                    }
                }
                SORRY -> {
                    chatPlayer(neutral, "Sorry for bothering you.")
                    chatNpc(neutral, "That's fine Bwana, not to worry. I hope your quest is going well in any case.")
                }
                NOT_SURE -> notSure()
                PURE_WATER -> {
                    pureWater()
                    return
                }
                VACU -> {
                    chatPlayer(neutral, "Ungadulu called me 'Vacu', what does that mean?")
                    chatNpc(neutral, "It seems that Ungadulu has started to lose his senses. In our native and ancient history, the 'Vacu' were the servants of the evil spirits from the underworld.")
                    chatNpc(neutral, "The 'Vacu' were priests who summoned the spirits of our ancestors. But the priests were enslaved by the evil spirits of the underworld. It is but a myth, perhaps a story told to scare badly behaved children.")
                }
                MUMBLED -> {
                    chatPlayer(neutral, "Ungadulu mumbled something about 'pure' water?")
                    chatNpc(neutral, "Hmm, that sounds strange. But it looks like the Shaman has lost his mind. I am sorry Bwana, but it looks like I've sent you on a fools errand. My apologies Bwana.")
                    if (choice2("Strange, why?", true, "Can we still get the people together?", false)) {
                        chatPlayer(neutral, "Strange, why?")
                        chatNpc(neutral, "Well, there is a pool of water that is sacred to us. It contains pure water, but I am not sure why he needs this. Perhaps you should go back and talk to him again and get more information.")
                    } else {
                        stillPeople()
                    }
                }
                else -> {
                    goodbye()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.notSure() {
        chatPlayer(neutral, "I'm not sure what to do.")
        if (legends.stage(player) >= STAGE_SPOKE_UNGADULU) {
            chatNpc(neutral, "I am at a loss as well Bwana. At this time Ungadulu is the only one who might know of a solution for this puzzle and at this time, he is part of that same puzzle.")
        } else {
            chatNpc(neutral, "Perhaps you should investigate the caves further? Have you been able to speak to Ungadulu yet? He may have some information to give you that could help?")
        }
    }

    private suspend fun Dialogue.stillPeople() {
        chatPlayer(neutral, "Can we still get the people together?")
        chatNpc(neutral, "I am sorry Bwana, my people are scattered throughout the Kharazi Jungle. Without the totem pole to allay their fears, I believe it is impossible to collect them together as a group.")
    }

    private suspend fun Dialogue.pureWater() {
        chatPlayer(neutral, "I need some pure water to douse some magic flames.")
        if (player.legendsAskedWho) {
            chatNpc(neutral, "This sounds very strange Bwana... but maybe I can help. There is a pool of water that is sacred to us. It is located in the middle of the Kharazi Jungle.")
            chatNpc(neutral, "The water contains special properties but it can only be contained in a blessed vessel made from metal of the sun. The water is difficult to get to, but I am sure you will manage to claim some.")
            legends.raiseTo(access, STAGE_ASKED_HOLY_WATER)
            vesselMenu()
            return
        }
        chatNpc(neutral, "Well there is a pool of water that is very sacred to us. But I am unsure why he would need that to douse flames. Perhaps the poor Shaman has lost his mind in the darkness of the caves?")
        if (choice2("Where is the pool of sacred water?", true, "Ok, thanks... Goodbye.", false)) {
            chatPlayer(neutral, "Where is the pool of sacred water?")
            chatNpc(neutral, "The pool of sacred water is very precious to us, it is located in the middle of the Kharazi Jungle.")
            chatNpc(neutral, "The water contains special properties which can only be retained if the water is contained in a blessed vessel made from metal of the sun.")
            legends.raiseTo(access, STAGE_ASKED_HOLY_WATER)
            vesselMenu()
        } else {
            goodbye()
        }
    }

    private suspend fun Dialogue.vesselMenu() {
        while (true) {
            when (
                choice4(
                    "Metal of the sun, what is that?", 1,
                    "What kind of a vessel?", 2,
                    "How do I bless the vessel?", 3,
                    "Ok, thanks... Goodbye.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "Metal of the sun, what is that?")
                    chatNpc(neutral, "It is a bright and precious metal that is much sought after, though uncommonly found. It is the same glorious colour as the sun and it never loses its wondrous lustre. A blessed vessel made of this metal protects the purity of the water.")
                    chatPlayer(neutral, "Where can I find this metal?")
                    chatNpc(neutral, "It is found in some rocks and must be extracted. It has a magical ability over some people, it possesses them. They fall within its power and seek to gain more and more of this precious metal for themselves.")
                }
                2 -> {
                    chatPlayer(neutral, "What kind of a vessel?")
                    chatNpc(neutral, "A vessel made of sun metal, but it can be of any shape. However, it must be blessed.")
                    if (access.inv.count(SKETCH) == 0) {
                        objbox(SKETCH, "Gujuo takes out a small scroll and some charcoal and draws a rough sketch. When he has finished, he gives the sketch to you.")
                        chatNpc(neutral, "Here, have this as an example... I pray that it will help you.")
                        access.invAddOrDrop(objRepo, SKETCH)
                    } else {
                        chatNpc(neutral, "Similar to the picture I have already given you.")
                    }
                }
                3 -> {
                    chatPlayer(neutral, "How do I bless the vessel?")
                    chatNpc(neutral, "When you have made the vessel, bring it to me. I will help but you need to ensure that you are devout and have faith. Your ability in prayer will be thoroughly tested.")
                }
                else -> {
                    goodbye()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.blessBowl() {
        if (player.prayerLvl < REQUIRED_PRAYER) {
            chatNpc(neutral, "Bwana, I am very sorry, but you are too inexperienced to bless this bowl.")
            access.mes("You need a Prayer level of at least 42 to complete this task.")
            return
        }
        if (BLESSED_BOWLS.any { access.inv.count(it) > 0 }) {
            chatNpc(sad, "Hello Bwana, I see that you already have a blessed golden bowl. Have you fallen under the spell of the metal of the sun? Alas, I cannot allow you to bless the vessel if you're possessed by greed.")
            return
        }
        chatNpc(neutral, "Very well Bwana...")
        access.ifClose()
        access.mes("Gujuo places the bowl on the floor in front of you, and leads you into a deep")
        access.mes("meditation...")
        val gujuo = npc ?: return
        for (i in 0 until 5) {
            access.delay(2)
            if (i % 2 == 0) gujuo.say("Ohhhhhmmmmmm") else access.say(if (i == 1) "Oooooommmmmmmmmm" else "Oooooohhhhmmmmmmmmmm")
        }
        access.delay(2)
        if (access.statRandom("stat.prayer", BLESS_LOW, BLESS_HIGH, 0).not()) {
            access.statSub("stat.prayer", 5, 0)
            mesbox("You were not able to go into a deep enough trance. You lose some prayer...")
            chatNpc(neutral, "Would you like to try again?")
            if (choice2("Yes, I'd like to bless my gold bowl.", true, "No thanks, I'll wait.", false)) {
                chatPlayer(happy, "Yes, I'd like to bless my gold bowl.")
                blessBowl()
            } else {
                chatPlayer(neutral, "No thanks, I'll wait.")
                chatNpc(neutral, "Very well, let me know when you want to try.")
            }
            return
        }
        val (from, into) =
            when {
                access.inv.count(GOLD_BOWL) > 0 -> GOLD_BOWL to BLESSED_BOWL
                access.inv.count(GOLD_BOWL_WATER) > 0 -> GOLD_BOWL_WATER to BLESSED_BOWL_WATER
                access.inv.count(GOLD_BOWL_PURE) > 0 -> GOLD_BOWL_PURE to BLESSED_BOWL_PURE
                else -> return
            }
        access.soundSynth(BLESS_SOUND)
        objbox(into, "You're surrounded by a totally peaceful aura as you bring the blessings of your god down on the bowl.")
        access.invDel(access.inv, from, 1)
        access.invAdd(access.inv, into, 1)
        objbox(into, "The bowl is blessed!")
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(neutral, "Ok, thanks... Goodbye.")
        chatNpc(neutral, "Farewell Bwana, good luck.")
        vanish()
    }

    private suspend fun Dialogue.thanks() {
        chatPlayer(neutral, "Ok thanks for your help.")
        chatNpc(neutral, "You are more than welcome bwana...")
        leave()
    }

    private companion object {
        const val GUJUO = "npc.gujuo"
        const val FORESTER_MALE = "npc.jungleforester_m"
        const val FORESTER_FEMALE = "npc.jungleforester_f"
        val JUNGLE_BEASTS = listOf("npc.oomlie_bird", "npc.jungle_savage", "npc.jungle_wolf")

        const val SWING_SEQ = "seq.human_bullroarer"
        const val SWING_SPOTANIM = "spotanim.bullroarer"
        const val BULLROARER_SOUND = "synth.bullroarer"
        const val BLESS_SOUND = "synth.prayer_recharge"
        const val SWING_COOLDOWN = 8
        const val NOTICE_RADIUS = 5
        const val SUMMON_RADIUS = 14
        const val SPAWN_RADIUS = 7
        const val SPAWN_DURATION = 500

        const val REQUIRED_PRAYER = 42
        const val BLESS_LOW = 80
        const val BLESS_HIGH = 250

        const val NEWS = 1
        const val PEOPLE = 2
        const val LOST_GIFT = 3
        const val THANKS = 4
        const val MADE_TOTEM = 5
        const val FOUND_SOURCE = 6
        const val KILLED_DEMON = 7
        const val HOW_TOTEM = 8
        const val STRANGE = 10
        const val SORRY = 11
        const val NOT_SURE = 12
        const val PURE_WATER = 13
        const val VACU = 14
        const val MUMBLED = 15
        const val GOODBYE = 16
    }
}
