package studio.lumen.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.lumen.reader.data.MicrosoftWeb

class MicrosoftWebTest {

    @Test
    fun `request body is a plain string array`() {
        val body = MicrosoftWeb.requestBody("Evidence from a randomised trial.")
        val parsed = Json.parseToJsonElement(body).jsonArray
        assertEquals(1, parsed.size)
        assertEquals("Evidence from a randomised trial.", parsed[0].jsonPrimitive.content)
    }

    @Test
    fun `response yields the translation`() {
        val payload = """[{"translations":[{"text":"一项随机试验的证据","to":"zh-Hans"}]}]"""
        assertEquals("一项随机试验的证据", MicrosoftWeb.parse(payload))
    }

    @Test
    fun `missing or malformed payload is rejected`() {
        for (payload in listOf("[]", """[{"translations":[]}]""", "not json", """{"error":"x"}""")) {
            val failure = runCatching { MicrosoftWeb.parse(payload) }.exceptionOrNull()
            assertTrue("expected rejection for $payload", failure is IllegalStateException)
        }
    }
}
