package com.uptrail.identity.web;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.web.ApiError;
import com.uptrail.shared.web.CorrelationId;

import tools.jackson.databind.json.JsonMapper;

/**
 * JSON responses for API calls that fail authentication or authorization, so that browser code never
 * receives a login page where it expects JSON.
 */
final class ApiSecurityResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiSecurityResponses() {
    }

    static AuthenticationEntryPoint unauthenticated() {
        return (request, response, exception) -> write(response, ErrorCode.AUTHENTICATION_REQUIRED,
                "Your session has ended. Sign in again to continue.");
    }

    static AccessDeniedHandler denied() {
        return (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                write(response, ErrorCode.CSRF_INVALID,
                        "The security token of this page expired. Reload the page and try again.");
            } else {
                write(response, ErrorCode.FORBIDDEN, "You do not have access to this operation.");
            }
        };
    }

    private static void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        ApiError body = new ApiError(code.httpStatus(), code.name(), message, Map.of(), CorrelationId.current());
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
