package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateVendorRequest;
import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for registering a vendor (BIAN Behavior Qualifier: {@code initiate}). Staff only. */
@Singleton
public class InitiateVendorUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateVendorUseCase.class);

    private final HashServicePort hashServicePort;
    private final VendorRepository repository;

    public InitiateVendorUseCase(HashServicePort hashServicePort, VendorRepository repository) {
        this.hashServicePort = hashServicePort;
        this.repository = repository;
    }

    public Mono<VendorResponse> execute(UUID organisationId, InitiateVendorRequest request, String executor, String role) {
        log.info("[USE CASE] Creating the Vendor [{}] for organisation: {}", request.name(), organisationId);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "register a vendor"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("vendor-creation")))
                .map(id -> Vendor.createNew(id, organisationId, request.name(), request.category(), executor))
                .flatMap(repository::create)
                .map(VendorMapper::toResponse);
    }
}
