package com.api.testing.models.request

/**
 * Flexible request model for negative / mutation tests of `POST /customer/licenses/assign`.
 *
 * All fields are nullable with a default of `null`.
 * Gson skips null fields by default, so only explicitly set fields appear in the serialized JSON.
 * This means any combination of missing/present/invalid fields can be constructed without
 * writing raw JSON strings.
 *
 * Examples:
 * ```kotlin
 * // Missing contact entirely
 * AssignLicenseFlexRequest(includeOfflineActivationCode = false, sendEmail = false, licenseId = "X")
 *
 * // Missing email inside contact
 * AssignLicenseFlexRequest(
 *     contact = ContactFlexRequest(firstName = "QA", lastName = "Automation"),
 *     includeOfflineActivationCode = false,
 *     sendEmail = false,
 *     license = LicenseFlexRequest(productCode = "II", team = 123)
 * )
 *
 * // Invalid product code
 * AssignLicenseFlexRequest(
 *     contact = ContactFlexRequest("qa@test.com", "QA", "Auto"),
 *     includeOfflineActivationCode = false,
 *     sendEmail = false,
 *     license = LicenseFlexRequest(productCode = "NOTAPRODUCT", team = 123)
 * )
 * ```
 */
data class AssignLicenseFlexRequest(
    val contact: ContactFlexRequest? = null,
    /** Boxed Boolean? so Gson omits it entirely when null (vs serializing `false`). */
    val includeOfflineActivationCode: Boolean? = null,
    val sendEmail: Boolean? = null,
    val licenseId: String? = null,
    val license: LicenseFlexRequest? = null
)

/** Nullable contact fields — any subset may be provided. */
data class ContactFlexRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null
)

/** Nullable license-ref fields — any subset may be provided. */
data class LicenseFlexRequest(
    val productCode: String? = null,
    val team: Int? = null
)
