package org.rsmod.content.quest.area.karamja.legendsquest.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onApNpcT
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsCoords
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BOOK_OF_BINDING
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.DARK_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GLOWING_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_FORCE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_COLLECTED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FINAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FIRE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_FILLED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GERMINATED_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GOT_GILDED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_POOL_DRIED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_PUSHED_BOULDER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RECEIVED_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_REPLACED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RETURNED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SPOKE_UNGADULU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SUMMONED_NEZIKCHENED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_SEEDS_GERMINATED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.Nezikchened
import org.rsmod.content.quest.area.karamja.legendsquest.chebyshevDistance
import org.rsmod.content.quest.area.karamja.legendsquest.hurt
import org.rsmod.content.quest.area.karamja.legendsquest.legendsAskedWhere
import org.rsmod.content.quest.area.karamja.legendsquest.legendsAskedWho
import org.rsmod.content.quest.area.karamja.legendsquest.legendsCalledVacu
import org.rsmod.content.quest.area.karamja.legendsquest.legendsGaveDagger
import org.rsmod.content.quest.area.karamja.legendsquest.legendsKilledViyeldi
import org.rsmod.content.quest.area.karamja.legendsquest.legendsSoakedUngadulu
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ungadulu, the Kharazi shaman trapped in his own flaming octagram with the demon Nezikchened
 * inside him. Until the demon is driven out he keeps changing into his possessed form and back
 * again, begging the player to leave while the demon calls them Vacu and lets slip that pure water
 * will douse the flames. He cannot be fought through the flames, and anyone who tries it from
 * inside is blasted back out of the octagram.
 *
 * Once free he hands over the Yommi tree seeds, and later turns the dagger Echned Zekin gave the
 * player into the Holy Force spell that unmasks the spirit.
 */
