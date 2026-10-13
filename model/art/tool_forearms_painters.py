# Face painters for tool_forearms_art.py: fn(face, x, y, w, h) -> palette key.
# Faces: north = -Z = the top seen from the cockpit, south = underside, east = outer (+X), west = inner,
# up = towards the elbow, down = towards the tool's tip.

def ring(f, x, y, w, h):          # steel coupling ring with bolts
    if f in ('up', 'down'):
        if x in (1, w - 2) and y in (1, w - 2): return 'k2'
        return 'k8' if x in (0, w - 1) or y in (0, w - 1) else 'k6'
    return 'kA' if x % 3 == 1 else 'k7'
def plate(f, x, y, w, h):         # empty socket face
    if f == 'down': return 'k4' if x in (0, w - 1) or y in (0, w - 1) else 'k1'
    return 'k5'
def pins(f, x, y, w, h):          # glowing coupling pins
    if f == 'down': return 'd3' if (x + y) % 2 == 0 else 'k1'
    return 'd2' if x % 2 == 0 else 'd1'

def armor(f, x, y, w, h):         # blue armour shell shared by the tool housings
    if f == 'up': return 'k3'
    if f == 'down': return 'k4' if x in (0, w - 1) or y in (0, w - 1) else 'k2'
    if y == 0: return 'b4' if x else 'b3'
    if y == h - 1: return 'b0'
    return 'b1' if x == w - 1 else 'b2'
def receiver(f, x, y, w, h):      # cannon: armour with a cyan status strip and inner cooling slots
    if f == 'west' and 1 <= y < h - 1 and 1 <= x < w - 1: return 'k1' if y % 2 else 'k4'
    if f == 'north' and y == h - 2 and 1 <= x < w - 1: return 'c2' if x % 2 else 'c1'
    if f in ('east', 'south') and y == 2: return 'b0'           # panel seam
    return armor(f, x, y, w, h)
def cell(f, x, y, w, h):          # glowing energy cell in a dark frame
    if f in ('up', 'down'): return 'k1'
    if f in ('north', 'south'):
        if x in (0, w - 1) or y in (0, h - 1): return 'k1'
        return 'c3' if y == 1 else ('c2' if (x + y) % 3 else 'c1')
    return 'k2' if y in (0, h - 1) else 'c1'
def mag(f, x, y, w, h):           # dark magazine with a cyan charge indicator
    if f in ('up', 'down'): return 'k3'
    if y == 0: return 'k6'
    if f == 'south' and x == 1 and 1 <= y < h - 1: return 'c2'
    return 'k4' if x == 0 else 'k3'
def fins(f, x, y, w, h):          # heat-sink fins
    if f in ('east', 'west'): return 'k7' if x % 2 == 0 else 'k2'
    if f in ('up', 'down'): return 'k5'
    return 'k6' if y % 2 == 0 else 'k3'
def shroud(f, x, y, w, h):        # dark gunmetal shroud with glowing cooling slots
    if f in ('up', 'down'): return 'k4' if x in (0, w - 1) or y in (0, w - 1) else 'k2'
    if y == 0: return 'k5'
    if y == 1 and 1 <= x < w - 1: return 'c1' if x % 2 else 'k1'
    return 'k3' if x % 2 else 'k2'
def barrel(f, x, y, w, h):        # polished barrel
    if f in ('up', 'down'): return 'k6'
    return 'kA' if x == 0 else ('k9' if x == 1 else 'k7')
def muzzle(f, x, y, w, h):        # muzzle brake: steel with side ports and a dark bore with a cyan rim
    if f == 'down':
        c = (w - 1) / 2; r = max(abs(x - c), abs(y - c))
        return 'k0' if r < 1 else ('c2' if r < 2 else ('k6' if r < w / 2 - 1 else 'k8'))
    if f == 'up': return 'k5'
    if y == 0: return 'kA'
    if y == h - 1: return 'k5'
    if y in (1, 3) and 1 <= x < w - 1: return 'k1'                  # ports
    return 'k8' if x else 'k9'

def em_housing(f, x, y, w, h):    # dark emitter housing with purple vents
    if f in ('up', 'down'): return 'k4' if x in (0, w - 1) or y in (0, w - 1) else 'k2'
    if y == 0: return 'k5'
    if y == h - 1: return 'k1'
    if y == 2 and 1 <= x < w - 1: return 'p1' if x % 2 else 'p0'
    return 'k3' if x % 3 else 'k2'
