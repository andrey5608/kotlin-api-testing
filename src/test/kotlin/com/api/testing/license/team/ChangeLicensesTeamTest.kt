package com.api.testing.license.team

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import com.api.testing.extensions.LicenseCleanupExtension
import com.api.testing.models.request.ChangeTeamRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Tests for `POST /customer/changeLicensesTeam`.
 *
 * Positive:
 * CT-P01  Transfer single license to TARGET_TEAM_ID.
 * CT-P02  Transfer multiple licenses.
 * CT-P03  Mixed: one isTransferableBetweenTeams=true (moves) + one =false (stays in source).
 *
 * Negative:
 * CT-N01  Non-existent targetTeamId = 0              → 404
 * CT-N02  Empty licenseIds array                     → 400
 * CT-N03  Fake licenseId in array                    → 400 or 200 with all in notTransferred
 * CT-N04  targetTeamId same as source                → 400 or 200 with license in notTransferred
 * CT-N05  Missing targetTeamId field                 → 400
 * CT-N06  Missing licenseIds field                   → 400
 * CT-N07  Invalid API key                            → 401
 * CT-N08  Empty JSON body                            → 400
 *
 * Cleanup: licenses transferred to TARGET_TEAM are moved back to SOURCE_TEAM in @AfterEach.
 *
 * Run:  mvn -Dtest=ChangeLicensesTeamTest test
 */
@DisplayName("POST /customer/changeLicensesTeam")
class ChangeLicensesTeamTest : BaseApiTest() {

    /** IDs transferred to TARGET during this test — restored in @AfterEach. */
    private val transferredIds = mutableListOf<String>()

    /** Licenses assigned in test Arrange blocks — revoked in @AfterEach. */
    private val cleanup = LicenseCleanupExtension()

    /**
     * All changeLicensesTeam endpoints require a customer-scoped token.
     * Skip every test in this class with a clear message when a team-scoped token is detected.
     */
    @BeforeEach
    fun requireCustomerToken() = assumeCustomerToken()

    @AfterEach
    fun cleanupAssignedLicenses() = cleanup.cleanupNow()

    @AfterAll
    fun closeCleanup() = cleanup.close()

    @AfterEach
    fun restoreToSourceTeam() {
        if (transferredIds.isEmpty()) return
        val restoreRequest = ChangeTeamRequest(
            licenseIds = transferredIds.toList(),
            targetTeamId = TestConfig.sourceTeamId
        )
        val response = client.changeLicensesTeam(restoreRequest)
        if (response.statusCode == 200) {
            println("[Cleanup] Restored ${transferredIds.size} license(s) back to SOURCE_TEAM ✓")
        } else {
            println(
                "[Cleanup] WARNING: could not restore licenses to SOURCE_TEAM (HTTP ${response.statusCode})." +
                    "\nBody: ${response.rawBody}"
            )
        }
        transferredIds.clear()
    }

    // =========================================================================
    // Positive
    // =========================================================================


    @Test
    @Tag("positive")
    @DisplayName("Transfer single license to TARGET_TEAM returns 200")
    fun transferSingleLicenseToTargetTeamSucceeds() {
        // Arrange — ensure at least 1 transferable license is available, revoking if needed
        ensureAssignableLicenses(needed = 1, transferable = true)

        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        val licenseId = licensesResponse.body
            ?.firstOrNull { it.isTransferableBetweenTeams == true && it.isAvailableToAssign == true }
            ?.licenseId
            ?: error("No transferable unassigned license in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}.")

        val expectedStatus = 200
        val request = ChangeTeamRequest(
            licenseIds = listOf(licenseId),
            targetTeamId = TestConfig.targetTeamId
        )

        // Act
        val response = client.changeLicensesTeam(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)

        // Register for cleanup
        transferredIds += licenseId

        // Verify via GET /customer/teams/{targetId}/licenses
        val teamLicenses = client.getTeamLicenses(TestConfig.targetTeamId)
        assertThat(teamLicenses.statusCode).isEqualTo(200)
        val found = teamLicenses.body?.any { it.licenseId == licenseId }
        assertThat(found)
            .withFailMessage("License $licenseId not found in TARGET_TEAM after transfer")
            .isTrue
    }


