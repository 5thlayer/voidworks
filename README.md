# Voidworks

Voidworks harvests **void** from the world itself. Feed a block to a Void Displacer and it is pressed into the space between blocks, yielding motes and leaving indestructible Voidstone behind, for good. Motes crawl through Voidstone, ride Void Dragons between dimensions, and are spent on endgame gear and on **Mutation**: upgrading an item up its Family, or breaking it down into its ingredients.

**NeoForge, Minecraft 26.1.2 only.** Voidworks is in design: nothing is playable yet. The design is [docs/spec/void-energy.md](docs/spec/void-energy.md), and the terms are in [CONTEXT.md](CONTEXT.md).

## What sets it apart

- **Void never becomes ordinary items.** Unlike EMC or UU-matter, motes buy power, Mutation and void-only materials. Mutation is the only path from motes to an ordinary item, and it needs the item's Family or recipe to start from.
- **Harvesting consumes the world.** Every harvested position becomes Voidstone and every Displacer ends as Voidstone, so void is bounded by the world you are willing to spend.
- **Void runs downhill.** A mote is worth more the higher the Void Pressure where it was harvested, and the lower the Pressure where it is spent. The End is the reservoir and the Nether the turbine, and moving motes between them is the logistics game.

## For pack authors

Planned, all as data: the Density of each block by tag, the displacement table that decides what an Overworld Displacer returns for a terrain block, the Families an upgrade climbs, and overrides for breakdowns.

## Dependencies

None beyond NeoForge.

## License

MIT. Source is on [GitHub](https://github.com/5thlayer/voidworks).
