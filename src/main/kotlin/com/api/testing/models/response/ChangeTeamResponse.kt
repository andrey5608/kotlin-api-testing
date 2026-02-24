package com.api.testing.models.response

/**
 * Response body for `POST /customer/changeLicensesTeam`.
 *
 * The Swagger spec exposes only `licenseIds` (successfully transferred list).
 * TODO: confirm whether `notTransferred` is also present in a live call (Open Question #4 in plan.md).
 *       If so, add it here and update CT-P03 assertions accordingly.
 */
data class ChangeTeamResponse(
    /** IDs of licenses that were successfully transferred to the target team. */
    val licenseIds: List<String>?
)
