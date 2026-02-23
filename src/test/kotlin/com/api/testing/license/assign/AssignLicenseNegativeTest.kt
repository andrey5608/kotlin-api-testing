package com.api.testing.license.assign

import com.api.testing.base.BaseApiTest
import com.api.testing.config.TestConfig
import org.assertj.core.api.Assertions.assertThat
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
 * AL-N01  Missing contact.email                   → 400
 * AL-N02  Missing contact.firstName               → 400
 * AL-N03  Missing contact.lastName                → 400
 * AL-N04  Neither licenseId nor license supplied  → 400
 * AL-N05  Invalid productCode                     → 400
 * AL-N06  Non-existent teamId                     → 404 (TEAM_NOT_FOUND)
 * AL-N07  Non-existent licenseId                  → 404 (LICENSE_NOT_FOUND)
 * AL-N08  Empty string email                      → 400
 * AL-N09  Malformed email                         → 400
 * AL-N10  Invalid API key                         → 403
 * AL-N11  Missing X-Api-Key header                → 403
 * AL-N12  Invalid X-Customer-Code                 → 403
 *
 * Negative tests never assign a real license, so no cleanup is needed.
 *
 * Run:  mvn -Dgroups=negative test
 *       mvn -Dtest=AssignLicenseNegativeTest test
 */
@Tag("negative")
@DisplayName("POST /customer/licenses/assign — negative")
class AssignLicenseNegativeTest : BaseApiTest() {

    // Shared valid base fields reused across mutation tests
    private val validContact get() = """
        "email": "${TestConfig.testUserEmail}",
        "firstName": "QA",
        "lastName": "Automation"
    """.trimIndent()

    private val validLicense get() = """
        "license": { "productCode": "II", "team": ${TestConfig.sourceTeamId} }
    """.trimIndent()

    // AL-N01 / AL-N02 / AL-N03 — parameterized: missing required contact field → 400 -----------

    companion object {
        @JvmStatic
        fun missingContactFieldBodies(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "email",
                """{"contact":{"firstName":"QA","lastName":"Automation"},"includeOfflineActivationCode":false,"sendEmail":false,"license":{"productCode":"II","team":${TestConfig.sourceTeamId}}}"""
            ),
            Arguments.of(
                "firstName",
                """{"contact":{"email":"${TestConfig.testUserEmail}","lastName":"Automation"},"includeOfflineActivationCode":false,"sendEmail":false,"license":{"productCode":"II","team":${TestConfig.sourceTeamId}}}"""
            ),
            Arguments.of(
                "lastName",
                """{"contact":{"email":"${TestConfig.testUserEmail}","firstName":"QA"},"includeOfflineActivationCode":false,"sendEmail":false,"license":{"productCode":"II","team":${TestConfig.sourceTeamId}}}"""
            )
        )
    }

    @ParameterizedTest(name = "missing contact.{0} returns 400")
    @MethodSource("missingContactFieldBodies")
    @DisplayName("Missing required contact field returns 400")
    fun missingRequiredContactFieldReturns400(field: String, rawJson: String) {
        // Arrange — rawJson has the specific field removed

        // Act
        val response = client.assignLicenseRaw(rawJson)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage(
                "[missing $field] Expected 400 but got %d\nBody: %s",
                response.statusCode, response.rawBody
            )
            .isEqualTo(400)
    }


    @Test
    @DisplayName("Neither licenseId nor license object returns 400")
    fun neitherLicenseIdNorLicenseReturns400() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(400)
    }


    @Test
    @DisplayName("Invalid productCode returns 400")
    fun invalidProductCodeReturns400() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              "license": { "productCode": "NOTAPRODUCT", "team": ${TestConfig.sourceTeamId} }
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(400)
    }


    @Test
    @DisplayName("Non-existent teamId in license.team returns 404")
    fun nonExistentTeamIdInLicenseReturns404() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              "license": { "productCode": "II", "team": 0 }
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 404 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(404)
    }


    @Test
    @DisplayName("Non-existent licenseId returns 404")
    fun nonExistentLicenseIdReturns404() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              "licenseId": "FAKE9999999"
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 404 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(404)
    }


    @Test
    @DisplayName("Empty string email returns 400")
    fun emptyStringEmailReturns400() {
        // Arrange
        val body = """
            {
              "contact": { "email": "", "firstName": "QA", "lastName": "Automation" },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              $validLicense
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(400)
    }


    @Test
    @DisplayName("Malformed email returns 400")
    fun malformedEmailReturns400() {
        // Arrange
        val body = """
            {
              "contact": { "email": "notanemail", "firstName": "QA", "lastName": "Automation" },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              $validLicense
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body)

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 400 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(400)
    }


    @Test
    @DisplayName("Invalid API key returns 403")
    fun invalidApiKeyReturns403() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              $validLicense
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body, overrideApiKey = "INVALID-KEY-0000000")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 403 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(403)
    }


    @Test
    @DisplayName("Missing X-Api-Key header returns 403")
    fun missingApiKeyHeaderReturns403() {
        // Arrange — passing empty string for the key effectively omits a valid value;
        // actual "no header" behaviour is tested via getTokenWithoutAuth() in TokenSmokeTest.
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              $validLicense
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body, overrideApiKey = "")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 403 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(403)
    }


    @Test
    @DisplayName("Invalid X-Customer-Code returns 403")
    fun invalidCustomerCodeReturns403() {
        // Arrange
        val body = """
            {
              "contact": { $validContact },
              "includeOfflineActivationCode": false,
              "sendEmail": false,
              $validLicense
            }
        """.trimIndent()

        // Act
        val response = client.assignLicenseRaw(body, overrideCustomerCode = "INVALID-CUSTOMER-00")

        // Assert
        assertThat(response.statusCode)
            .withFailMessage("Expected 403 but got %d\nBody: %s", response.statusCode, response.rawBody)
            .isEqualTo(403)
    }
}
