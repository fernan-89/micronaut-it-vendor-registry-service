package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateAgreementException;
import com.thinklab.domain.exception.DuplicateVendorException;
import com.thinklab.domain.exception.InvalidAgreementStatusException;
import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorStatus;
import com.thinklab.domain.repository.AgreementRepository;
import com.thinklab.domain.repository.VendorRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vendor registry persistence against a real MongoDB: the unique vendor name, the unique agreement reference per vendor, the calendar days
 * kept as written, the status-guarded saves, and the indexes created at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VendorPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "vendor_registry_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject VendorRepository vendors;
    @Inject AgreementRepository agreements;
    @Inject MongoClient mongoClient;

    private Vendor newVendor(UUID organisation, String name) {
        return Vendor.createNew(UUID.randomUUID(), organisation, name, Category.HARDWARE, "op-1");
    }

    private Agreement newAgreement(UUID organisation, UUID vendor, String reference) {
        return Agreement.createNew(UUID.randomUUID(), organisation, vendor, reference, Kind.WARRANTY, UUID.randomUUID(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true, "op-1");
    }

    @Test
    @DisplayName("a vendor is read back whole, only for its own organisation, oldest first; the name is unique per organisation")
    void vendorRoundTrip() {
        UUID organisation = UUID.randomUUID();
        Vendor first = vendors.create(newVendor(organisation, "First")).block();
        vendors.create(newVendor(organisation, "Second")).block();

        Vendor found = vendors.findById(first.getId(), organisation).block();

        assertEquals("First", found.getName());
        assertEquals(Category.HARDWARE, found.getCategory());
        assertEquals(VendorStatus.ACTIVE, found.getStatus());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(vendors.findById(first.getId(), UUID.randomUUID()).block());
        assertEquals(List.of("First", "Second"), vendors.findAll(organisation).map(Vendor::getName).collectList().block());
        assertTrue(vendors.findAll(UUID.randomUUID()).collectList().block().isEmpty());
        assertThrows(DuplicateVendorException.class, () -> vendors.create(newVendor(organisation, "First")).block());
        vendors.create(newVendor(UUID.randomUUID(), "First")).block();
    }

    @Test
    @DisplayName("a vendor change is guarded by the status loaded: the second writer from the same state loses; a rename onto a taken name loses too")
    void vendorGuard() {
        UUID organisation = UUID.randomUUID();
        Vendor created = vendors.create(newVendor(organisation, "Guarded")).block();
        vendors.create(newVendor(organisation, "Taken")).block();
        Vendor first = vendors.findById(created.getId(), organisation).block();
        Vendor second = vendors.findById(created.getId(), organisation).block();

        var off = first.deactivate("op-1");
        vendors.save(first, VendorStatus.ACTIVE, off).block();
        var secondOff = second.deactivate("op-2");

        assertThrows(InvalidVendorStatusException.class, () -> vendors.save(second, VendorStatus.ACTIVE, secondOff).block());
        Vendor stored = vendors.findById(created.getId(), organisation).block();
        assertEquals(VendorStatus.INACTIVE, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size());

        var rename = stored.update("Taken", Category.CLOUD, "op-1");
        assertThrows(DuplicateVendorException.class, () -> vendors.save(stored, VendorStatus.INACTIVE, rename).block());
    }

    @Test
    @DisplayName("an agreement is read back whole, with its days as written; the reference is unique per vendor, not across vendors")
    void agreementRoundTrip() {
        UUID organisation = UUID.randomUUID();
        UUID vendor = UUID.randomUUID();
        Agreement first = agreements.create(newAgreement(organisation, vendor, "CT-1")).block();

        Agreement found = agreements.findById(first.getId(), organisation).block();

        assertEquals("CT-1", found.getReference());
        assertEquals(LocalDate.parse("2026-01-01"), found.getStartsOn());
        assertEquals(LocalDate.parse("2026-12-31"), found.getEndsOn());
        assertEquals(30, found.getNoticeDays());
        assertTrue(found.isAutoRenew());
        assertEquals(first.getAssetId(), found.getAssetId());
        assertNull(agreements.findById(first.getId(), UUID.randomUUID()).block());
        assertThrows(DuplicateAgreementException.class, () -> agreements.create(newAgreement(organisation, vendor, "CT-1")).block());
        agreements.create(newAgreement(organisation, UUID.randomUUID(), "CT-1")).block();
        agreements.create(newAgreement(UUID.randomUUID(), vendor, "CT-1")).block();
        assertEquals(2, agreements.findAll(organisation).collectList().block().size());
    }

    @Test
    @DisplayName("an agreement change is guarded by the status loaded, and a renewal is a new last day that is kept")
    void agreementGuard() {
        UUID organisation = UUID.randomUUID();
        Agreement created = agreements.create(newAgreement(organisation, UUID.randomUUID(), "GUARD")).block();
        Agreement first = agreements.findById(created.getId(), organisation).block();
        Agreement second = agreements.findById(created.getId(), organisation).block();

        var pause = first.pause("op-1");
        agreements.save(first, AgreementStatus.ACTIVE, pause).block();
        var secondPause = second.pause("op-2");
        assertThrows(InvalidAgreementStatusException.class, () -> agreements.save(second, AgreementStatus.ACTIVE, secondPause).block());

        Agreement stored = agreements.findById(created.getId(), organisation).block();
        var renewal = stored.update(Kind.SUPPORT, null, LocalDate.parse("2026-01-01"), LocalDate.parse("2028-02-29"), 60, false, "op-1");
        agreements.save(stored, AgreementStatus.PAUSED, renewal).block();
        Agreement renewed = agreements.findById(created.getId(), organisation).block();
        assertEquals(LocalDate.parse("2028-02-29"), renewed.getEndsOn());
        assertEquals(Kind.SUPPORT, renewed.getKind());
        assertNull(renewed.getAssetId());
        assertEquals(AgreementStatus.PAUSED, renewed.getStatus());
        assertEquals(3, renewed.getAuditTrail().size());
    }

    @Test
    @DisplayName("startup created the indexes: the unique vendor name, the unique agreement reference per vendor, and the list ones")
    void indexesExist() {
        assertTrue(indexNames("vendors").containsAll(List.of("organisationId_1_name_1", "organisationId_1_status_1")));
        assertTrue(indexNames("agreements").containsAll(List.of("organisationId_1_vendorId_1_reference_1", "organisationId_1_endsOn_1")));
    }

    private List<String> indexNames(String collection) {
        return Flux.from(mongoClient.getDatabase(DATABASE).getCollection(collection).listIndexes()).map(index -> ((Document) index).getString("name")).collectList().block();
    }
}
