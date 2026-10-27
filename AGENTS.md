# Working on Power Armor

A NeoForge 26.3 + GeckoLib 5.5.7 Minecraft mod (Java 25): an enterable power-armor suit. This file is the rules; the
knowledge lives in the project skills and their references (read the one that matches the task):

| Read | When |
|---|---|
| `.agents/skills/minecraft-modding/SKILL.md` | any game code: setup, GeckoLib, riding, testing (its `references/`) |
| `.agents/skills/minecraft-modding/references/power-armor-lessons.md` | before changing code in an area: what each part of this mod already cost us |
| `.agents/skills/minecraft-modding/references/power-armor-runs.md` | every verification command and autopilot scene, what it drives and logs |
| `.agents/skills/blockbench-mcp/SKILL.md` + `ANIMATION.md` + `power-armor-model.md` | model, rig, animation, texture work |
| `.agents/skills/key-art/SKILL.md` | marketing pictures (Blender) |
| `README.md`, `docs/guide.md` | what the mod does, controls, the file map |
| Notion (below) | design decisions, roadmap, the user's taste |

## The user
- **First Minecraft mod.** Explain concepts briefly as you go (client vs server, registries, events, synced data,
  menus, packets...), tied to the change you just made. Comment the "why" in code: the user reads it to learn.
- **Do exactly what's asked.** Measure the cause, offer options with numbers (and a sketch or A/B prototypes filmed
  with the same cameras next to the committed model), then build the pick. Read the user's sketches literally and
  confirm your reading in words before building.
