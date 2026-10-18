package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use the vendor registry (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class VendorAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00403";

    public VendorAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
