package com.api.testing.models.request

/**
 * Selects an available license from a team + product combination.
 * Used inside [AssignLicenseRequest] when assigning by product code instead of explicit licenseId.
 */
data class AssignFromTeamRequest(
    /** Product code, e.g. "II" for IntelliJ IDEA Ultimate. */
    val productCode: String,
    /** ID of the team from which to take an assignable license. */
    val team: Int
)
