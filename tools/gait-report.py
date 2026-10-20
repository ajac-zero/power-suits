#!/usr/bin/env python3
"""Summarize run/autopilot_gait.csv (./gradlew runAutopilotGait) per phase.

slip = how fast a planted foot slides (blocks/s): the distance between the suit's real velocity and the one the playing
animation was authored for (along the legs). 0 = feet stick; walking speed is 2.2, so 0.5+ is a visible slide.
moon = ticks where the legs step forward while the suit moves backward relative to them (a moonwalk).
twist = view yaw minus leg yaw (the torso turns this far over the legs).
Usage: python3 tools/gait-report.py [csv]
"""
import csv, sys, statistics as st
path = sys.argv[1] if len(sys.argv) > 1 else "run/autopilot_gait.csv"
rows = list(csv.DictReader(open(path)))
phases = []
for r in rows:
    if not phases or phases[-1][0] != r["phase"]:
        phases.append((r["phase"], []))
    phases[-1][1].append(r)
print(f"{'phase':12} {'ticks':>5} {'anims':16} {'speed':>5} {'slip avg':>8} {'max':>5} {'>0.5':>5} {'moon':>4} {'|twist| avg':>11} {'max':>5} {'legturn max':>11}")
for name, rs in phases:
    f = lambda k: [float(r[k]) for r in rs]
    slip, tw, lt, sp = f("slip"), [abs(x) for x in f("twist")], [abs(x) for x in f("legTurn")], f("speed")
    anims = "/".join(sorted({r["anim"] for r in rs}))
    moon = sum(1 for r in rs if float(r["expected"]) > 0.1 and float(r["fwd"]) < -0.2)
    over = sum(1 for x in slip if x > 0.5)
    print(f"{name:12} {len(rs):5} {anims[:16]:16} {st.mean(sp):5.2f} {st.mean(slip):8.2f} {max(slip):5.2f} {over:5} {moon:4} {st.mean(tw):11.1f} {max(tw):5.1f} {max(lt):11.1f}")
