# The End Displacer: one awake, facing up, fed coal blocks from a hopper beside it. It grows its body
# of Voidstone up into the air until its harvests are spent. Outside the End it is shown as an item.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"end_displacer"}
execute if dimension minecraft:the_end run function voidworks:gallery/end_displacer/working
execute unless dimension minecraft:the_end run function voidworks:gallery/elsewhere {item:"voidworks:end_void_displacer"}
