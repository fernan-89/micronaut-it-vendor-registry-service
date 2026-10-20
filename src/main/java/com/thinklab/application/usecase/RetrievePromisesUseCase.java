package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Promise;
import com.thinklab.domain.repository.AgreementRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Use Case for the promises of the tenant's agreements, judged today (BIAN Behavior Qualifier: {@code promises/retrieve}, platform ADR-001): an agreement about to
 * end or ended is broken, one in force is kept, one not started or paused is not judged. The name is the contract's own reference. Staff only.
 */
@Singleton
public class RetrievePromisesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrievePromisesUseCase.class);

    private final AgreementRepository repository;
    private final Clock clock;

    public RetrievePromisesUseCase(AgreementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<List<PromiseResponse>> execute(UUID organisationId, Integer changedWithinDays, Integer limit, String role) {
        log.info("[USE CASE] Retrieving the promises of the Agreements of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "list the promises of agreements"))
                .then(Mono.fromCallable(() -> new int[]{PromiseSelection.days(changedWithinDays), PromiseSelection.limit(limit)}))
                .flatMap(window -> {
                    Instant now = Instant.now(clock);
                    LocalDate today = LocalDate.now(clock);
                    return repository.findAll(organisationId).map(agreement -> toResponse(agreement, today)).collectList()
                            .map(all -> PromiseSelection.select(all, now.minus(Duration.ofDays(window[0])), window[1]));
                });
    }

    private static PromiseResponse toResponse(Agreement agreement, LocalDate today) {
        Promise promise = agreement.promise(today);
        return new PromiseResponse(agreement.getId(), agreement.getReference(), agreement.getAssetId(), promise.verdict().name(), promise.code(), agreement.getStatus().name(),
                agreement.getUpdatedAt());
    }
}
