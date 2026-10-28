---
name: minecraft-modding
description: Use when building or changing a Minecraft Java mod with NeoForge (1.21.x or 26.x) and GeckoLib (4 or 5), or porting one between them, especially animated Blockbench models as entities, rideable/enterable vehicles or mechs, GameTests, scripted in-game autopilot verification (screenshots, CSV logs), camera/HUD tweaks, water/terrain movement, saved-data migration, or teaching a first-time modder. Covers project setup on macOS, the Blockbench -> GeckoLib export pipeline, vanilla riding hooks, verification loops and the pitfalls that cost real debugging rounds on the Power Suits mod.
---

# Minecraft modding (NeoForge + GeckoLib)

Learned while building Power Suits (`~/Developer/PowerSuits`, an enterable mech suit from a Blockbench
model) with a user who had never modded before. Read the reference that matches the task:

| Reference | Read when |
|---|---|
| [references/setup.md](references/setup.md) | new project, Java/Gradle setup, finding the real API (sources jars), commands |
| [references/porting-26.md](references/porting-26.md) | porting 1.21.1 -> 26.3 / GeckoLib 4 -> 5: rename table, saving, rendering, GameTests, measured differences (the other references' snippets are 1.21.1-era unless marked) |
| [references/geckolib.md](references/geckolib.md) | Blockbench model -> GeckoLib entity, animation controllers, rendering, flicker |
| [references/rideable-entity.md](references/rideable-entity.md) | vehicles/mechs: riding, movement, camera, damage, terrain, water, saved data |
| [references/testing.md](references/testing.md) | GameTests (incl. menus), the autopilot client harness, measuring in-game, protecting user worlds |
| [references/tools-projectiles.md](references/tools-projectiles.md) | vehicle-mounted tools, hold-to-use input packets, aiming from a muzzle, projectiles, damage types, tool bones, GeckoLib items cut from an entity model |
| [references/power-suits-lessons.md](references/power-suits-lessons.md) | changing Power Suits' code: the lessons each area cost, by topic (the rules are in the repo's `AGENTS.md`) |
| [references/power-suits-runs.md](references/power-suits-runs.md) | verifying Power Suits: every command and autopilot scene, what it drives and logs; the web page and releases |
| [references/menus-hud.md](references/menus-hud.md) | entity inventories, menus + screens, menu buttons, item data components, synced entity data, HUD layers, particles |

Scripts (run from anywhere, paths relative to this skill): `scripts/make_test_structure.py`
(GameTest floor .nbt without NBT libraries), `scripts/contact_sheet.py` (tile screenshots),
`scripts/export_model.mjs` (export geo/animations/texture from the open Blockbench project via MCP).
For model and animation work use the Blockbench skill:
`.agents/skills/blockbench-mcp/SKILL.md` (+ `ANIMATION.md`).

## Workflow that worked
1. **Pin versions and read real sources.** Never guess an API from memory: extract the NeoForge
   sources jar (`build/moddev/artifacts/*-sources.jar`) and the GeckoLib `-sources.jar`, then `grep`
   for signatures before writing code (setup.md). This avoided nearly all compile errors.
2. **Build the smallest playable slice first** (place item -> entity -> ride -> animate), then iterate
   on the user's play feedback.
3. **Verify every change three ways:** `./gradlew build`, headless GameTests for logic, and an
   **autopilot** client run that drives the real game, takes screenshots and logs numbers (testing.md).
   Screenshots found the cockpit flicker; CSV logs found that "slow" was a stale saved attribute.
4. **Measure, then change.** When feedback is vague ("feels slow", "arms flicker"), log the relevant
   state per tick (speed, ground contact, animation name, camera height) and look for the pattern.
   When a calculation and a measurement disagree, trust the measurement.
5. **Protect the user's things.** Never open their world (copy it, checksum the original), checkpoint
   Blockbench before edits, commit after each verified step (on Power Suits the user wants the .bbmodel
   saved together with every commit that changes art; ask what a new user prefers).
6. **Teach as you go** (first-time modders): after each change explain one concept in plain words:
   client vs server, registries, events, synced data, why a bug happened. Comment the "why" in code.
7. **Before handing off**, write the spec (with OPEN questions), the plan (phases, files, APIs, tests) and
   `AGENTS.md` (setup, verification loop, the rules every agent must follow; keep it short, put the lessons behind
   the rules in skill references, and keep one person's preferences in the agent's memory, not in the repo). Ask where
   the user keeps design docs: Power Suits' live outside the repo; a new project may want `docs/design.md` +
   `docs/implementation-plan.md` in the repo. Check that every API
   the plan names exists in the pinned versions.

## Pitfalls (each cost a round of debugging)
- **Saved data outlives code.** Entities save attribute base values in NBT; a suit placed in an early
  version kept its old speed forever. Re-apply tuning in `readAdditionalSaveData` (rideable-entity.md).
- **Riding is client-authoritative.** For a piloted vehicle, `travel`/`tickRidden` run on the pilot's
  client; the server only receives positions. Gate movement on synced state.
- **The pilot's eye inside the vehicle's hitbox** makes the crosshair always hit the vehicle (can't mine).
  Return `!isVehicle()` from `isPickable()`.
- **GeckoLib animation speed multiplies total elapsed time**: changing it smoothly mid-loop makes
  animations jump. Use one constant rate per state.
- **GeckoLib draws both sides of faces**: coplanar faces z-fight, and the cockpit sees the insides of cubes.
  Inflate cubes by 0.1 in Blockbench; cull back faces for the first-person view.
- **State flicker**: states that toggle every few ticks (run/fall on slopes) look like flickering parts.
  Add hysteresis (minimum ticks) and remove the cause (step-down snapping).
- **GameTest coordinates**: the structure sits one block above the test origin and is sealed by an
  invisible barrier box its own size; tall entities near the top take suffocation damage.
- **Vanilla quirks**: sprinting continues after the key is released; a freshly spawned `ArmorStand` used as
  a camera needs `yHeadRot` set (the camera follows head yaw); macOS has no `timeout` command.
- **Damage source argument order**: `damageSources().source(key, projectile, shooter)`; names in the signature are misleading.
- **Display asleep = game won't start** on macOS ("Failed to locate a primary monitor"); run under `caffeinate -d -u -i`.
- **Mob AI**: vanilla mobs move at speed squared (MoveControl feeds the speed in as the forward input; normalise it in
  `travel` for a vehicle that must walk like it's piloted); `Mob.serverAiStep` is final (gate goals in `canUse` +
  `updateControlFlags`); apply your own aim in `customServerAiStep` (after the goals, before `LookControl`, which
  zeroes the pitch every tick); paths of wide mobs end short of the target (finish with `setWantedPosition`).
  Goals that depend on equipment come and go with it (vanilla's skeletons: `reassessWeaponGoal`): let each item list
  its goals and swap them outside the selector's tick (Power Suits: `PowerTool.mindGoals`, `SuitBrain.updateToolGoals`),
  so the shared goal file never learns about items.
- **Tool caching**: the image `read` tool may return a cached image for a path it read before; write
  regenerated images to a new filename.
