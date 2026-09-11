package com.minelatino.afkfarm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class AfkFarmAttackPolicyTest {
    @Test void onlyExplicitlySelectedEntityIdsAreAllowed() {
        assertTrue(AfkFarmAttackPolicy.allowsId(true, List.of("minecraft:zombie"), "minecraft:zombie"));
        assertFalse(AfkFarmAttackPolicy.allowsId(true, List.of("minecraft:zombie"), "minecraft:skeleton"));
        assertFalse(AfkFarmAttackPolicy.allowsId(false, List.of("*"), "minecraft:zombie"));
    }

    @Test void fixedLimitAndRealWeaponCooldownMustBothPass() {
        assertFalse(AfkFarmAttackPolicy.mayAttempt(104, 100, 1f));
        assertFalse(AfkFarmAttackPolicy.mayAttempt(105, 100, .949f));
        assertTrue(AfkFarmAttackPolicy.mayAttempt(105, 100, .95f));
    }
}
