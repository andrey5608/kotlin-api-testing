package com.api.testing.models.response

/**
 * Response model for a single license object returned by:
 * - `GET /customer/licenses`  (inside a paged list)
 * - `GET /customer/licenses/{licenseId}`
 * - `GET /customer/teams/{teamId}/licenses`
 */
data class LicenseResponse(
    val licenseId: String?,
    val isAvailableToAssign: Boolean?,
    val isTransferableBetweenTeams: Boolean?,
    val isSuspended: Boolean?,
    val isTrial: Boolean?,
    val assignee: AssigneeInfo?,
    val product: ProductInfo?,
    val team: TeamInfo?,
    val subscription: SubscriptionInfo?,
    val lastSeen: LastSeenInfo?
) {
    data class AssigneeInfo(
        /** Discriminator: "USER", "SERVER", or "LICENSE_KEY". */
        val type: String?,
        /** Populated when type == "USER". */
        val email: String?,
        val name: String?
    )

    data class ProductInfo(
        val code: String?,
        val name: String?
    )

    data class TeamInfo(
        val id: Int?,
        val name: String?
    )

    data class SubscriptionInfo(
        val validUntilDate: String?,
        val isOutdated: Boolean?,
        val isAutomaticallyRenewed: Boolean?
    )

    data class LastSeenInfo(
        val lastAssignmentDate: String?,
        val lastSeenDate: String?,
        val isOfflineCodeGenerated: Boolean?
    )
}

