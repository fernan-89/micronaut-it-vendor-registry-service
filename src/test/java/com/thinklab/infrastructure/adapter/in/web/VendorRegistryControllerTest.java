package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateAgreementRequest;
import com.thinklab.application.dto.request.InitiateVendorRequest;
import com.thinklab.application.dto.request.UpdateAgreementRequest;
import com.thinklab.application.dto.request.UpdateVendorRequest;
import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.application.usecase.ControlAgreementUseCase;
import com.thinklab.application.usecase.ControlVendorUseCase;
import com.thinklab.application.usecase.InitiateAgreementUseCase;
import com.thinklab.application.usecase.InitiateVendorUseCase;
import com.thinklab.application.usecase.RetrieveAgreementAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveAgreementUseCase;
import com.thinklab.application.usecase.RetrieveAgreementsUseCase;
import com.thinklab.application.usecase.RetrieveVendorAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveVendorUseCase;
import com.thinklab.application.usecase.RetrieveVendorsUseCase;
import com.thinklab.application.usecase.UpdateAgreementUseCase;
import com.thinklab.application.usecase.UpdateVendorUseCase;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Agreement.Standing;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorStatus;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant, executor and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class VendorRegistryControllerTest {

    private static final String EXECUTOR = "op-1";
    private static final String ROLE = "ENGINEER";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");

    @Mock private InitiateVendorUseCase initiateVendor;
    @Mock private RetrieveVendorUseCase retrieveVendor;
    @Mock private RetrieveVendorsUseCase retrieveVendors;
    @Mock private UpdateVendorUseCase updateVendor;
    @Mock private ControlVendorUseCase controlVendor;
    @Mock private RetrieveVendorAuditLogUseCase vendorAuditLog;
    @Mock private InitiateAgreementUseCase initiateAgreement;
    @Mock private RetrieveAgreementUseCase retrieveAgreement;
    @Mock private RetrieveAgreementsUseCase retrieveAgreements;
    @Mock private UpdateAgreementUseCase updateAgreement;
    @Mock private ControlAgreementUseCase controlAgreement;
    @Mock private RetrieveAgreementAuditLogUseCase agreementAuditLog;

    private VendorRegistryController controller() {
        return new VendorRegistryController(initiateVendor, retrieveVendor, retrieveVendors, updateVendor, controlVendor, vendorAuditLog, initiateAgreement, retrieveAgreement,
                retrieveAgreements, updateAgreement, controlAgreement, agreementAuditLog);
    }

    private VendorResponse vendorResponse() {
        return new VendorResponse(id, tenant, "Dell", "HARDWARE", "ACTIVE", now, now);
    }

    private AgreementResponse agreementResponse() {
        return new AgreementResponse(id, tenant, UUID.randomUUID(), "WARRANTY", "WTY-1", null, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true, "ACTIVE",
                "IN_FORCE", 100L, now, now);
    }

    @Test
    @DisplayName("initiate a vendor answers 201 with the vendor")
    void initiateVendorRoute() {
        InitiateVendorRequest request = new InitiateVendorRequest("Dell", Category.HARDWARE);
        when(initiateVendor.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(vendorResponse()));

        StepVerifier.create(controller().initiate(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve a vendor and the vendor list answer the use cases' result with the filters")
    void retrieveVendorRoutes() {
        when(retrieveVendor.execute(id, tenant, ROLE)).thenReturn(Mono.just(vendorResponse()));
        when(retrieveVendors.execute(tenant, VendorStatus.ACTIVE, Category.HARDWARE, ROLE)).thenReturn(Flux.just(vendorResponse()));

        StepVerifier.create(controller().retrieve(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAll(tenantHeader, ROLE, VendorStatus.ACTIVE, Category.HARDWARE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, deactivate and activate a vendor answer 204 and pass the right action")
    void vendorChanges() {
        UpdateVendorRequest request = new UpdateVendorRequest("Dell EMC", Category.SERVICES);
        when(updateVendor.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlVendor.execute(id, tenant, ControlVendorUseCase.Action.DEACTIVATE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlVendor.execute(id, tenant, ControlVendorUseCase.Action.ACTIVATE, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().update(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().deactivate(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().activate(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("the audit logs answer the trails")
    void auditLogs() {
        List<AuditEntryResponse> trail = List.of(new AuditEntryResponse(now, "INITIATED", EXECUTOR, null, "ACTIVE", null));
        when(vendorAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(trail));
        when(agreementAuditLog.execute(id, tenant, ROLE)).thenReturn(Mono.just(trail));

        StepVerifier.create(controller().auditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        StepVerifier.create(controller().agreementAuditLog(id, tenantHeader, ROLE)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("initiate an agreement answers 201 with the agreement")
    void initiateAgreementRoute() {
        InitiateAgreementRequest request = new InitiateAgreementRequest(UUID.randomUUID(), Kind.WARRANTY, "WTY-1", null, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true);
        when(initiateAgreement.execute(tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.just(agreementResponse()));

        StepVerifier.create(controller().initiateAgreement(tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve an agreement and the agreement list answer the use cases' result with the filters")
    void retrieveAgreementRoutes() {
        UUID vendorId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(retrieveAgreement.execute(id, tenant, ROLE)).thenReturn(Mono.just(agreementResponse()));
        when(retrieveAgreements.execute(tenant, AgreementStatus.ACTIVE, vendorId, assetId, Kind.WARRANTY, Standing.EXPIRING, ROLE)).thenReturn(Flux.just(agreementResponse()));

        StepVerifier.create(controller().retrieveAgreement(id, tenantHeader, ROLE)).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().retrieveAgreements(tenantHeader, ROLE, AgreementStatus.ACTIVE, vendorId, assetId, Kind.WARRANTY, Standing.EXPIRING))
                .assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }

    @Test
    @DisplayName("update, pause and resume an agreement answer 204 and pass the right action")
    void agreementChanges() {
        UpdateAgreementRequest request = new UpdateAgreementRequest(Kind.SUPPORT, null, LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31"), 45, false);
        when(updateAgreement.execute(id, tenant, request, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlAgreement.execute(id, tenant, ControlAgreementUseCase.Action.PAUSE, EXECUTOR, ROLE)).thenReturn(Mono.empty());
        when(controlAgreement.execute(id, tenant, ControlAgreementUseCase.Action.RESUME, EXECUTOR, ROLE)).thenReturn(Mono.empty());

        StepVerifier.create(controller().updateAgreement(id, tenantHeader, EXECUTOR, ROLE, request)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().pauseAgreement(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller().resumeAgreement(id, tenantHeader, EXECUTOR, ROLE)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
    }
}
