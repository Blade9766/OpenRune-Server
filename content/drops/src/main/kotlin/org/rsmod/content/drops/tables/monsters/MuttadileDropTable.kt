package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val muttadileDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Muttadile Drops",
    npcs = npcs("npc.raids_dogodile", "npc.raids_dogodile_junior", "npc.raids_dogodile_submerged"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.raids_houndmaster_book" count 1 condition { player ->
            player.vars["varbit.raids_book_houndmaster_read"] == 0
        }
        "obj.raids_vial_overload_strong_4" count 1
        "obj.raids_vial_prayer_strong_4" count 1
        "obj.raids_vial_xericaid_strong_4" count 1 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.raids_dogodile" || npc.type.internalName == "npc.raids_dogodile_submerged"
        }
        "obj.raids_vial_revitalisation_strong_4" count 1
        "obj.raids_stinkhorn_mushroom" count 2 killCondition { player, npc, areaChecker ->
            npc.type.internalName == "npc.raids_dogodile_junior"
        }
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 400 weight "obj.dorgesh_construction_bone" count 1
        1 outOf 5013 weight "obj.dorgesh_construction_bone_curved" count 1
    },
)
