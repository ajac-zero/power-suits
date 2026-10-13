# starcraft_marine art scripts

One-off scripts that added parts to `../starcraft_marine.bbmodel` (kept as reference / to redo a part).

| File | What |
|---|---|
| `mining_tools_art.py` | Power Armor Phase 5: plans the `drill` and `chainsaw` cubes, finds free texture space (alpha **and** every existing cube's box-UV net, missing `uv_offset` = [0,0]), paints the nets procedurally, writes `plan.json` / `paint.json`. Inputs: `tex.png` (texture from `Texture.all[0].getDataURL()`) and `model.json` (live project JSON) in `/private/tmp/p5/`. |
| `mining_tools_apply.mjs` | Checkpoint, paint (`tex.edit`, aborts if a target pixel is used), create the bones under `right_forearm`, place the cubes. Uses `../.agents/skills/blockbench-mcp/scripts/bb.mjs`. |
| `tool_forearms_art.py` (+ `tool_forearms_painters.py`) | Power Armor: the right arm ends in an elbow socket and each socket tool is the forearm. Plans every cube under `right_forearm` (except `elbow_r` and the muzzle `flash`), frees the old nets, packs + paints the new ones; writes `tf_plan.json` / `tf_paint.json` / `tf_preview.png`. Inputs in `/private/tmp/sa/` (`tex.png`, `model.json`). |
| `tool_forearms_apply.mjs` | Checkpoint, repaint, remove the old cubes, move the tool bone pivots to the socket (`[12.5, 25.625, 0]`), raise the muzzle flash into the new muzzle brake, place the cubes. |
