package org.rsmod.content.quest.area.seers.murdermystery

/**
 * One of Lord Sinclair's six children. [id] is the value `varp.murdersus` holds when this child is
 * the murderer, rolled when the guard takes the player on.
 */
enum class Suspect(
    val id: Int,
    val displayName: String,
    val male: Boolean,
    val npcs: List<String>,
    val barrel: String,
    val silverItem: String,
    val dustedItem: String,
    val print: String,
    val thread: String,
    val poisonLoc: String,
    val poisonLocName: String,
) {
    Anna(
        id = 1,
        displayName = "Anna",
        male = false,
        npcs = listOf("npc.anna_sinclair", "npc.kr_anna_sinclair_multi"),
        barrel = "loc.murderbarrela",
        silverItem = "obj.murdernecklace",
        dustedItem = "obj.murdernecklacedust",
        print = "obj.murderfingerprinta",
        thread = GREEN_THREAD,
        poisonLoc = "loc.murdercompost",
        poisonLocName = "compost heap",
    ),
    Bob(
        id = 2,
        displayName = "Bob",
        male = true,
        npcs = listOf("npc.bob_sinclair", "npc.kr_bob_sinclair_multi"),
        barrel = "loc.murderbarrelb",
        silverItem = "obj.murdercup",
        dustedItem = "obj.murdercupdust",
        print = "obj.murderfingerprintb",
        thread = RED_THREAD,
        poisonLoc = "loc.murderhive",
        poisonLocName = "beehive",
    ),
    Carol(
        id = 3,
        displayName = "Carol",
        male = false,
        npcs = listOf("npc.carol_sinclair", "npc.kr_carol_sinclair_multi"),
        barrel = "loc.murderbarrelc",
        silverItem = "obj.murderbottle",
        dustedItem = "obj.murderbottledust",
        print = "obj.murderfingerprintc",
        thread = RED_THREAD,
        poisonLoc = "loc.murderdrain",
        poisonLocName = "drain",
    ),
    David(
        id = 4,
        displayName = "David",
        male = true,
        npcs = listOf("npc.david_sinclair", "npc.kr_david_sinclair_multi"),
        barrel = "loc.murderbarreld",
        silverItem = "obj.murderbook",
        dustedItem = "obj.murderbookdust",
        print = "obj.murderfingerprintd",
        thread = GREEN_THREAD,
        poisonLoc = "loc.murderweb",
        poisonLocName = "spiders nest",
    ),
    Elizabeth(
        id = 5,
        displayName = "Elizabeth",
        male = false,
        npcs = listOf("npc.elizabeth_sinclair", "npc.kr_elizabeth_sinclair_multi"),
        barrel = "loc.murderbarrele",
        silverItem = "obj.murderneedle",
        dustedItem = "obj.murderneedledust",
        print = "obj.murderfingerprinte",
        thread = BLUE_THREAD,
        poisonLoc = "loc.murderfountain",
        poisonLocName = "fountain",
    ),
    Frank(
        id = 6,
        displayName = "Frank",
        male = true,
        npcs = listOf("npc.frank_sinclair", "npc.kr_frank_sinclair_multi"),
        barrel = "loc.murderbarrelf",
        silverItem = "obj.murderpot",
        dustedItem = "obj.murderpotdust",
        print = "obj.murderfingerprintf",
        thread = BLUE_THREAD,
        poisonLoc = "loc.murdersign",
        poisonLocName = "Sinclair Crest",
    );

    val objectPronoun: String
        get() = if (male) "him" else "her"

    val possessivePronoun: String
        get() = if (male) "his" else "her"

    companion object {
        fun byId(id: Int): Suspect? = entries.firstOrNull { it.id == id }
    }
}

const val RED_THREAD = "obj.murderthreadr"
const val GREEN_THREAD = "obj.murderthreadg"
const val BLUE_THREAD = "obj.murderthreadb"