- **Never open or modify `run/saves/New World`** (the user's creative world, played between sessions). Automated runs
  use a copy (`runAutopilotTerrain`). A sorted md5 listing is in `.scratch/world_baseline.txt`
  (`find . -type f ! -name session.lock -exec md5 -r {} \; | sort -k2`): refresh it at the start of a session and check
  file mtimes before blaming a run.
- **The art lives in Blockbench: `model/power_armor.bbmodel`.** Checkpoint before edits. Save it
  (`BarItems.save_project.trigger()` via risky_eval; if it's open from elsewhere set `Project.save_path` to this file
  first) and commit it **with every mod commit that touches the art or animations**. Before a checkpoint, diff the live
  project against the saved file plus your planned edits: the user hides and moves things in Blockbench between turns.
- **Show every model change with the review sheet** (`.agents/skills/blockbench-mcp/scripts/shots.js`): it includes the
  in-game third-person back view, the one the user cares most about. Keep the model in its in-game rest pose.
- **Read Notion's Art direction page before Blockbench or animation work**, and add a dated line when the user rules on
  looks or motion.
- Commit freely once a step is verified (one approved step per commit). Stop testing when told.

## Notion (design, roadmap, taste)
The design docs live in the user's Notion (the repo is public): Minecraft Add-on Studio > Projects > *Power Armor*.
Read them with the Notion MCP (`notion_fetch` with the page id), from codemode.

| Page | Id | What |
|---|---|---|
| Power Armor (project page) | `3feeb0d9a8ce8122ad20f48b5dc4d0a2` | status, links to the rest |
| Design spec | `405eb0d9a8ce810bae56eb48d4837873` | what is decided and why; numbered sections (`design sec. 4b` in code comments points here); **OPEN** items need the user's decision before building |
| Roadmap (database) | `be28d19e58124d1d8421d605254d7c8f`, data source `collection://336c5748-bfbc-4f04-84cb-06891b2bba4d` | one row per phase / tool / open item: Status (Backlog, Parked, Drafting, Building, Done), Kind, Phase, Release, Done date, Branch, Summary; the page body holds the details (files, tests, scenes) |
| Art direction | `405eb0d9a8ce81c39a35d0979cf8b096` | the user's taste rulings on look and motion |
| Design history | `405eb0d9a8ce81d18f0fc3528967e031` | the pre-Phase-1 discussion (frozen) |

Keep them current as part of the work: a decision goes into the Design spec when it's made; a feature gets a Roadmap row
(Drafting/Building while in progress, Done + date + release when merged; its body: what was built, where, tests,
autopilot scene). Put paths in backticks (Notion turns bare file names into web links).

**Done** means: build passes, all GameTests pass, an autopilot run looks right (screenshots / CSV), README +
`docs/guide.md` + the Design spec are updated, it's committed, and the user got a short explanation. New tools: hidden
`proto_*` drafts in Blockbench -> the user picks -> game part (bone, code, icon, recipe, autopilot scene, tests).

## Layout
- `src/main/` the mod (what ships). `src/dev/` GameTests, autopilot scenes, the multiplayer script and `test_pad`: a
  separate source set that sees the mod but isn't in the jar (entry `dev/PowerArmorDev`; every run loads both). New dev
  or test code goes there.
- `model/` the Blockbench source (`power_armor.bbmodel`, `anim/` procedural animation specs: bake them as
  `model/anim/README.md` says, `art/` texture/part scripts); `tools/` export, art and test helpers; `run/` the dev game (not in git); `marketing/` key art (see its README).
- `site/` the mod's web page (GitHub Pages), a template filled by `tools/build-site.py` from the latest GitHub Release:
  mark features being built with `data-since="1.1.0"` / `<span data-coming="1.1.0">`. Preview:
  `python3 tools/build-site.py --version 1.0.0 /tmp/site`.
- `.pi/mcp.json` connects pi to Blockbench's MCP server (`localhost:3000/bb-mcp`): start sessions here.
- **Releasing:** bump `mod_version`, commit, `git tag -a vX.Y.Z -m ... && git push origin main vX.Y.Z`. The tag push runs
  `.github/workflows/release.yml` (reproducible build with the JDK pinned in `tools/jdk.env`, attestation, jar + SHA-256,
  republishes the page; details in `power-armor-runs.md`). v1.0.0 = 9c9cf00 (Modrinth); the flamethrower and later tools
  are 1.1.

## Setup
Java 25 from Homebrew (`brew install openjdk@25`) is keg-only. Every shell needs:
```sh
export JAVA_HOME=$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home
```
Reference sources: Minecraft (NeoForge-patched) in `build/moddev/artifacts/minecraft-patched-*-sources.jar`, NeoForge in
the Gradle cache (`neoforge-*-sources.jar`), GeckoLib on GitHub (`bernie-g/geckolib`, branch per Minecraft version).
Mojang's names are official since 26.1. Porting primers: `neoforged/.github` repo, `primers/<version>`. Never guess an
API: grep the sources.

## Verification loop (every change)
| Command | Must show |
|---|---|
| `./gradlew build` | compiles (also compiles `src/dev`) |
| `./gradlew runGameTestServer` | "All N required tests passed" |
| `./gradlew runGameTestOnly -Prepeat=5` | the whole suite 5 times side by side: all 675 pass (hunts flaky tests; `-Ptests='powerarmor:name*'` narrows it) |
| the autopilot scene for the area you touched | screenshots + CSV look right: pick it from `power-armor-runs.md` (`runAutopilot`, `...Mech`, `...Gait`, `...Pose`, `...Terrain`, a tool's own scene...) |

- Run game tasks in the background under `caffeinate -d -u -i` and poll the log (no `timeout` on macOS; the game can't
  start while the display sleeps). If one stalls, `jstack` its PID before killing it. Dev games must not linger
  (`tools/view-tuner.sh stop`).
- Check output file dates before trusting a CSV or report: an old file was once read as a fresh pass.
- Compare against a run of the previous commit when a number looks off: runs vary (mob AI, client/server tick
  timing), and the user's world changes between sessions.
- Each new feature gets an autopilot scenario that logs numbers to CSV: its own `*Scene` class in `client/dev`, chained
  after the last one (ticks from 0; `AutoPilot.script()` case labels count from their section's start, `WATER + 17`).

## Rules for code
Why each rule exists is in `power-armor-lessons.md`.
- **Where things go.** `PowerArmorEntity` is the suit as Minecraft sees it (vanilla hooks, the synced data table, the
  slots, the tick); its rules live in its parts, one class each: `SuitDriving`, `SuitPower`, `SuitSocket`, `SuitBrain`,
  client `SocketVisuals`, `SuitAnimator`, `SuitLights`. A tool lives in its own files: its item (`PowerTool`: what it
  does, `mindUse()` / `mindGoals()` its mind job, `createEffects()` its looks) and its `ToolEffects`; a new tool must not
  need changes to the entity, the renderers or `SuitGoals` beyond its bone name and glowmask.
- **Client classes never appear in shared code** (`Minecraft`, cameras, screens: a dedicated server crashes). Ask through
  a hook the client fills in at startup (`entity/ClientView`, `SocketAim.visualMuzzle`).
- **Server state with a synced copy has one setter that writes both** (`SuitPower.setBurnState`); slot copies are
  re-checked every tick. Persisted tuning is re-applied on load (`BASE_STATS` in `readAdditionalSaveData`). Keep old save
  keys and formats.
- **Balance values live in `PowerArmorConfig`** (hot-reloaded): read `.get()` where used, never cache them; tests compare
  against the config, not literals.
- **Ridden movement is client-authoritative**: gate it on synced state; read the pilot's keys on the server through
  `SuitDriving.pilotInput`.
- **Block breaking by tools goes through `ToolMining.breakBlock`** (posts the BreakEvent claims mods veto).
- **Client-side caches die with the world** (clear on logout, check `SoundManager.isActive`); looping sounds are client
  side from synced state.
- **GeckoLib 5:** pose bones in `adjustModelBonesForRender` from the render state only (copy what you need in
  `addRenderData`); code offsets use Blockbench's signs on all axes (check poses with `runAutopilotPose`); set animation
  speed in the state handler.
- **Effects:** glow fades by opacity (`SuitGlowLayer.energy`), never by darkening; glowing texels use the reserved colours
  in `tools/make-glowmasks.py` only; check a new effect with a frame diff with and without it.
- **No deprecated Minecraft calls** (check with `-Xlint:deprecation`), no `ItemStack` in static initializers, wrap yaw
  differences with `Mth.wrapDegrees`.
- **GameTests:** no shared mutable state (tests and their copies run side by side; world settings go in the batch's test
  environment); turn mock pilots with `lookAt` / `turn`; create mock server players with `mockServerPlayer`; search for
  entities near your own position only. A suit that should run needs an engine, fuel and a helmet (`suitAt` gives the
  engine and helmet; scenes call `DevUtil.firebox`); a powered suit's mind acts: take the fuel out to park one.
- **Autopilot worlds are peaceful** (set the difficulty in a scene that needs monsters); scripts that use a tool empty the
  pilot's hand first; call `setSprinting(false)` after a sprint.

## Rules for the model and textures
Details in `.agents/skills/blockbench-mcp/power-armor-model.md`.
- Simple boxes, detail in the texture; armour plates in the 8 steel shades only (paint swaps them); re-run
  `tools/make-paint-textures.py` after every texture export.
- Drafts are hidden `proto_*` groups with new, unused cube prefixes (grep the model first); keep them hidden when saving.
- After an export: `extract-view-geo.py`, `make-glowmasks.py`, `make-paint-textures.py`; diff bones and keyframes against
  the last export and re-measure code numbers (`SocketAim`, the tool raise) if bones moved.
- Moving a pivot or reshaping a joint invalidates baked animations: re-solve the specs in `model/anim/` and measure
  (`gaitsweep.mjs`, `livefeet.mjs`, `runAutopilotGait`). Carry hand tweaks to the legs into their rebuild script.
- Z-fighting: `inflate` 0.1 (scan with `zfight.mjs`); mirrored fractional faces need mirrored UVs (`rail_symcheck.py`).
