package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateVendorRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing a vendor (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateVendorUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateVendorUseCase.class);

    private final VendorWorkflow workflow;

    public UpdateVendorUseCase(VendorWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateVendorRequest request, String executor, String role) {
        log.info("[USE CASE] Updating Vendor ID: {}", id);
        return workflow.apply(id, organisationId, role, "change a vendor", vendor -> vendor.update(request.name(), request.category(), executor));
    }
}
