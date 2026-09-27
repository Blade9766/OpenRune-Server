package org.rsmod.content.other.barrows

import org.rsmod.map.CoordGrid

enum class Brother(
    val npc: String,
    val sarcophagus: String,
    val stairs: String,
    val killedVarbit: String,
    val overlayHead: String,
    val faceModel: Int,
    val mound: CoordGrid,
    val cryptLanding: CoordGrid,
    val items: List<String>,
) {
    Ahrim(
        npc = "npc.barrows_ahrim",
        sarcophagus = "loc.barrow_ahrim_sarcophagus",
        stairs = "loc.barrows_stairs_ahrim",
        killedVarbit = "varbit.barrows_killed_ahrim",
        overlayHead = "component.barrows_overlay:ahrim",
        faceModel = 2998,
        mound = CoordGrid(3565, 3289, 0),
        cryptLanding = CoordGrid(3557, 9703, 3),
        items = pieces("ahrim"),
    ),
    Dharok(
        npc = "npc.barrows_dharok",
        sarcophagus = "loc.barrow_dharok_sarcophagus",
        stairs = "loc.barrows_stairs_dharok",
        killedVarbit = "varbit.barrows_killed_dharok",
        overlayHead = "component.barrows_overlay:dharok",
        faceModel = 2419,
        mound = CoordGrid(3575, 3298, 0),
        cryptLanding = CoordGrid(3556, 9718, 3),
        items = pieces("dharok"),
    ),
    Guthan(
        npc = "npc.barrows_guthan",
        sarcophagus = "loc.barrow_guthan_sarcophagus",
        stairs = "loc.barrows_stairs_guthan",
        killedVarbit = "varbit.barrows_killed_guthan",
        overlayHead = "component.barrows_overlay:guthan",
        faceModel = 2421,
        mound = CoordGrid(3577, 3282, 0),
        cryptLanding = CoordGrid(3534, 9704, 3),
        items = pieces("guthan"),
    ),
    Karil(
        npc = "npc.barrows_karil",
        sarcophagus = "loc.barrow_karil_sarcophagus",
        stairs = "loc.barrows_stairs_karil",
        killedVarbit = "varbit.barrows_killed_karil",
        overlayHead = "component.barrows_overlay:karil",
        faceModel = 16860,
        mound = CoordGrid(3565, 3275, 0),
        cryptLanding = CoordGrid(3546, 9684, 3),
        items = pieces("karil"),
    ),
    Torag(
        npc = "npc.barrows_torag",
        sarcophagus = "loc.barrow_torag_sarcophagus",
        stairs = "loc.barrows_stairs_torag",
        killedVarbit = "varbit.barrows_killed_torag",
        overlayHead = "component.barrows_overlay:torag",
        faceModel = 2421,
        mound = CoordGrid(3554, 3283, 0),
        cryptLanding = CoordGrid(3568, 9683, 3),
        items = pieces("torag"),
    ),
    Verac(
        npc = "npc.barrows_verac",
        sarcophagus = "loc.barrow_verac_sarcophagus",
        stairs = "loc.barrows_stairs_verac",
        killedVarbit = "varbit.barrows_killed_verac",
        overlayHead = "component.barrows_overlay:verac",
        faceModel = 2419,
        mound = CoordGrid(3557, 3298, 0),
        cryptLanding = CoordGrid(3578, 9706, 3),
        items = pieces("verac"),
    );

    /** The varbit value naming this brother; 0 means none. */
    val code: Int
        get() = ordinal + 1

    companion object {
        fun ofCode(code: Int): Brother? = entries.getOrNull(code - 1)

        fun ofNpc(npcId: Int, resolve: (String) -> Int): Brother? =
            entries.firstOrNull { resolve(it.npc) == npcId }
    }
}

private fun pieces(brother: String): List<String> =
    listOf("head", "body", "legs", "weapon").map { "obj.barrows_${brother}_$it" }

/**
 * A room in the 3x3 tunnel grid below the crypts; [Ring] is the corridor running round the
 * outside that joins the four corner rooms.
 */
enum class TunnelRoom {
    NorthWest,
    North,
    NorthEast,
    West,
    Centre,
    East,
    SouthWest,
    South,
    SouthEast,
    Ring,
}

