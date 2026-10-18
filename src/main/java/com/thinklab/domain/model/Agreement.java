package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidAgreementStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Secondary aggregate of the IT Vendor Registry Service Domain: what a vendor owes the organisation, and until when. An agreement is a contract, a
 * warranty, a support plan or a licence term (a {@link Kind}); it names the vendor, the contract's own {@code reference} (an identifier, never a
 * sentence), optionally the asset it covers, the first and last day it is in force and how many days ahead of the end somebody must be told. It
 * judges itself when read (ADR-031): whether it has not started, is in force, is about to end or has ended is never stored, so it cannot go stale.
 * It does not renew itself: {@code autoRenew} only says what the contract does, and a renewal is the end date being moved.
 */
public class Agreement {

    public static final int MAX_NOTICE_DAYS = 365;
    /** The longest term accepted, a little over ten years: a longer one is almost surely a typo in the year. */
    public static final int MAX_TERM_DAYS = 3660;
    public static final int MAX_REFERENCE_LENGTH = 40;
    public static final Pattern REFERENCE_PATTERN = Pattern.compile("[A-Za-z0-9._/-]{1," + MAX_REFERENCE_LENGTH + "}");

    private final UUID id;
    private final UUID organisationId;
    private final UUID vendorId;
    private final String reference;
    private Kind kind;
    private UUID assetId;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private int noticeDays;
    private boolean autoRenew;
    private AgreementStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<AgreementAuditEntry> auditTrail;

