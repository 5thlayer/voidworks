# Voidworks

A 5thlayer Module: a mod its Consumers, other Modules or a pack, read from the local maven repository by version. See `GLOSSARY.md` for the domain glossary.

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word. Asking a session to steward a PR is the user's word for that PR's pushes, its rebases onto `main`, and its merge, under the `steward` skill.

Pull requests are squash-merged, the PR title becoming the conventional commit on `main`. Work after a merge starts on a new branch from `main`, never on the merged one.

Anything that changes the Module's behaviour gets a `/code-review`. That is the project skill in `.claude/skills/code-review`, from mattpocock/skills; never the built-in review of the same name. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no game running: Minecraft is on their classpath only for the game types the rules they test name. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server, and names each one it ran; it fails if it ran none. The `skillworks:gametest-loop` skill runs a subset, `--tests 'voidworks:<glob>' 1`, one glob a run, so a ticket's new game tests share a name prefix that selects them all. Game-test code and test-only resources live in the `gametest` source set and never ship (libworks ADR 0011): `VoidworksGameTests` hooks itself in from `FMLConstructModEvent`, and `src/main` never names game-test code. Where shipped code must report to a test, `main` holds a hook with no test vocabulary that the game-test side fills. A new game test class is registered by a line in `VoidworksGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that `sh ./gradlew buildGametestStructures` writes into `src/gametest/resources`. CI (`.github/workflows/ci.yml`) runs both on every push through libworks' shared `module-ci.yml`, at the tag of the plugin `build.gradle` applies, and never publishes. Data only the Module's own runs load lives in `src/gametest/resources`, which never ships (libworks ADR 0004): `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client, the datapacks that stand in for what a Consumer fills. Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`.

Vanilla's code, with NeoForge's patches, is in `build/moddev/artifacts/minecraft-patched-*-sources.jar`; NeoForge's own classes (`net/neoforged/...`) are in `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`, `<version>` being `neoforge_version` in `gradle.properties` (the cache keeps older ones). Read either with `unzip -p <jar> <path>/X.java`, and check a claim about vanilla or NeoForge behaviour there before stating it.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through the libworks plugin's `release` task, which uploads last with its `upload` task when `gradle.properties` names a project, and a published version never changes, in `~/.m2` or on either site. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach a Consumer in another checkout, a Module or a pack that pins the jar, follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues, managed with the `gh` CLI; specs live in `docs/spec/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

The design is `docs/spec/void-energy.md`: read it before building any part of the mechanic.

### After a grilling

When a grilling settles a design, write the outcome into `docs/spec/`, `GLOSSARY.md` and ADRs, then end by naming the user's next command: `/to-tickets <issue>` for a multi-session build, `/implement <issue>` for a single one.
