![Voidworks](https://raw.githubusercontent.com/5thlayer/voidworks/main/publish/voidworks-cover.png)

Voidworks lets you harvest **void** from the Minecraft dimensions. Each dimension gives up its void its own way: you feed dense blocks to a Void Displacer, or to a Void Siphon by hand, and every harvest yields void-infused **motes** and leaves indestructible **Voidstone** behind. Motes fly off as butterflies into the Voidstone network, which stores them, and you spend them to **upgrade items** up their Family, copper ingots into iron, iron into gold, or to **break items down** into their ingredients.

## Why?

Because void asks to be filled!

**NeoForge, Minecraft 26.1.2 only.** Voidworks is early work: harvesting, Voidstone networks, upgrading and breaking items down are in, the Void Well and the Void Dragon are still to come. Please report anything odd on [GitHub](https://github.com/5thlayer/voidworks/issues).

To play Voidworks beside every other FactoryWorks mod, install the [FactoryWorks Showcase](https://www.curseforge.com/minecraft/modpacks/factoryworks-showcase) modpack from CurseForge.

## What sets it apart

- **Void never becomes ordinary items.** Unlike EMC or UU-matter, motes buy upgrades and breakdowns, not items from nothing. An upgrade needs the item a Family starts from, and a breakdown never returns more than went in.
- **Harvesting consumes the world.** Every harvested position becomes Voidstone and every Displacer ends as Voidstone, so void is bounded by the world you are willing to spend.
- **Void runs downhill.** A mote is worth more the higher the Void Pressure where it was harvested, and the lower the Pressure where it is spent. The End is the reservoir and the Nether the turbine, and moving motes between them is the logistics game.

## Features

- **The Void Well.** A large store of motes on a Voidstone network, built as a 5×5×5 well, and the stop where a Void Dragon loads, unloads and heals.
- **The Void Dragon.** Raised on void from a Dragonling, it carries motes or matter between Void Wells, diving through the void between them, across dimensions.
- **Three Void Displacers, one per dimension.** The Overworld Displacer swaps fed blocks into the terrain around it and gives you that terrain back. The End Displacer grows its body out into the air, and harvests higher over the open void. The Nether Displacer drinks lava lakes and fills its tank, which any pipe can drain. Each harvests a fixed number of times, then turns to Voidstone.
- **Density.** What you feed sets how many motes a harvest yields: copper and coal blocks are the lightest, diamond and netherite blocks the densest.
- **Voidstone networks.** Touching Voidstone is one network holding one pool of motes, and every Voidstone adds room.
- **The Void Siphon.** A wand that harvests by hand, a step below a Displacer, and carries motes between networks and dimensions. Standing on Voidstone, it charges from the network or empties into it.
- **Dormant machines.** The End and Nether Displacers and the Mutation and Unmaking Chambers are placed Dormant and wake once their cost in void energy is paid, by the networks touching them or by a Siphon.
- **The Mutation Chamber.** Upgrades items up their Family for motes, with a chance per upgrade. It has no screen: hoppers and belts feed and empty it.
- **The Unmaking Chamber.** Breaks an item down into its crafting recipe's ingredients for motes, each coming back by chance at a yield that rises as it works.
- **The Void Crucible.** Upgrades for free, slowly. It is fastest in the End and slowest in the Nether, and Crucibles working near each other share one rate.
- **Jade support.** Jade shows a network's motes and what they are worth where you stand, a machine's upgrade, progress and what stalls it, and how much of a Dormant machine's cost is paid. Voidworks runs without Jade.

## Gallery

Voidworks carries an optional datapack, **Voidworks Gallery**, that builds its features as working scenes beside you. It is off in every world until you turn it on, in the world's Data Packs screen or with `/datapack enable "mod/voidworks:gallery_pack"`, then run `/function voidworks:gallery/build`, or `/function voidworks:gallery/<scene>` for one scene. It needs cheats in single player (on at creation, or Open to LAN with cheats) and operator permission on a server, and it builds only blocks and what its scenes need an owner for: run it as a player, who owns them. It lays out a scene for each feature above, in rows of three, and clicking a scene's sign builds it again from the start, to watch it play out. The End and Nether Displacers work only in their own dimension, so run it there to see them at work.

## For pack authors

- **Density** is item tags, `#voidworks:density/1` to `#voidworks:density/4`.
- **The displacement table**, the `voidworks:displacement` data map on blocks, decides what an Overworld Displacer gives back for a terrain block at each Density. Voidworks ships no entries, so it gives the terrain block itself.
- **Families** are the `voidworks:family` datapack registry: the ladders an upgrade climbs, each Rung with its count, cost and chance. Voidworks ships metals, raw metals, gems, dusts and mob drops, with a tag per Rung that takes in the `c:` tags, so other mods' items join by tag.
- **Breakdowns** come from crafting recipes; the `voidworks:breakdown` data map on items fills one in for an item with several recipes or none, or bars one.
- **Void Pressure** and each dimension's harvest kind are the `voidworks:void_pressure` data map, and Dormant costs the `voidworks:dormant_cost` data map.
- **Numbers** such as a Displacer's harvests, Voidstone's capacity and the Crucible's speed are in `voidworks-server.toml`. They are provisional while Voidworks is below 1.0.

The design is [docs/spec/void-energy.md](docs/spec/void-energy.md), and the terms are in [GLOSSARY.md](GLOSSARY.md).

## Dependencies

None beyond NeoForge. Voidworks nests libworks-runtime, so there is nothing more to install. Jade and EMI are optional.

## License

MIT. Source is on [GitHub](https://github.com/5thlayer/voidworks). The block and Siphon sprites are from Malcolm Riley's [unused-textures](https://github.com/malcolmriley/unused-textures), under CC BY 4.0; [NOTICE](NOTICE) credits each.
Releases are on [Modrinth](https://modrinth.com/mod/9JDW0GuH) and [CurseForge](https://www.curseforge.com/projects/1742531).
