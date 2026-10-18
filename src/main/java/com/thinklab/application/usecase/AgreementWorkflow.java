package com.thinklab.application.usecase;

import com.thinklab.domain.exception.AgreementNotFoundException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.repository.AgreementRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** The shape every staff change to an agreement shares: staff only, load (a 404 if it is not the tenant's), apply, save with the status loaded. */
@Singleton
public class AgreementWorkflow {

    private final AgreementRepository repository;

    public AgreementWorkflow(AgreementRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<Agreement, AgreementAuditEntry> action) {
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, operation))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new AgreementNotFoundException("Agreement " + id + " not found.")))
                .flatMap(agreement -> {
                    var statusBefore = agreement.getStatus();
                    AgreementAuditEntry entry = action.apply(agreement);
                    return repository.save(agreement, statusBefore, entry);
                });
    }
}
