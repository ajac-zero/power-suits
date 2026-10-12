#!/usr/bin/env python3
"""Write a GameTest structure (.nbt, gzip) with a solid floor at y=0, no NBT library needed.

usage: make_test_structure.py out.nbt [--size W H D] [--block minecraft:smooth_stone] [--data-version 3955]
DataVersion 3955 = Minecraft 1.21.1. Make H tall enough: GameTest seals the structure in barriers.
"""
import argparse, gzip, struct

def s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def tag(t, name, payload): return bytes([t]) + s(name) + payload
def i(v): return struct.pack('>i', v)
def lst(t, items): return bytes([t]) + i(len(items)) + b''.join(items)
def comp(fields): return b''.join(fields) + b'\x00'

ap = argparse.ArgumentParser()
ap.add_argument('out'); ap.add_argument('--size', nargs=3, type=int, default=[9, 10, 9])
ap.add_argument('--block', default='minecraft:smooth_stone'); ap.add_argument('--data-version', type=int, default=3955)
a = ap.parse_args()
W, H, D = a.size
blocks = [comp([tag(9, 'pos', lst(3, [i(x), i(0), i(z)])), tag(3, 'state', i(0))]) for x in range(W) for z in range(D)]
root = tag(10, '', comp([
    tag(3, 'DataVersion', i(a.data_version)),
    tag(9, 'size', lst(3, [i(W), i(H), i(D)])),
    tag(9, 'palette', lst(10, [comp([tag(8, 'Name', s(a.block))])])),
    tag(9, 'blocks', lst(10, blocks)),
    tag(9, 'entities', lst(10, [])),
]))
gzip.open(a.out, 'wb').write(root)
print(f'wrote {a.out} ({W}x{H}x{D}, floor {a.block})')
