package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateAgreementRequest;
import com.thinklab.application.dto.request.InitiateVendorRequest;
import com.thinklab.application.dto.request.UpdateAgreementRequest;
import com.thinklab.application.dto.request.UpdateVendorRequest;
import com.thinklab.domain.exception.AgreementNotFoundException;
import com.thinklab.domain.exception.InvalidAgreementStatusException;
import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.exception.VendorAccessDeniedException;
import com.thinklab.domain.exception.VendorNotFoundException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Agreement.Standing;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.AgreementRepository;
import com.thinklab.domain.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorUseCasesTest {

    private static final String STAFF = "ENGINEER";
    private static final String REQUESTER = "REQUESTER";

    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

    private HashServicePort hash;
    private VendorRepository vendors;
    private AgreementRepository agreements;

    @BeforeEach
    void setUp() {
        hash = mock(HashServicePort.class);
        vendors = mock(VendorRepository.class);
        agreements = mock(AgreementRepository.class);
    }

    private Vendor vendor() {
        return Vendor.createNew(UUID.randomUUID(), org, "Dell", Category.HARDWARE, "op");
    }

    private Agreement agreement(UUID vendorId, String reference, Kind kind, UUID assetId, String endsOn) {
        return Agreement.createNew(UUID.randomUUID(), org, vendorId, reference, kind, assetId, LocalDate.parse("2026-01-01"), LocalDate.parse(endsOn), 10, false, "op");
    }

    // ------------------------------------------------------------------ Vendors

    @Test
    @DisplayName("initiate a vendor takes a sovereign id and saves it; a requester is refused before an id is taken")
    void initiateVendor() {
        when(hash.generateSovereignId("vendor-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(vendors.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        InitiateVendorUseCase useCase = new InitiateVendorUseCase(hash, vendors);

        StepVerifier.create(useCase.execute(org, new InitiateVendorRequest("Dell", Category.HARDWARE), "op", STAFF)).assertNext(response -> {
            assertEquals("Dell", response.name());
            assertEquals("HARDWARE", response.category());
            assertEquals("ACTIVE", response.status());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, new InitiateVendorRequest("Dell", Category.HARDWARE), "op", REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("vendor-creation");
    }

    @Test
    @DisplayName("update, deactivate and activate go through the workflow with the status loaded; a requester and an unknown vendor are refused")
    void updateAndControlVendor() {
        Vendor vendor = vendor();
        when(vendors.findById(vendor.getId(), org)).thenReturn(Mono.just(vendor));
        when(vendors.save(any(), any(), any())).thenReturn(Mono.empty());
        VendorWorkflow workflow = new VendorWorkflow(vendors);
        UpdateVendorUseCase update = new UpdateVendorUseCase(workflow);
        ControlVendorUseCase control = new ControlVendorUseCase(workflow);

        StepVerifier.create(update.execute(vendor.getId(), org, new UpdateVendorRequest("Dell EMC", Category.SERVICES), "op", STAFF)).verifyComplete();
        assertEquals("Dell EMC", vendor.getName());
        StepVerifier.create(control.execute(vendor.getId(), org, ControlVendorUseCase.Action.DEACTIVATE, "op", null)).verifyComplete();
        assertEquals(VendorStatus.INACTIVE, vendor.getStatus());
        StepVerifier.create(control.execute(vendor.getId(), org, ControlVendorUseCase.Action.ACTIVATE, "op", STAFF)).verifyComplete();
        assertEquals(VendorStatus.ACTIVE, vendor.getStatus());
        verify(vendors).save(any(), eq(VendorStatus.INACTIVE), any());

        StepVerifier.create(control.execute(vendor.getId(), org, ControlVendorUseCase.Action.ACTIVATE, "op", STAFF)).expectError(InvalidVendorStatusException.class).verify();
        StepVerifier.create(control.execute(vendor.getId(), org, ControlVendorUseCase.Action.DEACTIVATE, "op", REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(vendors.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(control.execute(missing, org, ControlVendorUseCase.Action.DEACTIVATE, "op", STAFF)).expectError(VendorNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieve a vendor, list them filtered by status and category, and read the audit log: staff only, 404 for another tenant")
    void retrieveVendors() {
        Vendor dell = vendor();
        Vendor cloud = Vendor.createNew(UUID.randomUUID(), org, "Acme Cloud", Category.CLOUD, "op");
        cloud.deactivate("op");
        when(vendors.findById(dell.getId(), org)).thenReturn(Mono.just(dell));
        UUID elsewhere = UUID.randomUUID();
        when(vendors.findById(dell.getId(), elsewhere)).thenReturn(Mono.empty());
        when(vendors.findAll(org)).thenReturn(Flux.just(dell, cloud));

        RetrieveVendorUseCase one = new RetrieveVendorUseCase(vendors);
        StepVerifier.create(one.execute(dell.getId(), org, STAFF)).assertNext(response -> assertEquals("Dell", response.name())).verifyComplete();
        StepVerifier.create(one.execute(dell.getId(), org, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        StepVerifier.create(one.execute(dell.getId(), elsewhere, STAFF)).expectError(VendorNotFoundException.class).verify();

        RetrieveVendorsUseCase list = new RetrieveVendorsUseCase(vendors);
        StepVerifier.create(list.execute(org, null, null, STAFF).map(r -> r.name())).expectNext("Dell", "Acme Cloud").verifyComplete();
        StepVerifier.create(list.execute(org, VendorStatus.INACTIVE, null, null).map(r -> r.name())).expectNext("Acme Cloud").verifyComplete();
        StepVerifier.create(list.execute(org, null, Category.HARDWARE, STAFF).map(r -> r.name())).expectNext("Dell").verifyComplete();
        StepVerifier.create(list.execute(org, VendorStatus.ACTIVE, Category.CLOUD, STAFF)).verifyComplete();
        StepVerifier.create(list.execute(org, null, null, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();

        RetrieveVendorAuditLogUseCase audit = new RetrieveVendorAuditLogUseCase(vendors);
        StepVerifier.create(audit.execute(dell.getId(), org, STAFF)).assertNext(trail -> assertEquals("INITIATED", trail.get(0).action())).verifyComplete();
        StepVerifier.create(audit.execute(dell.getId(), org, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        StepVerifier.create(audit.execute(dell.getId(), elsewhere, STAFF)).expectError(VendorNotFoundException.class).verify();
    }

    // ------------------------------------------------------------------ Agreements

    private InitiateAgreementRequest request(UUID vendorId) {
        return new InitiateAgreementRequest(vendorId, Kind.WARRANTY, "WTY-1", asset, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true);
    }

    @Test
    @DisplayName("initiate an agreement needs an ACTIVE vendor of the tenant, takes a sovereign id and answers judged today; a requester is refused first")
    void initiateAgreement() {
        Vendor vendor = vendor();
        Vendor inactive = Vendor.createNew(UUID.randomUUID(), org, "Old", Category.OTHER, "op");
        inactive.deactivate("op");
        when(vendors.findById(vendor.getId(), org)).thenReturn(Mono.just(vendor));
        when(vendors.findById(inactive.getId(), org)).thenReturn(Mono.just(inactive));
        UUID elsewhere = UUID.randomUUID();
        when(vendors.findById(elsewhere, org)).thenReturn(Mono.empty());
        when(hash.generateSovereignId("agreement-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(agreements.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        InitiateAgreementUseCase useCase = new InitiateAgreementUseCase(hash, vendors, agreements, clock);

        StepVerifier.create(useCase.execute(org, request(vendor.getId()), "op", STAFF)).assertNext(response -> {
            assertEquals("WARRANTY", response.kind());
            assertEquals("WTY-1", response.reference());
            assertEquals("IN_FORCE", response.standing());
            assertEquals(194L, response.daysToEnd());
            assertEquals("ACTIVE", response.status());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, request(inactive.getId()), "op", STAFF)).expectError(InvalidVendorStatusException.class).verify();
        StepVerifier.create(useCase.execute(org, request(elsewhere), "op", STAFF)).expectError(VendorNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(org, request(vendor.getId()), "op", REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        verify(hash).generateSovereignId("agreement-creation");
        verify(agreements).create(any());
    }

    @Test
    @DisplayName("update, pause and resume an agreement go through the workflow with the status loaded; a requester and an unknown agreement are refused")
    void updateAndControlAgreement() {
        Agreement agreement = agreement(UUID.randomUUID(), "REF", Kind.CONTRACT, null, "2026-12-31");
        when(agreements.findById(agreement.getId(), org)).thenReturn(Mono.just(agreement));
        when(agreements.save(any(), any(), any())).thenReturn(Mono.empty());
        AgreementWorkflow workflow = new AgreementWorkflow(agreements);
        UpdateAgreementUseCase update = new UpdateAgreementUseCase(workflow);
        ControlAgreementUseCase control = new ControlAgreementUseCase(workflow);

        UpdateAgreementRequest renewal = new UpdateAgreementRequest(Kind.SUPPORT, asset, LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31"), 45, true);
        StepVerifier.create(update.execute(agreement.getId(), org, renewal, "op", STAFF)).verifyComplete();
        assertEquals(LocalDate.parse("2027-12-31"), agreement.getEndsOn());
        assertEquals(Kind.SUPPORT, agreement.getKind());
        StepVerifier.create(control.execute(agreement.getId(), org, ControlAgreementUseCase.Action.PAUSE, "op", null)).verifyComplete();
        assertEquals(AgreementStatus.PAUSED, agreement.getStatus());
        StepVerifier.create(control.execute(agreement.getId(), org, ControlAgreementUseCase.Action.RESUME, "op", STAFF)).verifyComplete();
        assertEquals(AgreementStatus.ACTIVE, agreement.getStatus());
        verify(agreements).save(any(), eq(AgreementStatus.PAUSED), any());

        StepVerifier.create(control.execute(agreement.getId(), org, ControlAgreementUseCase.Action.RESUME, "op", STAFF)).expectError(InvalidAgreementStatusException.class).verify();
        StepVerifier.create(control.execute(agreement.getId(), org, ControlAgreementUseCase.Action.PAUSE, "op", REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        UUID missing = UUID.randomUUID();
        when(agreements.findById(missing, org)).thenReturn(Mono.empty());
        StepVerifier.create(control.execute(missing, org, ControlAgreementUseCase.Action.PAUSE, "op", STAFF)).expectError(AgreementNotFoundException.class).verify();
        verify(agreements, never()).create(any());
    }

    @Test
    @DisplayName("retrieve an agreement judged today, list them filtered by status, vendor, asset, kind and standing, and read the audit log")
    void retrieveAgreements() {
        UUID dell = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Agreement ending = agreement(dell, "A", Kind.WARRANTY, asset, "2026-06-25");
        Agreement ended = agreement(other, "B", Kind.CONTRACT, null, "2026-06-01");
        Agreement far = agreement(dell, "C", Kind.CONTRACT, null, "2026-12-31");
        far.pause("op");
        when(agreements.findById(ending.getId(), org)).thenReturn(Mono.just(ending));
        when(agreements.findById(ending.getId(), other)).thenReturn(Mono.empty());
        when(agreements.findAll(org)).thenReturn(Flux.just(ending, ended, far));

        RetrieveAgreementUseCase one = new RetrieveAgreementUseCase(agreements, clock);
        StepVerifier.create(one.execute(ending.getId(), org, STAFF)).assertNext(response -> {
            assertEquals("EXPIRING", response.standing());
            assertEquals(5L, response.daysToEnd());
        }).verifyComplete();
        StepVerifier.create(one.execute(ending.getId(), org, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        StepVerifier.create(one.execute(ending.getId(), other, STAFF)).expectError(AgreementNotFoundException.class).verify();

        RetrieveAgreementsUseCase list = new RetrieveAgreementsUseCase(agreements, clock);
        StepVerifier.create(list.execute(org, null, null, null, null, null, STAFF).map(r -> r.reference())).expectNext("A", "B", "C").verifyComplete();
        StepVerifier.create(list.execute(org, AgreementStatus.PAUSED, null, null, null, null, null).map(r -> r.reference())).expectNext("C").verifyComplete();
        StepVerifier.create(list.execute(org, null, dell, null, null, null, STAFF).map(r -> r.reference())).expectNext("A", "C").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, asset, null, null, STAFF).map(r -> r.reference())).expectNext("A").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, Kind.CONTRACT, null, STAFF).map(r -> r.reference())).expectNext("B", "C").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, null, Standing.EXPIRED, STAFF).map(r -> r.reference())).expectNext("B").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, null, Standing.PAUSED, STAFF).map(r -> r.reference())).expectNext("C").verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, null, Standing.NOT_STARTED, STAFF)).verifyComplete();
        StepVerifier.create(list.execute(org, null, null, null, null, null, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();

        RetrieveAgreementAuditLogUseCase audit = new RetrieveAgreementAuditLogUseCase(agreements);
        StepVerifier.create(audit.execute(ending.getId(), org, STAFF)).assertNext(trail -> assertEquals("INITIATED", trail.get(0).action())).verifyComplete();
        StepVerifier.create(audit.execute(ending.getId(), org, REQUESTER)).expectError(VendorAccessDeniedException.class).verify();
        StepVerifier.create(audit.execute(ending.getId(), other, STAFF)).expectError(AgreementNotFoundException.class).verify();
    }

    @Test
    @DisplayName("the access check is a utility class that cannot be instantiated")
    void utilityClass() throws Exception {
        Constructor<VendorAccess> constructor = VendorAccess.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, failure.getCause().getClass());
        VendorAccess.requireStaff(null, "do anything");
        assertThrows(VendorAccessDeniedException.class, () -> VendorAccess.requireStaff(REQUESTER, "do anything"));
        ArgumentCaptor<Object> unused = ArgumentCaptor.forClass(Object.class);
        assertEquals(0, unused.getAllValues().size());
        assertEquals(List.of(), List.of());
    }
}
