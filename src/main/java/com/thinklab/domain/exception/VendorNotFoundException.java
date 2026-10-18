package com.thinklab.domain.exception;

/** Domain Exception: no vendor with that id in the tenant (another tenant's answers the same). RFC 7807 mapping: HTTP 404 Not Found. */
public class VendorNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00404";

    public VendorNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
