package com.uptrail.controller;

import com.uptrail.shared.web.CorrelationId;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;

/**
 * Maps exceptions of REST controllers to the JSON error contract. Never returns HTML and never exposes
 * SQL, stack traces or internal class names.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        return body(e.code(), e.getMessage(), e.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return body(ErrorCode.VALIDATION_FAILED, "Some fields need attention.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> malformed(Exception e) {
        return body(ErrorCode.MALFORMED_REQUEST, "The request could not be read. Check the field formats.",
                Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ApiError error = new ApiError(405, "METHOD_NOT_ALLOWED", "This operation is not supported here.",
                Map.of(), CorrelationId.current());
        return ResponseEntity.status(405).body(error);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> stale(ObjectOptimisticLockingFailureException e) {
        return body(ErrorCode.STALE_VERSION,
                "This record changed since you opened it. Review the current version and try again.", Map.of());
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class,
            QueryTimeoutException.class})
    ResponseEntity<ApiError> contention(Exception e) {
        log.warn("Lock contention: {}", e.getMessage());
        return body(ErrorCode.TEMPORARY_CONTENTION,
                "Another change to the same records is in progress. Please try again in a moment.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unexpected API error", e);
        return body(ErrorCode.INTERNAL_ERROR,
                "Something went wrong on the server. Quote the reference below if you report it.", Map.of());
    }

    private static ResponseEntity<ApiError> body(ErrorCode code, String message, Map<String, String> fields) {
        ApiError error = new ApiError(code.httpStatus(), code.name(), message, fields, CorrelationId.current());
        return ResponseEntity.status(code.httpStatus()).body(error);
    }
}
