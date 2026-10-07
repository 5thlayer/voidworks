# Changelog

Written for Consumers: what a mod building against Voidworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- The mote item, `voidworks:mote`: its grade is the `voidworks:grade` data component on the stack, so motes of one grade stack and motes of different grades never merge. `MoteItem.stack(grade, count)` makes a stack and `MoteItem.gradeOf(stack)` reads it; the name shows the grade.
