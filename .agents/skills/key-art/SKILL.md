---
name: key-art
description: Use when making marketing pictures for the Power Armor mod (key art, posters, gallery or social images) with Blender from the mod's own Blockbench model and Minecraft player skins. Covers the creative process that worked with this user (reference → staging → casting → backdrop picks → final), the marketing/ pipeline (glTF export, mclib.py scenes, previews, debug views, skin casting sheets, bloom pass), Minecraft-proportion staging rules and Blender/Cycles pitfalls on macOS.
---

# Key art for Power Armor

Learned on the first picture, `marketing/out/man_of_tomorrow.png` (2026-10-22): a homage to the "Man of Tomorrow"
comic cover. Lex Luthor in a lime suit fights Superman high above the clouds. The scripts and render commands are in
`marketing/README.md`; this file is the *how we work* part.

## The process (what the user liked)
1. **Start from the user's idea or reference and agree on the look first.** For this pilot the user chose
   "Minecraft key-art style" (soft Cycles lighting, crisp pixels, like Mojang's posters) over a comic/cel look. Ask
   once, with 2-3 named options, then keep to the answer.
2. **Stage, don't copy.** The reference punch couldn't be copied (see the staging rules below). The user proposed
   the fix: "a moment before contact". When a pose fights the Minecraft proportions, change the moment or the angle
   instead of forcing the pose.
3. **Iterate on cheap drafts.** Use `--preview` (≈15 s, 50 % size, 24 samples) for every change and look at it
   before saying anything. Use `--views` (orthographic front/side/back/top) when a 3D pose is unclear. Only render
   full size (≈2 min) once the user agrees.
4. **Casting by number.** For skins, search minecraftskins.com (firecrawl, see below) and render a numbered sheet
   with `blender/skin_lineup.py`. Each tile shows the 3D head as it will appear (helmetless pilots show only the
   head) plus its flat face. The user answers "head 8". Credit the chosen skin's page in `marketing/README.md`.
5. **Show options side by side for taste calls.** When the user says something is "distracting" or "off" without
   saying how, render 3 variants plus the current one in a 2x2 labelled sheet, recommend one with a reason, and let
   them pick. The background was settled this way: far cloud sea (B) over near clouds, blurred clouds or plain sky.
6. **Do the requested change, and only that.** When asked to turn one character, check what it does to the other
   one and the whole frame. Turning Lex toward the camera without re-staging Superman made Lex's head clip his
   shoulder, and the user rolled it back. If a change needs a second change, show both or ask first.
7. **Keep the last good version restorable.** Copy the scene script before a risky change (or commit) so "go back to
   the previous version" is exact. The rollback was verified pixel for pixel against the earlier render.
8. **Finish.** Render with `nohup` and poll the log, run `post.py` (bloom + vignette), commit the scripts, the skins
   and the final PNG (raw renders are git-ignored), and update `marketing/README.md` with the picture's notes.

## Staging rules for Minecraft bodies (each cost a round)
- **A Minecraft arm is only 12 px.** Raised straight up, the fist only reaches the top of the head, so uppercuts and
  overhead punches end up hidden behind somebody's head. Stage fists in clear space in front of or beside the bodies.
- **Pre-contact beats contact**: a gap of about a hand's width between fist and target (or two fists) reads as
  tension and avoids clipping.
- **An arm on the side nearer the camera, reaching across, hides the suit's chest.** Cock it back (at the hip, so it
  doesn't hide the backpack above the shoulder) or use the far arm.
- **A flying player seen from the front is mostly head and arm**; keep the body turned so its length shows, and pitch
  the head back about 50-60 degrees so the face looks ahead while the body is flat.
- **A leap, not a step**: tuck the front knee high (thigh ~70, shin ~-105) and kick the back leg out behind *and to
  the side* (thigh about (-55, -38)), or the camera can't see it.
- **The helmetless pilot's head needs a camera from above** (≈45 degrees down): from below, the collar and pads hide it.
  Looking down also turns the backdrop into a cloud sea, which works well for height.
- **Both faces 3/4 to the camera**: two characters facing each other can't both face the camera. Turn each one about
  30-40 degrees from the camera toward the other.
- **Show the suit's features:** tool raised so its length shows (a blade pointing at the camera reads as a box), glowing engines
  with their glowmask (`import_suit(glow='reactor')`), paint via `paint='lime'`.
- **The suit's shoulders barely move sideways.** The pads are rigid on the upper arms and sit against the collar:
  more than about 10 degrees out to the side and a pad goes into the collar (an upper arm can swing forward, even past
  horizontal, cleanly). Reach by swinging forward and bending the elbow (Big Daddy, 2026-10-22).
- **Hunch from the hips, never the torso alone.** The abdomen sits in the hip hoods, which hang from their own root
  bone (`hips`): bending `torso` or `body` alone pushed the abdomen into them (the user spotted it). Tip the whole root
  forward (legs make up for it) and pose `body` and `hips` together. The clip check (BVH overlap of the parts, each
  cube shrunk 0.2 px so touching seams don't count, minus the rest pose's overlaps; `mclib.suit_clip_checks(S)`, built
  before posing, `.report()` after) catches it before a render. The game's own torso twist (yaw on `torso`) pushes the
  abdomen's corners slightly into the hood tops (~14 face pairs at 20 degrees): check that spot in a crop render.
- **The helmet only shows from the front half, and not from the ground** (Suppressing Fire, 2026-10-22). It sits low
  between the pads: from the side the near pad hides it completely, and from a camera on the ground the chest hides
  most of the rest (23 % of its lens-facing surface showed at 0.3 blocks up, ~40 % at hip height, 66 % at helmet
  height). So a "low side view" can't show whose helmet it is: keep the camera within ~30 degrees of the suit's front
  and at hip height or above, and measure with `suppressing_fire.py --score` (ray casts from the lens) instead of
  rendering guesses. A gun fired sideways across the frame then needs the torso twisted toward the target
  (`torso` yaw, as the game does) plus the forearm turned out; fired straight ahead, a short stream leaves the frame.
- **The far-side tool arm is the clean one**: with the camera on the fist side, the tool fires away from the body;
  from the tool side its line of fire crosses the chest and helmet.
- **Aim the head at the lens**, don't guess its pitch: from a low camera the head must tip down ~20 degrees to meet it,
  and a "head down" by eye came out looking up at the distance. `M.aim(head, marker 20 px ahead, camera)`.
- **Centre the body, not the outline**: arms reaching to both sides pull a bounding box off-centre; `big_daddy.py`
  turns the camera until the helmet-to-feet midpoint is at x = 0.5.
- **A tool pointed straight at the lens reads as a box with a disc in it**: aim it a block or so beside the lens and
  keep it clear of the helmet (the drill at chest height, the user's pick).
- **Backgrounds stay quiet**: big near clouds behind the fighters distract the eye. Clouds far below (small blocks) keep
  the Minecraft feel; depth of field barely blurs them at these distances.

## Pipeline notes
- **Re-export the glb after model changes** (`marketing/README.md` has the `risky_eval` recipe). Wait for
  `Codecs.gltf.compile` in one call, then write it in a second call: a single long promise times out the MCP request.
  `requireNativeModule('fs')` hung; `Blockbench.writeFile(path, {content: Buffer.from(ab), savetype: 'buffer'})` works.
- **Coordinates**: suit and players face +Y in their own frame, right hand on +X; the world camera looks along +Y
  (screen right = +X). `M.aim(bone, tip, target)` (shortest-arc turn about the pivot) is the fastest way to pose:
  "put this fist here", "point the saw there". Find a tool tip with `M.marker` (the chainsaw's blade end is 16 px
  below its bone).
- **Blender 5.2 Cycles on Metal crashes now and then** compiling optimised kernels (double free in
  `ccl::path_cache_get`; previews also died silently, with no "Saved" line). `mclib.cycles` turns
  `kernel_optimization_level` off; `--cpu` is the fallback. Always check the log for `Saved:`.
- **Run Blender and the image read in separate steps**: reading a PNG in the same parallel batch as the command that
  writes it fails (file not there yet).
- **World sky**: Generated coordinates on a world are the view direction, so the gradient is keyed on Z (sin of
  elevation). The camera sees the sky at full strength while the lighting uses a lower strength (Light Path mix),
  so the sky can stay bright without washing out the characters.
- **Glowmasks take a tint** like the game's `SuitGlowLayer`: `glow=[('visor', M.VISOR_AMBER), 'tidal']` (the visor mask is
  grey, the game colours it amber / green / red; untinted it rendered white). Keep glow strength ~2: higher, AgX
  bleaches the colour to white.
- **Under water**: one Principled Volume box from below the floor up to the surface (dark scatter colour, deep
  blue-green absorption, anisotropy ~0.55). Light shafts need contrast: a hidden roof at the surface (shadow only,
  `visible_camera = False`) with a few square holes placed where beams should fall, a strong sun (40) and almost no
  world light; the suit gets its own spot from under the surface (a spot's bright tip shows in the water, so hang it
  out of frame). Volume above the roof glowed and washed everything out.
- **Vanilla scenery**: `mcblocks.py` (`texture('sea_lantern')` takes frame 0 of animated strips). An area light's shadow
  falling toward the camera made planted feet look like they float: check what casts the shadow before moving feet.
- **Skins**: minecraftskins.com pages sit behind Cloudflare. Scrape the page's HTML with firecrawl (`formats:
  ['rawHtml']`, an `objective` is required) to find `uploads/skins/...png`, then `curl` that file directly (not blocked).

## Two subjects in one frame (Containment, 2026-10-23)
- **A held mob sets the frame, not the suit**: a size-4 slime 3 blocks out from the projector spans feet-to-slime-top
  ~5.5 blocks, so the suit fills only ~40 % of the height (portrait didn't help). The user called that "perfect": with a
  second subject and a scene worth showing, a smaller suit is fine. Raise the camera to head height (2.0) and turn the
  head 60 % toward the lens so the visor still reads.
- **Search framing by numbers**: `containment.py --frame` prints where head, feet, tip and slime land (0..1) without
  rendering; settle the layout there before previews.
- **Mobs from vanilla models**: copy the model's boxes in Minecraft model space (y down, front -z) and map them
  (x -> -x, front -> +Y, up = 24 - y). Turn a mob's face to the lens with `to_track_quat('Y', 'Z')`. Wide entity
  textures (slime.png 64x32) aren't animation strips (`mcblocks.texture` handles it).
- **Particles like the game's**: tinted `generic_N` sprites with low emission (1-1.6). Strong emission bleaches to pink
  under AgX. A beam the game respawns every tick reads best as the newest helix plus a few older, sparser twists.

## Ideas queued (from the first session)
Helmet lineup, the 16 paint colours, a rogue suit at night in a lightning storm, and
in-game screenshots (an autopilot photo scene) for the gallery.
DC characters are fine for gallery/social fan art, not for the mod's icon or banner.
