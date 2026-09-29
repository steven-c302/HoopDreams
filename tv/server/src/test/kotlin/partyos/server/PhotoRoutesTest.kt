package partyos.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoRoutesTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2_000) { it.toByte() }

    private suspend fun io.ktor.client.HttpClient.upload(bytes: ByteArray) = post("/api/avatar") {
        contentType(ContentType.Image.JPEG)
        setBody(bytes)
    }

    @Test fun aPhotoUploadsOnceAndComesBackByItsId() = testApplication {
        serve(newHost())
        val first = client.upload(jpeg)
        assertEquals(200, first.status.value)
        val id = PartyJson.decodeFromString<PhotoResponse>(first.bodyAsText()).id
        assertTrue(PhotoStore.ID.matches(id), id)
        assertEquals(id, PartyJson.decodeFromString<PhotoResponse>(client.upload(jpeg).bodyAsText()).id)

        val back = client.get("/api/avatar/$id.jpg")
        assertEquals(200, back.status.value)
        assertEquals("image/jpeg", back.headers["Content-Type"])
        assertEquals("nosniff", back.headers["X-Content-Type-Options"])
        assertContentEquals(jpeg, back.bodyAsBytes())
    }

    @Test fun onlySmallJpegsAreKept() = testApplication {
        serve(newHost())
        assertEquals(400, client.upload("<svg onload=alert(1)>".toByteArray()).status.value)
        assertEquals(400, client.upload(ByteArray(0)).status.value)
        assertEquals(413, client.upload(jpeg + ByteArray(PhotoStore.MAX_BYTES)).status.value)
    }

    @Test fun unknownOrMalformedIdsAreNotFound() = testApplication {
        serve(newHost())
        assertEquals(404, client.get("/api/avatar/0123456789abcdef.jpg").status.value)
        assertEquals(404, client.get("/api/avatar/..%2F..%2Fetc%2Fpasswd").status.value)
        assertEquals(404, client.get("/api/avatar/XYZ.jpg").status.value)
    }

    @Test fun theStoreKeepsOnlyTheNewestPhotos() {
        val store = PhotoStore(maxPhotos = 2)
        val ids = (1..3).map { n -> store.put(jpeg + byteArrayOf(n.toByte())) }
        assertEquals(null, store.get(ids[0]))
        assertTrue(store.get(ids[1]) != null && store.get(ids[2]) != null)
    }

    @Test fun photosInAFolderOutliveTheServer() {
        val dir = java.nio.file.Files.createTempDirectory("photos").toFile()
        val ids = (1..3).map { n -> PhotoStore(maxPhotos = 2, dir = dir).put(jpeg + byteArrayOf(n.toByte())) }
        val restarted = PhotoStore(maxPhotos = 2, dir = dir)
        assertTrue((jpeg + byteArrayOf(3)).contentEquals(restarted.get(ids[2])))
        assertTrue(restarted.get(ids[1]) != null)
        assertEquals(null, restarted.get(ids[0])) // the oldest went when the folder passed the cap
        assertEquals(setOf("${ids[1]}.jpg", "${ids[2]}.jpg"), dir.list()!!.toSet())
    }
}
