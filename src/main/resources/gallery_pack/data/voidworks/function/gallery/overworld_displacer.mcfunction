# The Overworld Displacer: one at the top of a mound of stone's face, fed coal blocks from a hopper
# above and emptied by a hopper into a chest below, both in plain sight. It swaps the mound's stone for its coal, giving the stone back,
# until its 64 harvests are spent and it turns to Voidstone. Elsewhere it is shown as an item.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"overworld_displacer"}
execute if dimension minecraft:overworld run function voidworks:gallery/overworld_displacer/working
execute unless dimension minecraft:overworld run function voidworks:gallery/elsewhere {item:"voidworks:overworld_void_displacer"}
