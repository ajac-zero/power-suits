package dev.ajaczero.powerarmor.entity.ai;



/**
 * How the machine mind uses the tool in its socket (design sec. 8): each tool says which (PowerTool.mindUse), and the
 * mind's goals (SuitGoals) pick their job from it. A friendly mind does its job round its owner or its home; a rogue
 * only fights, and only with the weapons rogues carry (GUN, EM).
 */
public enum ToolUse {
    /** Nothing the mind can do with it (the TNT launcher, an empty socket): it walks home and looks around. */
    NONE,
    /** Aim and hold the trigger (chaingun, flamethrower): a friendly mind follows its owner and defends them. */
    GUN,
    /** Grab and throw with the EM projector: a friendly mind guards the area round home. */
    EM,
    /** Snipe with the rail cannon: a friendly mind shoots from home at what comes near. */
    RAIL,
    /** Dig with the drill: a friendly mind clears an open hall round home. */
    DRILL,
    /** Cut with the chainsaw: a friendly mind fells the natural trees round home. */
    SAW
}
