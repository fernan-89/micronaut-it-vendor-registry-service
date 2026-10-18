package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.exception.VendorNotFoundException;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of a vendor (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveVendorAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveVendorAuditLogUseCase.class);

    private final VendorRepository repository;

    public RetrieveVendorAuditLogUseCase(VendorRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of Vendor ID: {}", id);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "read the audit trail of a vendor"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new VendorNotFoundException("Vendor " + id + " not found.")))
                .map(vendor -> vendor.getAuditTrail().stream().map(VendorMapper::toResponse).collect(Collectors.toList()));
    }
}
