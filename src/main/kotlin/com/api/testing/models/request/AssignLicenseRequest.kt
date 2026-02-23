package com.api.testing.models.request

/**
 * Request body for `POST /customer/licenses/assign`.
 *
 * Either [licenseId] OR [license] (product+team) must be supplied; if neither is given the API
 * returns 400. Both [contact] and [includeOfflineActivationCode] are always required.
 */
data class AssignLicenseRequest(
    val contact: AssigneeContactRequest,
    val includeOfflineActivationCode: Boolean,
    val sendEmail: Boolean,
    /** Assign a specific license by ID. Mutually exclusive with [license]. */
    val licenseId: String? = null,
    /** Assign any available license from a team/product pool. Mutually exclusive with [licenseId]. */
    val license: AssignFromTeamRequest? = null
)
