"""Mirror check of the rail cannon as Blockbench draws it: every face sampled on a fine grid, its colour compared with
the colour at the mirrored point (x -> 2*CX - x) on the mirrored face (2026-10-26: 34 of 66 faces differed before
rail_mirror_apply.js; after it only hidden ones: the socket top, the receiver's faces inside the prongs and under the base).
Usage: uv run --with pillow python3 model/art/rail_symcheck.py mesh.json texture.png
mesh.json: Blockbench's own vertices + UVs of the rail_* cubes, dumped by risky_eval:
  (() => { const out = {cubes: {}}; Cube.all.filter(c => c.name.startsWith('rail_')).forEach(c => {
    const g = c.mesh.geometry, p = g.attributes.position.array, uv = g.attributes.uv.array, verts = [];
    for (let i = 0; i < p.length / 3; i++) verts.push([p[3*i] + c.origin[0], p[3*i+1] + c.origin[1], p[3*i+2] + c.origin[2],
      uv[2*i] * 256, (1 - uv[2*i+1]) * 128]);
    out.cubes[c.name] = {verts}; }); return JSON.stringify(out); })()
texture.png: Texture.all[0].getDataURL() decoded (or the saved texture)."""
import json, sys, math
from PIL import Image
CX = -11.5
mesh = json.load(open(sys.argv[1]))
mesh = (json.loads(mesh) if isinstance(mesh, str) else mesh)["cubes"]
tex = Image.open(sys.argv[2]).convert("RGBA")
NAMES = ["east", "west", "up", "down", "south", "north"]
faces = []
for name, c in mesh.items():
    V = c["verts"]
    for k in range(6):
        q = V[4 * k:4 * k + 4]
        p0, p1, p2 = [v[:3] for v in q[:3]]
        a = [p1[i] - p0[i] for i in range(3)]; b = [p2[i] - p0[i] for i in range(3)]
        n = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
        L = math.sqrt(sum(x*x for x in n)) or 1; n = [round(x / L) for x in n]
        faces.append(dict(cube=name, face=NAMES[k], q=q, a=a, b=b, n=n))

def colour(f, s, t):
    q = f["q"]
    u = q[0][3] + s * (q[1][3] - q[0][3]) + t * (q[2][3] - q[0][3])
    v = q[0][4] + s * (q[1][4] - q[0][4]) + t * (q[2][4] - q[0][4])
    return tex.getpixel((int(math.floor(u)), int(math.floor(v))))

def locate(f, P):
    """(s, t) of point P on face f, or None if off it."""
    q0 = f["q"][0][:3]; a, b = f["a"], f["b"]
    d = [P[i] - q0[i] for i in range(3)]
    if abs(sum(d[i] * f["n"][i] for i in range(3))) > 1e-4: return None
    aa = sum(x*x for x in a); bb = sum(x*x for x in b)
    s = sum(d[i]*a[i] for i in range(3)) / aa; t = sum(d[i]*b[i] for i in range(3)) / bb
    return (s, t) if -1e-6 <= s <= 1 + 1e-6 and -1e-6 <= t <= 1 + 1e-6 else None

STEP = 1 / 8
report = []
for f in faces:
    la = math.sqrt(sum(x*x for x in f["a"])); lb = math.sqrt(sum(x*x for x in f["b"]))
    na, nb = max(1, round(la / STEP)), max(1, round(lb / STEP))
    bad = tot = miss = 0; cells = []
    mn = [-f["n"][0], f["n"][1], f["n"][2]]
    for i in range(na):
        for j in range(nb):
            s, t = (i + .5) / na, (j + .5) / nb
            P = [f["q"][0][k] + s * f["a"][k] + t * f["b"][k] for k in range(3)]
            M = [2 * CX - P[0], P[1], P[2]]
            hit = None
            for g in faces:
                if g["n"] != mn: continue
                st = locate(g, M)
                if st: hit = (g, st); break
            tot += 1
            if not hit: miss += 1; continue
            if colour(f, s, t) != colour(hit[0], *hit[1]):
                bad += 1; cells.append((round(P[0], 3), round(P[1], 3), round(P[2], 3)))
    if bad or miss:
        report.append((f["cube"], f["face"], bad / tot, miss / tot, cells[:3]))
for r in sorted(report):
    print(f"{r[0]:18s} {r[1]:6s} differs {r[2]*100:5.1f}%  no-mirror {r[3]*100:5.1f}%  e.g. {r[4]}")
print("faces checked", len(faces), "asymmetric", len(report))
