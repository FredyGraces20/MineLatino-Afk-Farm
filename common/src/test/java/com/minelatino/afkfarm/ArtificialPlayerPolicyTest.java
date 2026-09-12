package com.minelatino.afkfarm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ArtificialPlayerPolicyTest {
    @Test void identifiesRemovedProfilesWithinOneSecond() {
        assertEquals(20, ArtificialPlayerPolicy.REMOVED_PROFILE_TICKS);
    }

    @Test void neverClassifiesAnEntityWhoseProfileWasNotObserved() {
        assertEquals(ArtificialPlayerPolicy.Verdict.UNKNOWN,
                ArtificialPlayerPolicy.classify(false, 0, false, 500));
    }

    @Test void waitsAfterAProfileIsRemoved() {
        assertEquals(ArtificialPlayerPolicy.Verdict.UNKNOWN,
                ArtificialPlayerPolicy.classify(true, 40, false,
                        ArtificialPlayerPolicy.REMOVED_PROFILE_TICKS - 1));
        assertEquals(ArtificialPlayerPolicy.Verdict.ARTIFICIAL_ENTITY,
                ArtificialPlayerPolicy.classify(true, 40, false,
                        ArtificialPlayerPolicy.REMOVED_PROFILE_TICKS));
    }

    @Test void stableProfilesAreAlwaysRealEvenIfLaterRemoved() {
        assertEquals(ArtificialPlayerPolicy.Verdict.REAL_PLAYER,
                ArtificialPlayerPolicy.classify(true, ArtificialPlayerPolicy.REAL_PROFILE_TICKS,
                        false, 500));
    }

    @Test void aProfileThatReturnsStopsArtificialClassification() {
        assertEquals(ArtificialPlayerPolicy.Verdict.UNKNOWN,
                ArtificialPlayerPolicy.classify(true, 40, true, 500));
    }
}
