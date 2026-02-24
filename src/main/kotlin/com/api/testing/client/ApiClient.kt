package com.api.testing.client

import com.api.testing.config.TestConfig
import com.api.testing.models.request.AssignLicenseFlexRequest
import com.api.testing.models.request.AssignLicenseRequest
import com.api.testing.models.request.ChangeTeamRequest
import com.api.testing.models.response.ChangeTeamResponse
import com.api.testing.models.response.LicenseResponse
import com.api.testing.models.response.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity

/**
 * Thin typed wrapper around Apache HttpClient 5.
 *
 * All calls share a single [CloseableHttpClient]. Auth headers (`X-Api-Key`, `X-Customer-Code`)
 * are added explicitly via [applyAuth] before each request — this keeps auth visible and makes
 * it trivial to override or omit headers in negative/unauthorised tests without a second client.
 *
 * Error handling: HTTP errors (4xx / 5xx) are NOT thrown by default — callers inspect [ApiResponse]
 * and assert on the status code they expect. This makes negative-test assertions explicit.
 */
class ApiClient(private val baseUrl: String = TestConfig.baseUrl) {

    private val gson = Gson()

    private val httpClient: CloseableHttpClient = HttpClients.createDefault()

    /**
     * Attaches `X-Api-Key` and `X-Customer-Code` headers to [this] request.
     * Callers that need to deviate (invalid key, missing header, team-scoped key) pass explicit values.
     */
    private fun HttpRequest.applyAuth(
        apiKey: String = TestConfig.orgAdminKey,
        customerCode: String = TestConfig.customerCode
    ) {
        setHeader("X-Api-Key", apiKey)
        setHeader("X-Customer-Code", customerCode)
    }

    // Token

    /** `GET /token` — returns entity details for the current API key. */
    fun getToken(): ApiResponse<TokenResponse> = get("/token")

    /** `GET /token` without auth — used for negative auth tests. */
    fun getTokenWithoutAuth(): ApiResponse<String> = get("/token", noAuth = true)

    /** `POST /token/rotate` — invalidates the current token and returns a new one. */
    fun rotateToken(): ApiResponse<String> = post("/token/rotate")

    // Licenses

    /**
     * `GET /customer/licenses` — returns paged list of licenses.
     * Optional filters: [assignmentStatus] (e.g. "UNASSIGNED"), [teamId].
     */
    fun getLicenses(
        assignmentStatus: String? = null,
        teamId: Int? = null
    ): ApiResponse<List<LicenseResponse>> {
        val params = buildList {
            assignmentStatus?.let { add("assignmentStatus=$it") }
            teamId?.let { add("teamId=$it") }
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return getLicenseList("/customer/licenses$query")
    }

    /** `GET /customer/licenses/{licenseId}` — returns full license details. */
    fun getLicenseById(licenseId: String): ApiResponse<LicenseResponse> =
        get("/customer/licenses/$licenseId")

    /** `GET /customer/teams/{teamId}/licenses` — returns licenses assigned to a team. */
    fun getTeamLicenses(teamId: Int): ApiResponse<List<LicenseResponse>> =
        getLicenseList("/customer/teams/$teamId/licenses")

    /** `POST /customer/licenses/assign` — assigns a license to a contact. */
    fun assignLicense(request: AssignLicenseRequest): ApiResponse<LicenseResponse> =
        post("/customer/licenses/assign", gson.toJson(request))

    /**
     * `POST /customer/licenses/assign` with a custom raw JSON body — used for negative tests
     * where the body intentionally omits or malforms required fields.
     */
    fun assignLicenseRaw(
        rawJson: String,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> =
        post("/customer/licenses/assign", rawJson, overrideApiKey, overrideCustomerCode)

    /**
     * `POST /customer/licenses/assign` from a [AssignLicenseFlexRequest] model — Gson skips null
     * fields by default, so only explicitly set fields appear in the serialized body.
     * Ideal for negative / mutation tests without writing raw JSON strings.
     */
    fun assignLicenseRaw(
        request: AssignLicenseFlexRequest,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> =
        assignLicenseRaw(gson.toJson(request), overrideApiKey, overrideCustomerCode)

    /**
     * `POST /customer/licenses/revoke?licenseId={id}` — revokes an assigned license.
     * Note: may return 400 RECENTLY_ASSIGNED_LICENSE_IS_NOT_AVAILABLE_FOR_REVOKE when license
     * was assigned less than 30 days ago — callers should handle this gracefully.
     */
    fun revokeLicense(licenseId: String): ApiResponse<String> =
        post("/customer/licenses/revoke?licenseId=$licenseId")

    // Change team

    /** `POST /customer/changeLicensesTeam` — moves licenses to another team. */
    fun changeLicensesTeam(request: ChangeTeamRequest): ApiResponse<ChangeTeamResponse> =
        post("/customer/changeLicensesTeam", gson.toJson(request))

    /** Raw variant for negative tests. */
    fun changeLicensesTeamRaw(
        rawJson: String,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<String> =
        post("/customer/changeLicensesTeam", rawJson, overrideApiKey, overrideCustomerCode)

    // =========================================================================
    // HTTP helpers
    // =========================================================================

    /**
     * Issues a GET. [noAuth] = true omits auth headers (negative auth tests).
     * Uses [T::class.java] for deserialization — covers all non-generic response types.
     * For [List] responses use [getLicenseList].
     */
    private inline fun <reified T> get(path: String, noAuth: Boolean = false): ApiResponse<T> {
        val request = HttpGet("$baseUrl$path")
        if (!noAuth) request.applyAuth()
        return httpClient.execute(request) { response ->
            val body = response.entity?.let { EntityUtils.toString(it) } ?: ""
            ApiResponse(
                statusCode = response.code,
                body = runCatching { gson.fromJson(body, T::class.java) }.getOrNull(),
                rawBody = body
            )
        }
    }

    /** Specialised GET for `List<LicenseResponse>` — preserves generic type info via [TypeToken]. */
    private fun getLicenseList(path: String): ApiResponse<List<LicenseResponse>> {
        val request = HttpGet("$baseUrl$path").also { it.applyAuth() }
        val listType = object : TypeToken<List<LicenseResponse>>() {}.type
        return httpClient.execute(request) { response ->
            val body = response.entity?.let { EntityUtils.toString(it) } ?: ""
            ApiResponse(
                statusCode = response.code,
                body = runCatching { gson.fromJson<List<LicenseResponse>>(body, listType) }.getOrNull(),
                rawBody = body
            )
        }
    }

    /**
     * Issues a POST. [jsonBody] = null means no request body.
     * Auth defaults to [TestConfig]; pass overrides for negative auth tests.
     * Uses [T::class.java] for deserialization — covers all non-generic response types.
     */
    private inline fun <reified T> post(
        path: String,
        jsonBody: String? = null,
        overrideApiKey: String? = null,
        overrideCustomerCode: String? = null
    ): ApiResponse<T> {
        val request = HttpPost("$baseUrl$path").also {
            it.applyAuth(
                apiKey = overrideApiKey ?: TestConfig.orgAdminKey,
                customerCode = overrideCustomerCode ?: TestConfig.customerCode
            )
            jsonBody?.let { body -> it.entity = StringEntity(body, ContentType.APPLICATION_JSON) }
        }
        return httpClient.execute(request) { response ->
            val body = response.entity?.let { EntityUtils.toString(it) } ?: ""
            ApiResponse(
                statusCode = response.code,
                body = runCatching { gson.fromJson(body, T::class.java) }.getOrNull(),
                rawBody = body
            )
        }
    }

    fun close() = httpClient.close()
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
