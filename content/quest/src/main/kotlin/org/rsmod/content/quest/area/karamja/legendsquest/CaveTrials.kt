package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.constants
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApLocT
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ENTERED_LOWER_DUNGEON
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_TALKED_GUJUO_POOL
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The catacombs east and south of Ungadulu's cave, a string of trials the dwarves who dug for the
 * source left behind. Each takes a different talent: a lockpick and Thieving for the outer gate,
 * Mining for the three boulders behind it, Strength for the inner gate, Agility for the jagged
 * wall, the runes that spell SMELL for the marked walls, seven gems for the carved rocks that
 * conjure the Book of Binding, an orb-charging spell for the magic gate and nerve for the winch
 * down into the Viyeldi caves.
 */
class CaveTrials
@Inject
constructor(
    private val legends: LegendsQuest,
    private val support: LegendsSupport,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val aiInteractions: AiPlayerInteractions,
    private val heldInteractions: HeldInteractions,
) : PluginScript() {

    private val smokepuff = SpotanimType(SMOKEPUFF.asRSCM(RSCMType.SPOTANIM))
    private val barrelSmash = SpotanimType(BARREL_SMASH.asRSCM(RSCMType.SPOTANIM))
    private val explosion = SpotanimType(EXPLOSION.asRSCM(RSCMType.SPOTANIM))
    private val bookGlow = SpotanimType(BOOK_EFFECT.asRSCM(RSCMType.SPOTANIM))

    override fun ScriptContext.startup() {
        for (side in OUTER_GATE) {
            onOpLoc1(side) { outerGate(it.vis, it.type, picking = false) }
            onOpLoc2(side) { outerGate(it.vis, it.type, picking = true) }
            onOpLocU(side) {
                if (it.objType.isType(LOCKPICK)) outerGate(it.vis, it.type, picking = true) else mes("Nothing interesting happens.")
            }
        }
        for (boulder in BOULDERS) {
            onOpLoc1(boulder) { smashBoulder(it.vis, boulder) }
        }
        for (side in INNER_GATE) {
            onOpLoc1(side) { innerGate(it.vis, it.type) }
        }
        onOpLoc1(JAGGED_WALL) { jumpWall() }
        onOpLoc1(MARKED_WALL) { useMarkedWall(it.vis) }
        onOpLoc2(MARKED_WALL) { searchMarkedWall(it.vis) }
        onOpLocU(MARKED_WALL) { runeOnWall(it.vis, it.objType.internalName) }
        onOpLoc1(CARVED_ROCK) {
            mesbox("It's meant to look like a stalagmite, but there is something that looks slightly artificial about it. Looking at it more carefully, it seems to look carved.")
        }
        onOpLoc2(CARVED_ROCK) { searchCarvedRock(it.vis) }
        onOpLocU(CARVED_ROCK) { gemOnRock(it.vis, it.objType.internalName) }
        for (gem in GEMS) {
            onOpHeld5(gem.obj) { dropGem(gem, it.slot) }
        }
        onOpLoc1(DWARF_REMAINS) { searchRemains() }
        onOpLoc1(MAGIC_GATE) { openMagicGate() }
        onOpLoc2(MAGIC_GATE) { searchMagicGate() }
        for (orb in ORB_SPELLS) {
            val spell = ServerCacheManager.getItem(orb.asRSCM(RSCMType.OBJ))?.let(spells::getObjSpell) ?: continue
            onApLocT(MAGIC_GATE, spell.component) { chargeOrbOnGate(it.vis, spell) }
        }
        onOpLoc1(BARREL) { smashBarrel(it.vis) }
        onOpLoc1(WINCH) { searchWinch(it.vis) }
        onOpLocU(WINCH) { ropeOnWinch(it.vis, it.objType.internalName) }
        onOpLoc1(WINCH_ROPED) { climbDownWinch(it.vis) }
    }

    private suspend fun ProtectedAccess.outerGate(gate: BoundLocInfo, type: ObjectServerType, picking: Boolean) {
        arriveDelay()
        val inside = coords.z < gate.coords.z
        if (inside) {
            mesbox("You see a lever which you pull on to open the door.")
            anim(PUSH_SEQ)
            mes("The doors make a satisfying click sound as they close.")
            with(passages) { walkThrough(gate, type) }
            return
        }
        delay(2)
        if (!picking) {
            anim(PUSH_SEQ)
            delay(1)
            mesbox("You push on the doors, they're really shut. It looks like the doors have a huge locking mechanism. Although ancient, it looks very sophisticated.")
            return
        }
        if (inv.count(LOCKPICK) == 0) {
            mesbox("These doors are huge! They have a very sophisticated locking mechanism. You're definitely going to need a lockpick to get through these doors.")
            return
        }
        if (player.thievingLvl < REQUIRED_LEVEL) {
            mesbox("You need a Thieving skill of at least 50 to attempt this.")
            return
        }
        mesbox("You attempt to pick the lock...")
        anim(SEARCH_SEQ)
        mesbox("It looks very sophisticated...")
        say("Hmmm, interesting...")
        anim(SEARCH_SEQ)
        mesbox("You carefully insert your lockpick into the lock.")
        say("This will be a challenge.")
        ifClose()
        anim(SEARCH_SEQ)
        soundSynth(PICK_LOCK_SOUND)
        delay(2)
        mesbox("You feel for the pins and levers in the mechanism.")
        say("Easy does it...")
        anim(SEARCH_SEQ)
        if (!statRandom(THIEVING, LOCK_LOW, LOCK_HIGH, 0)) {
            mesbox("But you fail to pick the lock.")
            return
        }
        mesbox("CLICK!")
        startDialogue { chatPlayer(happy, "Easy as pie...") }
        mesbox("You tumble the lock mechanism and the door opens easily.")
        statAdvance(THIEVING, LOCK_XP)
        ifClose()
        with(passages) { walkThrough(gate, type) }
    }

    private suspend fun ProtectedAccess.smashBoulder(boulder: BoundLocInfo, type: String) {
        arriveDelay()
        val trapped = coords.z < boulder.coords.z || type != BOULDERS[0]
        val pickaxe = bestPickaxe()
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the Mining level to use.")
            if (trapped) stuckBetweenBoulders()
            return
        }
        if (player.miningLvl < REQUIRED_MINING) {
            mes("You need a Mining level of 52 to mine your way through this rock.")
            if (trapped) stuckBetweenBoulders()
            return
        }
        mes("You swing your pick at the rock.")
        faceLoc(boulder)
        anim(toolAnim(pickaxe, DEFAULT_MINE_SEQ))
        soundSynth(MINE_SOUND)
        delay(MINE_TICKS)
        if (!statRandom(MINING, BOULDER_LOW, BOULDER_HIGH, 0)) {
            mes("You only succeed in scratching the rock.")
            mes("Your pickaxe clangs heavily against the rock face and the vibrations rattle your")
            mes("nerves.")
            statSub(MINING, 1, 0)
            return
        }
        mes("You manage to smash the rock to bits.")
        if (!inv.isFull()) {
            mes("You get some rock.")
            invAdd(inv, ROCK, 1)
            statAdvance(MINING, BOULDER_XP)
        }
        val fromSouth = coords.z < boulder.coords.z
        val dest =
            if (fromSouth) {
                CoordGrid(coords.x, boulder.coords.z + boulder.adjustedLength, coords.level)
            } else {
                CoordGrid(coords.x, boulder.coords.z - 1, coords.level)
            }
        resetAnim()
        teleport(dest, TeleportType.Exempt)
        mes("Another boulder drops down behind you.")
        locRepo.del(boulder, BOULDER_GONE_TICKS)
    }

    private suspend fun ProtectedAccess.stuckBetweenBoulders() {
        mesbox("You seem to be stuck! You may be able to climb out, but it looks pretty dangerous. Would you like to try?")
        val climb =
            choice2(
                "Yes, I'll try to climb out.", true,
                "No thanks, I think there's something else I can do.", false,
                title = "Climb out of dangerous mining trial.",
            )
        if (!climb) {
            mesbox("You decide to stay where you are.")
            return
        }
        teleport(LegendsCoords.BOULDER_TRIAL_ESCAPE)
        mes("You eventually manage to climb your way out, but you cut yourself to ribbons in")
        mes("the process.")
        hurt(2)
    }

    private suspend fun ProtectedAccess.innerGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val north = coords.z >= gate.coords.z
        if (north) {
            mesbox(
                "Two huge metal doors bar the way further... There is an intense and unpleasant " +
                    "feeling from this place and as you peer through the cracks in the door you can " +
                    "see why. You see dark, shadowy shapes flapping around in the still dark air.",
            )
        }
        if (player.strengthLvl < REQUIRED_LEVEL) {
            mesbox("You'll need at least 50 Strength to attempt to move these huge metal doors.")
            if (!north) {
                squeezeThroughGap()
            }
            return
        }
        mesbox("You push the doors... They're quite stiff... They won't budge with a normal push. Do you want to try to force them open with brute strength?")
        val force =
            choice2(
                "Yes, I'm very strong, I'll force them open.", true,
                "No, I'm having second thoughts.", false,
                title = "Use brute strength on the gates?",
            )
        if (!force) {
            mes("You decide against forcing the doors.")
            return
        }
        mesbox("You ripple your muscles and prepare to exert yourself...")
        say("Hup!")
        mesbox("You brace yourself against the doors...")
        anim(PUSH_SEQ)
        say("Urghhhhh!")
        mesbox("You start to force the doors open...")
        say("Arghhhhhhh!")
        anim(PUSH_SEQ)
        mesbox("You push and push...")
        anim(PUSH_SEQ)
        if (statRandom(STRENGTH, 0, 255, 0)) {
            mesbox("...and you just manage to force the doors open slightly, just enough to force yourself through.")
            ifClose()
            with(passages) { walkThrough(gate, type) }
            return
        }
        statSub(STRENGTH, 1, 0)
        mesbox("...but you run out of steam before you're able to force the doors open. You feel exhausted.")
        statSub(STRENGTH, random.of(1, 2), 0)
    }

    private suspend fun ProtectedAccess.squeezeThroughGap() {
        mesbox("You seem to be stuck! You may be able to push your way through a gap in the doors, but the edges are razor-sharp. Would you like to try?")
        val push =
            choice2(
                "Yes, I'll try to push through.", true,
                "No thanks, I think there's something else I can try.", false,
                title = "Push through doors?",
            )
        if (!push) {
            mes("You decide not to try and push through.")
            return
        }
        ifClose()
        delay(2)
        mes("You eventually manage to force your way through the door, but you are quite badly cut up as a result.")
        teleport(LegendsCoords.STRENGTH_GATE_THROUGH)
        delay(2)
        hurt(8)
    }

    private suspend fun ProtectedAccess.jumpWall() {
        arriveDelay()
        if (player.agilityLvl < REQUIRED_LEVEL) {
            mes("You need an Agility level of 50 to jump this wall.")
            return
        }
        mes("You prepare to jump over the crumbly wall.")
        val southbound = coords.z > LegendsCoords.JAGGED_WALL_SPLIT_Z
        val start = if (southbound) coords else LegendsCoords.JAGGED_WALL_SOUTH
        val end = if (southbound) start.translate(1, -1) else start.translate(-1, 1)
        val dir = if (southbound) constants.em_face_south else constants.em_face_north
        val clean = statRandom(AGILITY, WALL_LOW, WALL_HIGH, 0)
        if (clean) {
            mes("You take a good run up and sail majestically over the wall.")
        } else {
            mes("You fail to jump the wall properly and clip the wall with your leg.")
        }
        anim(JUMP_SEQ, delay = 30)
        soundSynth(JUMP_SOUND)
        exactMove(start, end, delay1 = 37, delay2 = 50, dir = dir, teleportType = TeleportType.Exempt)
        delay(2)
        if (clean) {
            mes("You land perfectly and stand ready for action.")
            return
        }
        mes("You're spun around mid air and hit the floor heavily.")
        delay(2)
        mes("The fall knocks the wind out of you.")
        anim(CRAWL_SEQ)
        hurt(5)
    }

    private suspend fun ProtectedAccess.useMarkedWall(wall: BoundLocInfo) {
        arriveDelay()
        if (player.legendsRunesPlaced >= RUNE_ORDER.size) {
            enterMarkedWall(wall)
            return
        }
        mes("You see no way to use that....")
        delay(1)
        mes("Perhaps you should search it?")
    }

    private suspend fun ProtectedAccess.searchMarkedWall(wall: BoundLocInfo) {
        arriveDelay()
        mes("You search the wall.")
        if (player.legendsRunesPlaced >= RUNE_ORDER.size) {
            mesbox("You find the word 'SMELL' marked on the wall. The outline of a door appears on the wall. What would you like to do?")
            if (choice2("Read the message on the wall?", true, "Investigate the outline of the door.", false)) {
                wallRiddle()
                return
            }
            offerMarkedDoor(wall)
            return
        }
        mesbox(
            "You find five slightly round depressions and some strange markings. There is a lot of " +
                "dirt and mould growing over the markings, but you clear it out. After a while you " +
                "manage to see that it is some form of message. Would you like to read it?",
        )
        if (choice2("Yes, I'll read it.", true, "No, I won't read it.", false, title = "Read message on wall?")) {
            wallRiddle()
        } else {
            mesbox("You decide against reading the message.")
        }
    }

    private suspend fun ProtectedAccess.wallRiddle() {
        mesbox(
            "'Place the five in order to pass or your life will dwindle until the last. All five are " +
                "stones of magical power, Place them wrong and your fate will sour.'",
        )
        mesbox(
            "'First is the spirit of man or beast, Second is the place where thoughts are born, Third " +
                "is the soil from which good things grow Four and five are the rules all men should know.'",
        )
        mesbox("'All put together make the word of a basic sense. And from perspective help make maps from indifference.'")
    }

    private suspend fun ProtectedAccess.offerMarkedDoor(wall: BoundLocInfo) {
        mesbox(
            "You see a small door outline starting to form in the wall. Slowly a well formed door " +
                "handle starts to emerge, suddenly, the door cracks open. Would you like to go through?",
        )
        if (choice2("Yes, I'll go through!", true, "No, I'll stay here.", false)) {
            enterMarkedWall(wall)
        } else {
            mes("You decide to stay where you are.")
        }
    }

    private suspend fun ProtectedAccess.runeOnWall(wall: BoundLocInfo, rune: String) {
        arriveDelay()
        val placed = player.legendsRunesPlaced
        if (rune !in RUNE_ORDER) {
            mes("Nothing interesting happens.")
            return
        }
        if (placed >= RUNE_ORDER.size) {
            enterMarkedWall(wall)
            return
        }
        if (RUNE_ORDER[placed] != rune) {
            mes("The rune burns red hot in your hand!")
            anim(WAVE_SEQ)
            delay(1)
            hurt(5)
            val name = ServerCacheManager.getItem(rune.asRSCM(RSCMType.OBJ))?.name?.lowercase() ?: "rune"
            mes("You drop the $name to the floor.")
            invDel(inv, rune, 1)
            objRepo.add(rune, coords, DROPPED_RUNE_TICKS, receiver = player)
            return
        }
        invDel(inv, rune, 1)
        anim(SEARCH_SEQ)
        player.legendsRunesPlaced = placed + 1
        val (name, letter) = RUNE_LETTERS[placed]
        val slot = SLOT_NAMES[placed]
        mesbox(
            "You slide the $name Rune into the $slot depression... It glows slightly and then merges " +
                "with the wall. The letter '$letter' appears where the $name Rune merged with the wall.",
        )
        if (placed + 1 == RUNE_ORDER.size) {
            offerMarkedDoor(wall)
        }
    }

    private suspend fun ProtectedAccess.enterMarkedWall(wall: BoundLocInfo) {
        mesbox("You walk into the darkness of the magical doorway. You walk for a short distance before pushing open another door.")
        ifClose()
        if (wall.coords == LegendsCoords.MARKED_WALL_WEST) {
            teleport(LegendsCoords.MARKED_WALL_WEST_EXIT)
            mes("You appear in a small walled cavern.")
            delay(1)
            mes("There seems to be an exit to the south east.")
        } else {
            teleport(LegendsCoords.MARKED_WALL_EAST_EXIT)
            mes("You appear in a large cavern like room filled with pools of water.")
        }
    }

    private suspend fun ProtectedAccess.searchCarvedRock(rock: BoundLocInfo) {
        arriveDelay()
        if (player.legendsGems == ALL_GEMS) {
            if (objRepo.findAll(LegendsCoords.BOOK_SPOT).any { it.type == BOOK_ID }) {
                mes("You see that a book has appeared in the centre of the room.")
                return
            }
            conjureBook()
            return
        }
        mes("You see a delicate inscription.")
        mesbox("You see a delicate inscription on the rock, it says. <col=0000ff>'Once there were crystals to make the pool shine. Ordered in stature to retrieve what's mine.'</col>")
        val gem = GEMS.firstOrNull { it.rock == rock.coords } ?: return
        if (player.legendsGems and gem.bit != 0) {
            CarvedRockGems.show(objRepo, player, gem, CarvedRockGems.SPIN_TICKS)
            mes("A barely visible ${gem.name} becomes visible again, spinning above the rock.")
            mes("The gem soon begins to fade.")
        }
    }

    private suspend fun ProtectedAccess.gemOnRock(rock: BoundLocInfo, obj: String) {
        arriveDelay()
        val gem = GEMS.firstOrNull { it.obj == obj }
        if (gem == null) {
            mes("Nothing interesting happens.")
            return
        }
        mes("You carefully move the gem closer to the rock.")
        if (inv.count(LegendsQuest.BOOK_OF_BINDING) > 0) {
            mes("Nothing seems to happen...")
            mes("The 'Book of Binding' seems to vibrate in your inventory.")
            return
        }
        if (gem.rock != rock.coords) {
            mes("But nothing happens.")
            return
        }
        if (player.legendsGems and gem.bit != 0) {
            mes("You have already placed a ${gem.name} above this rock.")
            CarvedRockGems.show(objRepo, player, gem, CarvedRockGems.SPIN_TICKS)
            mes("A barely visible ${gem.name} becomes visible again, spinning above the rock.")
            mes("The gem soon begins to fade.")
            return
        }
        placeGem(gem, inv.indexOfFirst { it?.id == gem.id })
    }

    /**
     * Dropping a gem beside its own carved rock in the cavern of pools places it; anywhere else it
     * is an ordinary drop.
     */
    private suspend fun ProtectedAccess.dropGem(gem: CarvedRockGems.Gem, slot: Int) {
        val besideRock =
            LegendsCoords.inGemRoom(coords) && coords.chebyshevDistance(gem.rock) <= 1 &&
                locRepo.findLoc(gem.rock, CARVED_ROCK)
        if (!besideRock) {
            heldInteractions.drop(this, inv, slot)
            return
        }
        if (player.legendsGems and gem.bit != 0 && (inv.count(LegendsQuest.BOOK_OF_BINDING) == 0 || !legends.isComplete(player))) {
            mes("You have already placed the ${gem.name} above this rock.")
            mes("The gem soon begins to fade.")
            CarvedRockGems.show(objRepo, player, gem, CarvedRockGems.SPIN_TICKS)
            return
        }
        mes("As you drop the gem, it slowly glides over to the sharp rock.")
        delay(1)
        mes("And gently floats above it, rotating slowly.")
        delay(1)
        if (inv.count(LegendsQuest.BOOK_OF_BINDING) > 0 || legends.isComplete(player)) {
            mes("Nothing seems to happen...")
            mes("You have already found the Book of Binding.")
            return
        }
        placeGem(gem, slot)
    }

    private suspend fun ProtectedAccess.placeGem(gem: CarvedRockGems.Gem, slot: Int) {
        if (slot < 0 || inv[slot]?.id != gem.id) {
            return
        }
        anim(SEARCH_SEQ)
        invDel(inv, gem.obj, 1, slot = slot)
        CarvedRockGems.show(objRepo, player, gem, CarvedRockGems.SPIN_TICKS)
        world.spotanimMap(bookGlow, gem.rock, height = GEM_GLOW_HEIGHT)
        mes("The ${gem.name} glows and starts spinning as it hovers above the rock.")
        mes("The gem soon begins to fade.")
        player.legendsGems = player.legendsGems or gem.bit
        if (player.legendsGems == ALL_GEMS) {
            conjureBook()
        }
    }

    /**
     * All seven gems in place: the player is lifted into the middle of the cavern, the gems flare
     * and stream their light into its centre, and the Book of Binding appears there.
     */
    private suspend fun ProtectedAccess.conjureBook() {
        mes("You feel a powerful force picking you up....")
        faceSquare(coords.translateZ(1))
        delay(1)
        val start = coords
        val middle = LegendsCoords.GEM_ROOM_CENTRE
        val distance = start.chebyshevDistance(middle)
        anim(BLOWN_SEQ, delay = 30)
        exactMove(start, middle, delay1 = 31, delay2 = 31 + 14 * distance, dir = constants.em_face_north, teleportType = TeleportType.Exempt)
        delay(maxOf(1, distance / 2))
        anim(GETUP_SEQ)
        delay(1)
        mes("All the gems suddenly appear again...")
        for (gem in GEMS) {
            CarvedRockGems.show(objRepo, player, gem, CarvedRockGems.REVEAL_TICKS)
        }
        mes("Suddenly the room is lit up with a brilliant light...")
        val uid = player.uid
        val spotanim = SHOOTING_STAR.asRSCM(RSCMType.SPOTANIM)
        for (pulse in 0 until BOOK_PULSES) {
            worldQueues.add(pulse + 1) {
                for (gem in GEMS) {
                    world.projAnim(ProjAnim.fromCoordToCoord(gem.rock, LegendsCoords.BOOK_SPOT, spotanim, PROJANIM))
                }
                if (pulse % 4 == 3) {
                    world.spotanimMap(bookGlow, LegendsCoords.BOOK_SPOT, height = BOOK_GLOW_HEIGHT)
                }
                if (pulse == BOOK_PULSES - 1) {
                    val caster = support.resolve(uid) ?: return@add
                    caster.mes("...Strangely a book appears near the middle of the room.")
                    objRepo.add(LegendsQuest.BOOK_OF_BINDING, LegendsCoords.BOOK_SPOT, BOOK_TICKS, receiver = caster)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.searchRemains() {
        arriveDelay()
        mesbox("It looks as if some poor unfortunate soul died here.")
        ifClose()
        delay(1)
        if (bestPickaxe() != null) {
            mes("There's nothing interesting about these remains.")
            return
        }
        objbox(BRONZE_PICKAXE, "You see that there's a Bronze Pickaxe among the skeletal remains. Would you like to take it?")
        if (!choice2("Yes,I'll take the pickaxe.", true, "No, I don't need a pickaxe.", false)) {
            mes("You decide not to take the pickaxe.")
            return
        }
        if (!invAdd(inv, BRONZE_PICKAXE, 1).success) {
            mes("You don't have room to carry the pickaxe.")
            return
        }
        objbox(BRONZE_PICKAXE, "You carefully prise the pickaxe from the unfortunate dwarf's cold, dead hands.")
    }

    private suspend fun ProtectedAccess.openMagicGate() {
        arriveDelay()
        if (coords.z > LegendsCoords.MAGIC_GATE_CAST_FROM.z + 1) {
            mes("The gate shimmers and changes as you approach.")
            mes("You feel yourself being pulled through the portal.")
            anim(BLOWN_START_SEQ)
            exactMove(coords, LegendsCoords.MAGIC_GATE_SOUTH, delay1 = 0, delay2 = 130, dir = constants.em_face_south, teleportType = TeleportType.Exempt)
            delay(4)
            return
        }
        mesbox("This door is fused with rock, it doesn't seem possible to open it. But it does look slightly strange in some way.")
    }

    private suspend fun ProtectedAccess.searchMagicGate() {
        arriveDelay()
        mesbox(
            "It just looks like a normal door... at first. And then you notice that some of the " +
                "inscriptions make up letters. After some time you manage to make sense of it. Would " +
                "you like to read it?",
        )
        when (choice3("Yes, I'll read it.", 1, "No, I don't want to read that.", 2, "Search further...", 3)) {
            1 -> {
                mesbox("You attempt to read the message on the door... It looks like some sort of riddle:")
                mesbox(
                    "<col=0000ff>Doors of metal will not be kind, To those who care not for the way of " +
                        "mind. To all men of learning and supernatural powers, With book and rune spend " +
                        "the long dark hours.</col>",
                )
                mesbox("<col=0000ff>If passage further you would endure, Give me a taste of your power so pure.</col>")
            }
            2 -> mes("You decide not to read the message.")
            else -> {
                mesbox(
                    "You scour the door for more clues. Something etched into the wall nearby catches " +
                        "your eye... It looks like a picture of four pillars!",
                )
                mesbox(
                    "Over the first pillar is a picture of a cloud. Etched over the second pillar are " +
                        "flickering flames. Over the third pillar is the carved image of a dew drop or a " +
                        "tear. Over the fourth pillar is the likeness of a ploughed field.",
                )
                mesbox("All of these images are contained within a sphere.")
            }
        }
    }

    private suspend fun ProtectedAccess.chargeOrbOnGate(gate: BoundLocInfo, spell: MagicSpell) {
        if (inv.count(UNPOWERED_ORB) == 0) {
            mes("You must be holding an orb to enchant it.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        if (coords != LegendsCoords.MAGIC_GATE_CAST_FROM) {
            teleport(LegendsCoords.MAGIC_GATE_CAST_FROM, TeleportType.Exempt)
        }
        faceLoc(gate)
        invDel(inv, UNPOWERED_ORB, 1)
        statAdvance("stat.magic", spell.castXp)
        anim(ORB_CAST_SEQ)
        spotanim(ORB_CAST_SPOTANIM, height = ORB_CAST_HEIGHT)
        soundSynth(ORB_SOUND)
        delay(1)
        mes("The gate changes!")
        resetAnim()
        delay(1)
        anim(BLOWN_START_SEQ)
        exactMove(coords, LegendsCoords.MAGIC_GATE_NORTH, delay1 = 0, delay2 = 130, dir = constants.em_face_north, teleportType = TeleportType.Exempt)
        delay(4)
        resetAnim()
        objbox(
            UNPOWERED_ORB,
            "The orb shatters with the power of the magic. The spell works and you magically appear " +
                "in a different part of the cave system. It seems that the gate was a test of magical ability.",
        )
        mesbox("As soon as you enter the room, you are filled with dread. In the centre of the room is a large gaping hole. It goes down a long way...")
    }

    private suspend fun ProtectedAccess.smashBarrel(barrel: BoundLocInfo) {
        arriveDelay()
        faceLoc(barrel)
        anim(PUNCH_SEQ)
        delay(1)
        if (!statRandom("stat.attack", BARREL_LOW, BARREL_HIGH, 0)) {
            mes("You were unable to smash this barrel open.")
            anim(STUNNED_SEQ)
            if (random.of(3) == 0) {
                val loss = random.of(1, 3)
                mes("You hit the barrel at the wrong angle.")
                mes("You're heavily jarred from the vibrations of the blow.")
                mes("Your attack is reduced by $loss")
                statSub("stat.attack", loss, 0)
            }
            return
        }
        val spot = barrel.coords
        world.spotanimMap(barrelSmash, spot)
        val roll = random.of(128)
        when {
            roll < 32 -> {
                mes("There's nothing in the barrel.")
                barrelMonster(spot)
            }
            roll < 64 -> {
                mes("You smash the barrel open.")
                barrelLoot(spot)
                barrelMonster(spot)
            }
            roll < 80 -> {
                mes("You smash the barrel open.")
                barrelLoot(spot)
            }
            roll < 112 -> {
                barrelLoot(spot)
                barrelExplodes(spot)
            }
            else -> barrelExplodes(spot)
        }
        val info = locRepo.findExact(spot, ServerCacheManager.getObject(barrel.id) ?: return) ?: return
        locRepo.del(info, random.of(BARREL_RESPAWN_MIN, BARREL_RESPAWN_MAX))
    }

    private fun ProtectedAccess.barrelExplodes(spot: CoordGrid) {
        mes("You're hit by splintering pieces of wood as a nearby barrel explodes.")
        world.spotanimMap(explosion, spot)
        soundSynth(EXPLOSION_SOUND)
        hurt(random.of(6, 12))
    }

    private fun ProtectedAccess.barrelLoot(spot: CoordGrid) {
        val junk = random.of(64)
        when {
            junk < 12 -> objRepo.add("obj.rock", spot, LOOT_TICKS, player, random.of(1, 3))
            junk < 24 -> objRepo.add("obj.rope", spot, LOOT_TICKS, player, random.of(1, 5))
        }
        val roll = random.of(100)
        val (obj, count) = BARREL_LOOT.firstOrNull { roll < it.first }?.second ?: return
        objRepo.add(obj, spot, LOOT_TICKS, player, count(this))
    }

    private fun ProtectedAccess.barrelMonster(spot: CoordGrid) {
        world.spotanimMap(smokepuff, spot)
        val type = ServerCacheManager.getNpc(BARREL_MONSTERS.random().asRSCM(RSCMType.NPC)) ?: return
        val monster = Npc(type, spot)
        npcRepo.add(monster, BARREL_MONSTER_TICKS)
        monster.opPlayer2(player, aiInteractions)
    }

    private suspend fun ProtectedAccess.searchWinch(winch: BoundLocInfo) {
        arriveDelay()
        if (player.legendsWinchRope) {
            mes("You search the wooden beams and find the rope you attached.")
            locRepo.change(winch, WINCH_ROPED, ROPED_TICKS)
            return
        }
        mes("You see nothing special about this...")
        delay(2)
        mes("Perhaps with a rope, it might be a bit more functional.")
    }

    private suspend fun ProtectedAccess.ropeOnWinch(winch: BoundLocInfo, obj: String) {
        arriveDelay()
        if (obj != ROPE) {
            mes("Nothing interesting happens.")
            return
        }
        if (player.legendsWinchRope) {
            mes("You have already thrown a rope around this wooden beam.")
            locRepo.change(winch, WINCH_ROPED, ROPED_TICKS)
            return
        }
        mes("You throw a rope around the winch.")
        invDel(inv, ROPE, 1)
        player.legendsWinchRope = true
        locRepo.change(winch, WINCH_ROPED, ROPED_TICKS)
    }

    private suspend fun ProtectedAccess.climbDownWinch(winch: BoundLocInfo) {
        arriveDelay()
        if (!player.legendsWinchRope) {
            mes("The rope snaps as you're about to climb down it.")
            locRepo.change(winch, WINCH, Int.MAX_VALUE)
            return
        }
        mesbox("The climb down looks pretty dangerous, it leads into a very dark hole. It's actually quite frightening. Are you sure you want to go down?")
        val down =
            choice2(
                "Yes, I'll shimmy down the rope into possible doom.", true,
                "'Err, no actually, I've had second thoughts about this.", false,
                title = "Climb down terrifying hole?",
            )
        if (!down) {
            mesbox("You decide not to shimmy down the rope into most likely oblivion.")
            return
        }
        ifClose()
        mes("You prepare to climb down the rope.")
        delay(2)
        if (!player.legendsBravery) {
            say("Gulp!")
            mes("But a terrible fear grips you...")
            delay(2)
            mes("And you can go no further.")
            startDialogue { chatPlayer(worried, "No! It's too scary!") }
            return
        }
        anim(CLIMB_SEQ)
        if (!statRandom(AGILITY, ROPE_LOW, ROPE_HIGH, 0)) {
            mes("Fear stabs at your heart....")
            say("Gulp!")
            delay(1)
            mes("...and you lose concentration but for a split second.")
            delay(1)
            mes("You slip off the rope and fall...")
            delay(1)
            say("Ahhhhhhhhhhhhhhh!")
            hurt(statBase("stat.hitpoints") / 3)
        } else {
            mes("And although fear stabs at your heart.")
            say("Gulp!")
            delay(1)
            mes("You shimmy down the rope and into the darkness.")
        }
        delay(1)
        mes("You find yourself on the top of a tall ledge...")
        delay(1)
        mes("It's a long way down!")
        legends.advanceFrom(this, STAGE_TALKED_GUJUO_POOL, STAGE_ENTERED_LOWER_DUNGEON)
        teleport(LegendsCoords.VIYELDI_LANDING)
    }

    private companion object {
        val OUTER_GATE = listOf("loc.lglockpickgatebottoml", "loc.lglockpickgatebottomr")
        val INNER_GATE = listOf("loc.lgstrengthtrialgatel", "loc.lgstrengthtrialgater")
        val BOULDERS = listOf("loc.mine_test_boulder1", "loc.mine_test_boulder2", "loc.mine_test_boulder3")
        const val JAGGED_WALL = "loc.crumbled_wall"
        const val MARKED_WALL = "loc.lgancientwalldoor"
        const val CARVED_ROCK = "loc.lg_gemplacerock"
        const val DWARF_REMAINS = "loc.lg_dwarfremains"
        const val MAGIC_GATE = "loc.lgmagictrialgateclosed"
        const val BARREL = "loc.skullcavebarrel"
        const val WINCH = "loc.lg_winchdown_norope"
        const val WINCH_ROPED = "loc.lg_winchdown_rope"

        const val LOCKPICK = "obj.lockpick"
        const val ROCK = "obj.swamprocks1"
        const val ROPE = "obj.rope"
        const val UNPOWERED_ORB = "obj.stafforb"
        const val BRONZE_PICKAXE = "obj.bronze_pickaxe"
        val BOOK_ID by lazy { LegendsQuest.BOOK_OF_BINDING.asRSCM(RSCMType.OBJ) }

        val ORB_SPELLS =
            listOf("obj.56_charge_water_orb", "obj.60_charge_earth_orb", "obj.63_charge_fire_orb", "obj.66_charge_air_orb")

        val RUNE_ORDER = listOf("obj.soulrune", "obj.mindrune", "obj.earthrune", "obj.lawrune", "obj.lawrune")
        val RUNE_LETTERS = listOf("Soul" to "S", "Mind" to "M", "Earth" to "E", "Law" to "L", "Law" to "L")
        val SLOT_NAMES = listOf("first", "second", "third", "fourth", "fifth")

        val GEMS = CarvedRockGems.GEMS
        const val ALL_GEMS = CarvedRockGems.ALL_GEMS

        val BARREL_MONSTERS =
            listOf(
                "npc.dwarf_chaos", "npc.black_knight", "npc.dark_warrior", "npc.bearded_dark_wizard",
                "npc.deadly_red_spider", "npc.deathwing", "npc.bat", "npc.giantrat1", "npc.giantspider1",
                "npc.giant", "npc.hobgoblin_unarmed", "npc.mossgiant", "npc.mugger", "npc.scorpion",
                "npc.skeleton_unarmed", "npc.zombie_unarmed",
            )

        val BARREL_LOOT: List<Pair<Int, Pair<String, ProtectedAccess.() -> Int>>> =
            listOf(
                6 to ("obj.earthrune" to { random.of(1, 12) }),
                12 to ("obj.firerune" to { random.of(1, 12) }),
                18 to ("obj.waterrune" to { random.of(1, 11) }),
                20 to ("obj.airrune" to { random.of(1, 11) }),
                22 to ("obj.bronze_dart" to { 1 }),
                24 to ("obj.bronze_knife" to { 1 }),
                26 to ("obj.iron_knife" to { 1 }),
                28 to ("obj.steel_knife" to { 1 }),
                30 to ("obj.mithril_knife" to { 1 }),
                32 to ("obj.adamant_knife" to { 1 }),
                34 to ("obj.rune_knife" to { 1 }),
                36 to ("obj.bread" to { 1 }),
                38 to ("obj.cheese" to { 1 }),
                40 to ("obj.spinach_roll" to { 1 }),
                42 to ("obj.half_a_redberry_pie" to { 1 }),
                44 to ("obj.half_a_meat_pie" to { 1 }),
                46 to ("obj.half_an_apple_pie" to { 1 }),
                48 to ("obj.cake_slice" to { 1 }),
                50 to ("obj.gold_bar" to { 1 }),
                52 to ("obj.stafforb" to { 1 }),
                54 to ("obj.bow_string" to { 1 }),
                56 to ("obj.casket" to { 1 }),
                58 to ("obj.coins" to { random.of(5, 90) }),
                60 to ("obj.1dose1strength" to { 1 }),
                62 to ("obj.1dose1defense" to { 1 }),
                63 to ("obj.bronze_pickaxe" to { 1 }),
                64 to ("obj.steel_pickaxe" to { 1 }),
                65 to ("obj.iron_dart" to { 1 }),
                66 to ("obj.steel_dart" to { 1 }),
                67 to ("obj.mithril_dart" to { 1 }),
                68 to ("obj.unidentified_snake_weed" to { 1 }),
                69 to ("obj.1doseprayerrestore" to { 1 }),
                70 to ("obj.pineapple" to { 1 }),
                72 to ("obj.tinderbox" to { 1 }),
                73 to ("obj.torch_unlit" to { 1 }),
                74 to ("obj.logs" to { 1 }),
            )

        const val THIEVING = "stat.thieving"
        const val MINING = "stat.mining"
        const val STRENGTH = "stat.strength"
        const val AGILITY = "stat.agility"
        const val REQUIRED_LEVEL = 50
        const val REQUIRED_MINING = 52
        const val LOCK_LOW = 0
        const val LOCK_HIGH = 255
        const val LOCK_XP = 10.0
        const val BOULDER_LOW = 90
        const val BOULDER_HIGH = 255
        const val BOULDER_XP = 35.0
        const val MINE_TICKS = 3
        const val BOULDER_GONE_TICKS = 6
        const val WALL_LOW = 50
        const val WALL_HIGH = 200
        const val ROPE_LOW = 120
        const val ROPE_HIGH = 250
        const val BARREL_LOW = 65
        const val BARREL_HIGH = 255
        const val BARREL_RESPAWN_MIN = 30
        const val BARREL_RESPAWN_MAX = 35
        const val BARREL_MONSTER_TICKS = 500
        const val LOOT_TICKS = 200
        const val DROPPED_RUNE_TICKS = 200
        const val ROPED_TICKS = 30
        const val BOOK_PULSES = 13
        const val BOOK_TICKS = 500
        const val GEM_GLOW_HEIGHT = 60
        const val BOOK_GLOW_HEIGHT = 110
        const val ORB_CAST_HEIGHT = 92

        const val PUSH_SEQ = "seq.human_push"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val JUMP_SEQ = "seq.human_spot_jump"
        const val WAVE_SEQ = "seq.emote_wave"
        const val BLOWN_SEQ = "seq.human_blown_middle"
        const val BLOWN_START_SEQ = "seq.human_blown_start"
        const val GETUP_SEQ = "seq.human_blown_end_getup"
        const val PUNCH_SEQ = "seq.human_unarmedpunch"
        const val STUNNED_SEQ = "seq.human_stunned"
        const val CLIMB_SEQ = "seq.human_reachforladder"
        const val ORB_CAST_SEQ = "seq.human_casting"
        const val DEFAULT_MINE_SEQ = "seq.human_mining_bronze_pickaxe"

        const val SMOKEPUFF = "spotanim.smokepuff"
        const val BARREL_SMASH = "spotanim.barrel_smash"
        const val EXPLOSION = "spotanim.zamorak_flame"
        const val BOOK_EFFECT = "spotanim.bookofbinding_effect"
        const val SHOOTING_STAR = "spotanim.shooting_star"
        const val ORB_CAST_SPOTANIM = "spotanim.bookofbinding_effect"
        const val PROJANIM = "projanim.magic_spell"

        const val PICK_LOCK_SOUND = "synth.pick_lock"
        const val MINE_SOUND = "synth.mine_quick"
        const val JUMP_SOUND = "synth.jump"
        const val EXPLOSION_SOUND = "synth.explosion"
        const val ORB_SOUND = "synth.charge_fire_orb"
    }
}
