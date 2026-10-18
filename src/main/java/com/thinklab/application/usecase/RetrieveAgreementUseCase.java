package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.exception.AgreementNotFoundException;
import com.thinklab.domain.repository.AgreementRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Use Case for reading one agreement of the tenant, judged today (BIAN Behavior Qualifier: {@code agreement/retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveAgreementUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveAgreementUseCase.class);

    private final AgreementRepository repository;
    private final Clock clock;

    public RetrieveAgreementUseCase(AgreementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<AgreementResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving Agreement ID: {}", id);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "read an agreement"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new AgreementNotFoundException("Agreement " + id + " not found.")))
                .map(agreement -> VendorMapper.toResponse(agreement, agreement.assess(LocalDate.now(clock))));
    }
}
