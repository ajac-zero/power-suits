package dev.ajaczero.powerarmor.entity;

/**
 * Who the suit's machine mind works for when nobody is inside (design sec. 8). It only acts while the suit is powered
 * and has no pilot: an unpowered suit is just a parked machine, a piloted one does what the pilot says.
 *
 * Saved by name and synced to clients by ordinal (the visor shows it), so keep the order: add new values at the end.
 */
public enum SuitMind {
    /** Works for its owner (the last pilot): green visor. Every suit a player made or claimed. */
    FRIENDLY,
    /** A wild suit whose mind went bad: red visor, attacks anything it sees. Only spawned naturally. */
    ROGUE;

    public static SuitMind byOrdinal(int ordinal) {
        SuitMind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FRIENDLY;
    }

    public static SuitMind byName(String name) {
        for (SuitMind mind : values()) {
            if (mind.name().equalsIgnoreCase(name)) {
                return mind;
            }
        }
        return FRIENDLY;
    }
}