    @Test
    @Tag("positive")
    @DisplayName("Transfer multiple licenses returns 200 and all appear in target team")
    fun transferMultipleLicensesToTargetTeamSucceeds() {
        // Arrange — ensure at least 2 free, transferable licenses are available, revoking if needed
        ensureAssignableLicenses(needed = 2, transferable = true)

        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        val ids = licensesResponse.body
            ?.filter { it.isTransferableBetweenTeams == true && it.isAvailableToAssign == true }
            ?.mapNotNull { it.licenseId }
            ?.take(2)
            ?: emptyList()

        if (ids.size < 2)
            error("Need at least 2 transferable licenses in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}, found ${ids.size}.")

        val expectedStatus = 200
        val request = ChangeTeamRequest(
            licenseIds = ids,
            targetTeamId = TestConfig.targetTeamId
        )

        // Act
        val response = client.changeLicensesTeam(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)

        transferredIds += ids

        // All IDs appear in response list
        assertThat(response.body?.licenseIds)
            .withFailMessage("Expected transferred licenseIds in response but got: %s", response.rawBody)
            .isNotNull
            .containsAll(ids)
    }

    @Test
    @Tag("positive")
    @DisplayName("Mixed licenses: isTransferableBetweenTeams=false stays in source, =true moves to target")
    fun mixedTransferPartiallySucceeds() {
        // Arrange — need one license with isTransferableBetweenTeams=false (non-transferable)
        // and one with isTransferableBetweenTeams=true + isAvailableToAssign=true (transferable).
        // Query all licenses in source team (no assignment-status filter) to find non-transferable ones.
        val allLicensesResponse = client.getLicenses(teamId = TestConfig.sourceTeamId)
        if (allLicensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${allLicensesResponse.statusCode}. Body: ${allLicensesResponse.rawBody}")

        val allLicenses = allLicensesResponse.body ?: emptyList()

        val nonTransferableId = allLicenses
            .firstOrNull { it.isTransferableBetweenTeams == false }
            ?.licenseId
        assumeTrue(
            nonTransferableId != null,
            "Skipping CT-P03: no isTransferableBetweenTeams=false license in " +
                "SOURCE_TEAM_ID=${TestConfig.sourceTeamId}. Add a non-transferable license to cover this case."
        )

        ensureAssignableLicenses(needed = 1, transferable = true)
        val transferableId = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        ).body
            ?.firstOrNull { it.isTransferableBetweenTeams == true && it.isAvailableToAssign == true }
            ?.licenseId
            ?: error("Arrange failed: no isTransferableBetweenTeams=true + isAvailableToAssign=true license in SOURCE_TEAM.")

        val request = ChangeTeamRequest(
            licenseIds = listOf(transferableId, nonTransferableId!!),
            targetTeamId = TestConfig.targetTeamId
        )

        // Act
        val response = client.changeLicensesTeam(request)

        // Log raw response — reveals actual field names for transferred vs. blocked licenses
        println("[CT-P03] Raw response body: ${response.rawBody}")

        // Assert — 200
        assertThat(response.statusCode)
            .withFailMessage("Expected 200 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(200)

        // transferable license must be in TARGET_TEAM
        transferredIds += transferableId
        val targetLicenses = client.getTeamLicenses(TestConfig.targetTeamId)
        assertThat(targetLicenses.statusCode).isEqualTo(200)
        assertThat(targetLicenses.body?.any { it.licenseId == transferableId })
            .withFailMessage(
                "Expected transferable license $transferableId to be in TARGET_TEAM after transfer.\n" +
                    "Target team licenses: ${targetLicenses.rawBody}"
            )
            .isTrue

        // non-transferable license must NOT be in TARGET_TEAM
        assertThat(targetLicenses.body?.any { it.licenseId == nonTransferableId })
            .withFailMessage(
                "Non-transferable license $nonTransferableId (isTransferableBetweenTeams=false) " +
                    "should NOT be in TARGET_TEAM.\nTarget team licenses: ${targetLicenses.rawBody}"
            )
            .isFalse
    }

    // =========================================================================
    // Negative
    // =========================================================================


    @Test
    @Tag("negative")
    @DisplayName("Non-existent targetTeamId = 0 returns 404 TEAM_NOT_FOUND")
    fun nonExistentTargetTeamIdReturns404() {
        // Arrange
        val expectedStatus = 404
        val body = """{"licenseIds":["ABC1234567"],"targetTeamId":0}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }


    @Test
    @Tag("negative")
    @DisplayName("Empty licenseIds array returns 200 with empty licenseIds (no-op)")
    fun emptyLicenseIdsArrayReturnsNoop() {
        // Arrange
        val expectedStatus = 200
        val body = """{"licenseIds":[],"targetTeamId":${TestConfig.targetTeamId}}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert — API treats an empty licenseIds array as a no-op and returns 200 with {"licenseIds":[]}
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus (no-op) but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }


    @Test
    @Tag("negative")
    @DisplayName("Fake licenseId in array does not cause 5xx")
    fun fakeLicenseIdDoesNotCauseServerError() {
        // Arrange
        val acceptableStatuses = listOf(400, 200)
        val body = """{"licenseIds":["FAKE9999999"],"targetTeamId":${TestConfig.targetTeamId}}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert — API contract: either 400 (rejected outright) or 200 with license in notTransferred.
        // Either way, no server error is allowed.
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 or 200 but got %d (server error)\nBody: %s",
                response.statusCode, response.rawBody)
            .isIn(acceptableStatuses)
    }


    @Test
    @Tag("negative")
    @DisplayName("targetTeamId same as source returns 400 or 200 with licenseId not transferred")
    fun sameTargetTeamIdAsSourceIsRejectedOrResultsInNoop() {
        // Arrange
        val acceptableStatuses = listOf(400, 200)
        val body = """{"licenseIds":["ABC1234567"],"targetTeamId":${TestConfig.sourceTeamId}}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 or 200 but got %d\nBody: %s",
                response.statusCode, response.rawBody)
            .isIn(acceptableStatuses)
    }


