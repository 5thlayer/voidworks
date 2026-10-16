# Tool and armour Families

Voidworks does not upgrade tools or armour up a Family (wooden → stone → copper → iron → golden →
diamond, or the armour ladders).

## Why this is out of scope

Equipment meets the void a different way: the Void Siphon serves as an FE provider that charges
powered equipment. Void energy reaches a player's gear as power, not by mutating the gear itself.

Upgrading equipment would also have meant an upgrade carrying item data across (enchantments,
damage, custom names, armour trims), which no other Family needs. A Family stays a ladder of plain
items, so `Upgrade` hands back a fresh result and never copies item data.

## Prior requests

- #36: "Tool and armour Families"
