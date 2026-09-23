package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLAMISH_OIL
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.CANDLESTICK
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.FEATHER
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.FISHING_ROD
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.ICE_GLOVES
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.MISC_KEY
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.OILY_ROD
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.SLIME
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Heroes' Quest items: blamish oil mixed from Gerrant's slime and rubbed into a fishing rod
 * for lava eels, and the miscellaneous key and candlesticks the two gang partners pass between
 * them. Using either of those on the partner drops it at the user's feet for the partner alone,
 * so players who cannot trade can still finish.
 */
class HeroesItems @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(SLIME, HARRALANDER_UNF) { mixOil() }
        onOpHeldU(BLAMISH_OIL, FISHING_ROD) { oilRod() }
        onOpHeld1(BLAMISH_OIL) {
            startDialogue { chatPlayer(confused, "You know... I'd really rather not.") }
        }
        onPlayerQueue(BURN_QUEUE) { burnFingers() }
        for ((obj, label) in HANDOVERS) {
            onOpPlayerU(objType(obj)) { handOver(it.target, obj, label) }
        }
    }

    private suspend fun ProtectedAccess.mixOil() {
        if (!QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)) {
            mesbox(
                "You need to complete the Druidic Ritual quest before you can use the Herblore " +
                    "skill. Speak to Kaqemeex at the stone circle north of Taverley.",
            )
            return
        }
        if (player.herbloreLvl < OIL_LEVEL) {
            mesbox("You need a Herblore level of $OIL_LEVEL to make this potion.")
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, SLIME)
        invDel(inv, HARRALANDER_UNF)
        invAdd(inv, BLAMISH_OIL)
        statAdvance("stat.herblore", OIL_XP)
        mes("You mix the slime into your potion.")
        delay(MIX_TICKS)
    }

    private suspend fun ProtectedAccess.oilRod() {
        invDel(inv, BLAMISH_OIL)
        invDel(inv, FISHING_ROD)
        invAdd(inv, VIAL)
        invAdd(inv, OILY_ROD)
        soundSynth(MIX_SOUND)
        objbox(OILY_ROD, "You rub the oil into the fishing rod.")
    }

    private fun ProtectedAccess.burnFingers() {
        val damage = (player.hitpoints / BURN_FRACTION + 1).coerceAtMost(player.hitpoints)
        if (damage > 0) {
            queueHit(delay = 0, type = HitType.Typeless, damage = damage)
        }
        say("Ouch!")
    }

    private suspend fun ProtectedAccess.handOver(target: Player, obj: String, label: String) {
        val confirmed =
            choice2("Yes.", true, "No.", false, title = "Drop your $label for ${target.displayName} to take?")
        if (!confirmed || inv.count(obj) == 0) {
            return
        }
        invDel(inv, obj)
        objRepo.add(obj, coords, DROP_TICKS, receiver = target, reveal = DROP_TICKS)
        soundSynth(DROP_SOUND)
        target.mes("<col=ff0000>${player.displayName} has dropped something for you to take.</col>")
    }

    internal companion object {
        const val HARRALANDER_UNF = "obj.harralandervial"
        const val VIAL = "obj.vial_empty"
        const val DRUIDIC_RITUAL = "quest_druidicritual"
        const val OIL_LEVEL = 25
        const val OIL_XP = 80.0
        const val MIX_TICKS = 2
        const val MIX_SEQ = "seq.human_herbing_vial"
        const val MIX_SOUND = "synth.liquid"

        const val BURN_QUEUE = "queue.hero_hot_feather"
        const val BURN_FRACTION = 8

        const val DROP_TICKS = 200
        const val DROP_SOUND = "synth.put_down"

        val HANDOVERS = listOf(MISC_KEY to "key", CANDLESTICK to "candlestick")

        fun objType(obj: String): ItemServerType =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $obj")
    }
}

/**
 * The Entranan Firebird's feather is too hot to pick up bare-handed; only the Ice Queen's gloves
 * are cold enough. Trying without them burns the player.
 */
class HotFeatherTakeHook @Inject constructor(private val heroes: HeroesQuest) :
    PlayerObjTakeValidateHook {
    private val featherId by lazy { FEATHER.asRSCM(RSCMType.OBJ) }

    override fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String? {
        if (objType.id != featherId) {
            return null
        }
        if (!heroes.isStarted(player)) {
            return "It looks dangerously hot, and you have no reason to take it."
        }
        if (player.worn.count(ICE_GLOVES) > 0) {
            return null
        }
        if (HeroesItems.BURN_QUEUE !in player.queueList) {
            player.queue(HeroesItems.BURN_QUEUE, 1)
        }
        return "It is too hot to take. You need something cold to pick it up with."
    }
}
