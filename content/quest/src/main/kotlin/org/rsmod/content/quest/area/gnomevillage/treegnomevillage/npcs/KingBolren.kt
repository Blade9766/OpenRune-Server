package org.rsmod.content.quest.area.gnomevillage.treegnomevillage.npcs

import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.GnomeMaze
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.GNOME_AMULET
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORBS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORBS_ONE_RETURNED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORBS_RESTORED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_BREACHED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_GATHERING_LOGS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_HAS_ORB
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_ORB_RETURNED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_WARLORD_SLAIN
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.elkoyGuides
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * King Bolren, the quest's start and end. He sits in the centre of the maze beside the spirit
 * tree with four Local Gnomes (`npc.chantergnome`) around him, who chant during the closing
 * ceremony.
 */
class KingBolren
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val quest
        get() = treeGnomeVillage.quest

    override fun ScriptContext.startup() {
        onOpNpc1(TreeGnomeVillageQuest.BOLREN) { startDialogue(it.npc) { bolren(it.npc) } }
    }

    private suspend fun Dialogue.bolren(npc: Npc) {
        when (treeGnomeVillage.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> {
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(worried, "Hello traveller, we must retrieve the orb. It's being held by Khazard troops north of here.")
                chatPlayer(neutral, "Ok, I'll try my best.")
            }
            in STAGE_GATHERING_LOGS until STAGE_BREACHED -> {
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(worried, "The orb is being held at the battlefield north of the maze.")
            }
            STAGE_BREACHED, STAGE_HAS_ORB -> {
                if (player.inv.contains(ORB)) {
                    returnFirstOrb()
                    return
                }
                chatPlayer(happy, "Hello Bolren.")
                chatNpc(quiz, "Do you have the orb?")
                chatPlayer(sad, "No, I'm afraid not.")
                chatNpc(worried, "Please, we must have the orb if we are to survive.")
            }
            STAGE_ORB_RETURNED, STAGE_WARLORD_SLAIN -> {
                chatPlayer(happy, "Bolren, I have returned.")
                chatNpc(quiz, "You made it back! Do you have the orbs?")
                if (player.inv.contains(ORBS)) {
                    returnOrbs(npc)
                    return
                }
                chatPlayer(sad, "No, I'm afraid not.")
                chatNpc(worried, "Please, we must have the orbs if we are to survive.")
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Well hello stranger. My name's Bolren, I'm the king of the tree gnomes.")
        chatNpc(happy, "I'm surprised you made it in, maybe I made the maze too easy.")
        chatPlayer(happy, "Maybe.")
        chatNpc(worried, "I'm afraid I have more serious concerns at the moment. Very serious.")
        when (choice2("Can I help at all?", 1, "I'll leave you to it then.", 2)) {
            1 -> {
                chatPlayer(quiz, "Can I help at all?")
                chatNpc(happy, "I'm glad you asked.")
                chatNpc(sad, "The truth is my people are in grave danger. We have always been protected by the Spirit Tree. No creature of dark can harm us while its three orbs are in place.")
                chatNpc(sad, "We are not a violent race, but we fight when we must. Many gnomes have fallen battling the dark forces of Khazard to the North.")
                chatNpc(sad, "We became desperate, so we took one orb of protection to the battlefield. It was a foolish move.")
                chatNpc(worried, "Khazard troops seized the orb. Now we are completely defenceless.")
                chatPlayer(quiz, "How can I help?")
                chatNpc(neutral, "You would be a huge benefit on the battlefield. If you would go there and try to retrieve the orb, my people and I will be forever grateful.")
                if (player.combatLevel < RECOMMENDED_COMBAT) {
                    mesbox(
                        "Before starting this quest, be aware that your combat level is lower than " +
                            "the recommended level of $RECOMMENDED_COMBAT."
                    )
                }
                when (choice2("Yes.", 1, "No.", 2, title = "Start the Tree Gnome Village quest?")) {
                    1 -> startQuest()
                    2 -> {
                        chatPlayer(neutral, "I'm sorry but I won't be involved.")
                        chatNpc(neutral, "Ok then, travel safe.")
                    }
                }
            }
            2 -> {
                chatPlayer(neutral, "I'll leave you to it then.")
                chatNpc(neutral, "Ok, take care.")
            }
        }
    }

    private suspend fun Dialogue.startQuest() {
        chatPlayer(happy, "I would be glad to help.")
        quest.advanceQuestStage(access)
        chatNpc(happy, "Thank you. The battlefield is to the north of the maze. Commander Montai will inform you of their current situation.")
        chatNpc(worried, "That is if he's still alive.")
        chatNpc(neutral, "My assistant shall guide you out. Good luck friend, try your best to return the orb.")
        access.elkoyGuides(
            GnomeMaze.ENTRANCE,
            "We're out of the maze now. Please hurry, we must have the orb if we are to survive.",
            boxText = "Elkoy guides you out of the maze.",
        )
    }

    private suspend fun Dialogue.returnFirstOrb() {
        chatPlayer(happy, "I have the orb.")
        chatNpc(sad, "Oh my... The misery, the horror!")
        chatPlayer(quiz, "King Bolren, are you OK?")
        chatNpc(sad, "Thank you traveller, but it's too late. We're all doomed.")
        chatPlayer(quiz, "What happened?")
        chatNpc(sad, "They came in the night. I don't know how many, but enough.")
        chatPlayer(quiz, "Who?")
        chatNpc(sad, "Khazard troops. They slaughtered anyone who got in their way. Women, children, my wife.")
        chatPlayer(sad, "I'm sorry.")
        chatNpc(sad, "They took the other orbs, now we are defenceless.")
        chatPlayer(quiz, "Where did they take them?")
        chatNpc(neutral, "They headed north of the stronghold. A warlord carries the orbs.")
        if (access.invDel(access.inv, ORB).failure) {
            return
        }
        treeGnomeVillage.orbsState.set(player, ORBS_ONE_RETURNED)
        treeGnomeVillage.syncVars(player)
        treeGnomeVillage.advanceTo(access, STAGE_ORB_RETURNED)
        when (
            choice2(
                "I will find the warlord and bring back the orbs.", 1,
                "I'm sorry but I can't help.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I will find the warlord and bring back the orbs.")
                chatNpc(happy, "You are brave, but this task will be tough even for you. I wish you the best of luck. Once again you are our only hope.")
                chatNpc(neutral, "I will safeguard this orb and pray for your safe return. My assistant will guide you out.")
                access.elkoyGuides(GnomeMaze.ENTRANCE, "Good luck friend.", boxText = "Elkoy guides you out of the maze.")
            }
            2 -> {
                chatPlayer(sad, "I'm sorry but I can't help.")
                chatNpc(sad, "I understand, this isn't your battle.")
            }
        }
    }

    private suspend fun Dialogue.returnOrbs(npc: Npc) {
        chatPlayer(happy, "I have them here.")
        chatNpc(happy, "Hooray, you're amazing. I didn't think it was possible but you've saved us.")
        chatNpc(happy, "Once the orbs are replaced we will be safe once more. We must begin the ceremony immediately.")
        chatPlayer(quiz, "What does the ceremony involve?")
        chatNpc(neutral, "The spirit tree has looked over us for centuries. Now we must pay our respects.")
        if (access.invDel(access.inv, ORBS).failure) {
            return
        }
        access.ceremony(npc)
        chatNpc(happy, "Now at last my people are safe once more. We can live in peace again.")
        chatPlayer(happy, "I'm pleased I could help.")
        chatNpc(happy, "You are modest brave traveller.")
        chatNpc(happy, "Please, for your efforts take this amulet. It's made from the same sacred stone as the orbs of protection. It will help keep you safe on your journeys.")
        access.invAddOrDrop(objRepo, GNOME_AMULET)
        chatPlayer(happy, "Thank you King Bolren.")
        chatNpc(neutral, "The tree has many other powers, some of which I cannot reveal. As a friend of the gnome people, I can now allow you to use the tree's magic to teleport to other trees grown from related seeds.")
        treeGnomeVillage.advanceTo(access, STAGE_COMPLETE)
    }

    /**
     * The closing ceremony. The Local Gnomes around the tree chant while Bolren holds the orbs
     * out, then the orbs settle into the spirit tree, which is the multiloc switching to its
     * orb-bearing form.
     */
    private suspend fun ProtectedAccess.ceremony(bolren: Npc) {
        val chanters = npcFindAll(TREE_CENTRE, CHANTER, CHANTER_RADIUS, HuntVis.Off, search).toList()
        val cast = chanters + bolren
        val homes = cast.associateWith { it.coords }
        fadeToBlack()
        try {
            hideEntityOps()
            minimapHideMap()
            for (npc in cast) {
                npc.mode = NpcMode.None
            }
            bolren.teleport(collision, BOLREN_MARK)
            bolren.lockFacingDirection(Direction.East)
            for (chanter in chanters) {
                chanter.faceSquare(TREE_CENTRE)
            }
            telejump(PLAYER_MARK)
            faceDirection(Direction.East)
            camMoveTo(CAMERA_FROM, height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            camLookAt(CAMERA_AT, height = CAMERA_LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()

            mesbox("The gnomes begin to chant. Meanwhile, King Bolren holds the orbs of protection out in front of him.")
            bolren.anim(HOLD_ORBS_SEQ)
            bolren.spotanim(ORBS_SPOTANIM, delay = 0, height = 0, slot = 0)
            val (north, south) = chanters.partition { it.coords.z > TREE_CENTRE.z }
            repeat(CHANT_ROUNDS) {
                north.forEach { it.say("Su tana.") }
                delay(CHANT_TICKS)
                south.forEach { it.say("En tania.") }
                delay(CHANT_TICKS)
            }
            soundSynth(RESTORE_SOUND)
            spotanimMap(worldRepo, ORBS_SPOTANIM, TREE_CENTRE, height = TREE_SPOTANIM_HEIGHT)
            treeGnomeVillage.orbsState.set(player, ORBS_RESTORED)
            treeGnomeVillage.syncVars(player)
            delay(2)
            mesbox("The orbs of protection come to rest gently in the branches of the ancient spirit tree.")
        } finally {
            camReset()
            showEntityOps()
            minimapReset()
            for (npc in cast) {
                npc.clearFacingLock()
                homes[npc]?.let { npc.teleport(collision, it) }
                npc.mode = npc.type.defaultMode
            }
            bolren.facePlayer(player)
        }
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello Bolren.")
        chatNpc(happy, "Hello friend. Thanks to you the orbs are back where they belong and my people can live in peace.")
        if (player.inv.contains(GNOME_AMULET) || player.worn.contains(GNOME_AMULET)) {
            chatPlayer(happy, "Glad I could help.")
            return
        }
        when (choice2("I've lost the amulet you gave me.", 1, "Glad I could help.", 2)) {
            1 -> {
                chatPlayer(sad, "I've lost the amulet you gave me.")
                chatNpc(happy, "Then take another, friend. May it keep you safe on your journeys.")
                access.invAddOrDrop(objRepo, GNOME_AMULET)
            }
            2 -> chatPlayer(happy, "Glad I could help.")
        }
    }

    private companion object {
        const val CHANTER = "npc.chantergnome"

        /** The spirit tree (`loc.ent`) sits at 2543..2546 x 3168..3171; this is its middle. */
        val TREE_CENTRE = CoordGrid(2544, 3169, 0)
        const val CHANTER_RADIUS = 6

        val PLAYER_MARK = CoordGrid(2541, 3169, 0)
        val BOLREN_MARK = CoordGrid(2542, 3170, 0)
        val CAMERA_FROM = CoordGrid(2536, 3170, 0)
        val CAMERA_AT = CoordGrid(2544, 3169, 0)
        const val CAMERA_HEIGHT = 700
        const val CAMERA_LOOK_HEIGHT = 250
        const val CAMERA_RATE = 100

        /** The gnome spell-casting animation, with its floating green globes. */
        const val HOLD_ORBS_SEQ = "seq.gnome_cast_globes"
        const val ORBS_SPOTANIM = "spotanim.gnome_globes"
        const val TREE_SPOTANIM_HEIGHT = 300
        const val RESTORE_SOUND = "synth.spirit_transform"

        const val CHANT_ROUNDS = 2
        const val CHANT_TICKS = 3
    }
}
