package com.api.testing.lifecycle

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import com.api.testing.models.request.AssignLicenseRequest
import com.api.testing.models.request.AssigneeContactRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Lifecycle tests — permanently alter system state.
 *
 * IMPORTANT: These tests are excluded from the default test run.
 * Run ONLY with: `mvn test -Plifecycle`
 *
 * LF-01  Revoke an assigned license.
 *   Precondition  — assign a license; record licenseId.
 *   Act           — POST /customer/licenses/revoke?licenseId={id} → 200.
 *   Postcondition — GET /customer/licenses/{id} → isAvailableToAssign = true.
 *
 * LF-02  Rotate API token.
 *   Precondition  — confirm current token works via GET /token.
 *   Act           — POST /token/rotate → 200; record new token in output.
 *   Postcondition — old token → 403 (ApiClient still holds old key); new token shown in output.
 *   NOTE: After LF-02 the API_KEY secret/env var must be updated to the new token.
 *
 * Each test is fully self-contained; no test relies on pre-existing production data.
 */
@Tag("lifecycle")
@DisplayName("Lifecycle tests (run with -Plifecycle)")
class LifecycleTest : BaseApiTest() {


    @Test
    @DisplayName("Revoke assigned license — license becomes unassigned again")
    fun revokeAssignedLicenseSucceedsAndLicenseIsAvailableAgain() {
        // Arrange — find a free license and assign it so we have something to revoke
        val licensesResponse = client.getLicenses(
            assignmentStatus = "UNASSIGNED",
            teamId = TestConfig.sourceTeamId
        )
        assertThat(licensesResponse.statusCode).isEqualTo(200)

        val freeId = licensesResponse.body?.content?.firstOrNull()?.licenseId
        assertThat(freeId)
            .withFailMessage(
                "LF-01 precondition: no unassigned license in SOURCE_TEAM_ID=${TestConfig.sourceTeamId}."
            )
            .isNotNull

        val assignResponse = client.assignLicense(
            AssignLicenseRequest(
                contact = AssigneeContactRequest(
                    email = TestConfig.testUserEmail,
                    firstName = "Lifecycle",
                    lastName = "Test"
                ),
                includeOfflineActivationCode = false,
                sendEmail = false,
                licenseId = freeId
            )
        )
        assertThat(assignResponse.statusCode)
            .withFailMessage(
                "LF-01 precondition: assign failed with %d.\nBody: %s",
                assignResponse.statusCode, assignResponse.rawBody
            )
            .isEqualTo(200)

        val licenseId = assignResponse.body?.licenseId ?: freeId!!

        // Act
        val revokeResponse = client.revokeLicense(licenseId)

        // Assert
        assertThat(revokeResponse.statusCode)
            .withFailMessage(
                "Expected 200 from revoke but got %d.\nBody: %s",
                revokeResponse.statusCode, revokeResponse.rawBody
            )
            .isEqualTo(200)

        // Postcondition: license must be available to assign again
        val verify = client.getLicenseById(licenseId)
        assertThat(verify.statusCode).isEqualTo(200)
        assertThat(verify.body?.isAvailableToAssign)
            .withFailMessage(
                "Expected isAvailableToAssign=true after revoke but was: %s\nBody: %s",
                verify.body?.isAvailableToAssign, verify.rawBody
            )
            .isTrue
    }


    @Test
    @DisplayName("Rotate token — old token is invalidated, new token is printed")
    fun rotateTokenInvalidatesOldToken() {
        // Precondition — current token works
        val preCheck = client.getToken()
        assertThat(preCheck.statusCode)
            .withFailMessage("LF-02 precondition: GET /token returned %d", preCheck.statusCode)
            .isEqualTo(200)

        // Act
        val rotateResponse = client.rotateToken()

        assertThat(rotateResponse.statusCode)
            .withFailMessage(
                "Expected 200 from POST /token/rotate but got %d.\nBody: %s",
                rotateResponse.statusCode, rotateResponse.rawBody
            )
            .isEqualTo(200)

        println("⚠ LF-02: Token rotated successfully. New token body: ${rotateResponse.rawBody}")
        println("  Update API_KEY secret/env var before running any further tests.")

        // Postcondition — the shared ApiClient still holds the old key; next call must be 403
        val postCheck = client.getToken()
        assertThat(postCheck.statusCode)
            .withFailMessage(
                "Expected old token to return 403 after rotation but got %d. " +
                    "The token may not have been invalidated immediately.",
                postCheck.statusCode
            )
            .isEqualTo(403)
    }
}
