package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.Category;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;
import com.thinklab.domain.model.Vendor.VendorStatus;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the Vendor Aggregate for MongoDB. */
@Introspected
public class VendorDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private String name;
    private String category;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(VendorAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        VendorAuditEntry toDomain() {
            return new VendorAuditEntry(occurredAt, action, executor, fromStatus != null ? VendorStatus.valueOf(fromStatus) : null, VendorStatus.valueOf(toStatus), detail);
        }
    }

    public static final class VendorPersistenceMapper {

        private VendorPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static VendorDocument toDocument(Vendor vendor) {
            VendorDocument doc = new VendorDocument();
            doc.setId(vendor.getId());
            doc.setOrganisationId(vendor.getOrganisationId());
            doc.setName(vendor.getName());
            doc.setCategory(vendor.getCategory().name());
            doc.setStatus(vendor.getStatus().name());
            doc.setCreatedAt(vendor.getCreatedAt());
            doc.setUpdatedAt(vendor.getUpdatedAt());
            doc.setAuditTrail(vendor.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Vendor toDomain(VendorDocument doc) {
            return Vendor.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getName(), Category.valueOf(doc.getCategory()), VendorStatus.valueOf(doc.getStatus()),
                    doc.getCreatedAt(), doc.getUpdatedAt(), doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
