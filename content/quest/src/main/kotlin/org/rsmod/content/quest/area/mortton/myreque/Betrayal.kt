package org.rsmod.content.quest.area.mortton.myreque

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.BIG_BONES
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.HELLHOUND
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BETRAYED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_WEAPONS_DELIVERED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.UNCUT_RUBY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.VELIAF
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Vanstrom's betrayal in the hideout and the Skeleton Hellhound he leaves behind.
 *
 * The scene plays once the weapons are handed over ([STAGE_WEAPONS_DELIVERED]). Its actors are
 * spawned for the length of it and removed in a `finally`, and the stage only moves on to
 * [STAGE_BETRAYED] at the moment the blood spell lands - that is also what makes the cache hide
 * the real Sani and Harold. A scene cut short before then simply plays again the next time the
 * player enters the hideout or speaks to Veliaf.
 *
 * At [STAGE_BETRAYED] the player always has a hellhound of their own: it is bound to them,
 * nobody else may attack it, it hunts nobody else, and a new one replaces it whenever the old
 * one is gone (the player died, fled the hideout, or it timed out). Its death drops the loot and
 * moves the quest on exactly once, keyed on the stage.
 */
@Singleton
class Betrayal
@Inject
constructor(
    private val myq: InSearchOfTheMyrequeQuest,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val owners = HashMap<Npc, PlayerUid>()
    private val inScene = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Delete> { owners.remove(npc) }
        onNpcQueue(npcType(HELLHOUND), "queue.death") { houndDied() }
    }

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc]

    fun houndOf(player: Player): Npc? = owners.entries.firstOrNull { it.value == player.uid && it.key.isSlotAssigned }?.key

    fun isInScene(player: Player): Boolean = player.uid in inScene

    suspend fun ProtectedAccess.enteredHideout() {
        when (myq.stage(player)) {
            STAGE_WEAPONS_DELIVERED -> playScene()
            STAGE_BETRAYED -> releaseHound()
        }
    }

    /** A hound left behind would only wait for its owner; it is sent away and a fresh one comes back. */
    fun ProtectedAccess.leaveHideout() {
        houndOf(player)?.let { npcRepo.del(it, Int.MAX_VALUE) }
    }

    /** Sets the player's hound on them, reusing one that is still alive in the hideout. */
    fun ProtectedAccess.releaseHound(): Npc {
        val hound = summonHound()
        val uid = player.uid
        worldQueues.add(HOUND_ATTACK_DELAY) {
            val target = uid.resolve(playerList) ?: return@add
            if (hound.isSlotAssigned) {
                hound.opPlayer2(target, aiInteractions)
            }
        }
        return hound
    }

    /** The player's hound, standing idle until it is released; a stray one elsewhere is replaced. */
    private fun ProtectedAccess.summonHound(): Npc {
        val existing = houndOf(player)
        if (existing != null && MyrequeCoords.inHideout(existing.coords)) {
            return existing
        }
        existing?.let { npcRepo.del(it, Int.MAX_VALUE) }
        val hound = Npc(HELLHOUND, MyrequeCoords.HOUND_SPAWN)
        hound.setHunt(0)
        hound.respawnDir = Direction.South
        npcRepo.add(hound, HOUND_LIFETIME)
        owners[hound] = player.uid
        hound.spotanim(HOUND_ARRIVE_SPOT)
        return hound
    }

    /**
     * Plays the betrayal for one player. The two victims and Vanstrom are spawned actors; the
     * chatbox carries the dialogue, so only the camera, minimap, side tabs and entity ops are
     * taken away for its length.
     */
    suspend fun ProtectedAccess.playScene() {
        if (myq.stage(player) != STAGE_WEAPONS_DELIVERED || !inScene.add(player.uid)) {
            return
        }
        val actors = ArrayList<Npc>()
        var betrayed = false
        try {
            beginScene()
            val sani = actor(SANI_ACTOR, MyrequeCoords.SANI, Direction.West).also(actors::add)
            val harold = actor(HAROLD_ACTOR, MyrequeCoords.HAROLD, Direction.East).also(actors::add)
            camMoveTo(MyrequeCoords.SCENE_CAMERA, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            camLookAt(MyrequeCoords.SCENE_LOOK, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            delay(1)

            val mist = actor(MIST_ACTOR, MyrequeCoords.MIST_ENTRY, Direction.North).also(actors::add)
            mist.spotanim(MIST_SPOT)
            soundSynth(MIST_SOUND)
            dimLights()
            mes("A cold, grey mist seeps into the cave.")
            startDialogue {
                chatNpcSpecific(VELIAF_NAME, VELIAF, worried, "Where is that mist coming from? Everyone, stay sharp!")
                chatNpcSpecific(SANI_NAME, SANI_CHAT, worried, "It's coming from the tunnel... it's moving against the draught.")
            }

            mist.anim(MIST_CLEAR_SEQ)
            delay(MIST_CLEAR_TICKS)
            npcRepo.del(mist, Int.MAX_VALUE)
            val vanstrom = actor(VANSTROM_ACTOR, MyrequeCoords.MIST_ENTRY, Direction.North).also(actors::add)
            vanstrom.facePlayer(player)
            vanstrom.anim(REVEAL_SEQ)
            vanstrom.spotanim(TRANSFORM_SPOT)
            delay(REVEAL_TICKS)
            startDialogue {
                chatNpcSpecific(VANSTROM_NAME, VANSTROM_ACTOR, laugh, "Thank you, my friend. I have spent years looking for this little nest, and you led me straight to it.")
                chatPlayer(shocked, "Vanstrom?! You... you're a vampyre!")
                chatNpcSpecific(VELIAF_NAME, VELIAF, angry, "You brought him here? You fool, you've doomed us all!")
                chatPlayer(worried, "I didn't know! He said he wanted to help you!")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM_ACTOR, neutral, "Steel for the Myreque. A touching gesture. Let me show you what it buys against your betters.")
            }

            vanstrom.faceSquare(MyrequeCoords.SANI)
            vanstrom.anim(CAST_SEQ)
            soundSynth(BLOOD_CAST_SOUND)
            delay(CAST_TICKS)
            sani.spotanim(BLOOD_IMPACT_SPOT)
            harold.spotanim(BLOOD_IMPACT_SPOT)
            soundSynth(BLOOD_IMPACT_SOUND)
            myq.advanceTo(this, STAGE_BETRAYED)
            betrayed = true
            sani.anim(COLLAPSE_SEQ)
            harold.anim(COLLAPSE_SEQ)
            delay(COLLAPSE_TICKS)
            npcRepo.del(sani, Int.MAX_VALUE)
            npcRepo.del(harold, Int.MAX_VALUE)
            mes("Sani Piliu and Harold Evans fall, and do not rise.")

            startDialogue {
                chatNpcSpecific(IVAN_NAME, IVAN, sad, "Sani! Harold! No!")
                chatNpcSpecific(VANSTROM_NAME, VANSTROM_ACTOR, neutral, "Grieve later, boy. My pet will make sure there is no later. Farewell, Myreque.")
            }
            vanstrom.facePlayer(player)
            vanstrom.anim(SUMMON_SEQ)
            delay(SUMMON_TICKS)
            summonHound()
            vanstrom.anim(DEPART_SEQ)
            vanstrom.spotanim(MIST_SPOT)
            soundSynth(MIST_SOUND)
            delay(DEPART_TICKS)
            npcRepo.del(vanstrom, Int.MAX_VALUE)
        } finally {
            for (actor in actors) {
                if (actor.isSlotAssigned) {
                    npcRepo.del(actor, Int.MAX_VALUE)
                }
            }
            endScene()
            inScene.remove(player.uid)
        }
        if (!betrayed) {
            return
        }
        startDialogue {
            chatNpcSpecific(VELIAF_NAME, VELIAF, angry, "He's left a hellhound! Kill it before it tears us apart!")
            chatNpcSpecific(VELIAF_NAME, VELIAF, neutral, "It hits hard but goes down fast. If it's too much, fight from the gap by the north-east bed, where it can't get its jaws on you.")
        }
        releaseHound()
    }

    private fun ProtectedAccess.actor(type: String, coords: CoordGrid, face: Direction): Npc {
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        npc.respawnDir = face
        npcRepo.add(npc, SCENE_LIFETIME)
        return npc
    }

    private fun ProtectedAccess.beginScene() {
        camModeClose()
        hideEntityOps()
        minimapHideMap()
        closeTopLevelTabsLenient()
    }

    private fun ProtectedAccess.endScene() {
        closeFadeOverlay()
        camReset()
        camModeReset()
        showEntityOps()
        minimapReset()
        openTopLevelTabs()
    }

    private fun ProtectedAccess.dimLights() {
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = DIM_COLOUR,
            endTransparency = DIM_TRANSPARENCY,
            clientDuration = DIM_DURATION,
        )
    }

    private suspend fun StandardNpcAccess.houndDied() {
        val owner = owners[npc]
        val hero = findHero(playerList)
        val dropAt = npc.coords
        death.deathNoDrops(this)
        rewardKill(owner, hero, dropAt)
    }

    /** Loot and progress go to the hound's owner, once, while the quest is waiting on the fight. */
    internal fun rewardKill(owner: PlayerUid?, hero: Player?, dropAt: CoordGrid) {
        if (hero == null || owner != hero.uid || myq.stage(hero) != STAGE_BETRAYED) {
            return
        }
        myq.markHoundSlain(hero)
        repeat(RUBIES) { objRepo.add(UNCUT_RUBY, dropAt, LOOT_TICKS, hero) }
        repeat(BONES) { objRepo.add(BIG_BONES, dropAt, LOOT_TICKS, hero) }
        hero.mes("The Skeleton Hellhound collapses into a heap of blackened bones.")
        hero.mes("You should speak to Veliaf.")
    }

    private fun npcType(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    internal companion object {
        const val SANI_ACTOR = "npc.route_sani_piliu_cutscene"
        const val HAROLD_ACTOR = "npc.route_harold_evans_cutscene"
        const val MIST_ACTOR = "npc.route_vanstrom_vampire_misty"
        const val VANSTROM_ACTOR = "npc.route_vanstrom_vampire"
        const val SANI_CHAT = "npc.route_sani_piliu_vis"
        const val IVAN = "npc.route_ivan_strom"

        const val VELIAF_NAME = "Veliaf Hurtz"
        const val SANI_NAME = "Sani Piliu"
        const val IVAN_NAME = "Ivan Strom"
        const val VANSTROM_NAME = "Vanstrom Klause"

        /** Vanstrom's vyrelord form animates on skeleton 1914 and the mist on 680; these all match. */
        const val MIST_CLEAR_SEQ = "seq.mist_disappear"
        const val REVEAL_SEQ = "seq.vyrelord_human_to_vampire_fade"
        const val CAST_SEQ = "seq.vyrelord_head_vamp_spell"
        const val SUMMON_SEQ = "seq.vyrelord_summon"
        const val DEPART_SEQ = "seq.vyrelord_drakan_despawn"
        const val COLLAPSE_SEQ = "seq.human_death"
        const val MIST_SPOT = "spotanim.misty"
        const val TRANSFORM_SPOT = "spotanim.myq3_head_vampyre_transform"
        const val BLOOD_IMPACT_SPOT = "spotanim.spell_blood_burst_impact"
        const val HOUND_ARRIVE_SPOT = "spotanim.misty"
        const val MIST_SOUND = "synth.vampire_arrives"
        const val BLOOD_CAST_SOUND = "synth.blood_cast"
        const val BLOOD_IMPACT_SOUND = "synth.blood_burst_impact"

        const val CAMERA_HEIGHT = 520
        const val LOOK_HEIGHT = 120
        const val CAMERA_RATE = 100
        const val DIM_COLOUR = 0x200010
        const val DIM_TRANSPARENCY = 170
        const val DIM_DURATION = 60

        const val COLLAPSE_TICKS = 3
        const val MIST_CLEAR_TICKS = 2
        const val REVEAL_TICKS = 4
        const val CAST_TICKS = 3
        const val SUMMON_TICKS = 3
        const val DEPART_TICKS = 4
        const val SCENE_LIFETIME = 300
        const val HOUND_LIFETIME = 1000
        const val HOUND_ATTACK_DELAY = 2

        const val RUBIES = 2
        const val BONES = 4
        const val LOOT_TICKS = 300
    }
}

/** The hellhound is the summoner's own fight. */
class HoundAttackHook @Inject constructor(private val betrayal: Betrayal) : NpcAttackValidateHook {
    private val houndId = HELLHOUND.asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != houndId) {
            return NpcAttackValidateResult.Pass
        }
        val owner = betrayal.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("That hellhound is someone else's fight.")
        }
        return NpcAttackValidateResult.Pass
    }
}
