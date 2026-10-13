> History of the design discussion. The current spec is `docs/design.md`; the plan is `docs/implementation-plan.md`.

# Design notes

## Current state (2026-10-12)
- Enterable suit (GeckoLib entity) from `model/starcraft_marine.bbmodel`.
- Jog 5 / sprint 10 blocks/s, jump wind-up, legs follow travel while the torso aims with the view,
  cockpit view at the visor, stabilized cannon, step smoothing and 2-block step-down on terrain.
- Water, Fallout-style: sinks, walks the bottom at half speed, suit never drowns, pilot's air drains.
- Damage to the pilot is redirected to the suit (except drowning, starvation, void//kill).

## Proposal under discussion: the right arm becomes a physics gun
Not decided yet. Idea from the user, with my suggestions:

1. **Standalone item** ("Gravity Gauntlet" working name): right-click grabs a block or mob, it floats
   in front and follows the aim; right-click again sets it down (blocks placed, mobs dropped);
   left-click throws (mob impact damage, blocks fly as falling blocks). On foot: small mobs and
   single blocks, short range, uses charge/durability. Never bosses, players or unbreakable blocks;
   no block entities (chests etc.) to avoid duplication bugs.
2. **Suit integration**: the gauntlet is a recipe ingredient and becomes the suit's right arm with
   bigger limits (iron golems, ravagers, a few blocks), longer range, harder throws. The left hand
   shows/uses the pilot's off-hand item.
3. **Special-purpose balance** (options): no mining/placing/main-hand use inside the suit (the grav arm
   replaces the hand); no containers/crafting while inside; power cells (Fallout fusion cores)
   drained by movement and lifting; already too tall for spaces under 3 blocks.

Model work: remodel the cannon forearm into the gauntlet (glowing emitter), a small item model, and
grab / hold / right-arm throw animations; the left-arm grenade goes away.
Tech: grab/hold/throw are server-authoritative, driven by custom client->server packets.

Suggested order: (1) standalone gauntlet with placeholder art, (2) suit integration + restrictions,
(3) power cells if still wanted.

Open questions: glove vs. gun shape; what each version can lift; off-hand any item or a whitelist;
which restrictions to use.

## Revised direction (2026-10-12, still under discussion)
- **Grav gun = a gun-shaped item.** Big in a player's hands, small when mounted on the suit. Used on
  foot, and locked onto the suit's **left arm** (utility hand).
- **Right arm = hardpoint for suit-only tools** (cannot be used on foot):
  - Plasma cannon: weapon. Reuses the current cannon art, attack animation and muzzle flash.
  - Drill: digs 3x3.
  - Chainsaw: fells a whole tree from the bottom log.
- **Power cells** limit sessions: walking/sprinting drains a little, tools and the grav gun drain more.
- **Exhaust particles** from the two back exhaust pipes: idle puffs, more smoke with throttle/sprint,
  sputtering when power is low.
- Tech: render the mounted grav gun and right-arm tool as item models attached to arm bones
  (GeckoLib bone item layer), so tools are swappable; split the cannon out of the suit model.

## Decisions (2026-10-12)
- **Power = fuel tank.** Burns furnace fuels (coal, charcoal, blaze rods, lava buckets...).
- **Empty tank = shutdown.** The suit freezes in place; the pilot can still climb out.
- **Right arm = one power socket.** Suit-only power tools plug in and draw fuel: plasma cannon,
  3x3 drill, tree-felling chainsaw (grav gun: TBD). Only one tool installed at a time.
- **Left arm = a real hand** that holds and uses normal items.

## Proposed mechanics (pending user OK)
- Controls in the suit: attack button (left click) = socket tool; use button (right click) = left-hand
  item. The left hand cannot break blocks (that's what tools are for).
- Suit screen (sneak + right-click a parked suit): fuel slot + tank gauge, socket slot, "pack up".
- Exhaust smoke scales with engine load; sputters black when fuel is low; stops on shutdown.
- Shutdown: power-down sound + slump pose; restarts when refuelled.
- Model: replace the cannon forearm with a socket coupling; tools are separate item models attached to
  the socket bone; left hand renders the held item; grenade prop removed.
