package studio.lumen.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerTest {
    private val now = 1_792_800_000_000L
    private val source = Source("bbc", "BBC News", "新闻", "英国", "https://bbc.com/news", "", 5, "")

    private fun article(title: String = "How quantum research changes energy technology"): Article =
        Ranker.normalize(title, "https://example.com/a", "Evidence and scientific research. ".repeat(8), now, source)!!

    @Test fun rejectsUnsafeUrls() {
        assertNull(Ranker.safeUrl("javascript:alert(1)"))
        assertNull(Ranker.safeUrl("https://a:b@example.com"))
        assertNotNull(Ranker.safeUrl("https://example.com/a"))
    }

    @Test fun freshnessWins() {
        val old = article().copy(id = "old", url = "https://example.com/old", publishedAt = now - 20 * 86400000)
        val fresh = article().copy(id = "fresh", url = "https://example.com/fresh", publishedAt = now)
        val ranked = Ranker.rank(listOf(old, fresh), Weights(freshness = 100, reach = 0, buzz = 0, frontier = 0, learning = 0), now, emptyList())
        assertEquals("fresh", ranked.first().id)
    }

    @Test fun reviewIntervals() {
        val known = Ranker.reviewCard(Vocab(level = 1), true, now)
        assertEquals(now + 3 * 86400000L, known.dueAt)
        val lapse = Ranker.reviewCard(Vocab(level = 4), false, now)
        assertEquals(now + 600000L, lapse.dueAt)
    }

    @Test fun csvNeutralizesFormula() {
        assertTrue(Ranker.csvCell("=1+1").startsWith("\"'"))
    }
}
