---
status: proposed
---

# Matter-line Void Dragons carry items between dimensions

An End Displacer gives the top grade only from dense blocks fed to it in the End
([#88](https://github.com/5thlayer/voidworks/issues/88)), and those blocks are made in Overworld
and Nether factories. Nothing in the suite carries items between dimensions, and vanilla's portals
carry them unattended only with `/forceload`. Their departure end and any long rail stay unloaded
otherwise, End arrivals break blocks around a fixed platform, and End returns land at world spawn
([#87](https://github.com/5thlayer/voidworks/issues/87)). The mods that move items unattended over
long range do it as saved data: a record with a route, an arrival and a cargo, never an entity
moving through unloaded chunks ([#89](https://github.com/5thlayer/voidworks/issues/89)). Voidworks
already has one such record, the Void Dragon ([ADR 0005](0005-a-void-dragon-is-server-state-not-an-entity.md)).

**Decision.** Voidworks carries matter between dimensions, and the Void Dragon carries it, in one
tier. Each dragon has a **Line**, fixed in its egg: the Mote line carries motes and the Matter line
carries items, never both. The vanilla egg founds the Mote line. A Drained Dragon Egg upgraded by
Mutation becomes a Matter Dragon Egg, which founds the Matter line, and nothing upgrades it back.
Each Line lays eggs of its own. A Matter-line dragon serves its owner's Wells as a Mote-line one
does. It loads and unloads at **Well hatches**, which are requester and provider containers on a
Well's wall, after Factorio's chests, and it routes items as the Mote line routes motes. A load is
counted in stacks. As it loads, it pays a **Toll** from the departure Well's network: void energy
per stack, doubled for each step of Void Pressure the cargo climbs. Before the Matter line, a
player carries items by hand.

The void is the medium, never the cargo. Motes stay a resource and never items
([ADR 0002](0002-motes-are-a-resource-never-items.md)). Nothing turns motes into items, and the
Displacer stays the only way matter becomes motes.

## Considered options

- **A long-range transport Module**: trains or drones inside a dimension, with a teleport between
  dimensions. To charge void it would need Voidworks, which breaks FactoryWorks ADR-0128, and it
  would be an asset-authoring effort of its own. Beltworks was passed over too, since it would need
  a rename.
- **Two tiers, an early one that carts items to a vanilla portal**: dragons come only after End
  cities, because the Void Well needs shulker shells. By then a dragon dives straight to the End
  base. A vanilla portal needs `/forceload` to work unattended, so an early tier would deliver
  nothing a dive doesn't.
- **One dragon carrying both motes and items**: no new egg is needed, but every trip would weigh
  motes against items. A separate Line gives the egg economy a second branch and makes the matter
  link something a player unlocks.
- **A motes-to-matter converter**: it reverses ADR 0002 and its promise that void never becomes
  ordinary items.
- **Carrying for free**: throughput would then be bounded only by dragons. The Toll makes void the
  price of moving matter, so a Density-2 stack lifted to the End breaks even. Density stays the
  reason to ship dense blocks.

## Consequences

- Only void-mediated movement costs void. The Mote line pays no Toll.
- A Well hatch keeps its contents in saved data, as a Well keeps its motes, so a dragon loads and
  unloads one with no chunk loaded. A hopper beneath it reaches it with nothing but vanilla.
- A filled container takes one stack of a load but pays the Toll for what it holds. Shipping
  shulker boxes rewards capacity and never discounts the price.
- A dragon dies only at a stop once it is carrying nothing (ADR 0010), so no item is lost to old
  age, and nothing else loses a load.
- Bred stat traits (capacity, speed, resilience) are left for a later effort. The Line alone
  answers how items cross.
