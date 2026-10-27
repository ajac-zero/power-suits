# Power Armor: the verification runs

Every command that checks the mod, and what each autopilot scene drives and logs (moved here from `AGENTS.md` on
2026-10-26; `AGENTS.md` keeps the loop every change goes through), and how the web page and releases work. Run game tasks in the background and poll the log
(macOS has no `timeout`), under `caffeinate -d -u -i`; check output file dates before trusting a CSV.
| Command | Use |
|---|---|
| `./gradlew build` | compile + jar |
| `./gradlew runGameTestServer` | headless GameTests in `src/dev/java/.../test/PowerArmorTests.java` (must print "All N required tests passed") |
| `./gradlew runGameTestOnly -Ptests='powerarmor:bullethits*' -Prepeat=40` | only the matching GameTests (ids = lower-cased method names, wildcards), each run N times side by side: hunts flaky tests. `-Prepeat=5` with no `-Ptests` runs the whole suite 5 times (all 675 must pass) |
| `./gradlew runAutopilot` | scripted flat-world drive (cockpit, third person, jump, pool, run out of fuel + refuel via the menu, chaingun range, right hand, EM projector, drill + chainsaw, tool items on foot). Screenshots `run/screenshots/ap_*.png`, logs `run/autopilot_{water,fuel,cannon,em,mine}.csv`. Quits by itself (~75 s). |
| `./gradlew runAutopilotMech` | only the autopilot's last scenes (~3 min): smashing blocks, stomp, lightning, punch, lava + poison (`ap_mech_*.png`, `run/autopilot_mech.csv`), then the machine mind: walk home, drill, chaingun follow/defend/fists, EM guard (`ap_ai_*`, `run/autopilot_mind.csv`), the workers (`client/dev/WorkScene`: `ap_work_*`, `run/autopilot_work.csv`), rogues (`RogueScene`) and the helmet (`HelmetScene`: `ap_helmet_*`, `ap_menu_helmet`, `run/autopilot_helmet.csv`) |
| `./gradlew runAutopilotPose` | ~30 s pose check (`client/dev/PoseScene`): side shots looking level / down 40 / up 30 / tool raised, top shots with the view at yaw +40 (gold block) / -40 (redstone): looking down must lean forward, the torso and raised tool must face the shot's block (`ap_pose_*.png`, `run/autopilot_pose.csv`) |
| `./gradlew runAutopilotGait` | ~45 s foot-slip drive (`client/dev/GaitScene`): walk, back up, strafe, diagonal, turn on the spot, look around, sprint + turn; per-tick `run/autopilot_gait.csv`, summarize with `python3 tools/gait-report.py` (slip = how fast a planted foot slides, moon = moonwalk ticks) |
| `./gradlew runAutopilotPaint` | ~30 s paint check (`client/dev/PaintScene`, also at the end of `runAutopilotMech`): all 16 dyes + a wet sponge on a suit, `ap_paint_*` (in-game back view + front), `run/autopilot_paint.csv` (server and client paint must agree a tick later) |
| `./gradlew runAutopilotStyles` | ~30 s helmet styles check (`client/dev/StyleScene`): six no-AI suits in a row (standard + 5 styles) from the in-game back view and the front, each painted in its inspiration's colours, the smithing table making a Spartan helmet (`ap_style_*`, `ap_menu_smithing`, `run/autopilot_style.csv`: server vs client style) |
| `./gradlew runAutopilotEngine` | ~35 s engine check (`client/dev/EngineScene`, also at the end of `runAutopilotMech`): a firebox floods in a glass pool (no pack-up), a tidal engine runs under water, dies on the dry stairs, drinks a water bucket, a star reactor sprints for free, the menu's engine slot (`ap_engine_*`, `ap_menu_engine`, `run/autopilot_engine.csv`) |
| `./gradlew runAutopilotFlame` | ~30 s flamethrower check (`client/dev/FlameScene`): three pigs, a plank wall, raw beef, an unlit campfire and ice in front of a flamethrower suit at dusk; cockpit, back and still side shots of the stream (`ap_flame_*`), `run/autopilot_flame.csv` (gobs in the air, pigs burning, fire blocks, beef, campfire, ice, the client's pilot light) |
| `./gradlew runAutopilotTnt` | ~35 s TNT launcher check (`client/dev/TntScene`): a survival pilot with 8 TNT in the ammo slot taps rounds into a cobblestone wall (back + still side shots), the drum's quarter turn up close (`ap_tnt_drum_*`), husks off to the side (cockpit), a held volley, an empty drum (dry click, red 0 on the HUD), the menu's ammo slot (`ap_menu_ammo`); `run/autopilot_tnt.csv` (tool uses, ammo server/client, rounds in the air, husks, wall blocks left, client drum angle/recoil/raise) |
| `./gradlew runAutopilotRail` | ~35 s rail cannon check (`client/dev/RailScene`): a survival pilot with 6 iron ingots lets go early (nothing fires), charges and fires through a glass wall and two husks in a line into a cobblestone wall (back, still side, cockpit shots), holds with no ingots (no charge, red 0), climbs out: the mind snipes a husk from home (`ap_rail_mind_*`); the menu's ammo slot (`ap_menu_rail`); `run/autopilot_rail.csv` (tool uses, ammo server/client, charge both sides, husks alive, glass/wall left, last shot's hits/broken blocks/length, client recoil/raise/core glow, mind goals) |
| `./gradlew runAutopilotTerrain` | drives over real terrain in a fresh copy of `New World`, logs `run/autopilot_terrain.csv` + per-render-frame `run/autopilot_terrain_frames.csv`; summarize with `python3 tools/terrain-report.py` (its `cockpit:` line must say OK: the model may not move against the camera) |
| `./gradlew runShowcase` | ~45 s gameplay screenshots for the mod page (`client/dev/ShowcaseScene`): a fresh seeded normal world (`saves/showcase`, never the user's), pinned places (blue suit on a sunflower-plains walk, red one with the chaingun at sunset by a village, yellow tidal suit on a coral reef: waits 30 s for vanilla's underwater view to clear; orange Protector-helmet suit drilling a stone ravine and a green Spartan one felling an oak, both from outside cameras; a purple Hunter-helmet suit with the star reactor lifting a creeper with the EM projector), front / back / cockpit views, 1920x1080, several frames per shot (`run/screenshots/showcase_*.png`, places in `run/showcase.csv`). A set with no pinned place searches for one (`findSpot`, `findReef`, `findWall`, `findTree`): pin what it finds. A file `run/showcase-only` with set numbers (`3 4`) shoots only those |
| `./gradlew runServerMp`, then `runMpDriver` + `runMpWatch` | multiplayer check (`dev/MultiplayerScript`, `client/dev/MultiplayerScene`): a dedicated server and two clients on this machine (game dirs `run/mp-driver`, `run/mp-watch`); the server scripts a story (board, paint, fuel pulled and back, climb out) and stops itself. What the watcher's client sees (`run/mp-watch/mp_client.csv`) must match the server's `run/mp_server.csv` (compare by hand) |
| `uv run --with pillow python3 tools/contact-sheet.py <prefix> out.png [cols] [width]` | tile screenshots (`fp`, `tp`, `side`, `water`, `terrain`) |
| `tools/view-tuner.sh start\|shoot\|stop` | live-tune the cockpit viewmodel (`CockpitHands`): the game stays in the cockpit and re-shoots `ap_view_*.png` with `run/view/tuning.json`; `stop` always (quits the game, stops the Gradle daemons) |
| `node tools/export-model.mjs` | re-export geo/animations/texture from the open Blockbench project (MCP at localhost:3000); then `extract-view-geo.py`, `uv run --with pillow python3 tools/make-glowmasks.py` and `tools/make-paint-textures.py` (one painted texture per dye). Item icons are hand-made 16 px pixel art (`model/art/item_icons_art.py`), not redrawn on export |
macOS has no `timeout`; run game tasks in the background and poll the log for completion.
Extend the autopilot scripts (`client/dev/AutoPilot.java`, `TerrainPilot.java`) with a scenario for each
new feature and log numbers to CSV: measuring beat guessing every time in this project.
New scenes go in their own `*Scene` class (ticks from 0, chained after the last one); in `AutoPilot.script()` the
case labels count from their section's start (`WATER + 17`): adding a step changes only that section's length in the
constants at the top, later sections move along by themselves.


