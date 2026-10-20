"""The waist core's back (abd_core south face, uv [60,35]-[72,38], 12 x 3) painted like its other sides (user,
2026-10-20: "the back of abd_core is not painted, fill that in in accordance with the other painted sides").

sm_chest_art.py painted the core's front and sides as dark-iron ribs (ribs(): a lit ridge row, a groove row, a lit
ridge row, with sparse brighter / darker flecks) and left the back flat iron (25292c), which shows from behind under
the backpack. Same ribs here, flecks placed by hand (no two side by side, not where the sides' are at the corners).

Run: python3 model/art/abd_back_art.py   prints the pixels as hex (xxyyrrggbb) for a tex.edit.
"""
I0, I1, I3, I4 = '1a1d1f', '25292c', '3b4246', '4c5559'
U, V, W = 60, 35, 12
rows = [[I3] * W, [I1] * W, [I3] * W]          # ridge, groove, ridge (as north / east / west)
rows[0][4] = I4; rows[1][2] = I0; rows[1][9] = I0; rows[2][7] = I4
print(''.join(f'{U + x:02x}{V + y:02x}{c}' for y, row in enumerate(rows) for x, c in enumerate(row)))
