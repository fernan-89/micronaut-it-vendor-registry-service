package com.thinklab.domain.exception;

/** Domain Exception: a vendor name already used in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateVendorException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00409";

    public DuplicateVendorException(String message) {
        super(ERROR_CODE, message);
    }
}
