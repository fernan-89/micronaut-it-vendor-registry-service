package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.exception.VendorNotFoundException;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for reading one vendor of the tenant (BIAN Behavior Qualifier: {@code retrieve}). Staff only; another tenant is a 404. */
@Singleton
public class RetrieveVendorUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveVendorUseCase.class);

    private final VendorRepository repository;

    public RetrieveVendorUseCase(VendorRepository repository) {
        this.repository = repository;
    }

    public Mono<VendorResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving Vendor ID: {}", id);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "read a vendor"))
                .then(Mono.defer(() -> repository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new VendorNotFoundException("Vendor " + id + " not found.")))
                .map(VendorMapper::toResponse);
    }
}