    private Agreement(UUID id, UUID organisationId, UUID vendorId, String reference, Kind kind, UUID assetId, LocalDate startsOn, LocalDate endsOn, int noticeDays,
                      boolean autoRenew, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.vendorId = vendorId;
        this.reference = reference;
        this.kind = kind;
        this.assetId = assetId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.noticeDays = noticeDays;
        this.autoRenew = autoRenew;
        this.status = AgreementStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new AgreementAuditEntry(this.createdAt, "INITIATED", executor, null, AgreementStatus.ACTIVE, kind + " " + reference + " until " + endsOn + "."));
    }

    private Agreement(UUID id, UUID organisationId, UUID vendorId, String reference, Kind kind, UUID assetId, LocalDate startsOn, LocalDate endsOn, int noticeDays,
                      boolean autoRenew, AgreementStatus status, Instant createdAt, Instant updatedAt, List<AgreementAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.vendorId = vendorId;
        this.reference = reference;
        this.kind = kind;
        this.assetId = assetId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.noticeDays = noticeDays;
        this.autoRenew = autoRenew;
        this.status = status != null ? status : AgreementStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Agreement createNew(UUID id, UUID organisationId, UUID vendorId, String reference, Kind kind, UUID assetId, LocalDate startsOn, LocalDate endsOn,
                                      int noticeDays, boolean autoRenew, String executor) {
        if (id == null || organisationId == null || vendorId == null) {
            throw new IllegalArgumentException("ID, Organisation ID and Vendor ID are mandatory for Agreement creation.");
        }
        validateReference(reference);
        validate(kind, startsOn, endsOn, noticeDays);
        requireExecutor(executor);
        return new Agreement(id, organisationId, vendorId, reference, kind, assetId, startsOn, endsOn, noticeDays, autoRenew, executor);
    }

    public static Agreement reconstitute(UUID id, UUID organisationId, UUID vendorId, String reference, Kind kind, UUID assetId, LocalDate startsOn, LocalDate endsOn,
                                         int noticeDays, boolean autoRenew, AgreementStatus status, Instant createdAt, Instant updatedAt, List<AgreementAuditEntry> auditTrail) {
        if (id == null || organisationId == null || vendorId == null || reference == null || kind == null || startsOn == null || endsOn == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Vendor ID, Reference, Kind and both dates are mandatory to reconstitute an Agreement.");
        }
        return new Agreement(id, organisationId, vendorId, reference, kind, assetId, startsOn, endsOn, noticeDays, autoRenew, status, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. Everything but the vendor and the reference, which identify the agreement; in any status. */
    public AgreementAuditEntry update(Kind newKind, UUID newAssetId, LocalDate newStartsOn, LocalDate newEndsOn, int newNoticeDays, boolean newAutoRenew, String executor) {
        validate(newKind, newStartsOn, newEndsOn, newNoticeDays);
        requireExecutor(executor);
        this.kind = newKind;
        this.assetId = newAssetId;
        this.startsOn = newStartsOn;
        this.endsOn = newEndsOn;
        this.noticeDays = newNoticeDays;
        this.autoRenew = newAutoRenew;
        return record("UPDATED", executor, this.status, this.status, "Agreement updated: in force " + newStartsOn + " to " + newEndsOn + ".");
    }

    /** Behavior Qualifier: {@code control/pause}. ACTIVE -&gt; PAUSED: the agreement is no longer judged. */
    public AgreementAuditEntry pause(String executor) {
        requireStatus(AgreementStatus.ACTIVE);
        requireExecutor(executor);
        this.status = AgreementStatus.PAUSED;
        return record("PAUSED", executor, AgreementStatus.ACTIVE, AgreementStatus.PAUSED, "Paused: the agreement is no longer judged.");
    }

    /** Behavior Qualifier: {@code control/resume}. PAUSED -&gt; ACTIVE. */
    public AgreementAuditEntry resume(String executor) {
        requireStatus(AgreementStatus.PAUSED);
        requireExecutor(executor);
        this.status = AgreementStatus.ACTIVE;
        return record("RESUMED", executor, AgreementStatus.PAUSED, AgreementStatus.ACTIVE, "Resumed.");
    }

    /**
     * Where the agreement stands on that day (ADR-031). A paused one is not judged; one that has not started is NOT_STARTED; the last day is still
     * in force; from {@code noticeDays} before the last day it is EXPIRING; after the last day it is EXPIRED. Nothing of it is stored.
     */
    public Assessment assess(LocalDate today) {
        if (status == AgreementStatus.PAUSED) {
            return new Assessment(Standing.PAUSED, null);
        }
        long daysToEnd = ChronoUnit.DAYS.between(today, endsOn);
        if (today.isBefore(startsOn)) {
            return new Assessment(Standing.NOT_STARTED, daysToEnd);
        }
        if (daysToEnd < 0) {
            return new Assessment(Standing.EXPIRED, daysToEnd);
        }
        return new Assessment(daysToEnd <= noticeDays ? Standing.EXPIRING : Standing.IN_FORCE, daysToEnd);
    }

    // --- Internal helpers ---

    private AgreementAuditEntry record(String action, String executor, AgreementStatus from, AgreementStatus to, String detail) {
        this.updatedAt = Instant.now();
        AgreementAuditEntry entry = new AgreementAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(AgreementStatus expected) {
        if (this.status != expected) {
            throw new InvalidAgreementStatusException(String.format("Illegal transition: Agreement is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Agreement mutations.");
        }
    }

    private static void validateReference(String reference) {
        if (reference == null || !REFERENCE_PATTERN.matcher(reference).matches()) {
            throw new IllegalArgumentException("The reference must be an identifier of up to " + MAX_REFERENCE_LENGTH + " letters, digits, dots, slashes, dashes or underscores.");
        }
    }

    private static void validate(Kind kind, LocalDate startsOn, LocalDate endsOn, int noticeDays) {
        if (kind == null) {
            throw new IllegalArgumentException("The kind of the agreement is mandatory.");
        }
        if (startsOn == null || endsOn == null) {
            throw new IllegalArgumentException("The first and the last day of the agreement are mandatory.");
        }
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("The agreement cannot end before it starts.");
        }
        if (ChronoUnit.DAYS.between(startsOn, endsOn) > MAX_TERM_DAYS) {
            throw new IllegalArgumentException("The agreement cannot last more than " + MAX_TERM_DAYS + " days.");
        }
        if (noticeDays < 0 || noticeDays > MAX_NOTICE_DAYS) {
            throw new IllegalArgumentException("The notice must be between 0 and " + MAX_NOTICE_DAYS + " days.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getVendorId() { return vendorId; }
    public String getReference() { return reference; }
    public Kind getKind() { return kind; }
    public UUID getAssetId() { return assetId; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public int getNoticeDays() { return noticeDays; }
    public boolean isAutoRenew() { return autoRenew; }
    public AgreementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AgreementAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum AgreementStatus { ACTIVE, PAUSED }

    /** What the vendor owes: a contract, a warranty, a support plan or a licence term. */
    public enum Kind { CONTRACT, WARRANTY, SUPPORT, LICENSE }

    /** PAUSED is not judged; the rest compare today with the first and last day and the notice. */
    public enum Standing { PAUSED, NOT_STARTED, IN_FORCE, EXPIRING, EXPIRED }

    public record AgreementAuditEntry(Instant occurredAt, String action, String executor, AgreementStatus fromStatus, AgreementStatus toStatus, String detail) {}

    /** The judgment of one day: nothing of it is stored. {@code daysToEnd} is negative once it has ended, and null for a paused agreement. */
    public record Assessment(Standing standing, Long daysToEnd) {}
}
