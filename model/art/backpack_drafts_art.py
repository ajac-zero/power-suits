"""Engine backpacks (2026-10-21, Phase 9 step 2; design sec. 4b): each engine gets its own backpack. The firebox keeps
today's pack (firebox + two exhausts); the tidal engine and the star reactor get full redesigns, built here as hidden
groups under torso_armor named proto_pack_* (export-model.mjs leaves proto_* out) until the renderer shows the
installed engine's pack. User's rules for packs: a simple Minecraft look, few boxes, the detail painted.

  tidal A tanks        (picked from 3: B turbine drum, C conduit cage) twin dark iron diving tanks where the exhausts
                       were, steel straps and a valve on top, a pump between them with the Heart of the Sea glowing in a
                       porthole
  reactor J canisters  (picked after three rounds: 1 arc disc / end-crystal cage / copper coils; 2 beacon / gold star
                       case / heat sinks / gyroscope / glass cells; 3 twin turbines after a Space Marine power pack,
                       from an inspiration board, .scratch/packs/inspo): a netherite housing (vent grille on top, a round
                       glowing core low on the back) between two upright energy canisters, steel caps at both ends and a
                       glass strip down each canister's back and outer side showing the energy inside. Its canisters went
                       from stepped octagon turbines ("too stepped") to exhaust-like boxes with a fan on the cap, to closed
                       canisters with glass strips (the user's idea).

Glow colours are new reserved ones (backpack_kit.GLOW_LIT: tidal teal a/b/e, reactor violet-white p/q/r): dark when
off, lit by new glowmasks once the packs are wired up.

    uv run --with pillow python3 model/art/backpack_drafts_art.py [name parts]  (reads .scratch/packs/source.json, a
    fresh snapshot of the live project; writes .scratch/packs/plan.json; apply: .scratch/packs/apply.js, review:
    .scratch/packs/look.js; leftover texels of a rebuilt draft: .scratch/packs/orphans.py)
"""
import json, math, sys
sys.path.insert(0, "model/art")
from backpack_kit import on, world, build

SRC, OUT = ".scratch/packs/source.json", ".scratch/packs/plan.json"
TORSO = [0, 27.125, 0.5]


def mx(f, t):
    """The same box mirrored across x = 0."""
    return [-t[0], f[1], f[2]], [-f[0], t[1], t[2]]


def pair(name, f, t, paint):
    """A box on the +x side and its mirror on the -x side."""
    mf, mt = mx(f, t)
    return {name + "_l": (f, t, paint), name + "_r": (mf, mt, paint)}


# ---------- tidal A: twin tanks + pump ----------
def strap(x, y, z):   # two steel straps round each tank: lit top row, shaded under it
    return 'G' if 34 <= y < 35 or 41 <= y < 42 else 'S' if 35 <= y < 36 or 42 <= y < 43 else '.'


# dark iron tanks with steel straps: steel tanks vanished against the steel suit (v1)
TANK = on('iron', south=world(strap)('south'), east=world(strap)('east'), west=world(strap)('west'))
PUMP = on('iron', south=['MMMMMM', 'dTGGTd', 'dGbeGd', 'dGabSd', 'dTSSTd', 'KKKKKK'],
          up=['DDDDDD', 'DdGGdD', 'DdGGdD', 'MMMMMM'])
TANKS = {**pair("pkTA_tank_a", [3, 31.625, 7], [7, 43.625, 10], TANK),   # x 3..7: where the exhausts were, wider
         **pair("pkTA_tank_b", [3.5, 32.125, 6.5], [6.5, 43.125, 10.5], TANK),
         **pair("pkTA_cap", [3.75, 43.625, 7.25], [6.25, 44.625, 9.75], on('steel')),   # the valve
         "pkTA_pump": ([-3, 31.625, 6], [3, 37.625, 10], PUMP)}


# ---------- reactor J: housing between two energy canisters ----------
def core(cy, rr=3.0):
    """A round glowing core on a back face (centre x 0, y cy), in a steel ring; '.' outside."""
    def fn(x, y, z):
        d = math.hypot(x, y - cy)
        return 'r' if d < 1.0 else 'q' if d < 1.9 else 'p' if d < 2.4 else 'T' if d < rr else '.'
    return fn


def grille(top_rows):
    """Dark vent slits across the top rows of a back face (with the core below)."""
    def rows(w, h, f, t):
        g = world(core(33.125))("south")(w, h, f, t)
        return [''.join(('K' if c % 2 else 'A') if r < top_rows and 0 < c < w - 1 else ch for c, ch in enumerate(row))
                for r, row in enumerate(g)]
    return rows


def canister_face(w, h, f, t):
    """A canister body face: steel band at the top and bottom, netherite edges, glass in the middle columns: energy
    brightest in a streak, with a few dimmer flecks (bubbles) so it doesn't read as a flat bar."""
    rows = []
    for r in range(h):
        if r == 0: rows.append('G' * (w - 1) + 'T'); continue
        if r == h - 1: rows.append('S' * w); continue
        row = ''
        for c in range(w):
            if c == 0: row += 'A'
            elif c == w - 1: row += '7'
            elif c == w // 2: row += 'p' if r % 3 == 1 else 'r'
            else: row += 'q' if (r + c) % 4 else 'p'
        rows.append(row)
    return rows


HOUSING = ([-3.5, 29.625, 6], [3.5, 38.625, 10.5], on('nether', south=grille(2)))
DOME = ([-2.5, 38.625, 6.75], [2.5, 39.625, 10], on('nether'))
CANISTER = on('nether', south=canister_face, east=canister_face, west=canister_face)
LID = on('steel', up=['WGGGT', 'GGGGT', 'GGSGT', 'GGGGT', 'TTTTS'])
CANISTERS = {"pkRJ_housing": HOUSING, "pkRJ_dome": DOME,
             **pair("pkRJ_canister", [2.75, 36.625, 7], [6.75, 42.625, 11], CANISTER),
             **pair("pkRJ_lid", [2.25, 42.625, 6.5], [7.25, 43.625, 11.5], LID),
             **pair("pkRJ_foot", [2.25, 35.625, 6.5], [7.25, 36.625, 11.5], on('steel'))}

DRAFTS = {"proto_pack_tidal_A_tanks": TANKS, "proto_pack_reactor_J_canisters": CANISTERS}

if __name__ == "__main__":
    only = {g: c for g, c in DRAFTS.items() if not sys.argv[1:] or any(k in g for k in sys.argv[1:])}
    plan = build(SRC, only, "torso_armor", TORSO, ("before backpack drafts: " + ", ".join(g.replace("proto_pack_", "") for g in only))[:120])
    json.dump(plan, open(OUT, "w"))
    print("drafts", list(only), "cubes", sum(len(c) for c in only.values()), "pixels", len(plan["pixels"]), "lit", len(plan["lit"]))
