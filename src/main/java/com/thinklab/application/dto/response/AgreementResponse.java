package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An agreement as it stands, with how it was judged on the day it was read ({@code standing}, {@code daysToEnd}); the judgment is never stored. */
@Serdeable
public record AgreementResponse(UUID id, UUID organisationId, UUID vendorId, String kind, String reference, @Nullable UUID assetId, LocalDate startsOn, LocalDate endsOn,
                                int noticeDays, boolean autoRenew, String status, String standing, @Nullable Long daysToEnd, Instant createdAt, Instant updatedAt) {}
