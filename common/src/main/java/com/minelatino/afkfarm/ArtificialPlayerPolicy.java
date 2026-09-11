package com.minelatino.afkfarm;

/**
 * Conservative, client-only classification for player-shaped artificial entities.
 * A stable PlayerInfo entry always wins: uncertain entities are never attackable.
 */
public final class ArtificialPlayerPolicy {
    /** Five seconds in the player list is enough to permanently classify the entity as a real player. */
    public static final int REAL_PROFILE_TICKS = 100;
    /** The entity must remain after PlayerInfo removal for three seconds before it is considered artificial. */
    public static final int REMOVED_PROFILE_TICKS = 60;

    public enum Verdict { UNKNOWN, REAL_PLAYER, ARTIFICIAL_ENTITY }

    private ArtificialPlayerPolicy() {}

    public static Verdict classify(boolean observedProfile, int longestProfilePresenceTicks,
                                   boolean profilePresent, int removedWhileEntityPresentTicks) {
        if (longestProfilePresenceTicks >= REAL_PROFILE_TICKS) return Verdict.REAL_PLAYER;
        if (observedProfile && !profilePresent && removedWhileEntityPresentTicks >= REMOVED_PROFILE_TICKS)
            return Verdict.ARTIFICIAL_ENTITY;
        return Verdict.UNKNOWN;
    }
}
