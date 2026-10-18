package com.thinklab.application.usecase;

import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for deactivating and activating a vendor (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlVendorUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlVendorUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        DEACTIVATE {
            @Override VendorAuditEntry apply(Vendor vendor, String executor) { return vendor.deactivate(executor); }
        },
        ACTIVATE {
            @Override VendorAuditEntry apply(Vendor vendor, String executor) { return vendor.activate(executor); }
        };

        abstract VendorAuditEntry apply(Vendor vendor, String executor);
    }

    private final VendorWorkflow workflow;

    public ControlVendorUseCase(VendorWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on Vendor ID: {}", action, id);
        return workflow.apply(id, organisationId, role, "deactivate or activate a vendor", vendor -> action.apply(vendor, executor));
    }
}
