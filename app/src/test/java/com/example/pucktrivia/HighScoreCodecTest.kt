package com.example.pucktrivia

import com.example.pucktrivia.data.HighScoreCodec
import com.example.pucktrivia.model.HighScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighScoreCodecTest {

    @Test
    fun `encode then decode round-trips the full history`() {
        val history =
            listOf(
                HighScore(score = 100, endedAt = 1_715_000_000_000L),
                HighScore(score = 0, endedAt = 1_716_000_000_000L),
                HighScore(score = 250, endedAt = 1_717_000_000_000L),
            )
        assertEquals(history, HighScoreCodec.decode(HighScoreCodec.encode(history)))
    }

    @Test
    fun `empty history round-trips to empty`() {
        assertTrue(HighScoreCodec.decode(HighScoreCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun `decode of null is empty`() {
        assertTrue(HighScoreCodec.decode(null).isEmpty())
    }

    @Test
    fun `decode of a garbled payload is empty rather than a crash`() {
        assertTrue(HighScoreCodec.decode("not-a-real-payload").isEmpty())
        assertTrue(HighScoreCodec.decode("v1;abc,def").isEmpty())
        assertTrue(HighScoreCodec.decode("v1;100").isEmpty())
    }

    @Test
    fun `decode of an unknown schema version is empty`() {
        assertTrue(HighScoreCodec.decode("v2;100,200").isEmpty())
    }
}
