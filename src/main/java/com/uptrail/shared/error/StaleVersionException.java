package com.uptrail.shared.error;

public class StaleVersionException extends BusinessException {

    public StaleVersionException() {
        super(ErrorCode.STALE_VERSION,
                "This record changed since you opened it. Review the current version and try again.");
    }
}
