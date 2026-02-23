package com.api.testing.base

import com.api.testing.client.ApiClient
import com.api.testing.config.TestConfig
import com.api.testing.models.response.TokenResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Base class for all API tests.
 *
 * Lifecycle:
 * - `@BeforeAll`   — verifies auth by calling `GET /token`; aborts the entire test class clearly
 *                    if credentials are wrong so no individual test produces a confusing failure.
 * - `@AfterAll`    — closes the shared [ApiClient] (HTTP connection pool).
 *
 * Subclasses inherit [client] and can call all API methods through it.
 * The `@TestInstance(PER_CLASS)` annotation is required so that `@BeforeAll` / `@AfterAll` can
 * be non-static in Kotlin.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseApiTest {

    protected val client = ApiClient()

    /**
     * Token response captured during [verifyAuth].
     * Available to all subclasses after `@BeforeAll` has run.
     */
    protected lateinit var tokenResponse: TokenResponse

    @BeforeAll
    fun verifyAuth() {
        val response = client.getToken()
        assertThat(response.statusCode)
            .withFailMessage(
                "Auth check failed — GET /token returned %d. " +
                    "Verify API_KEY is set and valid.\nBody: %s",
                response.statusCode, response.rawBody
            )
            .isEqualTo(200)
        tokenResponse = response.body!!
    }

    @AfterAll
    fun closeClient() {
        client.close()
    }

    // Shared assertion helpers

    /** Asserts [statusCode] is 200, printing the raw body on failure for easy debugging. */
    protected fun assertSuccess(statusCode: Int, rawBody: String) {
        assertThat(statusCode)
            .withFailMessage("Expected HTTP 200 but got %d.\nBody: %s", statusCode, rawBody)
            .isEqualTo(200)
    }

    /** Asserts [statusCode] is [expected], printing the raw body on failure. */
    protected fun assertStatus(expected: Int, statusCode: Int, rawBody: String) {
        assertThat(statusCode)
            .withFailMessage("Expected HTTP %d but got %d.\nBody: %s", expected, statusCode, rawBody)
            .isEqualTo(expected)
    }

    /** Convenience: returns BASE_URL from config (useful for diagnostic messages). */
    protected val baseUrl: String get() = TestConfig.baseUrl

    /**
     * Skips the test with a clear message when the configured API key is team-scoped.
     * Endpoints like `POST /customer/licenses/assign` and `POST /customer/changeLicensesTeam`
     * require a customer-level (account-wide) token; they return 403 TOKEN_TYPE_MISMATCH otherwise.
     */
    protected fun assumeCustomerToken() {
        Assumptions.assumeTrue(tokenResponse.type == "CUSTOMER") {
            "Skipped: API key has token type '${tokenResponse.type}'. " +
                "This test requires a customer-scoped (account-level) API key, not a team-scoped one."
        }
    }
}
