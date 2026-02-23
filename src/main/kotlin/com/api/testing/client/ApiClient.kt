package com.api.testing.client

import com.api.testing.auth.AuthInterceptor
import com.api.testing.config.TestConfig
import com.api.testing.models.request.AssignLicenseRequest
import com.api.testing.models.request.ChangeTeamRequest
import com.api.testing.models.response.ChangeTeamResponse
import com.api.testing.models.response.LicenseResponse
import com.api.testing.models.response.PagedLicenseResponse
import com.api.testing.models.response.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity

/**
 * Thin typed wrapper around Apache HttpClient 5.
 *
 * All calls share the same singleton [CloseableHttpClient] which has [AuthInterceptor] wired in,
 * so every request automatically carries `X-Api-Key` and `X-Customer-Code` headers.
 *
 * Error handling: HTTP errors (4xx / 5xx) do NOT throw by default — callers inspect [ApiResponse]
 * and assert on the status code they expect. This makes negative-test assertions explicit.
 */
class ApiClient(private val baseUrl: String = TestConfig.baseUrl) {

    private val gson = Gson()

    /** Authenticated client — [AuthInterceptor] injects X-Api-Key + X-Customer-Code on every request. */
    private val httpClient: CloseableHttpClient = HttpClients.custom()
        .addRequestInterceptorFirst(AuthInterceptor())
        .build()

    /**
     * Unauthenticated client — no interceptors.
     * Used by [getTokenWithoutAuth] and by [postRaw] / [changeLicensesTeamRaw] when custom auth
     * headers must be set precisely (override or absent) without the interceptor overwriting them.
     */
    private val rawClient: CloseableHttpClient = HttpClients.createDefault()

    // Token

    /** `GET /token` — returns entity details for the current API key. */
    fun getToken(): ApiResponse<TokenResponse> =
        get("/token", TokenResponse::class.java)

    /** `GET /token` without auth — used for negative auth tests. */
    fun getTokenWithoutAuth(): ApiResponse<String> =
        getRaw("/token", noAuth = true)

    /** `POST /token/rotate` — invalidates the current token and returns a new one. */
    fun rotateToken(): ApiResponse<String> =
        postRaw("/token/rotate", "")

    // Licenses

