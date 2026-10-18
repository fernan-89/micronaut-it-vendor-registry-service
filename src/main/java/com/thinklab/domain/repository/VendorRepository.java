package com.thinklab.domain.repository;

import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import com.thinklab.domain.model.Vendor.VendorStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for Vendor persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface VendorRepository {

    /** Inserts the vendor. A name already used in the organisation is {@link com.thinklab.domain.exception.DuplicateVendorException}: the unique index is the atomic backstop. */
    Mono<Vendor> create(Vendor vendor);

    Mono<Vendor> findById(UUID id, UUID organisationId);

    /** The tenant's vendors, oldest first. */
    Flux<Vendor> findAll(UUID organisationId);

    Mono<Void> save(Vendor vendor, VendorStatus expectedStatus, VendorAuditEntry auditEntry);
}
