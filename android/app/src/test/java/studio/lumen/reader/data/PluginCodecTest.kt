package studio.lumen.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCodecTest {
    private val crawler = """
        {
          "format": 1,
          "id": "demo-crawler",
          "name": "Demo",
          "version": "1.0.0",
          "type": "crawler",
          "sources": [{"id": "s", "name": "S", "url": "https://example.com"}]
        }
    """.trimIndent()

    @Test
    fun parsesDeclarativeJsWrappers() {
        for (wrapper in listOf("module.exports = %s;", "export default %s;", "window.__LUMEN_PLUGIN__ = %s;")) {
            val plugin = PluginCodec.parseJs(wrapper.format(crawler).toByteArray())
            assertEquals("demo-crawler", plugin.id)
            assertEquals(1, plugin.sources.size)
        }
    }

    @Test
    fun stripsBomAndLeadingComments() {
        val js = "\uFEFF// comment before export\nmodule.exports = $crawler;"
        assertEquals("demo-crawler", PluginCodec.parseJs(js.toByteArray()).id)
    }

    @Test
    fun rejectsCodeWithoutObject() {
        assertThrows(PluginFormatException::class.java) {
            PluginCodec.parseJs("console.log('hello');".toByteArray())
        }
    }

    @Test
    fun rejectsUnsafeIdAndInvalidType() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginCodec.parseJs("""module.exports = {"format":1,"id":"../bad","name":"Bad","type":"crawler","sources":[{"id":"s","name":"S","url":"https://e.com"}]};""".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginCodec.parseJson("""{"format":1,"id":"x","name":"X","type":"unknown"}""")
        }
    }

    @Test
    fun detectsZipMagic() {
        assertTrue(PluginCodec.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertTrue(!PluginCodec.isZip(crawler.toByteArray()))
    }
}
