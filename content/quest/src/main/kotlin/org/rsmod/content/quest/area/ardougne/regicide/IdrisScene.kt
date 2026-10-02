package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.ESSYLLT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IDRIS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.MORVRAN
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_ELVES
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The welcome party on a player's first arrival in Isafdar. Idris, one of the elven rebels,
 * stops the player outside the cave mouth and is shot down by Morvran and Essyllt before he can
 * finish, and they send the player on to Lord Iorwerth.
 *
 * The scene plays in a private copy of the clearing, so no other player sees the elves, and it
 * only counts once the elves have finished speaking: a player who logs out or is pulled away
 * part way through is returned to where they stood and meets the elves again next time. Lord
 * Iorwerth will also see a player who somehow never met them.
 */
@Singleton
class IdrisScene
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val scenes: RegicideScenes,
) : PluginScript() {
    private val playing = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        onArea(ARRIVAL_AREA) { meetElves() }
        onEvent<SessionStateEvent.PrepareLogin> { scenes.restoreLogin(player) }
        onPlayerLogin { scenes.discardLeakedRegion(player) }
    }

    /** Called by the cave mouth as well, since a telejump into the clearing may not count as entering it. */
    suspend fun trigger(access: ProtectedAccess) {
        if (regicide.stage(access.player) == STAGE_STARTED) {
            access.meetElves()
        }
    }

    suspend fun ProtectedAccess.meetElves() {
        if (regicide.stage(player) != STAGE_STARTED || !playing.add(player.uid)) {
            return
        }
        val returnTo = coords
        val spawned = mutableListOf<Npc>()
        try {
            with(scenes) {
                privateScene(
                    template = TEMPLATE,
                    vantage = VANTAGE,
                    faceAt = IDRIS_STOP,
                    camera = CAMERA,
                    returnTo = returnTo,
                ) { r ->
                    val idris = scenes.spawn(IDRIS, r[IDRIS_START]).also(spawned::add)
                    idris.walk(r[IDRIS_STOP])
                    delay(3)
                    idris.facePlayer(player)
                    startDialogue {
                        chatNpcSpecific("Idris", IDRIS, angry, "Halt human! What are you doing here?")
                        chatNpcSpecific("Idris", IDRIS, shocked, "Wait! What was that? What the...")
                    }
                    val morvran = scenes.spawn(MORVRAN, r[MORVRAN_START]).also(spawned::add)
                    val essyllt = scenes.spawn(ESSYLLT, r[ESSYLLT_START]).also(spawned::add)
                    for (elf in listOf(morvran, essyllt)) {
                        elf.faceNpc(idris)
                        elf.anim(SHOOT_SEQ)
                    }
                    delay(1)
                    idris.anim(DEATH_SEQ)
                    delay(DEATH_TICKS)
                    scenes.remove(idris)
                    morvran.walk(r[MORVRAN_STOP])
                    essyllt.walk(r[ESSYLLT_STOP])
                    delay(3)
                    morvran.facePlayer(player)
                    essyllt.facePlayer(player)
                    startDialogue {
                        chatNpcSpecific("Morvran", MORVRAN, quiz, "Are you the human by the name of ${player.displayName}?")
                        chatPlayer(neutral, "Yes that's me.")
                        chatNpcSpecific("Essyllt", ESSYLLT, neutral, "Good... We've been expecting you. King Lathas sent word of your coming.")
                        chatNpcSpecific("Essyllt", ESSYLLT, neutral, "You should speak with Lord Iorwerth. He will help you track down that brigand Tyras. You'll find his camp in the north west of the forest.")
                    }
                    regicide.advanceTo(this, STAGE_MET_ELVES)
                }
            }
        } finally {
            spawned.forEach(scenes::remove)
            playing.remove(player.uid)
        }
    }

    companion object {
        const val ARRIVAL_AREA = "area.regicide_isafdar_arrival"
        const val SHOOT_SEQ = "seq.human_bow"
        const val DEATH_SEQ = "seq.human_death"
        const val DEATH_TICKS = 3

        /** The clearing outside the cave mouth: zones 287-289 by 401-403, x 2296-2319 and z 3208-3231. */
        const val ZONE_X = 287
        const val ZONE_Z = 401
        const val BLOCK_ZONES = 3
        private const val BLOCK_OFFSET_ZONES = 6

        val VANTAGE = CoordGrid(2310, 3214, 0)
        val IDRIS_START = CoordGrid(2302, 3212, 0)
        val IDRIS_STOP = CoordGrid(2307, 3213, 0)
        val MORVRAN_START = CoordGrid(2301, 3211, 0)
        val ESSYLLT_START = CoordGrid(2303, 3210, 0)
        val MORVRAN_STOP = CoordGrid(2308, 3214, 0)
        val ESSYLLT_STOP = CoordGrid(2308, 3212, 0)

        internal val CAMERA = SceneCamera(eye = CoordGrid(2314, 3218, 0), eyeHeight = 700, lookAt = CoordGrid(2306, 3213, 0), lookAtHeight = 100)

        internal val TEMPLATE: RegionTemplate =
            RegionTemplate.create {
                copy(ZONE_X, ZONE_Z, 0) {
                    regionZoneX = BLOCK_OFFSET_ZONES
                    regionZoneZ = BLOCK_OFFSET_ZONES
                    zoneWidth = BLOCK_ZONES
                    zoneLength = BLOCK_ZONES
                }
            }

        val SCENE_TILES: List<CoordGrid>
            get() = listOf(VANTAGE, IDRIS_START, IDRIS_STOP, MORVRAN_START, ESSYLLT_START, MORVRAN_STOP, ESSYLLT_STOP, CAMERA.eye, CAMERA.lookAt)
    }
}
