package com.api.testing.extensions

import com.api.testing.client.ApiClient
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that revokes licenses assigned during a test.
 *
 * Usage:
 * ```kotlin
 * class MyTest {
 *     @RegisterExtension
 *     val cleanup = LicenseCleanupExtension()
 *
 *     fun someTest() {
 *         val response = client.assignLicense(...)
 *         cleanup.track(response.body!!.licenseId!!)
 *         // ... assertions ...
 *     }
 * }
 * ```
 *
 * Contract:
 * - Call [track] after every successful assign call to register the license ID for cleanup.
 * - After each test, all registered IDs are revoked via `POST /customer/licenses/revoke?licenseId=`.
 * - If revoke returns `400` (e.g. RECENTLY_ASSIGNED_LICENSE_IS_NOT_AVAILABLE_FOR_REVOKE) the
 *   warning is printed but the test result is NOT failed — manual cleanup will be required.
 * - The cleanup client uses the same config/auth as the main test client.
 */
class LicenseCleanupExtension : AfterEachCallback {

    private val licenseIds = mutableListOf<String>()
    private val cleanupClient = ApiClient()

    /** Register [licenseId] so it is revoked after the current test. */
    fun track(licenseId: String) {
        licenseIds += licenseId
    }

    /** Revoke all tracked licenses. Safe to call directly from @AfterEach without an ExtensionContext. */
    fun cleanupNow() {
        val toCleanup = licenseIds.toList()
        licenseIds.clear()
        toCleanup.forEach { id ->
            try {
                val response = cleanupClient.revokeLicense(id)
                if (response.statusCode == 200) {
                    println("[LicenseCleanupExtension] Revoked license $id ✓")
                } else {
                    println(
                        "[LicenseCleanupExtension] WARNING: could not revoke license $id " +
                            "(HTTP ${response.statusCode}). Manual cleanup may be required.\n" +
                            "Body: ${response.rawBody}"
                    )
                }
            } catch (e: Exception) {
                println("[LicenseCleanupExtension] ERROR revoking $id: ${e.message}")
            }
        }
    }

    override fun afterEach(context: ExtensionContext) = cleanupNow()

    fun close() = cleanupClient.close()
}
