package org.rsmod.content.quest.area.mortmyre.naturespirit

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.BLESSED_SICKLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FILLIMAN
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.NATURE_SPIRIT
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.POUCH_EMPTY
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.SILVER_SICKLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_ALL_GHASTS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_POUCH_GIVEN
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_PUZZLE_SOLVED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_SICKLE_BLESSED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_TRANSFORMED
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.SecateursEnchantment
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Inside Filliman's grotto. Searching the grotto calls Filliman, who completes his transformation
 * into a nature spirit in front of the player; the spirit then blesses a silver sickle, hands over
 * a druid pouch and sends the player after three ghasts. When they are slain the grotto becomes the
 * Altar of Nature, which is the level-1 copy of the same room, so the player is moved up into it.
 */
class NatureGrotto
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val spirits: SpiritSpawns,
    private val objRepo: ObjRepository,
    private val secateurs: SecateursEnchantment,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GROTTO) { searchGrotto() }
        onOpLocU(GROTTO, SILVER_SICKLE) { dipSickle(it.loc) }
        onOpLoc1(ALTAR) { prayAtAltar() }
        onOpLocU(ALTAR, SILVER_SICKLE) { dipSickle(it.loc) }
        onOpLoc1(DOOR) { exitGrotto() }
        onOpLoc1(DOOR_GREEN) { exitGrotto() }
        onOpNpc1(NATURE_SPIRIT) { startDialogue(it.npc) { natureSpirit(it.npc) } }
        onOpNpcU(NATURE_SPIRIT) {
            if (it.objType.internalName == SILVER_SICKLE || it.objType.internalName == SECATEURS) {
                startDialogue(it.npc) { natureSpirit(it.npc) }
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun ProtectedAccess.searchGrotto() {
        arriveDelay()
        val stage = natureSpirit.stage(player)
        when {
            stage == STAGE_PUZZLE_SOLVED -> transformation()
            stage in STAGE_TRANSFORMED until natureSpirit.quest.maxSteps -> {
                val spirit = spirits.summon(NATURE_SPIRIT, MortMyreCoords.SPIRIT_IN_GROTTO, Direction.South)
                spirit.facePlayer(player)
                startDialogue(spirit) { natureSpirit(spirit) }
            }
            else -> mes("You search the grotto but find nothing.")
        }
    }

    private suspend fun ProtectedAccess.transformation() {
        val filliman = spirits.summon(FILLIMAN, MortMyreCoords.SPIRIT_IN_GROTTO, Direction.South)
        filliman.facePlayer(player)
        faceEntitySquare(filliman)
        startDialogue(filliman) {
            chatNpc(
                happy,
                "Well, hello there again, I was just enjoying the grotto. Many thanks for your help, " +
                    "I couldn't have become a Spirit of nature without you.",
            )
            chatNpc(happy, "I must complete the transformation now. Just stand there and watch the show, apparently it's quite good!")
        }
        filliman.anim(CAST_SEQ)
        filliman.spotanim(SHOOTING_STAR_SPOTANIM)
        soundSynth(TRANSFORM_START_SOUND)
        delay(TRANSFORM_TICKS)
        filliman.spotanim(TRANSFORM_SPOTANIM)
        soundSynth(TRANSFORM_SOUND)
        spirits.dismiss(FILLIMAN)
        val spirit = spirits.summon(NATURE_SPIRIT, MortMyreCoords.SPIRIT_IN_GROTTO, Direction.South)
        spirit.facePlayer(player)
        natureSpirit.advanceTo(this, STAGE_TRANSFORMED)
        startDialogue(spirit) {
            chatNpc(
                happy,
                "Hmmm, good, the transformation is complete. Now, my friend, in return for your " +
                    "assistance, I will help you to kill the Ghasts. First bring to me a silver " +
                    "sickle so that I can bless it for you.",
            )
            chatPlayer(quiz, "A silver sickle? What's that?")
            chatNpc(
                neutral,
                "The sickle is the symbol and weapon of the Druid, you need to construct one of " +
                    "silver so that I can bless it, with its powers you will be able to defeat the " +
                    "Ghasts of Mort Myre.",
            )
            sickleQuestions()
        }
    }

    private suspend fun Dialogue.natureSpirit(spirit: Npc) {
        spirit.facePlayer(player)
        val stage = natureSpirit.stage(player)
        when {
            stage < STAGE_TRANSFORMED -> Unit
            stage < STAGE_SICKLE_BLESSED -> bringSickle()
            stage < STAGE_POUCH_GIVEN -> givePouch()
            stage < STAGE_ALL_GHASTS -> ghastQuestions()
            stage == STAGE_ALL_GHASTS -> complete()
            secateurs.hasBusiness(player) -> secateurs.talk(this, spirit)
            else -> access.mes("This spirit seems to be busy.")
        }
    }

    private suspend fun Dialogue.sickleQuestions() {
        while (true) {
            when (
                choice4(
                    "Where would I get a silver sickle?",
                    1,
                    "What will you do to the silver sickle?",
                    2,
                    "How can a blessed sickle help me to defeat the Ghasts?",
                    3,
                    "Ok, thanks.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where would I get a silver sickle?")
                    chatNpc(
                        neutral,
                        "You could make one yourself if you're artisan enough. I've heard of a " +
                            "distant sandy place where you can buy the mould that you require, it's " +
                            "similar in many respects to the creating of a holy symbol.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "What will you do to the silver sickle?")
                    chatNpc(happy, "Why, I will give it my blessings so that the very swamp in which you stand will blossom and bloom!")
                }
                3 -> {
                    chatPlayer(quiz, "How can a blessed sickle help me to defeat the Ghasts?")
                    chatNpc(
                        neutral,
                        "My blessings will entice nature to bloom in Mort Myre! And then with natures " +
                            "harvest you can fill a druids pouch and release the Ghasts from their " +
                            "torment.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.bringSickle() {
        chatNpc(quiz, "Have you brought me the silver sickle?")
        if (SILVER_SICKLE !in player.inv) {
            chatPlayer(sad, "No sorry, not yet!")
            chatNpc(neutral, "Well, come to me when you have it.")
            sickleQuestions()
            return
        }
        chatPlayer(happy, "Yes, here it is. What are you going to do with it?")
        chatNpc(
            neutral,
            "My friend, I will bless it for you and you will then be able to accomplish great things. " +
                "Now then, I must cast the enchantment. You can bless a new sickle by dipping it in " +
                "the holy water of the grotto.",
        )
        npc?.anim(CAST_SEQ)
        npc?.spotanim(TRANSFORM_SPOTANIM)
        access.soundSynth(BLESS_SOUND)
        access.invReplace(access.inv, SILVER_SICKLE, 1, BLESSED_SICKLE)
        natureSpirit.advanceTo(access, STAGE_SICKLE_BLESSED)
        objbox(
            BLESSED_SICKLE,
            "Your sickle has been blessed!<br><col=0000ff>~ If you lose the blessed sickle, you can " +
                "bless ~<br>~ a new sickle by dipping it in the grotto waters. ~</col>",
        )
        chatNpc(
            neutral,
            "Now you can go forth and make the swamp bloom. Collect natures bounty to fill a druids " +
                "pouch. So armed will the Ghasts be bound to you until, you flee or they are defeated.",
        )
        chatNpc(
            neutral,
            "Before I can make this grotto into an Altar of Nature, I need to be sure that the " +
                "Ghasts will be kept at bay. Go forth into Mort Myre and slay three Ghasts. You'll be " +
                "releasing their souls from Mort Myre.",
        )
        givePouch()
    }

    private suspend fun Dialogue.givePouch() {
        if (player.inv.isFull()) {
            chatNpc(neutral, "I would give you the druid pouch, but you don't seem to have enough space in your inventory.")
            return
        }
        if (POUCH_EMPTY !in player.inv && POUCH !in player.inv) {
            access.invAdd(access.inv, POUCH_EMPTY)
        }
        objbox(POUCH_EMPTY, "The nature spirit gives you an empty pouch.")
        natureSpirit.advanceTo(access, STAGE_POUCH_GIVEN)
        chatNpc(
            neutral,
            "You'll need this in order to collect together natures bounty. When it contains items, " +
                "it will bind the Ghast to you until you flee or it is defeated.",
        )
    }

    private suspend fun Dialogue.ghastQuestions() {
        chatNpc(quiz, "Hello again my friend, have you defeated three Ghasts as I asked you?")
        chatPlayer(neutral, "Not yet.")
        chatNpc(neutral, "Well, when you do, please come to me and I'll reward you!")
        while (true) {
            when (
                choice5(
                    "How do I get to attack the Ghasts?",
                    1,
                    "What's this pouch for?",
                    2,
                    "What can I do with this sickle?",
                    3,
                    "I've lost my sickle.",
                    4,
                    "Ok, thanks.",
                    5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "How do I get to attack the Ghasts?")
                    chatNpc(
                        neutral,
                        "Go forth and with the sickle make the swamp bloom. Collect natures bounty to " +
                            "fill a druids pouch. So armed will the Ghasts be bound to you until, you " +
                            "flee or they are defeated.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "What's this pouch for?")
                    chatNpc(
                        neutral,
                        "It is for collecting natures bounty, once it contains the blossomed items of " +
                            "the swamp, it will make the Ghasts appear and you can then attack them.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What can I do with this sickle?")
                    chatNpc(
                        neutral,
                        "You may use it wisely within the area of Mort Myre to affect natures balance " +
                            "and bring forth a bounty of natures harvest. Once collected into the druid " +
                            "pouch will the Ghast be apparent.",
                    )
                }
                4 -> {
                    chatPlayer(worried, "I've lost my sickle!")
                    chatNpc(
                        neutral,
                        "If you should lose the blessed sickle, simply bring another to my altar of " +
                            "nature and refresh it in the grotto waters.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.complete() {
        chatNpc(quiz, "Hello again my friend, have you defeated three Ghasts as I asked you?")
        chatPlayer(happy, "Yes, I've killed all three and their spirits have been released!")
        chatNpc(
            happy,
            "Many thanks my friend, you have completed your quest! I can now change this place into " +
                "a holy sanctuary! And forever will it now be an Altar of Nature!",
        )
        chatNpc(happy, "Welcome to my Altar to Nature! Farewell my friend, and keep those Ghasts at bay!")
        npc?.anim(CAST_SEQ)
        npc?.spotanim(TRANSFORM_SPOTANIM)
        access.soundSynth(TRANSFORM_SOUND)
        natureSpirit.quest.completeQuest(access)
        spirits.dismiss(NATURE_SPIRIT)
        val coords = player.coords
        access.telejump(CoordGrid(coords.x, coords.z, MortMyreCoords.ALTAR_ENTRY.level), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.dipSickle(loc: BoundLocInfo) {
        arriveDelay()
        if (natureSpirit.stage(player) < STAGE_SICKLE_BLESSED) {
            mes("Nothing interesting happens.")
            return
        }
        faceLoc(loc)
        anim(DIP_SEQ)
        soundSynth(BLESS_SOUND)
        invReplace(inv, SILVER_SICKLE, 1, BLESSED_SICKLE)
        objbox(BLESSED_SICKLE, "You dip the sickle into the grotto waters. Your sickle has been blessed!")
    }

    private suspend fun ProtectedAccess.prayAtAltar() {
        arriveDelay()
        val max = player.statBase(PRAYER) + ALTAR_BOOST
        if (player.stat(PRAYER) >= max) {
            mes("You already have full Prayer points.")
            return
        }
        anim(PRAY_SEQ)
        soundSynth(BLESS_SOUND)
        statHeal(PRAYER, constant = player.statBase(PRAYER), percent = 0)
        statBoost(PRAYER, constant = ALTAR_BOOST, percent = 0)
        mes("You recharge your Prayer points.")
    }

    private suspend fun ProtectedAccess.exitGrotto() {
        arriveDelay()
        delay(1)
        telejump(MortMyreCoords.GROTTO_DOOR_OUTSIDE, TeleportType.Exempt)
    }

    private companion object {
        const val GROTTO = "loc.druidic_spirit_grotto"
        const val ALTAR = "loc.druidic_spirit_grotto_naturealtar"
        const val DOOR = "loc.underground_rootwall_door"
        const val DOOR_GREEN = "loc.underground_rootwall_door_green"

        const val PRAYER = "stat.prayer"
        const val ALTAR_BOOST = 2

        const val CAST_SEQ = "seq.human_castteleport"
        const val DIP_SEQ = "seq.human_pickuptable"
        const val PRAY_SEQ = "seq.human_pray"
        const val SHOOTING_STAR_SPOTANIM = "spotanim.druid_shooting_star"
        const val TRANSFORM_SPOTANIM = "spotanim.druidicspirit_effect"
        const val TRANSFORM_START_SOUND = "synth.spirit_transform_start"
        const val TRANSFORM_SOUND = "synth.spirit_transform"
        const val BLESS_SOUND = "synth.prayer_recharge"
        const val TRANSFORM_TICKS = 3
    }
}
