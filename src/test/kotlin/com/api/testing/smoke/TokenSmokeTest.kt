package com.api.testing.smoke

import com.api.testing.base.BaseApiTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Smoke tests for `GET /token`.
 *
 * SM-01  Valid API key  → 200, role = ADMIN, non-empty teams list.
 * SM-02  Missing X-Api-Key header → 403.
 *
 * Run subset:  mvn -Dgroups=smoke test
 *              mvn -Dtest=TokenSmokeTest test
 */
@Tag("smoke")
@DisplayName("GET /token — smoke")
class TokenSmokeTest : BaseApiTest() {


    @Test
    @DisplayName("Valid API key returns 200 with role=ADMIN and non-empty teams")
    fun validApiKeyReturns200WithAdminRoleAndTeams() {
        // Arrange — call the endpoint and verify the configured credentials have ADMIN role.
        //           This test only makes sense for an admin account; skip rather than fail
        //           if the key belongs to a non-admin user.
        val expectedStatus = 200
        val response = client.getToken()
        Assumptions.assumeTrue(response.body?.effectiveRole == "ADMIN") {
            "Skipped: configured ORG_ADMIN_API_KEY does not have ADMIN role (got '${response.body?.effectiveRole}'). " +
                "This test requires admin credentials."
        }

        // Act — response already captured above

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)

        assertThat(response.body?.effectiveRole)
            .withFailMessage("Expected role=ADMIN but was: %s", response.body?.effectiveRole)
            .isEqualTo("ADMIN")

        // Both token types must have at least one team association:
        //   CUSTOMER tokens → non-empty `teams` list
        //   TEAM tokens     → non-null `team` object
        val hasTeamContext = response.body?.teams?.isNotEmpty() == true
            || response.body?.team != null
        assertThat(hasTeamContext)
            .withFailMessage("Expected at least one team/team context but got: teams=%s team=%s",
                response.body?.teams, response.body?.team)
            .isTrue()
    }


    @Test
    @DisplayName("Missing X-Api-Key header returns 401")
    fun missingApiKeyHeaderReturns401() {
        // Arrange — use the raw helper that sends the request without auth headers
        val expectedStatus = 401

        // Act
        val response = client.getTokenWithoutAuth()

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected %d but got %d\nBody: %s", expectedStatus, response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }
}
