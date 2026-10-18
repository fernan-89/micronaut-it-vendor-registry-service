package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.Assessment;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Agreement.Standing;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VendorMapperTest {

    private final UUID org = UUID.randomUUID();

    @Test
    @DisplayName("a vendor is mapped with its category and status as names")
    void vendor() {
        Vendor vendor = Vendor.createNew(UUID.randomUUID(), org, "Dell", Category.HARDWARE, "op");

        VendorResponse response = VendorMapper.toResponse(vendor);

        assertEquals(vendor.getId(), response.id());
        assertEquals("Dell", response.name());
        assertEquals("HARDWARE", response.category());
        assertEquals("ACTIVE", response.status());
        assertEquals(org, response.organisationId());
    }

    @Test
    @DisplayName("an agreement is mapped with the way it was judged: standing and days to the end")
    void agreement() {
        UUID asset = UUID.randomUUID();
        Agreement agreement = Agreement.createNew(UUID.randomUUID(), org, UUID.randomUUID(), "REF", Kind.SUPPORT, asset, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 30, true, "op");

        AgreementResponse response = VendorMapper.toResponse(agreement, new Assessment(Standing.EXPIRING, 12L));
        AgreementResponse paused = VendorMapper.toResponse(agreement, new Assessment(Standing.PAUSED, null));

        assertEquals("SUPPORT", response.kind());
        assertEquals("REF", response.reference());
        assertEquals(asset, response.assetId());
        assertEquals("EXPIRING", response.standing());
        assertEquals(12L, response.daysToEnd());
        assertEquals(LocalDate.parse("2026-12-31"), response.endsOn());
        assertEquals(30, response.noticeDays());
        assertEquals(true, response.autoRenew());
        assertEquals("ACTIVE", response.status());
        assertNull(paused.daysToEnd());
    }

    @Test
    @DisplayName("the audit entries of a vendor and of an agreement are mapped, with no earlier status for the first one")
    void auditEntries() {
        Vendor vendor = Vendor.createNew(UUID.randomUUID(), org, "Dell", Category.HARDWARE, "op");
        vendor.deactivate("op-2");
        Agreement agreement = Agreement.createNew(UUID.randomUUID(), org, UUID.randomUUID(), "REF", Kind.CONTRACT, null, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), 0, false, "op");
        agreement.pause("op-3");

        AuditEntryResponse first = VendorMapper.toResponse(vendor.getAuditTrail().get(0));
        AuditEntryResponse second = VendorMapper.toResponse(vendor.getAuditTrail().get(1));
        AuditEntryResponse agreementFirst = VendorMapper.toResponse(agreement.getAuditTrail().get(0));
        AuditEntryResponse agreementSecond = VendorMapper.toResponse(agreement.getAuditTrail().get(1));

        assertNull(first.fromStatus());
        assertEquals("ACTIVE", first.toStatus());
        assertEquals("ACTIVE", second.fromStatus());
        assertEquals("INACTIVE", second.toStatus());
        assertEquals("op-2", second.executor());
        assertNull(agreementFirst.fromStatus());
        assertEquals("PAUSED", agreementSecond.toStatus());
    }

    @Test
    @DisplayName("the mapper is a utility class that cannot be instantiated")
    void utilityClass() throws Exception {
        Constructor<VendorMapper> constructor = VendorMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, failure.getCause().getClass());
    }
}