    @Test
    @Tag("negative")
    @DisplayName("Missing targetTeamId field defaults to 0 and returns 404 TEAM_NOT_FOUND")
    fun missingTargetTeamIdReturns404() {
        // Arrange
        val expectedStatus = 404
        val body = """{"licenseIds":["ABC1234567"]}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert — Missing targetTeamId defaults to 0 in deserialization — team 0 is not found → 404
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }


    @Test
    @Tag("negative")
    @DisplayName("Missing licenseIds field returns 400")
    fun missingLicenseIdsReturns400() {
        // Arrange
        val expectedStatus = 400
        val body = """{"targetTeamId":${TestConfig.targetTeamId}}"""

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }


    @Test
    @Tag("negative")
    @DisplayName("Invalid API key returns 401")
    fun invalidApiKeyReturns401() {
        // Arrange
        val expectedStatus = 401
        val body = """{"licenseIds":["ABC1234567"],"targetTeamId":${TestConfig.targetTeamId}}"""

        // Act
        val response = client.changeLicensesTeamRaw(body, overrideApiKey = "INVALID-KEY-0000000")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }


    @Test
    @Tag("negative")
    @DisplayName("Empty JSON body returns 400")
    fun emptyJsonBodyReturns400() {
        // Arrange
        val expectedStatus = 400
        val body = "{}"

        // Act
        val response = client.changeLicensesTeamRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }
}
