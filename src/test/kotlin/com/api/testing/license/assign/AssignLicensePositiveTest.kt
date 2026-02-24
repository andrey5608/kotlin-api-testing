package com.api.testing.license.assign

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import com.api.testing.extensions.LicenseCleanupExtension
import com.api.testing.models.request.AssignFromTeamRequest
import com.api.testing.models.request.AssignLicenseRequest
import com.api.testing.models.request.AssigneeContactRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Positive tests for `POST /customer/licenses/assign`.
 *
 * AL-P01  Assign by productCode + teamId, sendEmail=false.
 * AL-P02  Assign by explicit licenseId, sendEmail=false.
 * AL-P03  includeOfflineActivationCode=true (user may or may not have an account — accepted).
 * AL-P04  Assign to a user already in the org (contact data updated/merged).
 *
 * Cleanup: [LicenseCleanupExtension] revokes every licenseId registered via cleanup.track()
 *          after each test. If revoke is blocked (license assigned < 30d), a warning is printed
 *          and the test result is not affected.
 *
 * Run:  mvn -Dgroups=positive test
 *       mvn -Dtest=AssignLicensePositiveTest test
 */
@Tag("positive")
@DisplayName("POST /customer/licenses/assign — positive")
class AssignLicensePositiveTest : BaseApiTest() {

    private val cleanup = LicenseCleanupExtension()

    /**
     * Assign endpoints require a customer-scoped token.
     * Skip every test in this class with a clear message when a team-scoped token is detected.
     */
    @BeforeEach
    fun requireCustomerToken() = assumeCustomerToken()

    /**
     * Ensure at least one assignable license exists in SOURCE_TEAM_ID before each test.
     * Revokes previously-assigned licenses if necessary; skips the test if it still can't.
     */
    @BeforeEach
    fun ensureLicenseAvailable() = ensureAssignableLicenses(needed = 1)

    private val testContact = AssigneeContactRequest(
        email = TestConfig.testUserEmail,
        firstName = "QA",
        lastName = "Automation"
    )

    @AfterEach
    fun runCleanup() {
        cleanup.cleanupNow()
    }

    @AfterAll
    fun closeCleanup() {
        cleanup.close()
    }


    @Test
    @DisplayName("Assign by productCode + teamId returns 200 and license is assigned")
    fun assignByProductCodeAndTeamIdSucceeds() {
        // Arrange — find a free license in SOURCE_TEAM to discover a valid productCode
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        // Grab both productCode and licenseId from the same candidate license.
        // POST /customer/licenses/assign returns HTTP 200 with empty body (by API design),
        // so we pre-capture the licenseId to use for cleanup and verification.
        val candidate = licensesResponse.body?.firstOrNull { it.isAvailableToAssign == true }
            ?: error("No assignable (isAvailableToAssign=true) licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}. Ensure the team has at least one active unassigned license before running positive tests.")
        val productCode = candidate.product?.code
            ?: error("Candidate license ${candidate.licenseId} has no productCode.")
        val candidateLicenseId = candidate.licenseId
            ?: error("Candidate license has no licenseId — cannot verify assignment or clean up.")

        val expectedStatus = 200
        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = AssignFromTeamRequest(
                productCode = productCode,
                team = TestConfig.sourceTeamId
            )
        )

        // Act
        val response = client.assignLicense(request)

        // Assert — API returns 200 with empty body on success (no licenseId in response by design)
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)

        candidateLicenseId.let { cleanup.track(it) }

        // Verify business state: the candidate license should now show the assignee email
        val verifyResponse = client.getLicenseById(candidateLicenseId)
        assertThat(verifyResponse.statusCode).isEqualTo(200)
        assertThat(verifyResponse.body?.assignee?.email)
            .withFailMessage(
                "Expected assignee.email='%s' but was '%s'\nBody: %s",
                testContact.email, verifyResponse.body?.assignee?.email, verifyResponse.rawBody
            )
            .isEqualTo(testContact.email)
    }


    @Test
    @DisplayName("Assign by explicit licenseId returns 200 and license is marked ASSIGNED")
    fun assignByExplicitLicenseIdSucceeds() {
        // Arrange — find any unassigned license and use its ID directly
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        val freeLicense = licensesResponse.body
            ?.firstOrNull { it.isAvailableToAssign == true }
            ?: error("No assignable (isAvailableToAssign=true) licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}.")
        val freeLicenseId = freeLicense.licenseId
            ?: error("Found assignable license but it has no licenseId.")

        val expectedStatus = 200
        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            licenseId = freeLicenseId
        )

        // Act
        val response = client.assignLicense(request)

        // Assert
        assertStatus(expectedStatus, response.statusCode, response.rawBody)

        val assignedId = response.body?.licenseId ?: freeLicenseId
        cleanup.track(assignedId)

        val verifyResponse = client.getLicenseById(assignedId)
        assertThat(verifyResponse.statusCode).isEqualTo(200)

        // isAvailableToAssign must be false after assignment
        assertThat(verifyResponse.body?.isAvailableToAssign)
            .withFailMessage("Expected isAvailableToAssign=false after assign but got true")
            .isFalse
    }


    @Test
    @DisplayName("includeOfflineActivationCode=true is accepted (code ignored if account exists)")
    fun includeOfflineActivationCodeTrueIsAccepted() {
        // Arrange
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        val candidate = licensesResponse.body?.firstOrNull { it.isAvailableToAssign == true }
            ?: error("No assignable licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}.")
        val productCode = candidate.product?.code
            ?: error("Candidate license ${candidate.licenseId} has no productCode.")
        val candidateLicenseId = candidate.licenseId
            ?: error("Candidate license has no licenseId — cannot clean up.")

        val expectedStatus = 200
        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = true,
            sendEmail = false,
            license = AssignFromTeamRequest(productCode, TestConfig.sourceTeamId)
        )

        // Act
        val response = client.assignLicense(request)

        // Assert — API accepts the flag; no 4xx/5xx regardless of account state
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)

        // POST returns empty body by design — track pre-captured licenseId
        cleanup.track(candidateLicenseId)
    }


    @Test
    @DisplayName("Assign to existing org member returns 200")
    fun assignToExistingOrgMemberSucceeds() {
        // Arrange — testContact.email is a confirmed org member (per plan assumption)
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        if (licensesResponse.statusCode != 200)
            error("Arrange failed: GET /customer/licenses returned ${licensesResponse.statusCode}. Body: ${licensesResponse.rawBody}")

        val candidate = licensesResponse.body?.firstOrNull { it.isAvailableToAssign == true }
            ?: error("No assignable licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}.")
        val productCode = candidate.product?.code
            ?: error("Candidate license ${candidate.licenseId} has no productCode.")
        val candidateLicenseId = candidate.licenseId
            ?: error("Candidate license has no licenseId — cannot clean up.")

        val expectedStatus = 200
        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = AssignFromTeamRequest(productCode, TestConfig.sourceTeamId)
        )

        // Act
        val response = client.assignLicense(request)

        // Assert
        assertStatus(expectedStatus, response.statusCode, response.rawBody)
        // POST returns empty body by design — track pre-captured licenseId
        cleanup.track(candidateLicenseId)
    }
}
