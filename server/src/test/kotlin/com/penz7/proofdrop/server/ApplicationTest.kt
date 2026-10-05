package com.penz7.proofdrop.server

import com.penz7.proofdrop.core.evidence.EvidenceChain
import com.penz7.proofdrop.core.evidence.Sha256
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.model.EvidenceDraft
import com.penz7.proofdrop.core.model.Order
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private fun app(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(simulate = false) }
        block()
    }

    @Test
    fun `lists seeded orders`() = app {
        val orders = AppJson.decodeFromString<List<Order>>(client.get("/orders").bodyAsText())
        assertEquals(5, orders.size)
    }

    @Test
    fun `device cannot be checked out by a second courier`() = app {
        val response = client.post("/devices/DEV-005/checkout") {
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(CheckoutRequest("courier-07", "Courier #07", 0)))
        }
        val device = AppJson.decodeFromString<Device>(response.bodyAsText())
        assertEquals("sim-2", device.holderId)
    }

    @Test
    fun `evidence upload rejects a file that does not match its hash`() = app {
        val photo = "real photo".toByteArray()
        val record = EvidenceChain.seal(
            EvidenceDraft("ev-1", "PD-1001", "ev-1.jpg", Sha256.of(photo), 0, null, null, false),
            previous = null,
        )

        suspend fun upload(bytes: ByteArray) = client.post("/evidence") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("record", AppJson.encodeToString(record))
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"ev-1.jpg\"")
                        })
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, upload(photo).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, upload("tampered".toByteArray()).status)
    }
}
