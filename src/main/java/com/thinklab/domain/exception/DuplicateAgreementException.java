package com.thinklab.domain.exception;

/** Domain Exception: an agreement reference already used for that vendor in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateAgreementException extends BusinessException {

    private static final String ERROR_CODE = "ERR-VND-00409";

    public DuplicateAgreementException(String message) {
        super(ERROR_CODE, message);
    }
}
