package com.minelatino.afkfarm;

import java.util.List;

/** Fixed client attack policy. Changes require an official rebuild and cannot come from JSON or the backend. */
public final class AfkFarmAttackPolicy {
    public static final int MINIMUM_ATTACK_INTERVAL_TICKS = 5; // 20 ticks / 5 = at most 4 attempts per second.
    public static final float REQUIRED_ATTACK_STRENGTH = 0.95f;

    private AfkFarmAttackPolicy() {}

    public static boolean allowsId(boolean categoryEnabled, List<String> selectedIds, String id) {
        return categoryEnabled && selectedIds != null && id != null
                && (selectedIds.contains("*") || selectedIds.contains(id));
    }

    public static boolean mayAttempt(long currentTick, long lastAttemptTick, float attackStrength) {
        return currentTick - lastAttemptTick >= MINIMUM_ATTACK_INTERVAL_TICKS
                && attackStrength >= REQUIRED_ATTACK_STRENGTH;
    }
}
