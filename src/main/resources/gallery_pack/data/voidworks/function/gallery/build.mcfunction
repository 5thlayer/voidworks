# The Voidworks Gallery: every scene of the Module's features, each building and clearing a plot of
# its own, 24 blocks wide and 13 deep. The scenes stand in rows of three, read west to east and then
# north, each row 16 blocks north of the one before, so the whole gallery stays near enough to where
# it is run for its machines to keep working. Run by an operator player, who owns what the scenes
# need an owner for (ADR 0008).
function voidworks:gallery/void_dragon
execute positioned ~24 ~ ~ run function voidworks:gallery/overworld_displacer
execute positioned ~48 ~ ~ run function voidworks:gallery/end_displacer
execute positioned ~ ~ ~-16 run function voidworks:gallery/nether_displacer
execute positioned ~24 ~ ~-16 run function voidworks:gallery/density
execute positioned ~48 ~ ~-16 run function voidworks:gallery/voidstone_networks
execute positioned ~ ~ ~-32 run function voidworks:gallery/void_siphon_charging
execute positioned ~24 ~ ~-32 run function voidworks:gallery/void_siphon_emptying
execute positioned ~48 ~ ~-32 run function voidworks:gallery/dormant_machines
execute positioned ~ ~ ~-48 run function voidworks:gallery/mutation_chamber
execute positioned ~24 ~ ~-48 run function voidworks:gallery/unmaking_chamber
execute positioned ~48 ~ ~-48 run function voidworks:gallery/void_crucible
