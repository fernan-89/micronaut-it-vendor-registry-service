package com.thinklab.domain.exception;

/** Domain Exception: an illegal Vendor lifecycle transition, the vendor changed while the write was applied, or an agreement for an inactive vendor. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidVendorStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00409";

    public InvalidVendorStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