def coil(f, x, y, w, h):          # copper coil ring with purple glints
    if f in ('up', 'down'):
        c = (w - 1) / 2; r = max(abs(x - c), abs(y - c))
        if r < 1.5: return 'k1'
        return 'r3' if (x + y) % 2 == 0 and r > 2.5 else ('r2' if r > 2.5 else 'r1')
    return 'p2' if x % 4 == 1 else ('r3' if x % 2 else 'r2')
def core(f, x, y, w, h):          # glowing core
    if f in ('up', 'down'): return 'p3'
    if x == 1: return 'p3' if y % 3 == 0 else 'p2'
    return 'p1' if x == 0 else 'p0'
def prong(f, x, y, w, h):         # steel prong with a glowing tip
    if f == 'down': return 'p3'
    if f == 'up': return 'k5'
    if y >= h - 2: return 'p2' if y == h - 1 else 'p1'
    return 'k8' if x == 0 else 'k5'

def drill_motor(f, x, y, w, h):   # armour, hazard stripe, grille on both sides
    if f in ('north', 'south') and y == 1 and 1 <= x < w - 1: return 'o2' if (x // 2) % 2 else 'k1'
    if f in ('east', 'west') and 2 <= x < w - 2 and 2 <= y < h - 1: return 'k1' if y % 2 else 'k4'
    return armor(f, x, y, w, h)
def saw_motor(f, x, y, w, h):     # orange engine cover over blue armour, grille outside
    if f == 'east' and 2 <= x < w - 2 and 1 <= y < h - 1: return 'k1' if y % 2 else 'k4'
    if f == 'north' and 1 <= y <= 2: return 'o2' if y == 1 else 'o1'
    if f == 'south' and y == 1 and 1 <= x < w - 1: return 'o2' if (x // 2) % 2 else 'k1'
    return armor(f, x, y, w, h)
def guard(f, x, y, w, h):
    if f in ('up', 'down'): return 'k8' if x in (0, w - 1) or y in (0, w - 1) else 'k6'
    return 'k9'
def hub(f, x, y, w, h):           # dark motor hub with bolts and a blue band
    if f in ('up', 'down'):
        c = (w - 1) / 2; r = max(abs(x - c), abs(y - c))
        return 'k1' if r < 1 else ('k4' if r < 2 else 'k3')
    if y == 0: return 'b3' if x % 3 else 'k9'
    return 'b1' if x % 3 else 'k8'
def steel_cone(studs):
    def fn(f, x, y, w, h):
        if f == 'up': return 'k5'
        if f == 'down':
            if studs and (x in (0, w - 1)) and (y in (0, w - 1)): return 'd2'
            return 'k7' if (x + y) % 2 else 'k8'
        k = (x + y) % 3                                            # spiral flutes
        if y == 0: return 'kA' if k else 'k9'
        if studs and y == h - 1 and x % 2 == 0: return 'd3'
        return 'k4' if k == 0 else ('k9' if k == 1 else 'k7')
    return fn
def diamond(f, x, y, w, h):
    if w == 1 and h == 1: return 'd4' if f in ('down', 'north', 'east') else 'd2'
    if (x + y) == 0: return 'd4'
    if x == w - 1 or y == h - 1: return 'd1' if (x + y) % 2 else 'd0'
    return 'd3' if (x + y) % 3 == 0 else 'd2'
def bar(f, x, y, w, h):           # guide bar: steel plates, rim, warning label
    if f in ('up', 'down'): return 'k6'
    if f in ('north', 'south'): return 'k6' if y % 2 else 'k5'
    if x in (0, w - 1) or y == h - 1: return 'k6'
    if 1 <= y <= 2 and 1 <= x < w - 1: return 'o2' if y == 1 else 'o1'
    return 'kA' if (x == 1 and y < h - 2) else 'k9'
def chain(f, x, y, w, h):         # dark links with bright teeth
    if f == 'up': return 'k1'
    if f == 'down': return 'kW' if x % 2 == 0 else 'k1'
    if f in ('north', 'south'): return 'kW' if y % 2 == 0 else 'k2'
    if x in (0, w - 1) or y == h - 1: return 'kA' if (y + x) % 2 == 0 else 'k1'
    return 'k1'
def exhaust(f, x, y, w, h):
    if f in ('east', 'west'): return 'k0' if (0 < x < w - 1 and 0 < y < h - 1) else 'k4'
    return 'k3' if y else 'k6'
