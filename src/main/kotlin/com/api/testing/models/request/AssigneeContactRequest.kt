package com.api.testing.models.request

/**
 * Contact information for the license assignee.
 * All three fields are required by the API (`POST /customer/licenses/assign`).
 */
data class AssigneeContactRequest(
    val email: String,
    val firstName: String,
    val lastName: String
)
