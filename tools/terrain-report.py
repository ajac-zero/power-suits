import csv, collections
rows=list(csv.DictReader(open('run/autopilot_terrain.csv')))
for r in rows:
    for k in ('x','y','z','speed','vy','hipBob'): r[k]=float(r[k])
    r['onGround']=r['onGround']=='true'; r['airTicks']=int(r['airTicks']); r['hCollide']=r['hCollide']=='true'
by=collections.defaultdict(list)
for r in rows: by[r['phase']].append(r)
print(f"{'phase':8} {'avgSpd':>6} {'free':>5} {'target':>6} {'%air':>5} {'drops':>5} {'stepUps':>7} {'hColl%':>6} {'y range':>12} anims")
for ph,rs in by.items():
    rs=rs[10:]
    tgt=6.25 if ph.startswith('sprint') else 2.22  # PowerArmorEntity: the run's and walk's authored speeds
    free=[r['speed'] for r in rs if not r['hCollide'] and r['onGround']]
    air=sum(not r['onGround'] for r in rs)/len(rs)*100
    ups=sum(1 for a,b in zip(rs,rs[1:]) if b['y']-a['y']>0.3 and a['onGround'] and b['onGround'])
    drops=sum(1 for a,b in zip(rs,rs[1:]) if a['onGround'] and not b['onGround'])
    hc=sum(r['hCollide'] for r in rs)/len(rs)*100
    anims=collections.Counter(r['anim'] for r in rs)
    print(f"{ph:8} {sum(r['speed'] for r in rs)/len(rs):6.2f} {(sum(free)/len(free) if free else 0):5.2f} {tgt:6} {air:5.0f} {drops:5} {ups:7} {hc:6.0f} {min(r['y'] for r in rs):5.1f}-{max(r['y'] for r in rs):5.1f}  {dict(anims)}")

# Cockpit steadiness: the pilot's eye must stay at a fixed height above the drawn model in every render frame,
# also on steps (an eased-step bug made the arm jerk a block for a frame on every step).
import os
if os.path.exists('run/autopilot_terrain_frames.csv'):
    fr=list(csv.DictReader(open('run/autopilot_terrain_frames.csv')))
    gap=[float(r['eyeAboveModel']) for r in fr]
    jump=max((abs(b-a) for a,b in zip(gap,gap[1:])), default=0)
    print(f"\ncockpit: {len(fr)} frames, eye above model {min(gap):.3f}-{max(gap):.3f}, max change between frames {jump:.4f}"
          f"  -> {'OK' if jump < 0.01 else 'JERKS (model moves against the camera)'}")
