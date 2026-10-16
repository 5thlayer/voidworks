# The Nether Displacer: one awake beside a walled pool of nine lava sources, fed coal blocks from a
# hopper above. It drinks the pool into its tank, a bucket a source, leaving Voidstone. Outside the
# Nether it is shown as an item.
function voidworks:gallery/clear
function voidworks:gallery/sign {scene:"nether_displacer"}
execute if dimension minecraft:the_nether run function voidworks:gallery/nether_displacer/working
execute unless dimension minecraft:the_nether run function voidworks:gallery/elsewhere {item:"voidworks:nether_void_displacer"}
