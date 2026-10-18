package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidVendorStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root (BIAN Control Record) of the IT Vendor Registry Service Domain: a company the organisation buys from, or is supported by. It
 * holds only a name and a {@link Category}: no contact person, address or tax number (ADR-030), because a registry of what is owed to whom does
 * not need personal data. A vendor is never deleted; one that is no longer used is deactivated, and no new agreement can be made with it.
 */
public class Vendor {

    public static final int MAX_NAME_LENGTH = 80;

    private final UUID id;
    private final UUID organisationId;
    private String name;
    private Category category;
    private VendorStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<VendorAuditEntry> auditTrail;

    private Vendor(UUID id, UUID organisationId, String name, Category category, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.category = category;
        this.status = VendorStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new VendorAuditEntry(this.createdAt, "INITIATED", executor, null, VendorStatus.ACTIVE, "Vendor of category " + category + "."));
    }

    private Vendor(UUID id, UUID organisationId, String name, Category category, VendorStatus status, Instant createdAt, Instant updatedAt, List<VendorAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.name = name;
        this.category = category;
        this.status = status != null ? status : VendorStatus.ACTIVE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static Vendor createNew(UUID id, UUID organisationId, String name, Category category, String executor) {
        if (id == null || organisationId == null) {
            throw new IllegalArgumentException("ID and Organisation ID are mandatory for Vendor creation.");
        }
        validate(name, category);
        requireExecutor(executor);
        return new Vendor(id, organisationId, name, category, executor);
    }

    public static Vendor reconstitute(UUID id, UUID organisationId, String name, Category category, VendorStatus status, Instant createdAt, Instant updatedAt,
                                      List<VendorAuditEntry> auditTrail) {
        if (id == null || organisationId == null || name == null || category == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Name and Category are mandatory to reconstitute a Vendor.");
        }
        return new Vendor(id, organisationId, name, category, status, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. The whole definition again, in any status. */
    public VendorAuditEntry update(String newName, Category newCategory, String executor) {
        validate(newName, newCategory);
        requireExecutor(executor);
        this.name = newName;
        this.category = newCategory;
        return record("UPDATED", executor, this.status, this.status, "Vendor updated.");
    }

    /** Behavior Qualifier: {@code control/deactivate}. ACTIVE -&gt; INACTIVE: no new agreement can be made with it. */
    public VendorAuditEntry deactivate(String executor) {
        requireStatus(VendorStatus.ACTIVE);
        requireExecutor(executor);
        this.status = VendorStatus.INACTIVE;
        return record("DEACTIVATED", executor, VendorStatus.ACTIVE, VendorStatus.INACTIVE, "Deactivated: no new agreement can be made with this vendor.");
    }

    /** Behavior Qualifier: {@code control/activate}. INACTIVE -&gt; ACTIVE. */
    public VendorAuditEntry activate(String executor) {
        requireStatus(VendorStatus.INACTIVE);
        requireExecutor(executor);
        this.status = VendorStatus.ACTIVE;
        return record("ACTIVATED", executor, VendorStatus.INACTIVE, VendorStatus.ACTIVE, "Activated.");
    }

    // --- Internal helpers ---

    private VendorAuditEntry record(String action, String executor, VendorStatus from, VendorStatus to, String detail) {
        this.updatedAt = Instant.now();
        VendorAuditEntry entry = new VendorAuditEntry(this.updatedAt, action, executor, from, to, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(VendorStatus expected) {
        if (this.status != expected) {
            throw new InvalidVendorStatusException(String.format("Illegal transition: Vendor is [%s], expected [%s].", this.status, expected));
        }
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable Vendor mutations.");
        }
    }

    private static void validate(String name, Category category) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name is mandatory for a Vendor (up to " + MAX_NAME_LENGTH + " characters).");
        }
        if (category == null) {
            throw new IllegalArgumentException("The category of the vendor is mandatory.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public VendorStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<VendorAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum VendorStatus { ACTIVE, INACTIVE }

    /** What the vendor mainly sells. */
    public enum Category { HARDWARE, SOFTWARE, CLOUD, SERVICES, OTHER }

    public record VendorAuditEntry(Instant occurredAt, String action, String executor, VendorStatus fromStatus, VendorStatus toStatus, String detail) {}
}
