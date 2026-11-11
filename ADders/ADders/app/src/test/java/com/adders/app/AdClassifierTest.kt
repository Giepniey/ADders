package com.adders.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AdClassifierTest {
    private val fb = 60_000L

    @Test fun spotifyAdIsAd() =
        assertEquals(Kind.AD, AdClassifier.classify("Advertisement", "Spotify", null, 30_000, fb))

    @Test fun shortKnownSongStaysSong() =
        assertEquals(Kind.SONG, AdClassifier.classify("Intro", "Band", "Album", 20_000, fb))

    @Test fun unknownShortIsSuspectedAd() =
        assertEquals(Kind.AD, AdClassifier.classify(null, null, null, 25_000, fb))

    @Test fun unknownLongOrNoDurationIsUnknown() {
        assertEquals(Kind.UNKNOWN, AdClassifier.classify(null, null, null, 180_000, fb))
        assertEquals(Kind.UNKNOWN, AdClassifier.classify(null, null, null, 0, fb))
    }
}
