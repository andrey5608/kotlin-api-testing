package com.api.testing.models.request

/**
 * Request body for `POST /customer/changeLicensesTeam`.
 *
 * Both fields are required. Licenses that cannot be transferred remain in their current team and
 * are reported in the response.
 */
data class ChangeTeamRequest(
    /** IDs of the licenses to move to [targetTeamId]. */
    val licenseIds: List<String>,
    /** ID of the team that should receive the licenses. */
    val targetTeamId: Int
)
