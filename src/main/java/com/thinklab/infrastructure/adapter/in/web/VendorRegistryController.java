package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateAgreementRequest;
import com.thinklab.application.dto.request.InitiateVendorRequest;
import com.thinklab.application.dto.request.UpdateAgreementRequest;
import com.thinklab.application.dto.request.UpdateVendorRequest;
import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.application.usecase.RetrievePromisesUseCase;
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-vendor-registry} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Vendor} is the Control Record, at the root;
 * {@link com.thinklab.domain.model.Agreement} is the secondary aggregate, under {@code /agreement}: what a vendor owes, judged when read. There is
 * no {@code DELETE}: a vendor is deactivated and an agreement is paused.
 *
 * <p><b>Tenant on every route, staff only (ADR-032):</b> {@code X-Tenant-Id} scopes every lookup (another tenant's vendor is a 404) and
 * {@code X-Role} makes every route refuse a {@code REQUESTER} with 403 {@code ERR-VND-00403}.
 */
@Controller("/it-vendor-registry/v1")
public class VendorRegistryController {

    private static final Logger log = LoggerFactory.getLogger(VendorRegistryController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateVendorUseCase initiateVendor;
    private final RetrieveVendorUseCase retrieveVendor;
    private final RetrieveVendorsUseCase retrieveVendors;
    private final UpdateVendorUseCase updateVendor;
    private final ControlVendorUseCase controlVendor;
    private final RetrieveVendorAuditLogUseCase vendorAuditLog;
    private final InitiateAgreementUseCase initiateAgreement;
    private final RetrieveAgreementUseCase retrieveAgreement;
    private final RetrieveAgreementsUseCase retrieveAgreements;
    private final UpdateAgreementUseCase updateAgreement;
    private final ControlAgreementUseCase controlAgreement;
    private final RetrieveAgreementAuditLogUseCase agreementAuditLog;

    private final RetrievePromisesUseCase retrievePromises;

    public VendorRegistryController(InitiateVendorUseCase initiateVendor, RetrieveVendorUseCase retrieveVendor, RetrieveVendorsUseCase retrieveVendors,
                                    UpdateVendorUseCase updateVendor, ControlVendorUseCase controlVendor, RetrieveVendorAuditLogUseCase vendorAuditLog,
                                    InitiateAgreementUseCase initiateAgreement, RetrieveAgreementUseCase retrieveAgreement, RetrieveAgreementsUseCase retrieveAgreements,
                                    UpdateAgreementUseCase updateAgreement, ControlAgreementUseCase controlAgreement, RetrieveAgreementAuditLogUseCase agreementAuditLog,
            RetrievePromisesUseCase retrievePromises) {
        this.retrievePromises = retrievePromises;
        this.initiateVendor = initiateVendor;
        this.retrieveVendor = retrieveVendor;
        this.retrieveVendors = retrieveVendors;
        this.updateVendor = updateVendor;
        this.controlVendor = controlVendor;
        this.vendorAuditLog = vendorAuditLog;
        this.initiateAgreement = initiateAgreement;
        this.retrieveAgreement = retrieveAgreement;
        this.retrieveAgreements = retrieveAgreements;
        this.updateAgreement = updateAgreement;
        this.controlAgreement = controlAgreement;
        this.agreementAuditLog = agreementAuditLog;
    }

    // ------------------------------------------------------------------ Vendors

    /** Behavior Qualifier: {@code initiate}. Registers a vendor. */
    @Post("/initiate")
    public Mono<HttpResponse<VendorResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateVendorRequest request
    ) {
        log.info("[ACTION: INITIATE_VENDOR] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);
        return Mono.defer(() -> initiateVendor.execute(UUID.fromString(tenantId), request, executor, role)).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. One vendor of the tenant. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<VendorResponse>> retrieve(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveVendor.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection), oldest first, filterable by status and category. */
    @Get("/retrieve")
    public Mono<List<VendorResponse>> retrieveAll(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                  @QueryValue @Nullable VendorStatus status, @QueryValue @Nullable Category category) {
        return Mono.defer(() -> retrieveVendors.execute(UUID.fromString(tenantId), status, category, role).collectList());
    }

    /**
     * Behavior Qualifier: {@code promises/retrieve} (platform ADR-001, the promises contract). Every broken promise and every one that changed within the days asked
     * (30 unless asked, 1 to 365), at most {@code limit} of each (500 unless asked, at most 1000).
     */
    @Get("/promises/retrieve")
    public Mono<List<PromiseResponse>> promises(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                @QueryValue @Nullable Integer changedWithinDays, @QueryValue @Nullable Integer limit) {
        return Mono.defer(() -> retrievePromises.execute(UUID.fromString(tenantId), changedWithinDays, limit, role));
    }

    /** Behavior Qualifier: {@code update}. The whole definition again. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateVendorRequest request
    ) {
        return Mono.defer(() -> updateVendor.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/deactivate}. ACTIVE -&gt; INACTIVE: no new agreement can be made with the vendor. */
    @Put("/{id}/control/deactivate")
    public Mono<HttpResponse<Void>> deactivate(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                               @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return controlVendor(id, tenantId, ControlVendorUseCase.Action.DEACTIVATE, executor, role);
    }

    /** Behavior Qualifier: {@code control/activate}. INACTIVE -&gt; ACTIVE. */
    @Put("/{id}/control/activate")
    public Mono<HttpResponse<Void>> activate(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                             @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return controlVendor(id, tenantId, ControlVendorUseCase.Action.ACTIVATE, executor, role);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> auditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> vendorAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    // ------------------------------------------------------------------ Agreements

    /** Behavior Qualifier: {@code agreement/initiate}. What a vendor owes, from the first to the last day. */
    @Post("/agreement/initiate")
    public Mono<HttpResponse<AgreementResponse>> initiateAgreement(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid InitiateAgreementRequest request
    ) {
        log.info("[ACTION: INITIATE_AGREEMENT] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);
        return Mono.defer(() -> initiateAgreement.execute(UUID.fromString(tenantId), request, executor, role)).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code agreement/retrieve}. One agreement of the tenant, judged today. */
    @Get("/agreement/{id}/retrieve")
    public Mono<HttpResponse<AgreementResponse>> retrieveAgreement(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveAgreement.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code agreement/retrieve} (collection), oldest first, filterable by status, vendor, asset, kind and standing. */
    @Get("/agreement/retrieve")
    public Mono<List<AgreementResponse>> retrieveAgreements(@Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role,
                                                            @QueryValue @Nullable AgreementStatus status, @QueryValue @Nullable UUID vendorId, @QueryValue @Nullable UUID assetId,
                                                            @QueryValue @Nullable Kind kind, @QueryValue @Nullable Standing standing) {
        return Mono.defer(() -> retrieveAgreements.execute(UUID.fromString(tenantId), status, vendorId, assetId, kind, standing, role).collectList());
    }

    /** Behavior Qualifier: {@code agreement/update}. Everything but the vendor and the reference. */
    @Put("/agreement/{id}/update")
    public Mono<HttpResponse<Void>> updateAgreement(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateAgreementRequest request
    ) {
        return Mono.defer(() -> updateAgreement.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code agreement/control/pause}. ACTIVE -&gt; PAUSED: the agreement is no longer judged. */
    @Put("/agreement/{id}/control/pause")
    public Mono<HttpResponse<Void>> pauseAgreement(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                   @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return controlAgreement(id, tenantId, ControlAgreementUseCase.Action.PAUSE, executor, role);
    }

    /** Behavior Qualifier: {@code agreement/control/resume}. PAUSED -&gt; ACTIVE. */
    @Put("/agreement/{id}/control/resume")
    public Mono<HttpResponse<Void>> resumeAgreement(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                    @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return controlAgreement(id, tenantId, ControlAgreementUseCase.Action.RESUME, executor, role);
    }

    /** Behavior Qualifier: {@code agreement/audit-log/retrieve}. */
    @Get("/agreement/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> agreementAuditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> agreementAuditLog.execute(id, UUID.fromString(tenantId), role));
    }

    private Mono<HttpResponse<Void>> controlVendor(UUID id, String tenantId, ControlVendorUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_VENDOR] [EXECUTOR: {}] for ID: {}", action, executor, id);
        return Mono.defer(() -> controlVendor.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }

    private Mono<HttpResponse<Void>> controlAgreement(UUID id, String tenantId, ControlAgreementUseCase.Action action, String executor, String role) {
        log.info("[ACTION: {}_AGREEMENT] [EXECUTOR: {}] for ID: {}", action, executor, id);
        return Mono.defer(() -> controlAgreement.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
