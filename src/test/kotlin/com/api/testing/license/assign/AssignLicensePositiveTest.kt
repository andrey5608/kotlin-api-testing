package com.api.testing.license.assign

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import com.api.testing.extensions.LicenseCleanupExtension
import com.api.testing.models.request.AssignFromTeamRequest
import com.api.testing.models.request.AssignLicenseRequest
import com.api.testing.models.request.AssigneeContactRequest
import org.assertj.core.api.Assertions.assertThat
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

    private val testContact = AssigneeContactRequest(
        email = TestConfig.testUserEmail,
        firstName = "QA",
        lastName = "Automation"
    )

    @AfterEach
    fun runCleanup() {
        cleanup.afterEach(null!!)  // delegate to extension
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
        assertThat(licensesResponse.statusCode)
            .withFailMessage("Expected 200 from GET /customer/licenses but got %d\nBody: %s",
                licensesResponse.statusCode, licensesResponse.rawBody)
            .isEqualTo(200)

        val productCode = licensesResponse.body?.content?.firstOrNull()?.product?.code
        assertThat(productCode)
            .withFailMessage(
                "No unassigned licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}. " +
                    "Ensure the team has at least one unassigned license before running positive tests."
            )
            .isNotNull

        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = AssignFromTeamRequest(
                productCode = productCode!!,
                team = TestConfig.sourceTeamId
            )
        )

        // Act
        val response = client.assignLicense(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 200 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(200)

        val assignedId = response.body?.licenseId
        assertThat(assignedId)
            .withFailMessage("Expected licenseId in response but got null\nBody: %s", response.rawBody)
            .isNotNull

        cleanup.track(assignedId!!)

        // Verify business state: GET /customer/licenses/{id} must show the assignee email
        val verifyResponse = client.getLicenseById(assignedId)
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
        assertThat(licensesResponse.statusCode).isEqualTo(200)

        val freeLicenseId = licensesResponse.body?.content?.firstOrNull()?.licenseId
        assertThat(freeLicenseId)
            .withFailMessage(
                "No unassigned licenses found in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}."
            )
            .isNotNull

        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            licenseId = freeLicenseId
        )

        // Act
        val response = client.assignLicense(request)

        // Assert
        assertStatus(200, response.statusCode, response.rawBody)

        val assignedId = response.body?.licenseId ?: freeLicenseId!!
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
        assertThat(licensesResponse.statusCode).isEqualTo(200)

        val productCode = licensesResponse.body?.content?.firstOrNull()?.product?.code
        assertThat(productCode).isNotNull

        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = true,
            sendEmail = false,
            license = AssignFromTeamRequest(productCode!!, TestConfig.sourceTeamId)
        )

        // Act
        val response = client.assignLicense(request)

        // Assert — API accepts the flag; no 4xx/5xx regardless of account state
        assertThat(response.statusCode)
            .withFailMessage("Expected 200 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(200)

        response.body?.licenseId?.let { cleanup.track(it) }
    }


    @Test
    @DisplayName("Assign to existing org member returns 200")
    fun assignToExistingOrgMemberSucceeds() {
        // Arrange — TEST_USER_EMAIL is a confirmed org member (per plan assumption)
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        assertThat(licensesResponse.statusCode).isEqualTo(200)

        val productCode = licensesResponse.body?.content?.firstOrNull()?.product?.code
        assertThat(productCode).isNotNull

        val request = AssignLicenseRequest(
            contact = testContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = AssignFromTeamRequest(productCode!!, TestConfig.sourceTeamId)
        )

        // Act
        val response = client.assignLicense(request)

        // Assert
        assertStatus(200, response.statusCode, response.rawBody)
        response.body?.licenseId?.let { cleanup.track(it) }
    }
}
