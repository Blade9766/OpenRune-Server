package org.rsmod.content.quest.area.ikov.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.ARMADYL_PENDANT
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.GUARDIANS
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_ARMADYL
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_LUCIEN
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ikov.ikovSidedWithLucien
import org.rsmod.content.quest.area.ikov.wearingArmadylPendant
import org.rsmod.content.quest.area.ikov.wearingPendantOfLucien
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Guardians of Armadyl, the order that has kept the staff since before Varrock was built.
 *
 * They read the pendant round a visitor's neck before they read their face: anyone still wearing
 * Lucien's is an agent of his and is set on at once. Take the pendant off and the eldest of them
 * will talk, cleanse the player of Lucien's mark and hand over an Armadyl pendant of their own -
 * the only thing that makes a Mahjarrat mortal.
 *
 * A player who sided with Lucien can still get the Armadyl pendant afterwards, by killing a
 * guardian for it; that is handled in [guardianDefeated].
 */
class GuardiansOfArmadyl
@Inject
constructor(
    private val quest: TempleOfIkovQuest,
    private val death: NpcDeath,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (guardian in GUARDIANS) {
            onOpNpc1(guardian) { startDialogue(it.npc) { talk() } }
            val type =
                ServerCacheManager.getNpc(guardian.asRSCM(RSCMType.NPC))
                    ?: error("Missing npc: $guardian")
            onNpcQueue(type, "queue.death") { guardianDefeated() }
        }
    }

    private suspend fun Dialogue.talk() {
        val thief = quest.stage(access.player) == STAGE_LUCIEN && !quest.isComplete(access.player)
        if (thief || access.player.wearingPendantOfLucien()) {
            chatNpc(angry, "Thou art a foul agent of Lucien! Such an agent must die!")
            access.setGuardiansOn()
            return
        }
        if (quest.isComplete(access.player)) {
            afterTheQuest()
            return
        }
        chatNpc(
            neutral,
            "Thou hast ventured deep into the tunnels; thou hast reached the temple of our " +
                "master. It is many ages since a pilgrim came here.",
        )
        val opening =
            choice3(
                "I seek the Staff of Armadyl.",
                1,
                "Who is your master?",
                2,
                "I am only passing through.",
                3,
            )
        when (opening) {
            1 -> aboutTheStaff()
            2 -> {
                aboutArmadyl()
                aboutTheStaff()
            }
            else -> chatNpc(neutral, "Then pass in peace, and touch nothing.")
        }
    }

    private suspend fun Dialogue.aboutArmadyl() {
        chatPlayer(quiz, "Who is your master?")
        chatNpc(
            neutral,
            "Armadyl, lord of the sky. This is the last of his temples left standing in Gielinor, " +
                "and we are what is left of his people.",
        )
    }

    private suspend fun Dialogue.aboutTheStaff() {
        chatPlayer(neutral, "I seek the Staff of Armadyl.")
        chatNpc(
            quiz,
            "We are the guardians of the staff. Our fathers were guardians, and our fathers' " +
                "fathers before that. Why dost thou seek it?",
        )
        val answer =
            choice2(
                "Lucien will give me a grand reward for it!",
                true,
                "It is safer in my hands than in his.",
                false,
            )
        if (!answer) {
            chatPlayer(neutral, "It is safer in my hands than in his.")
            chatNpc(angry, "It is safest where it lies. Do not reach for it.")
            return
        }
        chatPlayer(happy, "Lucien will give me a grand reward for it!")
        chatNpc(
            angry,
            "Thou art working for that spawn of evil?! Fool! Thou must be cleansed to save thy " +
                "soul!",
        )
        val cleansed =
            choice2(
                "You're right, it's time for my yearly bath.",
                true,
                "Keep your water, old man.",
                false,
            )
        if (!cleansed) {
            chatPlayer(neutral, "Keep your water, old man.")
            chatNpc(angry, "Then thou art his, and there is nothing here for thee.")
            return
        }
        chatPlayer(happy, "You're right, it's time for my yearly bath.")
        cleanse()
    }

    private suspend fun Dialogue.cleanse() {
        access.anim(CLEANSE_SEQ)
        access.spotanim(CLEANSE_SPOTANIM, height = CLEANSE_HEIGHT)
        access.soundSynth(CLEANSE_SOUND)
        access.delay(2)
        chatNpc(neutral, "It is done. His mark is off thee.")
        chatNpc(
            worried,
            "Hear us now. Lucien is no man. He is Mahjarrat, a thing that has fed on war since " +
                "before thy people had a word for it, and he is a necromancer besides.",
        )
        chatNpc(
            worried,
            "With the staff in his hand no army in Gielinor would stand a season against him. He " +
                "must not have it, and he must not be left to try again.",
        )
        chatPlayer(quiz, "So what do you want me to do about it?")
        chatNpc(
            neutral,
            "Kill him. Steel alone will not touch a Mahjarrat, but this pendant will make him as " +
                "mortal as thee. Wear it when thou goest to him.",
        )
        val help = choice2("Ok! I'll help!", true, "I want no part of this.", false)
        if (!help) {
            chatPlayer(neutral, "I want no part of this.")
            chatNpc(sad, "Then Gielinor must hope for another.")
            return
        }
        chatPlayer(happy, "Ok! I'll help!")
        giveArmadylPendant()
        if (quest.stage(access.player) == STAGE_STARTED) {
            quest.advanceTo(access, STAGE_ARMADYL)
        }
    }

    private suspend fun Dialogue.giveArmadylPendant() {
        access.invAdd(access.inv, ARMADYL_PENDANT)
        objbox(
            ARMADYL_PENDANT,
            "The guardian lifts an Armadyl pendant over your head. It is lighter than it looks.",
        )
    }

    private suspend fun Dialogue.afterTheQuest() {
        if (access.player.ikovSidedWithLucien) {
            chatNpc(angry, "Thief. The staff was ours to keep and thou gavest it to that thing.")
            return
        }
        chatNpc(happy, "The Mahjarrat is fallen and the sky is a little clearer for it. Well met.")
    }

    /** Every guardian within the chamber turns on the player at once. */
    fun ProtectedAccess.setGuardiansOn() {
        for (guardian in GUARDIANS) {
            for (npc in search.findAll(player.coords, guardian, CHAMBER_RADIUS, HuntVis.Off)) {
                npc.facePlayer(player)
                npc.opPlayer2(player, aiInteractions)
            }
        }
    }

    /**
     * The guardians carry their pendants on them. Someone who took Lucien's side and has no
     * Armadyl pendant of their own can still take one this way, which is how the quest's reward
     * is reached from that ending.
     */
    private suspend fun StandardNpcAccess.guardianDefeated() {
        val killer = findHero(playerList)
        val coords = npc.coords
        death.deathWithDrops(this)
        if (killer == null || !owedAPendant(killer)) {
            return
        }
        objRepo.add(ARMADYL_PENDANT, coords, PENDANT_DURATION, receiver = killer)
    }

    private fun owedAPendant(player: Player): Boolean =
        quest.isComplete(player) && ARMADYL_PENDANT !in player.inv && !player.wearingArmadylPendant()

    private companion object {
        const val CHAMBER_RADIUS = 16
        const val PENDANT_DURATION = 200

        const val CLEANSE_SEQ = "seq.human_bow"
        const val CLEANSE_SPOTANIM = "spotanim.druidicspirit_priest_bless"
        const val CLEANSE_HEIGHT = 0
        const val CLEANSE_SOUND = "synth.holy_water_pour"
    }
}
