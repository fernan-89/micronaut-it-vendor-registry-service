package com.thinklab.application.mapper;

import com.thinklab.application.dto.response.AgreementResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.dto.response.VendorResponse;
import com.thinklab.domain.model.Agreement;
import com.thinklab.domain.model.Agreement.AgreementAuditEntry;
import com.thinklab.domain.model.Agreement.Assessment;
import com.thinklab.domain.model.Vendor;
import com.thinklab.domain.model.Vendor.VendorAuditEntry;

public final class VendorMapper {

    private VendorMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(vendor.getId(), vendor.getOrganisationId(), vendor.getName(), vendor.getCategory().name(), vendor.getStatus().name(), vendor.getCreatedAt(),
                vendor.getUpdatedAt());
    }

    /** The agreement as it stands, with how it was judged. */
    public static AgreementResponse toResponse(Agreement agreement, Assessment judged) {
        return new AgreementResponse(agreement.getId(), agreement.getOrganisationId(), agreement.getVendorId(), agreement.getKind().name(), agreement.getReference(),
                agreement.getAssetId(), agreement.getStartsOn(), agreement.getEndsOn(), agreement.getNoticeDays(), agreement.isAutoRenew(), agreement.getStatus().name(),
                judged.standing().name(), judged.daysToEnd(), agreement.getCreatedAt(), agreement.getUpdatedAt());
    }

    public static AuditEntryResponse toResponse(VendorAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }

    public static AuditEntryResponse toResponse(AgreementAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(), entry.fromStatus() != null ? entry.fromStatus().name() : null,
                entry.toStatus().name(), entry.detail());
    }
}
