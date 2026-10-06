package com.penz7.proofdrop.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.penz7.proofdrop.core.data.shift.ShiftService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * End-to-end tests on a real phone against a running backend.
 *
 *   docker compose up -d
 *   adb reverse tcp:3000 tcp:3000
 *   ./gradlew :app:connectedDebugAndroidTest
 *
 * Override with instrumentation args: serverUrl, courierEmail, courierPassword,
 * dispatcherEmail, dispatcherPassword.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RealDeviceE2ETest {

    @get:Rule val compose = createEmptyComposeRule()

    /**
     * Runtime permissions must already be granted on the device (Settings → Apps → ProofDrop).
     * GrantPermissionRule needs shell permission-granting, which some OEM builds (e.g. MIUI
     * without "USB debugging (Security settings)") forbid, so we check instead of granting.
     */
    @Before
    fun requirePermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val required = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        assertTrue("Grant these permissions to ProofDrop on the device first: $missing", missing.isEmpty())
    }

    private val args = InstrumentationRegistry.getArguments()
    private val serverUrl = args.getString("serverUrl") ?: "http://127.0.0.1:3000"
    private val courierEmail = args.getString("courierEmail") ?: "courier1@proofdrop.dev"
    private val courierPassword = args.getString("courierPassword") ?: "courier123"
    private val dispatch by lazy {
        DispatchApi(
            serverUrl,
            args.getString("dispatcherEmail") ?: "dispatcher@proofdrop.dev",
            args.getString("dispatcherPassword") ?: "dispatch123",
        )
    }
    private val courierId by lazy { dispatch.courierId(courierEmail) }

    // ---- helpers -------------------------------------------------------------------------

    /** Runs [block] with the app open; always Unit so JUnit accepts the test method. */
    private fun scenario(block: () -> Unit) {
        openApp()
        block()
    }

    /**
     * Brings MainActivity to the front via the shell (`am start`), exactly like tapping the
     * launcher icon. Some OEM builds (MIUI) refuse activity starts from the instrumentation
     * process, which is what ActivityScenario does.
     */
    private fun openApp(orderId: String? = null) {
        val extra = orderId?.let { " --es ${ShiftService.EXTRA_ORDER_ID} $it" }.orEmpty()
        shell("am start -W -f 0x20000000 -n com.penz7.proofdrop/.MainActivity$extra")
        compose.waitUntil(20_000) { runCatching { exists("Sign in") || exists("My deliveries") || exists("Capture proof of delivery") }.getOrDefault(false) }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            .use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() } }
    }

    /**
     * Looks in the merged tree first, then the unmerged one: some Material components (e.g. the
     * extended FAB) keep their label text only on a child node.
     */
    private fun exists(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, substring: Boolean = false, timeoutMs: Long = 20_000) {
        try {
            compose.waitUntil(timeoutMs) { exists(text, substring) }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("\"$text\" did not appear within ${timeoutMs}ms. ${screenState(text)}", e)
        }
    }

    /** Visible texts + a screenshot saved on the device, to explain a failed wait. */
    private fun screenState(label: String): String {
        val texts = runCatching {
            compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                }
                .distinct()
        }.getOrElse { listOf("<no compose tree: ${it.message}>") }
        val shot = runCatching {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val file = java.io.File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null), "e2e-fail-${label.filter(Char::isLetterOrDigit)}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
            file.absolutePath
        }.getOrElse { "screenshot failed: ${it.message}" }
        return "Visible: $texts. Screenshot: $shot"
    }

    private fun waitGone(text: String, timeoutMs: Long = 20_000) {
        try {
            compose.waitUntil(timeoutMs) { !exists(text) }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("\"$text\" was still shown after ${timeoutMs}ms", e)
        }
    }

    /** Waits for the node first: screens compose a frame after navigation, not instantly. */
    private fun click(text: String, substring: Boolean = false) {
        waitFor(text, substring, timeoutMs = 15_000)
        val merged = compose.onAllNodesWithText(text, substring = substring)
        if (merged.fetchSemanticsNodes().isNotEmpty()) merged.onFirst().performClick()
        else compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst().performClick()
    }

    /** Waits for either the login screen or the deliveries list after launch. */
    private fun waitForStart() = compose.waitUntil(20_000) {
        exists("Sign in") || exists("My deliveries") || exists("Deliveries")
    }

    private fun signOutIfNeeded() {
        waitForStart()
        if (exists("Sign in")) return
        if (exists("Deliveries")) click("Deliveries")
        waitFor("My deliveries")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("More").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("More").performClick()
        click("Sign out")
        compose.waitUntil(10_000) { exists("Sign in") || exists("Sign out anyway") }
        if (exists("Sign out anyway")) click("Sign out anyway")
        waitFor("Sign in")
    }

    private fun signIn(email: String, password: String) {
        compose.onNodeWithText("Email").performTextReplacement(email)
        compose.onNodeWithText("Password").performTextReplacement(password)
        compose.onNodeWithText("Server URL").performTextReplacement(serverUrl)
        click("Sign in")
    }

    private fun signInAsCourier() {
        waitForStart()
        if (exists("My deliveries") && exists("Online")) return
        signOutIfNeeded()
        signIn(courierEmail, courierPassword)
        waitFor("My deliveries")
    }

    // ---- tests ---------------------------------------------------------------------------

    @Test
    fun t01_wrongPasswordShowsAnError() = scenario {
        signOutIfNeeded()
        signIn(courierEmail, "definitely-wrong")
        waitFor("Wrong email or password")
    }

    @Test
    fun t02_dispatcherAccountIsRejectedInTheApp() = scenario {
        signOutIfNeeded()
        signIn("dispatcher@proofdrop.dev", "dispatch123")
        waitFor("This is a dispatcher account", substring = true)
        assertFalse(exists("My deliveries"))
    }

    @Test
    fun t03_courierSignsInAndSeesTheirOrders() = scenario {
        signInAsCourier()
        waitFor("Online")
    }

    @Test
    fun t04_newAssignmentArrivesLiveOverSse() = scenario {
        signInAsCourier()
        val order = dispatch.createOrder(courierId, "Device SSE test")
        waitFor(order.getString("code"))
    }

    @Test
    fun t05_captureProofIsSealedUploadedAndVerifiedByTheServer() = scenario {
        signInAsCourier()
        val order = dispatch.createOrder(courierId, "Device capture test")
        val code = order.getString("code")
        waitFor(code)
        click(code)
        waitFor("Capture proof of delivery")
        click("Capture proof of delivery")

        // Real camera: wait for the preview to bind, then shoot.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Capture proof").fetchSemanticsNodes().isNotEmpty()
        }
        Thread.sleep(3_000)
        compose.onNodeWithContentDescription("Capture proof").performClick()
        waitFor("Delivery sealed as evidence", substring = true, timeoutMs = 30_000)
        click("Done")

        // Upload happens in the background (WorkManager); the server marks the order delivered.
        compose.waitUntil(60_000) { dispatch.order(order.getString("id")).getString("status") == "DELIVERED" }
        val evidence = dispatch.latestEvidence(courierId)
        assertEquals(code, evidence.getString("orderCode"))
        assertTrue("photo stored", evidence.getInt("sizeBytes") > 10_000)
        val verify = dispatch.verifyChain(courierId)
        assertTrue("server chain verification: $verify", verify.getBoolean("valid"))

        click("Ledger")
        waitFor("Chain intact")
        waitFor("Synced", timeoutMs = 30_000)
    }

    @Test
    fun t06_cancelledOrderDisappearsFromThePhone() = scenario {
        signInAsCourier()
        val order = dispatch.createOrder(courierId, "Device cancel test")
        val code = order.getString("code")
        waitFor(code)
        assertEquals("CANCELLED", dispatch.cancel(order.getString("id")).getString("status"))
        waitGone(code)
    }

    @Test
    fun t07_reassignedOrderLeavesAndComesBack() = scenario {
        signInAsCourier()
        val order = dispatch.createOrder(courierId, "Device reassign test")
        val code = order.getString("code")
        waitFor(code)
        dispatch.assign(order.getString("id"), dispatch.courierId("courier2@proofdrop.dev"))
        waitGone(code)
        dispatch.assign(order.getString("id"), courierId)
        waitFor(code)
        dispatch.cancel(order.getString("id"))
    }

    @Test
    fun t08_notificationTapOpensTheOrder() {
        scenario { signInAsCourier() }
        val order = dispatch.createOrder(courierId, "Device deep link test")
        // Same extra a "New delivery" notification carries.
        openApp(orderId = order.getString("id"))
        waitFor("Capture proof of delivery")
        waitFor(order.getString("code"))
        dispatch.cancel(order.getString("id"))
    }

    @Test
    fun t09_shiftSharesLocationAndPostsAssignmentNotifications() = scenario {
        signInAsCourier()
        click("Start shift")
        waitFor("On shift")

        // Dispatch sees the courier online, and a pushed order raises a notification.
        compose.waitUntil(30_000) { dispatch.courierOnline(courierEmail) }
        val order = dispatch.createOrder(courierId, "Device notification test")
        val code = order.getString("code")
        val notifications = ApplicationProvider.getApplicationContext<Context>().getSystemService(NotificationManager::class.java)
        compose.waitUntil(20_000) {
            notifications.activeNotifications.any { it.notification.extras.getString("android.title") == "New delivery $code" }
        }

        click("Fleet")
        waitFor("● Live")
        click("Deliveries")
        click("On shift")
        waitFor("Start shift")
        dispatch.cancel(order.getString("id"))
    }

    @Test
    fun t10_deviceCheckoutAndReturnSyncWithTheServer() = scenario {
        signInAsCourier()
        click("Devices")
        waitFor("Device checkout")
        waitFor("DEV-00", substring = true)
        val deviceId = (1..6).map { "DEV-00$it" }.first { dispatch.deviceHolder(it) == null }
        // Each card shows "<id> · S/N …"; its button is the matching "Check out" in list order.
        val index = (1..6).map { "DEV-00$it" }.filter { dispatch.deviceHolder(it) == null }.indexOf(deviceId)
        compose.onAllNodesWithText("Check out")[index].performClick()
        compose.waitUntil(15_000) { dispatch.deviceHolder(deviceId) == courierId }
        waitFor("With you")
        click("Return")
        compose.waitUntil(15_000) { dispatch.deviceHolder(deviceId) == null }
    }

    @Test
    fun t11_qrScannerOpensTheCamera() = scenario {
        signInAsCourier()
        click("Devices")
        click("Scan QR")
        waitFor("Point at the QR sticker on the device")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Close scanner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close scanner").performClick()
        waitFor("Device checkout")
    }

    @Test
    fun t12_demoModeWorksWithoutAServer() = scenario {
        signOutIfNeeded()
        click("Try demo mode (no server)")
        waitFor("Demo")
        waitFor("PD-1001")
        click("Fleet")
        waitFor("Demo simulation")
        signOutIfNeeded()
    }

    @Test
    fun t13_finallySignBackInForManualUse() = scenario {
        signInAsCourier()
        assertNotNull(courierId)
    }
}
