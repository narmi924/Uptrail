package com.uptrail.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An expected, explainable failure of a business rule. The message is shown to the user as-is, so it
 * must be specific and must not contain internal details.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(ErrorCode code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
