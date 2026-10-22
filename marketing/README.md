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

   Each picture is one script.
3. **Render** (Blender 5.2, Cycles on Metal):
   ```sh
   /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- --preview --out /tmp/p.png  # ~15 s draft
   /Applications/Blender.app/Contents/MacOS/Blender -b -P marketing/blender/man_of_tomorrow.py -- --out marketing/out/man_of_tomorrow_raw.png
   uv run --with pillow --with numpy python3 marketing/post.py marketing/out/man_of_tomorrow_raw.png marketing/out/man_of_tomorrow.png
   ```
   `--cpu` renders on the CPU (if Metal crashes). `--views` renders orthographic front/side/back/top checks of a pose instead.

## Pictures
- `man_of_tomorrow`: homage to the "Man of Tomorrow" cover (Superman vs Lex Luthor's warsuit), mirrored and staged
  a moment before contact. Lex flies the suit painted lime, without a helmet, so his head sits in the collar the way
  the game draws a helmetless pilot. He has the chainsaw raised overhead and the Nether Star reactor
  on his back, with its glowmask lit. The camera looks down from above so his head reads. Superman and Lex are DC characters, so use this as
  gallery or social fan art, not as the mod's icon or banner.

## Credits
Skins from minecraftskins.com:
- `skins/superman.png`: https://www.minecraftskins.com/skin/23386722/superman---superman--2025-/
- `skins/lex_luthor.png`: https://www.minecraftskins.com/skin/23403417/lex-luthor/
