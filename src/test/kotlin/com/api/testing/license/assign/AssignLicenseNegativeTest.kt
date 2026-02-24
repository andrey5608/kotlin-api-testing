package com.api.testing.license.assign

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import com.api.testing.models.request.AssignLicenseFlexRequest
import com.api.testing.models.request.ContactFlexRequest
import com.api.testing.models.request.LicenseFlexRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Negative tests for `POST /customer/licenses/assign`.
 *
 * All tests use [AssignLicenseFlexRequest] + typed sub-models instead of raw JSON strings.
 * Gson skips null fields by default, so any mutation of the request is expressed as a
 * data-class construction — no string templating needed.
 *
 * AL-N01  Missing contact.email                         → 400
 * AL-N02  Missing contact.firstName                     → 400
 * AL-N03  Missing contact.lastName                      → 400
 * AL-N04  Neither licenseId nor license object supplied → 400
 * AL-N05  Contact object entirely absent                → 400
 * AL-N06  includeOfflineActivationCode field absent     → 400
 * AL-N07  Invalid productCode                           → 404 (PRODUCT_NOT_FOUND)
 * AL-N08  Non-existent teamId in license.team           → 404 (TEAM_NOT_FOUND)
 * AL-N09  Non-existent licenseId                        → 404 (LICENSE_NOT_FOUND)
 * AL-N10  Empty string email                            → 400
 * AL-N11  Malformed email                               → 400
 * AL-N12  Both licenseId and license object provided    → 404 (licenseId takes precedence)
 * AL-N13  Invalid API key                               → 401
 * AL-N14  Missing X-Api-Key header (empty value)        → 401
 * AL-N15  Invalid X-Customer-Code                       → 401
 * AL-N16  Foreign licenseId (belongs to another org)    → 403  [skipped if foreignLicenseId unset]
 *
 * Negative tests never assign a real license — no cleanup needed.
 *
 * Run:  mvn -Dgroups=negative test
 *       mvn -Dtest=AssignLicenseNegativeTest test
 */
@Tag("negative")
@DisplayName("POST /customer/licenses/assign — negative")
class AssignLicenseNegativeTest : BaseApiTest() {

    // Valid base objects reused across tests
    private val validContact = ContactFlexRequest(
        email = TestConfig.testUserEmail,
        firstName = "QA",
        lastName = "Automation"
    )
    private val validLicense = LicenseFlexRequest(
        productCode = "II",
        team = TestConfig.sourceTeamId
    )

    //  missing required contact sub-field → 400 

