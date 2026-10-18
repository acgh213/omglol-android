package lol.omg.pocket

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import lol.omg.pocket.data.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class ProfileSaveApiTest {
    private val credentials = Credentials("a@b.co", "test-key")
    private fun ok() = MockResponse().setBody("""{"request":{"success":true},"response":{"message":"Saved"}}""")
    @Test fun saveDraftOmitsPublishAndNeverRoundTripsOtherConfiguration() = runTest {
        MockWebServer().use { s ->
            s.enqueue(ok())
            val content = "# A page\n\"Quotes\" and <html>"
            assertFalse(OmgApi(s.url("/")).saveProfile(credentials, "a/b", content, false).published)
            val req=s.takeRequest();assertEquals("POST",req.method);assertEquals("/address/a%2Fb/web",req.path)
            assertEquals("Bearer test-key",req.getHeader("Authorization"))
            val body=JsonParser.parseString(req.body.readUtf8()).asJsonObject
            assertEquals(setOf("content"),body.keySet());assertEquals(content,body.get("content").asString)
        }
    }
    @Test fun publishRequiresExplicitTrueAndAllowsDeliberateEmptySource() = runTest {
        MockWebServer().use { s ->
            s.enqueue(ok());assertTrue(OmgApi(s.url("/")).saveProfile(credentials,"x","",true).published)
            val body=JsonParser.parseString(s.takeRequest().body.readUtf8()).asJsonObject
            assertEquals(setOf("content","publish"),body.keySet());assertTrue(body.get("publish").asBoolean)
            assertEquals("",body.get("content").asString)
        }
    }
    @Test fun saveNeverReplaysOnImmediateServerRetryAndRequiresAcknowledgement() = runTest {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After","0"));s.enqueue(ok())
            try { OmgApi(s.url("/")).saveProfile(credentials,"x","text",false);fail() }
            catch(e:ApiFailure){assertEquals(FailureKind.SERVER,e.kind)}
            assertEquals(1,s.requestCount)
        }
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("""{"request":{"success":true},"response":{}}"""))
            try { OmgApi(s.url("/")).saveProfile(credentials,"x","text",true);fail() }
            catch(e:ApiFailure){assertEquals(FailureKind.FORMAT,e.kind)}
        }
    }
}