/**
 * One corridor of the tunnels. The cache gives each corridor a single door varbit
 * (`varbit.barrows_door_a`..`_p`, 1 = locked) shared by every door leaf on it. The four outer
 * corridors each join two corner rooms to the ring at once.
 */
enum class TunnelCorridor(val links: List<Pair<TunnelRoom, TunnelRoom>>) {
    A(listOf(TunnelRoom.NorthWest to TunnelRoom.Ring, TunnelRoom.NorthEast to TunnelRoom.Ring)),
    B(listOf(TunnelRoom.NorthWest to TunnelRoom.Ring, TunnelRoom.SouthWest to TunnelRoom.Ring)),
    C(listOf(TunnelRoom.NorthWest to TunnelRoom.West)),
    D(listOf(TunnelRoom.NorthWest to TunnelRoom.North)),
    E(listOf(TunnelRoom.North to TunnelRoom.Centre)),
    F(listOf(TunnelRoom.North to TunnelRoom.NorthEast)),
    G(listOf(TunnelRoom.NorthEast to TunnelRoom.East)),
    H(listOf(TunnelRoom.NorthEast to TunnelRoom.Ring, TunnelRoom.SouthEast to TunnelRoom.Ring)),
    I(listOf(TunnelRoom.West to TunnelRoom.Centre)),
    J(listOf(TunnelRoom.Centre to TunnelRoom.East)),
    K(listOf(TunnelRoom.West to TunnelRoom.SouthWest)),
    L(listOf(TunnelRoom.Centre to TunnelRoom.South)),
    M(listOf(TunnelRoom.East to TunnelRoom.SouthEast)),
    N(listOf(TunnelRoom.SouthWest to TunnelRoom.South)),
    O(listOf(TunnelRoom.South to TunnelRoom.SouthEast)),
    P(listOf(TunnelRoom.SouthWest to TunnelRoom.Ring, TunnelRoom.SouthEast to TunnelRoom.Ring));

    val varbit: String = "varbit.barrows_door_${name.lowercase()}"
    val rightDoor: String = "loc.barrows_door_${name.lowercase()}_r"
    val leftDoor: String = "loc.barrows_door_${name.lowercase()}_l"

    val entersCentre: Boolean
        get() = links.any { TunnelRoom.Centre in it.toList() }
}

object BarrowsCoords {
    const val MIN_X = 3520
    const val MAX_X = 3583
    const val MIN_Z = 9664
    const val MAX_Z = 9727
    const val TUNNEL_LEVEL = 0
    const val CRYPT_LEVEL = 3

    val CHEST = CoordGrid(3551, 9694, 0)

    val CENTRE_MIN = CoordGrid(3546, 9689, 0)
    val CENTRE_MAX = CoordGrid(3557, 9700, 0)

    /** Where the hidden tunnel drops the player in each corner room, beside its ladder. */
    val CORNER_ENTRIES =
        listOf(
            CoordGrid(3535, 9712, 0),
            CoordGrid(3569, 9712, 0),
            CoordGrid(3535, 9678, 0),
            CoordGrid(3569, 9678, 0),
        )

    val SURFACE_MIN = CoordGrid(3546, 3268, 0)
    val SURFACE_MAX = CoordGrid(3583, 3319, 0)

    fun inUnderground(coords: CoordGrid): Boolean =
        coords.x in MIN_X..MAX_X && coords.z in MIN_Z..MAX_Z

    fun inTunnels(coords: CoordGrid): Boolean =
        inUnderground(coords) && coords.level == TUNNEL_LEVEL

    fun inCrypts(coords: CoordGrid): Boolean =
        inUnderground(coords) && coords.level == CRYPT_LEVEL

    fun inCentreRoom(coords: CoordGrid): Boolean =
        coords.level == TUNNEL_LEVEL &&
            coords.x in CENTRE_MIN.x..CENTRE_MAX.x &&
            coords.z in CENTRE_MIN.z..CENTRE_MAX.z

    fun onSurface(coords: CoordGrid): Boolean =
        coords.level == 0 &&
            coords.x in SURFACE_MIN.x..SURFACE_MAX.x &&
            coords.z in SURFACE_MIN.z..SURFACE_MAX.z

    fun inBarrows(coords: CoordGrid): Boolean = onSurface(coords) || inUnderground(coords)
}
