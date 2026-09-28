package com.uptrail.shared.error;

/**
 * Raised for records that do not exist and for records the caller may not see; both produce the same
 * response so that the existence of other people's records is not revealed.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "The requested record was not found.");
    }
}
