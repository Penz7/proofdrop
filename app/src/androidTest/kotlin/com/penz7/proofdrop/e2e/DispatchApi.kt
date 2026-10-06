package com.penz7.proofdrop.e2e

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plays the dispatcher during device tests: talks to the same backend the app uses
 * (reachable from the phone through `adb reverse tcp:3000 tcp:3000`).
 */
class DispatchApi(private val baseUrl: String, email: String, password: String) {

    private val token: String = request("POST", "/api/auth/login", JSONObject().put("email", email).put("password", password))
        .let { JSONObject(it).getString("accessToken") }

    fun courierId(email: String): String {
        val couriers = JSONArray(request("GET", "/api/dispatch/couriers"))
        return (0 until couriers.length()).map { couriers.getJSONObject(it) }
            .first { it.getString("email") == email }
            .getString("id")
    }

    fun courierOnline(email: String): Boolean {
        val couriers = JSONArray(request("GET", "/api/dispatch/couriers"))
        return (0 until couriers.length()).map { couriers.getJSONObject(it) }
            .first { it.getString("email") == email }
            .getBoolean("online")
    }

    /** Creates an order assigned to [courierId] and returns it (has "id" and "code"). */
    fun createOrder(courierId: String, customer: String): JSONObject = JSONObject(
        request(
            "POST",
            "/api/dispatch/orders",
            JSONObject()
                .put("customerName", customer)
                .put("customerPhone", "0909 000 111")
                .put("address", "20 Lý Tự Trọng, Q.1, TP.HCM")
                .put("latitude", 10.7781)
                .put("longitude", 106.7019)
                .put("items", "Device test parcel")
                .put("courierId", courierId),
        ),
    )

    fun cancel(orderId: String): JSONObject = JSONObject(request("POST", "/api/dispatch/orders/$orderId/cancel"))

    fun assign(orderId: String, courierId: String?): JSONObject = JSONObject(
        request("POST", "/api/dispatch/orders/$orderId/assign", JSONObject().put("courierId", courierId ?: JSONObject.NULL)),
    )

    fun order(orderId: String): JSONObject {
        val orders = JSONArray(request("GET", "/api/dispatch/orders"))
        return (0 until orders.length()).map { orders.getJSONObject(it) }.first { it.getString("id") == orderId }
    }

    fun verifyChain(courierId: String): JSONObject =
        JSONObject(request("GET", "/api/dispatch/evidence/verify?courierId=$courierId"))

    /** Newest evidence record of a courier. */
    fun latestEvidence(courierId: String): JSONObject =
        JSONArray(request("GET", "/api/dispatch/evidence?courierId=$courierId")).getJSONObject(0)

    fun deviceHolder(deviceId: String): String? {
        val devices = JSONArray(request("GET", "/api/dispatch/devices"))
        val device = (0 until devices.length()).map { devices.getJSONObject(it) }.first { it.getString("id") == deviceId }
        return if (device.isNull("holderId")) null else device.getString("holderId")
    }

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (path != "/api/auth/login") connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(code in 200..299) { "$method $path → HTTP $code: $text" }
        return text
    }
}
