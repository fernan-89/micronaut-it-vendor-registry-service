package com.thinklab.application.usecase;

import com.thinklab.domain.exception.VendorAccessDeniedException;

/** The vendor registry is for IT staff (ADR-032): a REQUESTER is refused everywhere. */
final class VendorAccess {

    static final String REQUESTER_ROLE = "REQUESTER";

    private VendorAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new VendorAccessDeniedException("A requester cannot " + operation + ": vendors and agreements are handled by IT staff.");
        }
    }
}
