package lol.omg.pocket

import lol.omg.pocket.data.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OmgApiTest {
    private fun envelope(response: String) = """{"request":{"success":true,"status_code":200},"response":$response}"""
    @Test fun accountUsesEncodedPathAndBearerHeader() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(envelope("""{"name":"Cassie","email":"c+a@example.com","created":{"relative_time":"4 years ago"}}""")))
            val api = OmgApi(server.url("/"))
            val account = api.account(Credentials("c+a@example.com", "test-token"))
            assertEquals("Cassie", account.name)
            assertEquals("4 years ago", account.created)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("/account/c+a@example.com/info", request.path)
        }
    }
    @Test fun addressesUseArrayAndPreserveLifetimeAndExpiry() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(envelope("""[{"address":"cassie","expiration":{"will_expire":false,"expired":false}},{"address":"other","expiration":{"expired":true}}]""")))
            val result = OmgApi(server.url("/")).addresses(Credentials("a@b.co", "k"))
            assertEquals(listOf("cassie", "other"), result.map { it.name })
            assertEquals("Does not expire", result[0].expiration)
            assertEquals("Expired", result[1].expiration)
        }
    }
    @Test fun emptyAddressesAreValid() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("[]")))
            assertTrue(OmgApi(s.url("/")).addresses(Credentials("a@b.co", "k")).isEmpty())
        }
    }
    @Test fun detailReadsPrivateInformation() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"address":"cassie","registration":{"relative_time":"four years ago"},"expiration":{"will_expire":false},"verification":{"verified":true}}""")))
            val detail = OmgApi(s.url("/")).address(Credentials("a@b.co", "k"), "cassie")
            assertEquals("cassie", detail.name)
            assertEquals(true, detail.verified)
            assertEquals("/address/cassie/info", s.takeRequest().path)
        }
    }
    @Test fun mapsHttpAndEnvelopeErrorsWithoutLeakingBody() = runTest {
        for ((status, kind) in listOf(401 to FailureKind.AUTH, 403 to FailureKind.AUTH, 429 to FailureKind.RATE_LIMIT, 503 to FailureKind.SERVER)) {
            MockWebServer().use { s ->
                s.enqueue(MockResponse().setResponseCode(status).setBody("private test-token"))
                try { OmgApi(s.url("/")).account(Credentials("a@b.co", "test-token")); fail("Expected failure") }
                catch (e: ApiFailure) { assertEquals(kind, e.kind); assertFalse(e.message!!.contains("test-token")) }
            }
        }
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("""{"request":{"success":false,"status_code":401},"response":{"message":"private"}}"""))
            try { OmgApi(s.url("/")).account(Credentials("a@b.co", "k")); fail() }
            catch (e: ApiFailure) { assertEquals(FailureKind.AUTH, e.kind) }
        }
    }
    @Test fun rejectsMalformedResponsesInsteadOfInventingEmptyAccounts() = runTest {
        for (body in listOf("not json", "{}", envelope("{}"), envelope("null"))) {
            MockWebServer().use { s ->
                s.enqueue(MockResponse().setBody(body))
                try { OmgApi(s.url("/")).account(Credentials("a@b.co", "k")); fail(body) }
                catch (e: ApiFailure) { assertEquals(FailureKind.FORMAT, e.kind) }
            }
        }
    }
    @Test fun redirectsNeverForwardCredentials() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/other"))
            try { OmgApi(s.url("/")).account(Credentials("a@b.co", "k")); fail() }
            catch (e: ApiFailure) { assertEquals(FailureKind.SERVER, e.kind) }
            assertEquals(1, s.requestCount)
        }
    }
    @Test fun unavailableServerMapsToNetworkError() = runTest {
        val server = MockWebServer()
        val api = OmgApi(server.url("/"))
        server.shutdown()
        try { api.account(Credentials("a@b.co", "k")); fail() }
        catch (e: ApiFailure) { assertEquals(FailureKind.NETWORK, e.kind) }
    }
    @Test fun addressIsOneEncodedPathSegment() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(envelope("""{"address":"a/b","expiration":{"will_expire":false}}""")))
            OmgApi(s.url("/")).address(Credentials("a@b.co", "k"), "a/b")
            assertEquals("/address/a%2Fb/info", s.takeRequest().path)
        }
    }

}
