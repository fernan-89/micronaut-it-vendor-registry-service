package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Assessment;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Agreement.Standing;
import com.thinklab.domain.repository.AgreementRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use Case for listing the tenant's agreements, oldest first, judged today, filterable by status, vendor, asset, kind and standing (BIAN Behavior
 * Qualifier: {@code agreement/retrieve}, collection). The standing is judged when read, so it is filtered in memory over the tenant's agreements
 * (ADR-031); the order is kept.
 */
@Singleton
public class RetrieveAgreementsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveAgreementsUseCase.class);

    private final AgreementRepository repository;
    private final Clock clock;

    public RetrieveAgreementsUseCase(AgreementRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Flux<AgreementResponse> execute(UUID organisationId, AgreementStatus status, UUID vendorId, UUID assetId, Kind kind, Standing standing, String role) {
        log.info("[USE CASE] Retrieving the Agreements of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "list agreements"))
                .thenMany(Flux.defer(() -> {
                    LocalDate today = LocalDate.now(clock);
                    return repository.findAll(organisationId)
                            .filter(agreement -> status == null || agreement.getStatus() == status)
                            .filter(agreement -> vendorId == null || vendorId.equals(agreement.getVendorId()))
                            .filter(agreement -> assetId == null || assetId.equals(agreement.getAssetId()))
                            .filter(agreement -> kind == null || agreement.getKind() == kind)
                            .map(agreement -> new Judged(agreement, agreement.assess(today)))
                            .filter(judged -> standing == null || judged.assessment().standing() == standing)
                            .map(judged -> VendorMapper.toResponse(judged.agreement(), judged.assessment()));
                }));
    }

    private record Judged(Agreement agreement, Assessment assessment) {}
}
