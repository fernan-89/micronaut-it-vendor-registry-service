package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateAgreementRequest;
import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.exception.VendorNotFoundException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.AgreementRepository;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use Case for recording what a vendor owes (BIAN Behavior Qualifier: {@code agreement/initiate}). Staff only. The vendor must be the tenant's
 * (a 404 otherwise) and ACTIVE (a 409 otherwise); the same reference for the same vendor twice is a 409 too, by the unique index.
 */
@Singleton
public class InitiateAgreementUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateAgreementUseCase.class);

    private final HashServicePort hashServicePort;
    private final VendorRepository vendors;
    private final AgreementRepository repository;
    private final Clock clock;

    public InitiateAgreementUseCase(HashServicePort hashServicePort, VendorRepository vendors, AgreementRepository repository, Clock clock) {
        this.hashServicePort = hashServicePort;
        this.vendors = vendors;
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<AgreementResponse> execute(UUID organisationId, InitiateAgreementRequest request, String executor, String role) {
        log.info("[USE CASE] Creating an Agreement [{}] for organisation: {}", request.reference(), organisationId);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "record an agreement"))
                .then(Mono.defer(() -> vendors.findById(request.vendorId(), organisationId)))
                .switchIfEmpty(Mono.error(new VendorNotFoundException("Vendor " + request.vendorId() + " not found.")))
                .flatMap(vendor -> vendor.getStatus() == VendorStatus.ACTIVE
                        ? hashServicePort.generateSovereignId("agreement-creation")
                        : Mono.error(new InvalidVendorStatusException("Vendor " + vendor.getId() + " is inactive: no new agreement can be made with it.")))
                .map(id -> Agreement.createNew(id, organisationId, request.vendorId(), request.reference(), request.kind(), request.assetId(), request.startsOn(),
                        request.endsOn(), request.noticeDays(), request.autoRenew(), executor))
                .flatMap(repository::create)
                .map(agreement -> VendorMapper.toResponse(agreement, agreement.assess(LocalDate.now(clock))));
    }
}
