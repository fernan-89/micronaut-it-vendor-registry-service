package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.exception.AgreementNotFoundException;
import com.thinklab.domain.repository.AgreementRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of an agreement (BIAN Behavior Qualifier: {@code agreement/audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveAgreementAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveAgreementAuditLogUseCase.class);

    private final AgreementRepository repository;

    public RetrieveAgreementAuditLogUseCase(AgreementRepository repository) {
        this.repository = repository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of Agreement ID: {}", id);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "read the audit trail of an agreement"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new AgreementNotFoundException("Agreement " + id + " not found.")))
                .map(agreement -> agreement.getAuditTrail().stream().map(VendorMapper::toResponse).collect(Collectors.toList()));
    }
}
