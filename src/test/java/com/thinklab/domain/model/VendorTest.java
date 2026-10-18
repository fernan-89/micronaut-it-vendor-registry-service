package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidVendorStatusException;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import com.thinklab.domain.model.Vendor.VendorStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendorTest {

    private final UUID org = UUID.randomUUID();

    private Vendor created() {
        return Vendor.createNew(UUID.randomUUID(), org, "Dell", Category.HARDWARE, "op");
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a new vendor is ACTIVE, keeps a name and a category and nothing else, and starts its trail")
    void createNew() {
        Vendor vendor = created();

        assertEquals(VendorStatus.ACTIVE, vendor.getStatus());
        assertEquals("Dell", vendor.getName());
        assertEquals(Category.HARDWARE, vendor.getCategory());
        assertEquals(1, vendor.getAuditTrail().size());
        assertEquals("INITIATED", vendor.getAuditTrail().get(0).action());
        assertNull(vendor.getAuditTrail().get(0).fromStatus());
        assertEquals(vendor.getCreatedAt(), vendor.getUpdatedAt());
    }

    @Test
    @DisplayName("a vendor needs an identity, a name of up to 80 characters, a category and an executor")
    void validation() {
        UUID id = UUID.randomUUID();
        rejects(() -> Vendor.createNew(null, org, "Dell", Category.HARDWARE, "op"));
        rejects(() -> Vendor.createNew(id, null, "Dell", Category.HARDWARE, "op"));
        rejects(() -> Vendor.createNew(id, org, null, Category.HARDWARE, "op"));
        rejects(() -> Vendor.createNew(id, org, " ", Category.HARDWARE, "op"));
        rejects(() -> Vendor.createNew(id, org, "x".repeat(81), Category.HARDWARE, "op"));
        rejects(() -> Vendor.createNew(id, org, "Dell", null, "op"));
        rejects(() -> Vendor.createNew(id, org, "Dell", Category.HARDWARE, null));
        rejects(() -> Vendor.createNew(id, org, "Dell", Category.HARDWARE, " "));
        assertEquals("x".repeat(80), Vendor.createNew(id, org, "x".repeat(80), Category.OTHER, "op").getName());
    }

    @Test
    @DisplayName("update changes the whole definition in any status and records it")
    void update() {
        Vendor vendor = created();

        VendorAuditEntry entry = vendor.update("Dell Technologies", Category.SERVICES, "op-2");

        assertEquals("Dell Technologies", vendor.getName());
        assertEquals(Category.SERVICES, vendor.getCategory());
        assertEquals("UPDATED", entry.action());
        assertEquals(VendorStatus.ACTIVE, entry.fromStatus());
        vendor.deactivate("op");
        vendor.update("Dell", Category.HARDWARE, "op");
        assertEquals(VendorStatus.INACTIVE, vendor.getStatus());
        rejects(() -> vendor.update(" ", Category.HARDWARE, "op"));
        rejects(() -> vendor.update("Dell", null, "op"));
        rejects(() -> vendor.update("Dell", Category.HARDWARE, null));
    }

    @Test
    @DisplayName("deactivate and activate go one way each; the wrong state is a conflict")
    void lifecycle() {
        Vendor vendor = created();

        assertThrows(InvalidVendorStatusException.class, () -> vendor.activate("op"));
        VendorAuditEntry off = vendor.deactivate("op");
        assertEquals(VendorStatus.INACTIVE, vendor.getStatus());
        assertEquals("DEACTIVATED", off.action());
        assertThrows(InvalidVendorStatusException.class, () -> vendor.deactivate("op"));
        VendorAuditEntry on = vendor.activate("op");
        assertEquals(VendorStatus.ACTIVE, vendor.getStatus());
        assertEquals(VendorStatus.INACTIVE, on.fromStatus());
        rejects(() -> vendor.deactivate(null));
        vendor.deactivate("op");
        rejects(() -> vendor.activate(" "));
        assertEquals(4, vendor.getAuditTrail().size());
        assertTrue(vendor.getUpdatedAt().compareTo(vendor.getCreatedAt()) >= 0);
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing identity")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-18T10:00:00Z");
        Vendor full = Vendor.reconstitute(id, org, "Dell", Category.CLOUD, VendorStatus.INACTIVE, created, created.plusSeconds(5),
                List.of(new VendorAuditEntry(created, "INITIATED", "op", null, VendorStatus.ACTIVE, "d")));
        assertEquals(VendorStatus.INACTIVE, full.getStatus());
        assertEquals(created.plusSeconds(5), full.getUpdatedAt());
        assertEquals(1, full.getAuditTrail().size());

        Vendor bare = Vendor.reconstitute(id, org, "Dell", Category.CLOUD, null, null, null, null);
        assertEquals(VendorStatus.ACTIVE, bare.getStatus());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        rejects(() -> Vendor.reconstitute(null, org, "Dell", Category.CLOUD, null, null, null, null));
        rejects(() -> Vendor.reconstitute(id, null, "Dell", Category.CLOUD, null, null, null, null));
        rejects(() -> Vendor.reconstitute(id, org, null, Category.CLOUD, null, null, null, null));
        rejects(() -> Vendor.reconstitute(id, org, "Dell", null, null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> full.getAuditTrail().clear());
    }
}
