---
status: accepted
---

# Prices are datapack data, and physics is server config

Voidworks is a Sink as well as a source, and a pack must be able to retune what it drains without
code. Its numbers were split by accident: Families, Rungs and Dormant costs were datapack data, the
first egg's price was server config beside the Siphon's rates and the Well's capacity.

**Decision.** What a thing costs is datapack data: a Rung's cost, a Dormant machine's cost, an egg's
first price and how it grows per dragon, a Displacer's zone upgrades, Displacer recipes and void-only
materials. How the void behaves is server config: rates, intervals, capacities, the FE a void energy
charges, the Dragonling's growth and feeding, the Unmaking Chamber's yield curve, and the dragon's
travel, load and health. No spend has a last step, so its price data rises without a cap.

## Considered options

- **Everything in datapack data**: a pack ships datapacks, but rates and capacities are a server's
  balance, which a pack still sets through its default configs, and they would lose the config's
  reload and comments.
- **Leave the split as it was**: a pack retuning hatching would need a config file where every other
  price is data.

## Consequences

- A new spend puts its price in data from the start, keyed by what is bought.
- Zone upgrades have no maximum level; their cost per level is data and keeps rising.
