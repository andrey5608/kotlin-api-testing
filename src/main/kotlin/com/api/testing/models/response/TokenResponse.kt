package com.api.testing.models.response

/**
 * Minimal representation of the `GET /token` response.
 * The API returns either a ManagedCustomerResponse or ManagedTeamResponse; we model the common
 * fields used by smoke / base tests.
 */
data class TokenResponse(
    /** Token scope: "CUSTOMER" for account-level tokens, "TEAM" for team-scoped tokens. */
    val type: String? = null,
    /** Role at the customer level — present on CUSTOMER tokens. */
    val role: String? = null,
    /** List of teams — present on CUSTOMER tokens. */
    val teams: List<TeamInfo>? = null,
    /** Team details — present on TEAM tokens. */
    val team: TeamDetails? = null
) {
    data class TeamInfo(val id: Int?, val name: String?)

    data class TeamDetails(val id: Int?, val name: String?, val role: String?)

    /** Effective role regardless of token type: root `role` (CUSTOMER) or `team.role` (TEAM). */
    val effectiveRole: String? get() = role ?: team?.role
}
