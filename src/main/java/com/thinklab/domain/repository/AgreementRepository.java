package com.thinklab.domain.repository;

import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Outbound Port for Agreement persistence. {@link #save} is a guarded write on the status loaded (ADR-033); every lookup is tenant-scoped. */
public interface AgreementRepository {

    /** Inserts the agreement. A reference already used for that vendor is {@link com.thinklab.domain.exception.DuplicateAgreementException}: the unique index is the atomic backstop. */
    Mono<Agreement> create(Agreement agreement);

    Mono<Agreement> findById(UUID id, UUID organisationId);

    /** The tenant's agreements, oldest first. */
    Flux<Agreement> findAll(UUID organisationId);

    Mono<Void> save(Agreement agreement, AgreementStatus expectedStatus, AgreementAuditEntry auditEntry);
}
