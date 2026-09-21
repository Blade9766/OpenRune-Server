package org.rsmod.content.quest.area.camelot.merlinscrystal

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BEEHIVE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BEES_SOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BUCKET
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BUCKET_OF_WAX
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.INSECT_REPELLENT
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.LIT_BLACK_CANDLE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.LIT_CANDLE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.PICK_UP_SEQ
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.TINDERBOX
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The hives in the fenced field north of Keep Le Faye, which are the only source of the bucket of
 * wax the Catherby candle maker wants. The repellent drives the bees out for one bucketful and
 * then wears off, so each bucket of wax costs another dose.
 */
class Beehives @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BEEHIVE) { takeWax() }
        onOpLocU(BEEHIVE, BUCKET) { takeWax() }
        onOpLocU(BEEHIVE, INSECT_REPELLENT) { pourRepellent() }
        for (flame in listOf(TINDERBOX, LIT_CANDLE, LIT_BLACK_CANDLE)) {
            onOpLocU(BEEHIVE, flame) {
                mes("Burning the hive would be a rather extreme measure for repelling bees.")
            }
        }
    }

    private suspend fun ProtectedAccess.pourRepellent() {
        arriveDelay()
        if (player.merlinBeehiveFree) {
            mesbox("You pour insect repellent on the beehive again. The bees have already left.")
            return
        }
        player.merlinBeehiveFree = true
        soundSynth(BEES_SOUND)
        mesbox("You pour insect repellent on the beehive. You see bees leaving the hive.")
    }

    private suspend fun ProtectedAccess.takeWax() {
        arriveDelay()
        if (!inv.contains(BUCKET)) {
            mesbox("You'd need a suitable container to carry the wax in.")
            return
        }
        if (!player.merlinBeehiveFree) {
            soundSynth(BEES_SOUND)
            mes("Suddenly bees fly out of the hive and sting you.")
            takeInstantHit(HitType.Typeless, STING_DAMAGE)
            return
        }
        mesbox("You try to get some wax from the beehive.")
        if (invDel(inv, BUCKET).failure) {
            return
        }
        anim(PICK_UP_SEQ)
        invAdd(inv, BUCKET_OF_WAX)
        player.merlinBeehiveFree = false
        mesbox("You get some wax from the hive.")
        mesbox("The bees fly back to the hive as the repellent wears off.")
    }

    private companion object {
        const val STING_DAMAGE = 2
    }
}