    companion object {
        @JvmStatic
        fun missingContactFieldRequests(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "email",
                AssignLicenseFlexRequest(
                    contact = ContactFlexRequest(firstName = "QA", lastName = "Automation"),
                    includeOfflineActivationCode = false,
                    sendEmail = false,
                    license = LicenseFlexRequest(productCode = "II", team = TestConfig.sourceTeamId)
                )
            ),
            Arguments.of(
                "firstName",
                AssignLicenseFlexRequest(
                    contact = ContactFlexRequest(email = TestConfig.testUserEmail, lastName = "Automation"),
                    includeOfflineActivationCode = false,
                    sendEmail = false,
                    license = LicenseFlexRequest(productCode = "II", team = TestConfig.sourceTeamId)
                )
            ),
            Arguments.of(
                "lastName",
                AssignLicenseFlexRequest(
                    contact = ContactFlexRequest(email = TestConfig.testUserEmail, firstName = "QA"),
                    includeOfflineActivationCode = false,
                    sendEmail = false,
                    license = LicenseFlexRequest(productCode = "II", team = TestConfig.sourceTeamId)
                )
            )
        )
    }

    @ParameterizedTest(name = "missing contact.{0} returns 400")
    @MethodSource("missingContactFieldRequests")
    @DisplayName("Missing required contact field returns 400")
    fun missingRequiredContactFieldReturns400(field: String, request: AssignLicenseFlexRequest) {
        // Arrange
        val expectedStatus = 400

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage(
                "[missing contact.$field] Expected $expectedStatus but got %d\nBody: %s",
                response.statusCode, response.rawBody
            )
            .isEqualTo(expectedStatus)
    }

    //  neither licenseId nor license → 400 

    @Test
    @DisplayName("Neither licenseId nor license object returns 400")
    fun neitherLicenseIdNorLicenseReturns400() {
        // Arrange — both licenseId and license are null → omitted from serialized JSON
        val expectedStatus = 400
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  contact object entirely absent → 400 

    @Test
    @DisplayName("Absent contact object returns 400")
    fun absentContactObjectReturns400() {
        // Arrange — contact is null → omitted from JSON
        val expectedStatus = 400
        val request = AssignLicenseFlexRequest(
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  includeOfflineActivationCode absent → 400 

    @Test
    @DisplayName("Missing includeOfflineActivationCode returns 400")
    fun missingIncludeOfflineActivationCodeReturns400() {
        // Arrange — includeOfflineActivationCode is null → omitted from JSON
        val expectedStatus = 400
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  invalid productCode → 404 

    @Test
    @DisplayName("Invalid productCode returns 404 (PRODUCT_NOT_FOUND)")
    fun invalidProductCodeReturns404() {
        // Arrange
        val expectedStatus = 404
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = LicenseFlexRequest(productCode = "NOTAPRODUCT", team = TestConfig.sourceTeamId)
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert — API resolves the product before other fields; unknown code → 404
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  non-existent teamId → 404 

    @Test
    @DisplayName("Non-existent teamId in license.team returns 404 (TEAM_NOT_FOUND)")
    fun nonExistentTeamIdInLicenseReturns404() {
        // Arrange — team=0 is guaranteed to not exist
        val expectedStatus = 404
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = LicenseFlexRequest(productCode = "II", team = 0)
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  non-existent licenseId → 404 

    @Test
    @DisplayName("Non-existent licenseId returns 404 (LICENSE_NOT_FOUND)")
    fun nonExistentLicenseIdReturns404() {
        // Arrange
        val expectedStatus = 404
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            licenseId = "FAKE9999999"
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  empty email → 400 

    @Test
    @DisplayName("Empty string email returns 400")
    fun emptyStringEmailReturns400() {
        // Arrange
        val expectedStatus = 400
        val request = AssignLicenseFlexRequest(
            contact = ContactFlexRequest(email = "", firstName = "QA", lastName = "Automation"),
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  malformed email → 400 

    @Test
    @DisplayName("Malformed email returns 400")
    fun malformedEmailReturns400() {
        // Arrange
        val expectedStatus = 400
        val request = AssignLicenseFlexRequest(
            contact = ContactFlexRequest(email = "notanemail", firstName = "QA", lastName = "Automation"),
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  both licenseId and license provided — licenseId takes precedence 

    @Test
    @DisplayName("Both licenseId and license provided: licenseId takes precedence, non-existent returns 404")
    fun bothLicenseIdAndLicenseObjectLicenseIdTakesPrecedence() {
        // Arrange — request carries both; API resolves licenseId first and ignores license
        val expectedStatus = 404
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            licenseId = "FAKE9999999",
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request)

        // Assert — API tried to find the licenseId and got LICENSE_NOT_FOUND → 404
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  invalid API key → 401 

    @Test
    @DisplayName("Invalid API key returns 401")
    fun invalidApiKeyReturns401() {
        // Arrange
        val expectedStatus = 401
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request, overrideApiKey = "INVALID-KEY-0000000")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  missing X-Api-Key header (empty value) → 401 

    @Test
    @DisplayName("Missing X-Api-Key header returns 401")
    fun missingApiKeyHeaderReturns401() {
        // Arrange — overrideApiKey="" sends header with empty value, effectively unauthenticated
        val expectedStatus = 401
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request, overrideApiKey = "")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  invalid X-Customer-Code → 401 

    @Test
    @DisplayName("Invalid X-Customer-Code returns 401")
    fun invalidCustomerCodeReturns401() {
        // Arrange
        val expectedStatus = 401
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            license = validLicense
        )

        // Act
        val response = client.assignLicenseRaw(request, overrideCustomerCode = "INVALID-CUSTOMER-00")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected $expectedStatus but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(expectedStatus)
    }

    //  team admin tries to assign a license owned by another team → 403 

    @Test
    @DisplayName("Team admin cannot assign a licenseId that belongs to another team — returns 403")
    fun foreignLicenseIdReturns403() {
        // Requires both a team-scoped API key (TEAM_ADMIN_API_KEY) and a licenseId from a different team.
        // Set TEAM_ADMIN_API_KEY env var and foreignLicenseId in config.yml to enable this test.
        Assumptions.assumeTrue(TestConfig.teamAdminKey != null) {
            "Skipped: TEAM_ADMIN_API_KEY env var not set. " +
                "Provide a team-admin API key that has NO access to foreignLicenseId's team."
        }
        Assumptions.assumeTrue(TestConfig.foreignLicenseId != null) {
            "Skipped: 'foreignLicenseId' not set in config.yml. " +
                "Provide a licenseId from a different team to run this test."
        }

        // Arrange — team admin key for Team 1, licenseId owned by Team 2
        val expectedStatus = 403
        val request = AssignLicenseFlexRequest(
            contact = validContact,
            includeOfflineActivationCode = false,
            sendEmail = false,
            licenseId = TestConfig.foreignLicenseId
        )

        // Act — use team-scoped key (not org-admin key)
        val response = client.assignLicenseRaw(request, overrideApiKey = TestConfig.teamAdminKey)

        // Assert — team admin must be denied access to another team's license
        assertThat(response.statusCode)
            .withFailMessage(
                "Expected $expectedStatus (team admin cannot use another team's licenseId) but got %d\nBody: %s",
                response.statusCode, response.rawBody
            )
            .isEqualTo(expectedStatus)
    }
}

