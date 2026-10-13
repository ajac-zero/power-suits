"""Checks / fits cockpit viewmodel poses (CockpitHands) so each part's cut end stays off screen.

    uv run --with numpy python tools/viewmodel-fit.py check  x y z yaw pitch roll scale  tool|gauntlet
    uv run --with numpy python tools/viewmodel-fit.py fit    x yaw roll scale tipX tipY  tool|gauntlet

Same transform as CockpitHands: translate(x,y,z) * rotY(yaw) * rotX(pitch) * rotY(roll) * scale, model pixels / 16,
in view space (x right, y up, -z forward). The hand pass projects with a fixed 70 degree FOV; 16:9 is assumed for the
horizontal NDC. 'check' prints the highest screen y (NDC, -1 = bottom edge) of the cut end and where the tip lands;
'fit' searches y, z and pitch for a pose whose cut end sits below the bottom edge (< -1.08, room for the gait bob)
with the tip at (tipX, tipY) in NDC. The tool sits on the left (x < 0), the gauntlet on the right; 'fit'
searches with the x, yaw and roll you give, so to fit one side from the other, mirror x, yaw and roll.
"""
import sys
import numpy as np

T, ASP = np.tan(np.radians(35)), 16 / 9
# cut end corners and tip, model pixels (geo/view/power_armor_view.geo.json): tools start at the socket face (y 0),
# the gauntlet at the wrist (y -6, the elbow cuff is left out); the widest tool section is the cannon/saw body.
PARTS = {'tool': ([(x, 0, z) for x in (-4.5, 5.5) for z in (-5.5, 6.5)], (0, -16, 0)),
         'gauntlet': ([(x, -6, z) for x in (-3, 4) for z in (-3.5, 3.5)], (0, -15, 0))}


def rot(yaw, pitch, roll):
    a, b, c = np.radians([yaw, pitch, roll])
    ry = lambda t: np.array([[np.cos(t), 0, np.sin(t)], [0, 1, 0], [-np.sin(t), 0, np.cos(t)]])
    rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
    return ry(a) @ rx @ ry(c)


def project(v):
    return v[..., 0] / (-v[..., 2] * T * ASP), v[..., 1] / (-v[..., 2] * T)


def pose(part, pos, yaw, pitch, roll, scale):
    back, tip = PARTS[part]
    m = rot(yaw, pitch, roll) * scale / 16
    b = np.array(back) @ m.T + pos
    t = np.array(tip) @ m.T + pos
    if (b[:, 2] > -0.05).any():
        return None
    return project(b)[1].max(), project(t)


def main(argv):
    mode, part = argv[0], argv[-1]
    v = [float(a) for a in argv[1:-1]]
    if mode == 'check':
        x, y, z, yaw, pitch, roll, scale = v
        r = pose(part, np.array([x, y, z]), yaw, pitch, roll, scale)
        print('behind the camera' if r is None else f'cut end max y {r[0]:.2f} ({"hidden" if r[0] < -1.08 else "VISIBLE"}), tip at ({r[1][0]:.2f}, {r[1][1]:.2f})')
        return
    x, yaw, roll, scale, tx, ty = v
    best = None
    for pitch in range(90, 150, 2):
        for y in np.arange(-0.9, -0.3, 0.01):
            for z in np.arange(-0.8, -0.25, 0.01):
                r = pose(part, np.array([x, y, z]), yaw, pitch, roll, scale)
                if r is None or r[0] > -1.08:
                    continue
                e = (r[1][0] - tx) ** 2 + (r[1][1] - ty) ** 2
                if best is None or e < best[0]:
                    best = (e, y, z, pitch, r[1])
    if best is None:
        print('no pose hides the cut end')
    else:
        e, y, z, pitch, (px, py) = best
        print(f'pos ({x:.2f}, {y:.2f}, {z:.2f}) yaw {yaw:g} pitch {pitch} roll {roll:g} scale {scale:g} -> tip ({px:.2f}, {py:.2f})')


main(sys.argv[1:])
