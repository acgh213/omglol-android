package lol.omg.pocket

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import lol.omg.pocket.data.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class FeatureApiTest {
    private fun envelope(value: String) = """{"request":{"success":true},"response":$value}"""
    @Test fun postUsesSingleAuthenticatedJsonRequestAndEscapesContent() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"id":"new-id"}""")))
            val draft = StatusDraft("☕", "A quote: \"hello\"\nSecond line", false)
            assertEquals("new-id", OmgApi(s.url("/")).createStatus(Credentials("a@b.co", "test-key"), "a/b", draft).id)
            val req = s.takeRequest()
            assertEquals("POST", req.method); assertEquals("/address/a%2Fb/statuses/", req.path)
            assertEquals("Bearer test-key", req.getHeader("Authorization"))
            assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
            val json = JsonParser.parseString(req.body.readUtf8()).asJsonObject
            assertEquals(draft.content, json.get("content").asString)
            assertTrue(json.get("skip_mastodon_post").asBoolean)
            assertEquals(1, s.requestCount)
        }
    }
    @Test fun crossPostingRequiresExplicitTrue() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"id":"new"}""")))
            OmgApi(s.url("/")).createStatus(Credentials("a@b.co", "k"), "x", StatusDraft("✨", "Hello", true))
            assertFalse(JsonParser.parseString(s.takeRequest().body.readUtf8()).asJsonObject.get("skip_mastodon_post").asBoolean)
        }
    }
    @Test fun postRejectsRedirectsMissingReceiptAndServerFailureWithoutRetry() = runTest {
        for ((code, body, kind) in listOf(Triple(302, "", FailureKind.SERVER), Triple(200, envelope("{}"), FailureKind.FORMAT), Triple(503, "", FailureKind.SERVER), Triple(422, "", FailureKind.REJECTED))) {
            MockWebServer().use { s ->
                s.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "/again").setBody(body))
                try { OmgApi(s.url("/")).createStatus(Credentials("a@b.co", "k"), "x", StatusDraft("✨", "Hello", false)); fail() }
                catch (e: ApiFailure) { assertEquals(kind, e.kind) }
                assertEquals(1, s.requestCount)
            }
        }
    }
    @Test fun profilePreservesRawSourceAndAllowsEmptyContent() = runTest {
        MockWebServer().use { s ->
            for (content in listOf("<script>raw</script>", "")) {
                s.enqueue(MockResponse().setBody(envelope("""{"content":"$content","theme":"default","type":"profile","css":"a{}","head":"<meta>","metadata":"{}","pfp":"x.jpg","branding":"default"}""")))
                val profile = OmgApi(s.url("/")).profile(Credentials("a@b.co", "k"), "x")
                assertEquals(content, profile.content); assertEquals("a{}", profile.css); assertEquals("<meta>", profile.head)
                assertEquals("{}", profile.metadata)
                val req = s.takeRequest(); assertEquals("GET", req.method)
                assertEquals("/address/x/web", req.path); assertEquals("Bearer k", req.getHeader("Authorization"))
            }
        }
    }
    @Test fun disconnectAfterPostNeverAutomaticallyResubmits() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            try { OmgApi(s.url("/")).createStatus(Credentials("a@b.co", "k"), "x", StatusDraft(content = "Hello")); fail() }
            catch (e: ApiFailure) { assertEquals(FailureKind.NETWORK, e.kind) }
            assertEquals(1, s.requestCount)
        }
    }

    @Test fun immediate503RetryAfterNeverReplaysPost() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            s.enqueue(MockResponse().setBody(envelope("""{"id":"duplicate"}""")))
            try { OmgApi(s.url("/")).createStatus(Credentials("a@b.co", "k"), "x", StatusDraft(content = "Hello")); fail("Must retain unknown outcome, not replay") }
            catch (e: ApiFailure) { assertEquals(FailureKind.SERVER, e.kind) }
            assertEquals(1, s.requestCount)
        }
    }

}