## The web page and releases
- `site/` the mod's web page (GitHub Pages, https://ajac-zero.github.io/power-suits/). A template: `tools/build-site.py`
  fills in `{{version}}` / `{{tag}}` / `{{java}}` from the latest GitHub Release, greys out elements marked
  `data-since="1.1.0"` and shows `<span data-coming="1.1.0">` as "coming in 1.1" until that version is released (mark new
  features that way while building them). Preview: `python3 tools/build-site.py --version 1.0.0 /tmp/site`. Pictures are
  web copies made by `tools/make-site-images.py`. `.github/workflows/pages.yml` publishes it on pushes to main that touch
  it and after every release.
- Releasing: bump `mod_version`, commit, `git tag -a vX.Y.Z -m ... && git push origin main vX.Y.Z`. The tag push starts
  `.github/workflows/release.yml`: GitHub builds the tag with the JDK pinned in `tools/jdk.env` (same version as Homebrew's)
  and `tools/reproducible.gradle` (same source = same jar bytes), signs a build attestation, publishes jar + SHA-256 and
  republishes the page. v1.0.0 = 9c9cf00 (the Modrinth upload, no flamethrower; its tag predates the workflow, so it was
  run by hand: `gh workflow run release.yml -f tag=v1.0.0`); the flamethrower and the next tools are 1.1.
