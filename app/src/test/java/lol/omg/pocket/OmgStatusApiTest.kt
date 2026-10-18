package lol.omg.pocket

import kotlinx.coroutines.test.runTest
import lol.omg.pocket.data.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class OmgStatusApiTest {
    private fun envelope(value: String) = """{"request":{"success":true},"response":$value}"""
    @Test fun publicRequestHasNoBearerAndEncodesAddressWithTrailingSlash() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"statuses":[{"id":"1","address":"a/b","emoji":"☕️","content":"Hi, 世界","created":"100"}]}""")))
            val entries = OmgApi(s.url("/")).statuses("a/b")
            assertEquals("Hi, 世界", entries.single().content)
            assertEquals("☕️", entries.single().emoji)
            val request = s.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/address/a%2Fb/statuses/", request.path)
            assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun sortsNumericAndStringDatesWithStableUndatedTail() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"statuses":[
                {"id":"unknown","address":"x","content":"a","created":"oops"},
                {"id":"old","address":"x","content":"b","created":100},
                {"id":"new","address":"x","content":"c","created":"200"},
                {"id":"tie","address":"x","content":"d","created":200},
                {"id":"missing","address":"x","content":"e"},
                {"id":"huge","address":"x","content":"f","created":"9223372036854775807"},
                {"id":"object","address":"x","content":"g","created":{}},
                {"id":"array","address":"x","content":"h","created":[1,2]}]}""")))
            val result = OmgApi(s.url("/")).statuses("x")
            assertEquals(listOf("new", "tie", "old", "unknown", "missing", "huge", "object", "array"), result.map { it.id })
            assertNull(result.last().createdEpochSeconds)
        }
    }
    @Test fun emptyIsValidButMalformedAndForeignEntriesAreNot() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"statuses":[]}""")))
            assertTrue(OmgApi(s.url("/")).statuses("x").isEmpty())
            for (value in listOf("{}", """{"statuses":{}}""", """{"statuses":[{"id":"1","address":"other","content":"private"}]}""", """{"statuses":[{"id":"1","address":"x"}]}""")) {
                s.enqueue(MockResponse().setBody(envelope(value)))
                try { OmgApi(s.url("/")).statuses("x"); fail(value) }
                catch (e: ApiFailure) { assertEquals(FailureKind.FORMAT, e.kind) }
            }
        }
    }
    @Test fun oversizedResponseHasDistinctSafeError() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("x".repeat(1_048_577)))
            try { OmgApi(s.url("/")).statuses("x"); fail() }
            catch (e: ApiFailure) { assertEquals(FailureKind.TOO_LARGE, e.kind); assertTrue(e.message!!.contains("large")) }
        }
    }
    @Test fun publicErrorsAndRedirectRefusalStayBounded() = runTest {
        for ((code, kind) in listOf(302 to FailureKind.SERVER, 429 to FailureKind.RATE_LIMIT, 503 to FailureKind.SERVER, 401 to FailureKind.AUTH)) {
            MockWebServer().use { s ->
                s.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "/elsewhere").setBody("private body"))
                try { OmgApi(s.url("/")).statuses("x"); fail() }
                catch (e: ApiFailure) { assertEquals(kind, e.kind); assertFalse(e.message!!.contains("private body")) }
                assertEquals(1, s.requestCount)
            }
        }
        val s = MockWebServer(); val api = OmgApi(s.url("/")); s.shutdown()
        try { api.statuses("x"); fail() } catch (e: ApiFailure) { assertEquals(FailureKind.NETWORK, e.kind) }
    }
    @Test fun demoAddressesHaveDistinctEntries() = runTest {
        val demo = DemoStatusRepository()
        assertNotEquals(demo.statuses("example"), demo.statuses("little-space"))
        assertTrue(demo.statuses("little-space").all { it.address == "little-space" })
    }
}
