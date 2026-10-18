package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.application.mapper.VendorMapper;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.repository.VendorRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for listing the tenant's vendors, oldest first, filterable by status and category (BIAN Behavior Qualifier: {@code retrieve}, collection). */
@Singleton
public class RetrieveVendorsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveVendorsUseCase.class);

    private final VendorRepository repository;

    public RetrieveVendorsUseCase(VendorRepository repository) {
        this.repository = repository;
    }

    public Flux<VendorResponse> execute(UUID organisationId, VendorStatus status, Category category, String role) {
        log.info("[USE CASE] Retrieving the Vendors of organisation: {}", organisationId);
        return Mono.fromRunnable(() -> VendorAccess.requireStaff(role, "list vendors"))
                .thenMany(Flux.defer(() -> repository.findAll(organisationId)
                        .filter(vendor -> status == null || vendor.getStatus() == status)
                        .filter(vendor -> category == null || vendor.getCategory() == category)
                        .map(VendorMapper::toResponse)));
    }
}
