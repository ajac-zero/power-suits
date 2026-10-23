# Marketing art

Key art rendered in Blender from the mod's own model, so it always matches the game.

## Pipeline
1. **Model**: `assets/power_armor.glb`, exported from the open Blockbench project (glTF, binary, scale 16 = 1 unit per
   block, animations included). Re-export after model changes. Blockbench's MCP `risky_eval`:
   `Codecs.gltf.compile({encoding:'binary', animations:true, scale:16})`, then `Blockbench.writeFile(path,
   {content: Buffer.from(result), savetype: 'buffer'})`. Wait for the compile promise in a separate call, because MCP
   requests time out on long promises.
2. **Scene scripts** in `blender/`: `mclib.py` holds the shared parts:
   - the suit, with a dye texture from `src/.../textures/entity/paint` and only the installed tool, helmet and engine
   - Minecraft players built from a skin, with a hinged cape
   - pose helpers (`pose` for bone offsets, `aim` to point a limb at a world point)
   - backdrop: blocky clouds and the square sun
   - lights and Cycles settings

   `metropolis.py` builds a Minecraft city seen from the air: a street grid of skyscrapers whose facades are one
   quad per wall, tiled with a pattern made from vanilla block textures. It also adds parks, green copper crowns on
   the art-deco towers, the Daily Planet with its gold globe, haze coloured like the sky, and a sun that lights only
   the city (light linking).

   `mcblocks.py` builds Minecraft scenery (blocks with only their visible faces, crossed-quad plants, particle sprites)
   with the game's own textures, read from the Minecraft client jar in the Gradle cache. They're cached in
   `marketing/.cache/` (git-ignored: Mojang's files stay out of the repo).

   Each picture is one script.
3. **Render** (Blender 5.2, Cycles on Metal):
   ```sh
   /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- --preview --out /tmp/p.png  # ~15 s draft
   /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- --out marketing/out/man_of_tomorrow_raw.png
   uv run --with pillow --with numpy python3 marketing/post.py marketing/out/man_of_tomorrow_raw.png marketing/out/man_of_tomorrow.png
   ```
   Man of Tomorrow's final look is on white: `post.py raw.png out.png 0 0 --white` (no bloom, no vignette; the
   transparent render is laid on pure white, `--ink N` would add an outline). `--bg white|sky|city|far|near|blur|clean`
   picks the backdrop. `--cpu` renders on the CPU (if Metal crashes). `--views` renders orthographic front/side/back/top checks of a pose instead.

   Skin casting sheet (numbered heads, or `--body` for whole players):
   `/Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/skin_lineup.py -- sheet.png skin1.png skin2.png ...`

The creative process and staging rules are in `.agents/skills/key-art/SKILL.md`.

## Pictures
- `man_of_tomorrow`: homage to the "Man of Tomorrow" cover (Superman vs Lex Luthor's warsuit), mirrored and staged
  a moment before contact. Lex flies the suit painted lime, without a helmet, so his head sits in the collar the way
  the game draws a helmetless pilot. He has the chainsaw raised overhead and the Nether Star reactor
  on his back, with its glowmask lit. The camera looks down from above so his head reads. Like the cover, the fighters
  are alone on pure white, lit flat by an even white light with no sun and no lamps, so the colours read as flat
  areas (2026-10-23; the first version had a cloud sea far below, which looked bland next to the other pictures).
  `--bg sky` keeps the other try: a clear sky over a Minecraft metropolis far below, with the fighters above
  downtown. The world is turned round the camera (`mclib.env_tilt`) so the horizon tilts into the frame without
  moving the fighters, and a hard sun comes from the south-west. Superman and Lex are DC characters, so use this as
  gallery or social fan art, not as the mod's icon or banner.
- `big_daddy`: a BioShock homage. The suit painted brown, with the Protector helmet (Subject Delta's riveted visor,
  lit amber as with a pilot inside: Big Daddies' portholes are yellow too), the tidal engine and the drill. It comes
  at the viewer on the sea floor in front of an ocean monument, after the BioShock (2007) cover's pose (mirrored), with
  the drill levelled at us instead of the fist. The camera sits at the sand, close and wide. Bubbles rise from the tidal
  pack, as they do under water in the game, and sunlight comes down in shafts through holes in a hidden roof at the
  surface. The script checks the pose for clipping on every run (`--check` poses only). BioShock is 2K's: gallery or
  social fan art.
- `suppressing_fire`: a Halo nod. The suit painted green, with the Spartan helmet (Master Chief's), the firebox and the
  chaingun, holds its ground at night in a burning battlefield: a ruined village house, a watchtower stump, rubble,
  dry grass, embers and smoke, under a plain dark sky with Minecraft's square full moon (no stars: they made the sky
  too busy). It fires a level burst at an enemy off the right edge and looks down the gun; the tracers are the game's
  (white-yellow core in an orange sheath), drawn closer together than the game's 8 blocks so three fit in the frame,
  with a pixel muzzle flash and the barrels' heat glow. The camera stands at hip height in front of the suit, a little
  to its right, looking up: lower, the chest and pads hide the helmet (`--score` measures how much of it shows and how
  many tracers are in the frame, without rendering). Its angles are flags (`--cz`, `--camangle`, `--twist`, `--aimout`,
  `--headcam`...). Master Chief is Microsoft's: gallery or social fan art.
- `containment`: a Metroid nod. The suit painted orange, with the Hunter helmet (Samus's, visor lit green), the star
  reactor and the EM projector, hauls an escaped big slime (size 4, the Hunter recipe's "Metroid") through the cargo
  bay of a spaceship: deepslate deck plates, oxidized copper walls, sea-lantern light strips, the broken glass tank it
  came from (hazard stripes, red alarm lamps) and a blocky Minecraft world turning below the window. The slime is the
  game's own model and texture (inner cube with the face, thinned see-through jelly), turned to look at the lens and
  tumbling a little; the beam is the game's (`EmBeamEffects`: two twisting strands of redstone dust, older twists
  fading round the newest, sparks at the prongs, dust swirling round the slime). Wide frame, camera at head height a
  little right of the suit's front. `--frame` prints where the head, feet, tip and slime land in the frame without
  rendering; `--visor amber`, `--portrait` and the slime/camera numbers are flags. Samus is Nintendo's: gallery or
  social fan art.

## Credits
Skins from minecraftskins.com:
- `skins/superman.png`: https://www.minecraftskins.com/skin/23386722/superman---superman--2025-/
- `skins/lex_luthor.png`: https://www.minecraftskins.com/skin/23403417/lex-luthor/
