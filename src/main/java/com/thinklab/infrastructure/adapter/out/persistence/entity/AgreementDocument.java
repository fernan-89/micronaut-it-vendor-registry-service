package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.model.Agreement.AgreementStatus;
import com.thinklab.domain.model.Agreement.Kind;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure-specific representation of the Agreement Aggregate for MongoDB. The two days are stored as ISO text ({@code 2026-12-31}): they
 * are calendar days, not moments, so no time zone can move them, and the text sorts like the date.
 */
@Introspected
public class AgreementDocument {

    @BsonId
    private UUID id;
    private UUID organisationId;
    private UUID vendorId;
    private String reference;
    private String kind;
    private UUID assetId;
    private String startsOn;
    private String endsOn;
    private int noticeDays;
    private boolean autoRenew;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getStartsOn() { return startsOn; }
    public void setStartsOn(String startsOn) { this.startsOn = startsOn; }
    public String getEndsOn() { return endsOn; }
    public void setEndsOn(String endsOn) { this.endsOn = endsOn; }
    public int getNoticeDays() { return noticeDays; }
    public void setNoticeDays(int noticeDays) { this.noticeDays = noticeDays; }
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
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

        public static AuditEntryDocument fromDomain(AgreementAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                    entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        AgreementAuditEntry toDomain() {
            return new AgreementAuditEntry(occurredAt, action, executor, fromStatus != null ? AgreementStatus.valueOf(fromStatus) : null, AgreementStatus.valueOf(toStatus), detail);
        }
    }

    public static final class AgreementPersistenceMapper {

        private AgreementPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static AgreementDocument toDocument(Agreement agreement) {
            AgreementDocument doc = new AgreementDocument();
            doc.setId(agreement.getId());
            doc.setOrganisationId(agreement.getOrganisationId());
            doc.setVendorId(agreement.getVendorId());
            doc.setReference(agreement.getReference());
            doc.setKind(agreement.getKind().name());
            doc.setAssetId(agreement.getAssetId());
            doc.setStartsOn(agreement.getStartsOn().toString());
            doc.setEndsOn(agreement.getEndsOn().toString());
            doc.setNoticeDays(agreement.getNoticeDays());
            doc.setAutoRenew(agreement.isAutoRenew());
            doc.setStatus(agreement.getStatus().name());
            doc.setCreatedAt(agreement.getCreatedAt());
            doc.setUpdatedAt(agreement.getUpdatedAt());
            doc.setAuditTrail(agreement.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Agreement toDomain(AgreementDocument doc) {
            return Agreement.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getVendorId(), doc.getReference(), Kind.valueOf(doc.getKind()), doc.getAssetId(),
                    LocalDate.parse(doc.getStartsOn()), LocalDate.parse(doc.getEndsOn()), doc.getNoticeDays(), doc.isAutoRenew(), AgreementStatus.valueOf(doc.getStatus()),
                    doc.getCreatedAt(), doc.getUpdatedAt(), doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }
    }
}