    /**
     * `GET /customer/licenses` — returns paged list of licenses.
     * Optional filters: [assignmentStatus] (e.g. "UNASSIGNED"), [teamId].
     */
    fun getLicenses(
        assignmentStatus: String? = null,
        teamId: Int? = null
    ): ApiResponse<PagedLicenseResponse> {
        val params = buildList {
            assignmentStatus?.let { add("assignmentStatus=$it") }
            teamId?.let { add("teamId=$it") }
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return get("/customer/licenses$query", PagedLicenseResponse::class.java)
    }

    /** `GET /customer/licenses/{licenseId}` — returns full license details. */
    fun getLicenseById(licenseId: String): ApiResponse<LicenseResponse> =
        get("/customer/licenses/$licenseId", LicenseResponse::class.java)

    /** `GET /customer/teams/{teamId}/licenses` — returns licenses assigned to a team. */
    fun getTeamLicenses(teamId: Int): ApiResponse<PagedLicenseResponse> =
        get("/customer/teams/$teamId/licenses", PagedLicenseResponse::class.java)

    /**
     * `POST /customer/licenses/assign` — assigns a license to a contact.
     * Returns raw [ApiResponse] so tests can inspect status for both positive and negative cases.
     */
    fun assignLicense(request: AssignLicenseRequest): ApiResponse<LicenseResponse> =
        post("/customer/licenses/assign", request, LicenseResponse::class.java)

    /**
     * `POST /customer/licenses/assign` with a custom raw JSON body — used for negative tests
     * where the body intentionally omits or malforms required fields.
     */
    fun assignLicenseRaw(
        rawJson: String,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> =
        postRaw("/customer/licenses/assign", rawJson, overrideApiKey, overrideCustomerCode)

    /**
     * `POST /customer/licenses/revoke?licenseId={id}` — revokes an assigned license.
     * Note: may return 400 RECENTLY_ASSIGNED_LICENSE_IS_NOT_AVAILABLE_FOR_REVOKE when license
     * was assigned less than 30 days ago — callers should handle this gracefully.
     */
    fun revokeLicense(licenseId: String): ApiResponse<String> =
        postRaw("/customer/licenses/revoke?licenseId=$licenseId", "")

    // Change team

    /** `POST /customer/changeLicensesTeam` — moves licenses to another team. */
    fun changeLicensesTeam(request: ChangeTeamRequest): ApiResponse<ChangeTeamResponse> =
        post("/customer/changeLicensesTeam", request, ChangeTeamResponse::class.java)

    /** Raw variant for negative tests. */
    fun changeLicensesTeamRaw(
        rawJson: String,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> =
        postRaw("/customer/changeLicensesTeam", rawJson, overrideApiKey, overrideCustomerCode)

    // =========================================================================
    // HTTP helpers
    // =========================================================================

    private fun <T> get(path: String, responseType: Class<T>): ApiResponse<T> {
        val request = HttpGet("$baseUrl$path")
        return httpClient.execute(request) { response ->
            val body = EntityUtils.toString(response.entity)
            ApiResponse(
                statusCode = response.code,
                body = runCatching { gson.fromJson(body, responseType) }.getOrNull(),
                rawBody = body
            )
        }
    }

    private fun getRaw(path: String, noAuth: Boolean = false): ApiResponse<String> {
        val request = HttpGet("$baseUrl$path")
        // When noAuth=true, use the plain client that has no interceptors; the headers are simply
        // never added, which is the correct "missing header" scenario for negative auth tests.
        val client = if (noAuth) rawClient else httpClient
        return client.execute(request) { response ->
            val body = EntityUtils.toString(response.entity)
            ApiResponse(statusCode = response.code, body = body, rawBody = body)
        }
    }

    private fun <T> post(path: String, payload: Any, responseType: Class<T>): ApiResponse<T> {
        val request = HttpPost("$baseUrl$path")
        request.entity = StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON)
        return httpClient.execute(request) { response ->
            val body = EntityUtils.toString(response.entity)
            ApiResponse(
                statusCode = response.code,
                body = runCatching { gson.fromJson(body, responseType) }.getOrNull(),
                rawBody = body
            )
        }
    }

    private fun postRaw(
        path: String,
        rawJson: String,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> {
        val request = HttpPost("$baseUrl$path")
        if (rawJson.isNotEmpty()) {
            request.entity = StringEntity(rawJson, ContentType.APPLICATION_JSON)
        }
        // When auth overrides are requested, bypass the interceptor entirely: use rawClient and
        // set all auth headers manually so the interceptor never overwrites the override value.
        return if (overrideApiKey != null || overrideCustomerCode != null) {
            request.setHeader("X-Api-Key", overrideApiKey ?: TestConfig.apiKey)
            request.setHeader("X-Customer-Code", overrideCustomerCode ?: TestConfig.customerCode)
            rawClient.execute(request) { response ->
                val body = EntityUtils.toString(response.entity)
                ApiResponse(statusCode = response.code, body = body, rawBody = body)
            }
        } else {
            httpClient.execute(request) { response ->
                val body = EntityUtils.toString(response.entity)
                ApiResponse(statusCode = response.code, body = body, rawBody = body)
            }
        }
    }

    fun close() {
        httpClient.close()
        rawClient.close()
    }
}

/**
 * Typed response wrapper.
 *
 * @param statusCode HTTP status code.
 * @param body       Deserialized response object, or `null` if the body was empty / parse failed.
 * @param rawBody    Raw response body string, always available for failure messages.
 */
data class ApiResponse<T>(
    val statusCode: Int,
    val body: T?,
    val rawBody: String
)
