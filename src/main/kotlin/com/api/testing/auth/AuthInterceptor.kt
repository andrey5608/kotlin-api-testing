package com.api.testing.auth

import com.api.testing.config.TestConfig
import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.protocol.HttpContext

/**
 * Apache HttpClient 5 request interceptor.
 * Adds [X-Api-Key] and [X-Customer-Code] authentication headers to every outgoing request.
 */
class AuthInterceptor : HttpRequestInterceptor {

    override fun process(request: HttpRequest, entity: EntityDetails?, context: HttpContext?) {
        request.setHeader("X-Api-Key", TestConfig.apiKey)
        request.setHeader("X-Customer-Code", TestConfig.customerCode)
    }
}
