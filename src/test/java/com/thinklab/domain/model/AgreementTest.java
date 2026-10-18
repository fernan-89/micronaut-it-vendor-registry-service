package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidAgreementStatusException;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Assessment;
import com.thinklab.domain.model.Agreement.Kind;
import com.thinklab.domain.model.Agreement.Standing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgreementTest {

    private static final LocalDate START = LocalDate.parse("2026-01-01");
    private static final LocalDate END = LocalDate.parse("2026-12-31");

    private final UUID org = UUID.randomUUID();
    private final UUID vendor = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();

    private Agreement created() {
        return Agreement.createNew(UUID.randomUUID(), org, vendor, "CT-2026/01", Kind.CONTRACT, asset, START, END, 30, true, "op");
    }

    private Agreement ending(LocalDate startsOn, LocalDate endsOn, int noticeDays) {
        return Agreement.createNew(UUID.randomUUID(), org, vendor, "REF", Kind.WARRANTY, null, startsOn, endsOn, noticeDays, false, "op");
    }

    private static void rejects(Executable action) {
        assertThrows(IllegalArgumentException.class, action);
    }

    @Test
    @DisplayName("a new agreement is ACTIVE, keeps what it was given and starts its trail")
    void createNew() {
        Agreement agreement = created();

        assertEquals(AgreementStatus.ACTIVE, agreement.getStatus());
        assertEquals(vendor, agreement.getVendorId());
        assertEquals("CT-2026/01", agreement.getReference());
        assertEquals(Kind.CONTRACT, agreement.getKind());
        assertEquals(asset, agreement.getAssetId());
        assertEquals(START, agreement.getStartsOn());
        assertEquals(END, agreement.getEndsOn());
        assertEquals(30, agreement.getNoticeDays());
        assertTrue(agreement.isAutoRenew());
        assertEquals(1, agreement.getAuditTrail().size());
        assertEquals("CONTRACT CT-2026/01 until 2026-12-31.", agreement.getAuditTrail().get(0).detail());
        assertNull(agreement.getAuditTrail().get(0).fromStatus());
        assertNull(ending(START, END, 0).getAssetId());
    }

    @Test
    @DisplayName("an agreement needs identities, an identifier for a reference, a kind, two days in order, a term of up to 3660 days, a notice of 0 to 365 and an executor")
    void validation() {
        UUID id = UUID.randomUUID();
        rejects(() -> Agreement.createNew(null, org, vendor, "REF", Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, null, vendor, "REF", Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, null, "REF", Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, null, Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "has a space", Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "x".repeat(41), Kind.CONTRACT, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", null, null, START, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, null, END, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, null, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, END, START, 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, START.plusDays(3661), 0, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, END, -1, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, END, 366, false, "op"));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, END, 0, false, null));
        rejects(() -> Agreement.createNew(id, org, vendor, "REF", Kind.CONTRACT, null, START, END, 0, false, " "));
        assertEquals(START, Agreement.createNew(id, org, vendor, "x".repeat(40), Kind.LICENSE, null, START, START, 365, false, "op").getEndsOn());
        assertEquals(START.plusDays(3660), Agreement.createNew(id, org, vendor, "A.b_c-1/2", Kind.SUPPORT, null, START, START.plusDays(3660), 0, false, "op").getEndsOn());
    }

    @Test
    @DisplayName("update changes everything but the vendor and the reference, in any status, and records it")
    void update() {
        Agreement agreement = created();
        UUID other = UUID.randomUUID();

        AgreementAuditEntry entry = agreement.update(Kind.SUPPORT, other, START, LocalDate.parse("2027-12-31"), 60, false, "op-2");

        assertEquals(Kind.SUPPORT, agreement.getKind());
        assertEquals(other, agreement.getAssetId());
        assertEquals(LocalDate.parse("2027-12-31"), agreement.getEndsOn());
        assertEquals(60, agreement.getNoticeDays());
        assertFalse(agreement.isAutoRenew());
        assertEquals(vendor, agreement.getVendorId());
        assertEquals("CT-2026/01", agreement.getReference());
        assertEquals("UPDATED", entry.action());
        agreement.pause("op");
        agreement.update(Kind.CONTRACT, null, START, END, 0, true, "op");
        assertEquals(AgreementStatus.PAUSED, agreement.getStatus());
        assertNull(agreement.getAssetId());
        rejects(() -> agreement.update(null, null, START, END, 0, true, "op"));
        rejects(() -> agreement.update(Kind.CONTRACT, null, END, START, 0, true, "op"));
        rejects(() -> agreement.update(Kind.CONTRACT, null, START, END, 0, true, null));
    }

    @Test
    @DisplayName("pause and resume go one way each; the wrong state is a conflict")
    void lifecycle() {
        Agreement agreement = created();

        assertThrows(InvalidAgreementStatusException.class, () -> agreement.resume("op"));
        AgreementAuditEntry paused = agreement.pause("op");
        assertEquals(AgreementStatus.PAUSED, agreement.getStatus());
        assertEquals("PAUSED", paused.action());
        assertThrows(InvalidAgreementStatusException.class, () -> agreement.pause("op"));
        AgreementAuditEntry resumed = agreement.resume("op");
        assertEquals(AgreementStatus.ACTIVE, agreement.getStatus());
        assertEquals(AgreementStatus.PAUSED, resumed.fromStatus());
        rejects(() -> agreement.pause(null));
        agreement.pause("op");
        rejects(() -> agreement.resume(" "));
    }

    @Test
    @DisplayName("an agreement is judged on the day it is read: not started, in force, expiring from the notice on, last day included, then expired")
    void assess() {
        Agreement agreement = ending(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), 10);

        assertEquals(new Assessment(Standing.NOT_STARTED, 30L), agreement.assess(LocalDate.parse("2026-05-31")));
        assertEquals(new Assessment(Standing.IN_FORCE, 29L), agreement.assess(LocalDate.parse("2026-06-01")));
        assertEquals(new Assessment(Standing.IN_FORCE, 11L), agreement.assess(LocalDate.parse("2026-06-19")));
        assertEquals(new Assessment(Standing.EXPIRING, 10L), agreement.assess(LocalDate.parse("2026-06-20")));
        assertEquals(new Assessment(Standing.EXPIRING, 0L), agreement.assess(LocalDate.parse("2026-06-30")));
        assertEquals(new Assessment(Standing.EXPIRED, -1L), agreement.assess(LocalDate.parse("2026-07-01")));
        assertEquals(new Assessment(Standing.EXPIRED, -100L), agreement.assess(LocalDate.parse("2026-10-08")));
    }

    @Test
    @DisplayName("with no notice an agreement is in force until its last day; a paused one is not judged at all")
    void assessEdges() {
        Agreement agreement = ending(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), 0);

        assertEquals(Standing.IN_FORCE, agreement.assess(LocalDate.parse("2026-06-29")).standing());
        assertEquals(Standing.EXPIRING, agreement.assess(LocalDate.parse("2026-06-30")).standing());
        agreement.pause("op");
        assertEquals(new Assessment(Standing.PAUSED, null), agreement.assess(LocalDate.parse("2030-01-01")));
    }

    @Test
    @DisplayName("reconstitute keeps what was stored, defaults what is missing, and refuses a missing identity")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-18T10:00:00Z");
        Agreement full = Agreement.reconstitute(id, org, vendor, "REF", Kind.LICENSE, asset, START, END, 15, true, AgreementStatus.PAUSED, created, created.plusSeconds(5),
                List.of(new AgreementAuditEntry(created, "INITIATED", "op", null, AgreementStatus.ACTIVE, "d")));
        assertEquals(AgreementStatus.PAUSED, full.getStatus());
        assertEquals(created.plusSeconds(5), full.getUpdatedAt());
        assertEquals(1, full.getAuditTrail().size());
        assertEquals(15, full.getNoticeDays());

        Agreement bare = Agreement.reconstitute(id, org, vendor, "REF", Kind.LICENSE, null, START, END, 0, false, null, null, null, null);
        assertEquals(AgreementStatus.ACTIVE, bare.getStatus());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());
        rejects(() -> Agreement.reconstitute(null, org, vendor, "REF", Kind.LICENSE, null, START, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, null, vendor, "REF", Kind.LICENSE, null, START, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, org, null, "REF", Kind.LICENSE, null, START, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, org, vendor, null, Kind.LICENSE, null, START, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, org, vendor, "REF", null, null, START, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, org, vendor, "REF", Kind.LICENSE, null, null, END, 0, false, null, null, null, null));
        rejects(() -> Agreement.reconstitute(id, org, vendor, "REF", Kind.LICENSE, null, START, null, 0, false, null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> full.getAuditTrail().clear());
    }
}
