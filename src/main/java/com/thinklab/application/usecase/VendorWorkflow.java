package com.thinklab.application.usecase;

import com.thinklab.domain.exception.VendorNotFoundException;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to a vendor shares: staff only, load (a 404 if it is not the tenant's), apply, save with the status loaded. */
@Singleton
public class VendorWorkflow {

    private final VendorRepository repository;

    public VendorWorkflow(VendorRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<Vendor, VendorAuditEntry> action) {
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new VendorNotFoundException("Vendor " + id + " not found.")))
                .flatMap(vendor -> {
                    var statusBefore = vendor.getStatus();
                    VendorAuditEntry entry = action.apply(vendor);
                    return repository.save(vendor, statusBefore, entry);
                });
    }
}
