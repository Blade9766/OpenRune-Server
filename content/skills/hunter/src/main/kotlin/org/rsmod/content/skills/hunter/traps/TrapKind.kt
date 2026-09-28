package org.rsmod.content.skills.hunter.traps

enum class TrapKind(
    val item: String,
    val levelReq: Int,
    val setLoc: String,
    val failingLoc: String,
    val failedLoc: String,
    val trapName: String,
    val catchRange: Int,
) {
    BirdSnare(
        item = "obj.hunting_ojibway_bird_snare",
        levelReq = 1,
        setLoc = "loc.hunting_ojibway_trap",
        failingLoc = "loc.hunting_ojibway_trap_failing",
        failedLoc = "loc.hunting_ojibway_trap_broken",
        trapName = "bird snare",
        catchRange = 0,
    ),
    BoxTrap(
        item = "obj.hunting_box_trap",
        levelReq = 27,
        setLoc = "loc.hunting_boxtrap_empty",
        failingLoc = "loc.hunting_boxtrap_failing",
        failedLoc = "loc.hunting_boxtrap_failed",
        trapName = "box trap",
        catchRange = 1,
    ),
}
