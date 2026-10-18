package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateAgreementRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for changing an agreement (BIAN Behavior Qualifier: {@code agreement/update}); a renewal is a new last day. */
@Singleton
public class UpdateAgreementUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateAgreementUseCase.class);

    private final AgreementWorkflow workflow;

    public UpdateAgreementUseCase(AgreementWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateAgreementRequest request, String executor, String role) {
        log.info("[USE CASE] Updating Agreement ID: {}", id);
        return workflow.apply(id, organisationId, role, "change an agreement",
                agreement -> agreement.update(request.kind(), request.assetId(), request.startsOn(), request.endsOn(), request.noticeDays(), request.autoRenew(), executor));
    }
}
