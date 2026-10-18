package com.thinklab.application.usecase;

import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for pausing and resuming an agreement (BIAN Behavior Qualifier: {@code agreement/control/*}). */
@Singleton
public class ControlAgreementUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlAgreementUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        PAUSE {
            @Override AgreementAuditEntry apply(Agreement agreement, String executor) { return agreement.pause(executor); }
        },
        RESUME {
            @Override AgreementAuditEntry apply(Agreement agreement, String executor) { return agreement.resume(executor); }
        };

        abstract AgreementAuditEntry apply(Agreement agreement, String executor);
    }

    private final AgreementWorkflow workflow;

    public ControlAgreementUseCase(AgreementWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on Agreement ID: {}", action, id);
        return workflow.apply(id, organisationId, role, "pause or resume an agreement", agreement -> action.apply(agreement, executor));
    }
}
