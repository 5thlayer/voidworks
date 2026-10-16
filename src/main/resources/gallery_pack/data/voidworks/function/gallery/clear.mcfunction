# Clears a scene's plot, from ~2 to ~25 east and from ~-12 to ~0 south, 16 high, onto a smooth stone
# floor, which no Displacer harvests: the item displays and dropped items in it, then its blocks.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=minecraft:item_display,dx=23,dy=16,dz=12] run kill @s
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=minecraft:item,dx=23,dy=16,dz=12] run kill @s
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:smooth_stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air
