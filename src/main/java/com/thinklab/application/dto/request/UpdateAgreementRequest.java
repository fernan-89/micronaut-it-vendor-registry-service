package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Agreement.Kind;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** DTO for {@code agreement/update}: everything but the vendor and the reference, which identify the agreement. A renewal is a new last day. */
@Serdeable
public record UpdateAgreementRequest(
        @NotNull(message = "The kind is required") Kind kind,
        @Nullable UUID assetId,
        @NotNull(message = "The first day is required") LocalDate startsOn,
        @NotNull(message = "The last day is required") LocalDate endsOn,
        @NotNull(message = "The notice is required") @Min(value = 0, message = "The notice must not be negative") @Max(value = 365, message = "The notice must not exceed 365 days")
        Integer noticeDays,
        @NotNull(message = "Say whether it renews itself") Boolean autoRenew
) {}