@Singleton
class Ungadulu
@Inject
constructor(
    private val legends: LegendsQuest,
    private val nezikchened: Nezikchened,
    private val objRepo: ObjRepository,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val accuracy: AccuracyFormulae,
    private val world: WorldRepository,
    private val mapClock: MapClock,
) : PluginScript() {

    /**
     * When each player's window to break the demon's grip closes. Talking to the shaman leaves him
     * weakened for a short while, and a curse cast on him then frees him long enough to speak.
     */
    private val weakenedUntil = HashMap<PlayerUid, Int>()

    override fun ScriptContext.startup() {
        onOpNpc1(UNGADULU) { startDialogue(it.npc) { talk() } }
        onOpNpc2(UNGADULU) { attack(it.npc) }
        onApNpc2(UNGADULU) { attack(it.npc) }
        onOpNpc2(UNGADULU_POSSESSED) { attack(it.npc) }
        onApNpc2(UNGADULU_POSSESSED) { attack(it.npc) }
        val shamanType = ServerCacheManager.getNpc(UNGADULU.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $UNGADULU")
        for (curse in CURSES) {
            val spell = ServerCacheManager.getItem(curse.obj.asRSCM(RSCMType.OBJ))?.let(spells::getObjSpell) ?: continue
            onApNpcT(shamanType, spell.component) { curseShaman(it.npc, spell, curse) }
        }
        onOpNpcU(UNGADULU) { shown(it.npc, it.objType.internalName) }
    }

    private fun Dialogue.possess(duration: Int) {
        val shaman = npc ?: return
        val type = ServerCacheManager.getNpc(UNGADULU_POSSESSED.asRSCM(RSCMType.NPC)) ?: return
        access.npcChangeType(shaman, type, duration)
    }

    private fun Dialogue.release() {
        val shaman = npc ?: return
        val type = ServerCacheManager.getNpc(UNGADULU.asRSCM(RSCMType.NPC)) ?: return
        access.npcChangeType(shaman, type, 1)
    }

    private suspend fun Dialogue.talk() {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE) {
            afterDemon()
            return
        }
        chatNpc(neutral, "Please run for your life...")
        mesbox("The Shaman seems to be fighting an inner battle.")
        chatNpc(neutral, "Go... go now...!")
        mesbox("The Shaman seems to change in front of your eyes...")
        possess(POSSESSED_TICKS)
        chatNpc(angry, "Welcome Vacu, to eternal...")
        access.ifClose()
        access.mes("The Shaman starts an incantation...")
        access.delay(1)
        access.mes("You feel a strange power coming over you...")
        access.delay(1)
        access.mes("You feel very weak...")
        access.delay(1)
        access.mes("The Shaman seems to get stronger!")
        access.delay(1)
        release()
        access.mes("The Shaman seems to return to normal...")
        chatNpc(neutral, "Run, run away... Run like the leopard Bwana!")
        weakenedUntil[player.uid] = this@Ungadulu.mapClock.cycle + WEAKENED_TICKS
    }

    /** What the shaman says to a player who looks at him through the flames. */
    suspend fun ProtectedAccess.comeNoCloser(shaman: Npc) {
        startDialogue(shaman) {
            chatNpc(sad, "Please come no closer... the flames will incinerate you.")
            legends.raiseTo(access, STAGE_SPOKE_UNGADULU)
            if (choice2("How can I extinguish the flames?", true, "Who are you?", false)) {
                extinguish()
            } else {
                whoAreYou()
            }
        }
    }

    private suspend fun Dialogue.extinguish() {
        chatPlayer(neutral, "How can I extinguish the flames?")
        chatNpc(shocked, "Please don't try to extinguish...")
        possess(2)
        chatNpc(angry, "Yes, douse the flames with water, pure water... foo...")
        release()
        chatNpc(shocked, "Please, leave now... don't listen to me... I beg you, leave now,  don't touch the flames...")
        player.legendsAskedWhere = true
        if (choice2("Where do I get pure water from?", true, "Who are you?", false)) {
            wherePureWater()
        } else {
            whoAreYou()
        }
    }

    private suspend fun Dialogue.wherePureWater() {
        chatPlayer(neutral, "Where do I get pure water from?")
        chatNpc(shocked, "Please, leave now...")
        possess(2)
        chatNpc(angry, "...from the above lands... hurry and release me...")
        release()
        chatNpc(shocked, "Leave here, please, go... now...")
        possess(2)
        chatNpc(madlaugh, "Hurry, Vacu, the heat kills me... ha ha ha")
        release()
        player.legendsCalledVacu = true
        mesbox("The Shaman throws himself down on the floor and starts convulsing.")
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(neutral, "Who are you?")
        chatNpc(sad, "I am Ungadulu, trapped here many years now... Leave these caves and save yourself...")
        possess(2)
        chatNpc(angry, "Wait... get pure water from the pool... above lands...")
        release()
        player.legendsAskedWhere = true
        player.legendsAskedWho = true
        chatNpc(shocked, "Please Bwana, don't listen to me... run, save yourself...")
        if (choice2("How can I extinguish the flames?", true, "Where do I get pure water from?", false)) {
            extinguish()
        } else {
            wherePureWater()
        }
    }

    private suspend fun Dialogue.afterDemon() {
        val stage = legends.stage(player)
        when {
            legends.isComplete(player) || stage >= STAGE_GOT_GILDED_TOTEM -> {
                chatNpc(happy, "Your Legendary exploits are travelling the whole jungle.")
                if (legends.isComplete(player)) {
                    chatNpc(
                        neutral,
                        "Gujuo has been to see me. He told me that you have been given a sacred totem " +
                            "pole. It was constructed by one of my ancestors many moons ago. It is a " +
                            "noble prize Bwana, you have earned it. Look after it well.",
                    )
                } else {
                    chatNpc(neutral, "You have done well, Bwana, to have succeeded where so many others have failed. You are a true Legend Bwana.")
                }
            }
            stage == STAGE_DEFEATED_NEZIKCHENED_FIRE -> {
                chatNpc(
                    happy,
                    "Greetings bwana... Many thanks for defeating the demon... and releasing me from " +
                        "this dreadful possession... Pray tell me, what can I do to repay this great favour?",
                )
                freedMenu()
            }
            stage in STAGE_GERMINATED_SEEDS..STAGE_PUSHED_BOULDER -> {
                chatNpc(happy, "Hello Bwana, how goes your quest with the Yommi tree?")
                when (stage) {
                    STAGE_GERMINATED_SEEDS -> {
                        when (choice3("I have germinated the seeds.", 1, "Where do I plant the seeds?", 2, "I need more Yommi tree seeds.", 3)) {
                            1 -> {
                                chatPlayer(neutral, "I have germinated the seeds.")
                                chatNpc(happy, "Great Bwana, now go plant them in the fertile soil. You should soon have a great Yommi tree worthy of a most marvelous totem pole.")
                            }
                            2 -> wherePlant()
                            else -> moreSeeds()
                        }
                    }
                    STAGE_POOL_DRIED -> {
                        when (
                            choice3(
                                "The magic pool has dried up and I need some more pure water.", 1,
                                "Where can I get more pure water?", 2,
                                "I need more Yommi tree seeds.", 3,
                            )
                        ) {
                            1 -> {
                                chatPlayer(neutral, "The magic pool has dried up and I need some more pure water.")
                                chatNpc(neutral, "Hmmm, that sounds odd. I'm sure that Gujuo will tell you the same as me though. Searching for the source of the water pool will be difficult. However, with some help, it might be possible.")
                            }
                            2 -> moreWater()
                            else -> moreSeeds()
                        }
                    }
                    else -> {
                        when (
                            choice3(
                                "I am on a quest to get more pure water.", 1,
                                "What do you know about the source of the sacred water?", 2,
                                "I need more Yommi tree seeds.", 3,
                            )
                        ) {
                            1 -> {
                                chatPlayer(happy, "I am on a quest to get more pure water.")
                                chatNpc(neutral, "Well, good luck with your quest Bwana. You may find it worthwhile exploring these catacombs. There is said to be an entrance to the Viyeldi caves, which is where the sacred source of the magic pool exists.")
                                chatNpc(neutral, "Beware though as it is said that the area is cursed. Anyone who is killed seeking the sacred water, will forever be sworn to protect its secret.")
                            }
                            2 -> sacredSource()
                            else -> moreSeeds()
                        }
                    }
                }
            }
            stage == STAGE_RECEIVED_DAGGER || legends.owns(access, DARK_DAGGER) || legends.owns(access, GLOWING_DAGGER) -> {
                chatNpc(quiz, "Hello Bwana, how goes your quest to find the water?")
                daggerMenu()
            }
            stage in STAGE_DEFEATED_NEZIKCHENED_WATER until STAGE_COLLECTED_TOTEM -> {
                mesbox("You approach Ungadulu...")
                if (player.legendsGaveDagger) {
                    chatNpc(quiz, "Blessings on you Bwana. Have you managed to deal with the spirit? Do you have the water?")
                } else {
                    chatNpc(quiz, "Blessings on you Bwana. Did you use the spell and kill the spirit? Do you have the sacred water yet?")
                }
                when (choice3("Yes, I've killed the Spirit.", 1, "Yes, I've got the water.", 2, "I need more Yommi tree seeds.", 3)) {
                    1 -> killedSpirit()
                    2 -> gotWater()
                    else -> moreSeeds()
                }
            }
            else -> {
                if (stage < STAGE_REPLACED_TOTEM && !legends.owns(access, YOMMI_SEEDS) && !legends.owns(access, YOMMI_SEEDS_GERMINATED) && !legends.owns(access, YOMMI_TOTEM)) {
                    chatNpc(neutral, "I see you have no totem pole, or Yommi tree seeds, is everything ok?")
                    if (!choice2("Yes, everything's fine.", true, "I need more Yommi tree seeds.", false)) {
                        moreSeeds()
                        return
                    }
                    chatPlayer(neutral, "Yes, everything's fine.")
                }
                chatNpc(happy, "Your Legendary exploits are travelling the whole jungle.")
                chatNpc(neutral, "How goes your quest to grow the sacred Yommi tree?")
                if (stage >= STAGE_DEFEATED_NEZIKCHENED_FINAL) {
                    when (choice3("I've killed Nezikchened the Demon again.", 1, "I've replaced the evil Totem pole.", 2, "Ok, thanks...", 3)) {
                        1 -> {
                            chatPlayer(happy, "I've killed Nezikchened the Demon again.")
                            chatNpc(happy, "If you have killed him for the third time, then you have banished him from our world completely. This is indeed a legendary accomplishment Bwana, you should feel proud.")
                        }
                        2 -> {
                            chatPlayer(happy, "I've replaced the evil Totem pole.")
                            chatNpc(happy, "Many thanks Bwana, my people are truly grateful. Have you seen Gujuo? I am sure that he may have something for you as a token of our appreciation.")
                        }
                        else -> thanks()
                    }
                    return
                }
                when (choice3("I've already made the totem pole.", 1, "I'm not sure what to do with the Totem pole.", 2, "Ok, thanks...", 3)) {
                    1 -> {
                        chatPlayer(neutral, "I've already made the totem pole.")
                        chatNpc(happy, "This is great news Bwana, you've done really well. Perhaps we can start to rally our people together now. And live once again without fear in the jungle.")
                    }
                    2 -> {
                        chatPlayer(quiz, "I'm not sure what to do with the Totem pole.")
                        chatNpc(happy, "Well, Bwana, you can simply replace the corrupted totem pole with the good one you have created. This will make my people very happy.")
                    }
                    else -> thanks()
                }
            }
        }
    }

    private suspend fun Dialogue.freedMenu() {
        while (true) {
            when (
                choice3(
                    "I need to collect some Yommi tree seeds for Gujuo.", 1,
                    "How do I get out of here?", 2,
                    "Ok, thanks...", 3,
                )
            ) {
                1 -> {
                    yommiSeeds()
                    return
                }
                2 -> {
                    chatPlayer(neutral, "How do I get out of here?")
                    chatNpc(neutral, "Now that you have defeated the demon you can come and go as you please. I cast a spell on you which means that you can pass through the flames without harm.")
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.yommiSeeds() {
        chatPlayer(neutral, "I need to collect some Yommi tree seeds for Gujuo.")
        if (access.inv.count(YOMMI_SEEDS) > 0) {
            chatNpc(confused, "I already gave you some Yommi tree seeds. Try using those first.")
        } else {
            chatNpc(
                confused,
                "Oh, yes, Bwana... you will be doing a great favour to our people by doing this. " +
                    "However, you must know that it is a difficult task, the Yommi tree is difficult to grow.",
            )
            chatNpc(neutral, "You must have a natural ability with such things to have a chance...")
            objbox(YOMMI_SEEDS, "The Shaman holds out his gnarly old hand and reveals three largish green seeds.")
            chatNpc(
                neutral,
                "Here you are, accept these with my gratitude. You'll need to soak them in sacred " +
                    "water before planting them. I notice that you must already be familiar with " +
                    "sacred water to have passed into the flaming octagram.",
            )
            access.invAddOrDrop(objRepo, YOMMI_SEEDS, SEED_COUNT)
        }
        while (true) {
            when (choice3("How do I grow the Yommi tree?", 1, "What do you know about the pure water?", 2, "Ok, thanks...", 3)) {
                1 -> {
                    chatPlayer(neutral, "How do I grow the Yommi tree?")
                    chatNpc(neutral, "A good question Bwana, but it is essentially quite simple. First you will need to soak the seeds in some pure water. This will help to germinate the seed and begin the growing process.")
                    chatNpc(neutral, "The tree should show some remarkable growth quite early but will slow down, you may be able to speed the process up by watering the tree with more pure water, although it can be difficult to find it.")
                }
                2 -> {
                    chatPlayer(neutral, "What do you know about the pure water?")
                    chatNpc(neutral, "Hmmm, the pure water is sacred to us. It is from a sacred spring which is fed from deep underground.")
                    chatNpc(neutral, "Legend has it that the spring is protected by spirits of long dead adventurers who went in search of the source of the spring. It is likely a myth and the source of the spring is buried deep underground with no paths to it.")
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.wherePlant() {
        chatPlayer(neutral, "Where do I plant the seeds?")
        chatNpc(neutral, "Above ground and spaced out throughout the whole jungle area are specially cultivated fertile soil areas. Seek one out and plant the Yommi tree in that. Be prepared to water it though...")
    }

    private suspend fun Dialogue.moreWater() {
        chatPlayer(quiz, "Where can I get more pure water?")
        chatNpc(quiz, "There is said to be a stream of the sacred water that exists underground. I'm sure that Gujuo will tell you quite a lot about it.")
        chatNpc(quiz, "I have not explored outside of this room, but I have heard that there is a huge metallic door of strange construction within these catacombs which challenges any person with a riddle. Very few have solved the riddle.")
        chatNpc(neutral, "And even fewer have been returned alive if they did solve it. You can try to explore these caverns, it may help. You may just be able to find the Viyeldi caves. That is where the source of the sacred water resides.")
    }

    private suspend fun Dialogue.sacredSource() {
        chatPlayer(quiz, "What do you know about the source of the sacred water?")
        chatNpc(neutral, "It is said that the caves where the stream is located, are littered with strange remains of a past civilisation. The dwarves are said to have excavated the area in search of the source of the sacred water.")
        chatNpc(neutral, "Something bad must have happened because soon the area was cursed. Anyone who entered the area looking for the source of the water, and who died, would be forever cursed to protect the water......Forever...")
    }

    private suspend fun Dialogue.moreSeeds() {
        chatPlayer(neutral, "I need more Yommi tree seeds.")
        if (legends.owns(access, YOMMI_SEEDS) || legends.owns(access, YOMMI_SEEDS_GERMINATED)) {
            chatNpc(neutral, "You already have some Yommi tree seeds. Use those first and then come back to me if you need any more.")
            mesbox("Ungadulu goes back to his studies.")
            return
        }
        access.invAddOrDrop(objRepo, YOMMI_SEEDS, SEED_COUNT)
        objbox(YOMMI_SEEDS, "Ungadulu gives you some more seeds.")
        chatNpc(neutral, "Take more care of these this time around.")
    }

    private suspend fun Dialogue.daggerMenu() {
        while (true) {
            val choice =
                if (player.legendsKilledViyeldi) {
                    choice5(
                        "I have killed Viyeldi!", KILLED_VIYELDI,
                        "I met a spirit in the Viyeldi Caves.", MET_SPIRIT,
                        "The spirit told me to kill Viyeldi.", TOLD_KILL,
                        "Do you know anything about daggers?", DAGGERS,
                        "I need more Yommi tree seeds.", SEEDS,
                    )
                } else {
                    choice5(
                        "I met a spirit in the Viyeldi Caves.", MET_SPIRIT,
                        "The spirit told me to kill Viyeldi.", TOLD_KILL,
                        "Do you know anything about daggers?", DAGGERS,
                        "I need more Yommi tree seeds.", SEEDS,
                        "Ok, thanks...", THANKS,
                    )
                }
            when (choice) {
                KILLED_VIYELDI -> {
                    killedViyeldi()
                    return
                }
                MET_SPIRIT -> {
                    chatPlayer(neutral, "I met a spirit in the Viyeldi Caves.")
                    chatNpc(confused, "You did well to come to me Bwana. As I said before, I am an expert in spirits of the underworld. In most circumstances you should just ignore them. However, beware as many spirits will try to trick you.")
                }
                TOLD_KILL -> {
                    chatPlayer(neutral, "The spirit told me to kill Viyeldi.")
                    chatNpc(neutral, "That sounds very strange Bwana, I'm glad to see you didn't commit such a foul act. I can make a spell that would help you to defeat the spirit. But I need an item that belongs to the spirit to make it work.")
                    chatNpc(neutral, "If you have something like that, please show it to me and I'll give you the spell. Beware of everything in these caves, I was tricked very easily and was enslaved, as you well know. Learn from my example.")
                }
                DAGGERS -> {
                    chatPlayer(quiz, "Do you know anything about daggers?")
                    chatNpc(neutral, "I know something about them, especially magical daggers. If you have a specific one, show it to me and I'll help as much as I can.")
                }
                SEEDS -> {
                    moreSeeds()
                    return
                }
                else -> {
                    thanks()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.killedViyeldi() {
        chatPlayer(happy, "I have killed Viyeldi!")
        chatNpc(shocked, "Why on earth did you do that? <col=0000ff>---The Shaman screams at you---</col>")
        chatPlayer(confused, "A spirit called Echned Zekin said I had to avenge his spirit by killing Viyeldi if I wanted to get the pure water.")
        mesbox("The Shaman puts his head in his hands.")
        chatNpc(sad, "Bwana, you have been tricked by a spirit! And you have done the worst thing imaginable. Viyeldi was the sorcerer who controlled the Heroes who protect the source.")
        chatNpc(shocked, "The spirits of these heroes are now free to be controlled by other, more powerful evil forces. Most likely the spirit that tricked you.")
        if (choice2("What can we do?", true, "Ok, thanks...", false).not()) {
            thanks()
            return
        }
        chatPlayer(quiz, "What can we do?")
        if (legends.owns(access, HOLY_FORCE)) {
            chatNpc(neutral, "You can use that Holy Force spell to try and defeat the spirit. Come back and let me know if I can help in any other way.")
            return
        }
        chatNpc(confused, "I am not sure at this time Bwana. Give me a few moments to think.")
        chatNpc(neutral, "Hmmm....")
        val dagger = listOf(GLOWING_DAGGER, DARK_DAGGER).firstOrNull { access.inv.count(it) > 0 || access.worn.count(it) > 0 }
        if (dagger == null) {
            chatNpc(neutral, "I could make a spell that would help you to defeat the spirit. But I need you to bring me a possession that it once owned. If you have something like that, please show it to me. And I'll give you the spell.")
            return
        }
        chatNpc(neutral, "Take this spell and pray that you can defeat this evil spirit before it's too late. I'll take that dagger from you now!")
        takeDagger(dagger)
        access.invAddOrDrop(objRepo, HOLY_FORCE)
        objbox(HOLY_FORCE, "The wizened old Shaman hands over a piece of paper.")
    }

    private fun Dialogue.takeDagger(dagger: String) {
        if (access.inv.count(dagger) > 0) {
            access.invDel(access.inv, dagger, 1)
        } else if (access.worn.count(dagger) > 0) {
            access.invDel(access.worn, dagger, 1)
            access.rebuildAppearance()
        }
    }

    private suspend fun Dialogue.killedSpirit() {
        if (player.legendsGaveDagger) {
            chatPlayer(happy, "Yes, I've killed the Spirit. Actually the spirit turned out to be the Demon - Nezikchened.")
        } else {
            chatPlayer(quiz, "Yes, I've killed the Spirit. I used the spell you gave me, it was very effective in fact. Actually the spirit turned out to be the Demon - Nezikchened.")
        }
        chatNpc(happy, "That's truly a miracle Bwana, very few come out of Viyeldi's caves alive. And you managed to defeat Nezikchened a second time? You are truly a legend bwana. Do you have the sacred water as well?")
        if (choice2("Yes, I've got the water.", true, "What do I do now?", false)) gotWater() else whatNow()
    }

    private suspend fun Dialogue.gotWater() {
        chatPlayer(happy, "Yes, I've got the water.")
        chatNpc(neutral, "That is truly great Bwana... Well done! You have the spirit of the jungle lion.")
        if (choice2("What do I do now?", true, "Ok, thanks...", false)) whatNow() else thanks()
    }

    private suspend fun Dialogue.whatNow() {
        chatPlayer(neutral, "What do I do now?")
        chatNpc(neutral, "Well, you should be able to plant the Yommi tree. And then water it with the sacred water. You should then be able to start making the Totem pole.")
        chatNpc(worried, "So long as you have banished the spirit and managed to get some of the sacred water. Let us pray that we have seen the last of that evil!")
    }

    private suspend fun Dialogue.thanks() {
        chatPlayer(neutral, "Ok, thanks...")
        chatNpc(happy, "My sincerest pleasure Bwana...")
    }

    private suspend fun ProtectedAccess.shown(shaman: Npc, obj: String) {
        if (legends.stage(player) >= STAGE_RETURNED) {
            mes("You have no further business with Ungadulu.")
            return
        }
        when (obj) {
            BOOK_OF_BINDING -> bind(shaman)
            DARK_DAGGER -> startDialogue(shaman) { darkDagger() }
            GLOWING_DAGGER -> startDialogue(shaman) { glowingDagger() }
            HOLY_WATER -> soak(shaman)
            else -> mes("Nothing interesting happens.")
        }
    }

    /**
     * Holding the Book of Binding open before the possessed shaman forces Nezikchened out of him.
     * The shaman collapses and the demon, bound to the player, turns on them.
     */
    private suspend fun ProtectedAccess.bind(shaman: Npc) {
        val stage = legends.stage(player)
        if (stage !in STAGE_FILLED_BOWL until STAGE_DEFEATED_NEZIKCHENED_FIRE) {
            startDialogue(shaman) { chatNpc(happy, "Ha ha ha! There's no need to use that on me anymore... I'm cured now, remember?") }
            return
        }
        legends.advanceFrom(this, STAGE_FILLED_BOWL, STAGE_SUMMONED_NEZIKCHENED)
        mesbox(
            "You open the Book of Binding in front of Ungadulu and a supernatural light starts " +
                "emanating from the pages, illuminating Ungadulu whose face twists and contorts in spasms.",
        )
        startDialogue(shaman) {
            chatNpcNoTurn(angry, "Curse you foul intruder... 'Ere near to death ye come now that ye has meddled in my dealings..")
        }
        mesbox("Without warning, the outline of a huge demon starts to emerge in front of you as Ungadulu falls to the floor unconscious.")
        ifClose()
        shaman.anim(COLLAPSE_SEQ)
        val demon = with(nezikchened) { summonDemon(Nezikchened.Fight.Fire, 2, "Your faith will help you little here.") }
        mes("A sense of hopelessness fills your body...")
        statSub("stat.prayer", 0, 90)
        if (player.legendsSoakedUngadulu) {
            player.legendsSoakedUngadulu = false
            if (demon != null) {
                for (stat in SOAK_DRAINED_STATS) {
                    SpellEffects.drainPercent(demon, stat, SOAK_DRAIN_PERCENT)
                }
                demon.spotanim(SOAK_IMPACT_SPOTANIM, height = SOAK_IMPACT_HEIGHT)
                mes("The holy water soaked into Ungadulu burns the demon, weakening it!")
            }
        }
    }

    /**
     * Holy water thrown over the possessed shaman scalds the demon inside him: he briefly shows
     * his possessed face, and the demon comes out of him weakened when the Book of Binding is
     * next opened before him.
     */
    private suspend fun ProtectedAccess.soak(shaman: Npc) {
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE) {
            startDialogue(shaman) { chatNpc(happy, "Ha ha ha! There's no need to use that on me anymore... I'm cured now, remember?") }
            return
        }
        if (player.legendsSoakedUngadulu) {
            mes("Ungadulu is already soaked through with holy water.")
            return
        }
        faceEntitySquare(shaman)
        anim(SOAK_THROW_SEQ)
        soundSynth(SOAK_THROW_SOUND)
        val proj = world.projAnim(player, shaman, SpotanimType(SOAK_TRAVEL_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), SOAK_PROJANIM)
        invDel(inv, HOLY_WATER, 1)
        mes("You throw the holy water over Ungadulu.")
        delay(1)
        val landing = shaman.coords
        shaman.spotanim(SOAK_IMPACT_SPOTANIM, delay = proj.clientCycles, height = SOAK_IMPACT_HEIGHT)
        world.soundArea(landing, SOAK_POUR_SOUND, delay = proj.clientCycles)
        world.soundArea(landing, SOAK_GLASS_SOUND, delay = proj.clientCycles)
        objRepo.add(SOAK_SMASHED_GLASS, landing, SOAK_GLASS_TICKS, receiver = player)
        val possessed = ServerCacheManager.getNpc(UNGADULU_POSSESSED.asRSCM(RSCMType.NPC))
        if (possessed != null) {
            npcChangeType(shaman, possessed, SOAK_POSSESSED_TICKS)
        }
        shaman.anim(STUNNED_SEQ)
        shaman.say("Aaarrgghh! It burns, Vacu!")
        player.legendsSoakedUngadulu = true
        mes("The Shaman's face twists into something inhuman as the water soaks into him...")
        mes("Whatever is inside him seems to have been weakened.")
    }

    private suspend fun Dialogue.darkDagger() {
        objbox(DARK_DAGGER, "You hand the dagger over to the shaman and his face turns pale.")
        chatNpc(shocked, "This dagger has been made for one purpose only... Praise the gods that you brought it to me.")
        chatNpc(neutral, "I can make you a spell with this item which will force the spirit to reveal its true self. Once activated, you will be able to attack it like any normal creature.")
        if (legends.owns(access, HOLY_FORCE)) {
            access.invDel(access.inv, DARK_DAGGER, 1)
            chatNpc(neutral, "Use the Holy Force spell that I gave you earlier to defeat this spirit. Good luck. I'll take that dagger for safe keeping...")
            return
        }
        objbox(HOLY_FORCE, "The Shaman takes the dagger and gives you a folded piece of paper.")
        access.invDel(access.inv, DARK_DAGGER, 1)
        access.invAddOrDrop(objRepo, HOLY_FORCE)
        chatNpc(neutral, "Use this spell on the Spirit. It will force the spirit to show its true self. And it will also be vulnerable to normal attacks.")
    }

    private suspend fun Dialogue.glowingDagger() {
        objbox(GLOWING_DAGGER, "You hand the dagger over to the shaman and his face turns pale.")
        chatNpc(shocked, "Oh dear Bwana, I sense something terrible has happened. This dagger is a portent of some evil action. Please, reveal to me anything that you have done so that I might understand this better.")
        if (!choice2("I've killed Viyeldi.", true, "Er, I can't think of anything.", false)) {
            chatPlayer(neutral, "Er, I can't think of anything.")
            chatNpc(neutral, "Well, that is strange. I sense a growing evil power since you visited the caves.")
            return
        }
        chatPlayer(neutral, "I've killed Viyeldi.")
        chatNpc(sad, "Poor Viyeldi, he was the guardian of the dead heroes that protected the source. Their tormented spirits will now be at the beck and call of the one who gave you the dagger.")
        chatNpc(neutral, "Take this spell and pray that you can defeat this evil spirit before it's too late. The spell will force the spirit to reveal its true self. And it will also be vulnerable to normal attacks.")
        access.invDel(access.inv, GLOWING_DAGGER, 1)
        access.invAddOrDrop(objRepo, HOLY_FORCE)
        objbox(HOLY_FORCE, "The wizened old Shaman hands over a piece of paper.")
    }

    /**
     * Curse, Weaken or Confuse cast on the shaman. From outside the octagram the spell dies in the
     * flames. From inside it, a landed curse while he is still reeling from talking to the player
     * loosens the demon's grip long enough for him to tell them about the Book of Binding; any other
     * cast he shrugs off and turns on the caster.
     */
    private suspend fun ProtectedAccess.curseShaman(shaman: Npc, spell: MagicSpell, curse: Curse) {
        if (!isWithinDistance(shaman, CURSE_RANGE)) {
            apRange(CURSE_RANGE)
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        faceEntitySquare(shaman)
        statAdvance("stat.magic", spell.castXp)
        anim(curse.anim)
        spotanim("spotanim.${curse.name}_casting", height = CAST_HEIGHT)
        soundSynth(curse.castSound)
        val proj = world.projAnim(player, shaman, SpotanimType("spotanim.${curse.name}_travel".asRSCM(RSCMType.SPOTANIM)), CURSE_PROJANIM)
        delay(1)
        val landed = accuracy.rollSpellAccuracy(player, shaman, spell.obj, spell.spellbook, false, random)
        if (landed) {
            if (!LegendsCoords.inOctagram(coords)) {
                mesbox("The spell fizzles and has no effect as it passes through the flames.")
                return
            }
            shaman.spotanim("spotanim.${curse.name}_impact", delay = proj.clientCycles, height = IMPACT_HEIGHT)
            curse.hitSound?.let { world.soundArea(shaman.coords, it, delay = proj.clientCycles) }
            if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE) {
                startDialogue(shaman) {
                    chatNpc(happy, "Ha ha ha! There's no need to use that on me anymore... I'm cured now, remember?")
                }
                return
            }
            val window = weakenedUntil[player.uid] ?: 0
            if (window > this@Ungadulu.mapClock.cycle) {
                weakenedUntil.remove(player.uid)
                startDialogue(shaman) {
                    chatNpc(
                        shocked,
                        "Thank you, Bwana! You have released me from the demon's grasp, but only for " +
                            "a short while! You must seek out the Book of Binding - it's hidden in these " +
                            "caves somewhere!",
                    )
                    chatNpc(shocked, "There are many trials you must pass... but please, you're my only hope of escape!")
                    mesbox("The shaman's eyes roll back and he returns to his quiet, unassuming self.")
                }
                return
            }
        } else {
            shaman.spotanim(SPLASH_SPOTANIM, delay = proj.clientCycles, height = IMPACT_HEIGHT)
            world.soundArea(shaman.coords, SPLASH_SOUND, delay = proj.clientCycles)
        }
        shaman.say("What...what's happening...")
        shaman.anim(STUNNED_SEQ)
        delay(1)
        shaman.queueCombatRetaliate(player)
        mes("The shaman somehow resists the power of the spell.")
        mes("Perhaps you should try again?")
    }

    data class Curse(
        val obj: String,
        val name: String,
        val anim: String,
        val castSound: String,
        val hitSound: String?,
    )

    /**
     * Weapons melt in the flames, so the shaman can only be reached from inside the octagram, and
     * there he blasts anyone who raises a hand to him back out of it.
     */
    private suspend fun ProtectedAccess.attack(shaman: Npc) {
        if (!LegendsCoords.inOctagramZone(coords)) {
            mesbox("You feel certain that your weapon would melt if you tried to attack through these magical flames.")
            return
        }
        delay(1)
        shaman.facePlayer(player)
        mes("The shaman casts a spell!")
        val freed = legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE
        if (!freed) {
            val possessed = ServerCacheManager.getNpc(UNGADULU_POSSESSED.asRSCM(RSCMType.NPC))
            if (possessed != null) {
                npcChangeType(shaman, possessed, POSSESSED_ATTACK_TICKS)
            }
            shaman.say("Ha ha ha ha! Die, Vacu!")
        } else {
            shaman.say("Huh, that's no way to treat a shaman.")
        }
        shaman.anim(ZAP_CAST_SEQ)
        shaman.spotanim(ZAP_SPOTANIM, height = ZAP_HEIGHT)
        delay(1)
        val start = coords
        val spot = LegendsCoords.THROW_SPOTS.minByOrNull { it.chebyshevDistance(start) } ?: return
        val dest = mapFindSquareLineOfWalk(spot, 0, 1) ?: spot
        anim(BLOWN_SEQ)
        exactMove(
            start = start,
            end = dest,
            delay1 = 31,
            delay2 = 31 + 14 * start.chebyshevDistance(dest),
            dir = faceDirection(dest, start),
            teleportType = TeleportType.Exempt,
        )
        delay(maxOf(1, start.chebyshevDistance(dest) / 2))
        anim(GETUP_SEQ)
        delay(1)
        hurt(random.of(4, 10))
        mesbox("Ungadulu unleashes a savage bolt of energy at you. You're knocked out of the octagram by the power of the spell.")
        startDialogue(shaman) {
            if (freed) {
                chatNpc(sad, "I hope that teaches you a lesson!")
            } else {
                chatNpc(madlaugh, "Ha, ha ha Vacu... Come for me again and you'll taste more of my power!")
            }
        }
    }

    private fun faceDirection(from: CoordGrid, to: CoordGrid): Int {
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        return when {
            dx == 0 && dz > 0 -> org.rsmod.api.config.constants.em_face_north
            dx == 0 && dz < 0 -> org.rsmod.api.config.constants.em_face_south
            dx > 0 && dz == 0 -> org.rsmod.api.config.constants.em_face_east
            dx < 0 && dz == 0 -> org.rsmod.api.config.constants.em_face_west
            dx > 0 && dz > 0 -> org.rsmod.api.config.constants.em_face_northeast
            dx < 0 && dz > 0 -> org.rsmod.api.config.constants.em_face_northwest
            dx > 0 -> org.rsmod.api.config.constants.em_face_southeast
            else -> org.rsmod.api.config.constants.em_face_southwest
        }
    }

    companion object {
        const val UNGADULU = "npc.ungadulu_good"
        const val UNGADULU_POSSESSED = "npc.ungadulu_bad"
        const val WEAKENED_TICKS = 40
        const val CURSE_RANGE = 10
        const val CAST_HEIGHT = 92
        const val IMPACT_HEIGHT = 124
        const val CURSE_PROJANIM = "projanim.confuse"
        const val SPLASH_SPOTANIM = "spotanim.failedspell_impact"
        const val SPLASH_SOUND = "synth.spellfail"
        const val STUNNED_SEQ = "seq.human_stunned"

        val CURSES =
            listOf(
                Curse("obj.03_confuse", "confuse", "seq.human_castconfuse", "synth.confuse_cast_and_fire", "synth.confuse_hit"),
                Curse("obj.11_weaken", "weaken", "seq.human_castweaken", "synth.weaken_all", null),
                Curse("obj.19_curse", "curse", "seq.human_castcurse", "synth.curse_cast_and_fire", "synth.curse_hit"),
            )
        const val POSSESSED_TICKS = 100
        const val SOAK_POSSESSED_TICKS = 5
        const val SOAK_THROW_SEQ = "seq.human_throw"
        const val SOAK_THROW_SOUND = "synth.thrown"
        const val SOAK_TRAVEL_SPOTANIM = "spotanim.holy_water_travel"
        const val SOAK_PROJANIM = "projanim.thrown"
        const val SOAK_IMPACT_SPOTANIM = "spotanim.watersplash"
        const val SOAK_IMPACT_HEIGHT = 92
        const val SOAK_POUR_SOUND = "synth.holy_water_pour"
        const val SOAK_GLASS_SOUND = "synth.glass_break"
        const val SOAK_SMASHED_GLASS = "obj.smashed_glass"
        const val SOAK_GLASS_TICKS = 200
        const val SOAK_DRAIN_PERCENT = 10
        val SOAK_DRAINED_STATS = listOf(SpellEffects.ATTACK, SpellEffects.STRENGTH, SpellEffects.DEFENCE)
        const val POSSESSED_ATTACK_TICKS = 20
        const val SEED_COUNT = 3

        const val COLLAPSE_SEQ = "seq.human_death_backwards"
        const val ZAP_CAST_SEQ = "seq.human_castzap"
        const val ZAP_SPOTANIM = "spotanim.zap"
        const val ZAP_HEIGHT = 100
        const val BLOWN_SEQ = "seq.human_blown_middle"
        const val GETUP_SEQ = "seq.human_blown_end_getup"

        const val KILLED_VIYELDI = 1
        const val MET_SPIRIT = 2
        const val TOLD_KILL = 3
        const val DAGGERS = 4
        const val SEEDS = 5
        const val THANKS = 6
    }
}
