package com.thinklab.domain.exception;

/** Domain Exception: an illegal Agreement lifecycle transition, or the agreement changed while the write was applied. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidAgreementStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00409";

    public InvalidAgreementStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
