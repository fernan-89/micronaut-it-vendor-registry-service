package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record VendorResponse(UUID id, UUID organisationId, String name, String category, String status, Instant createdAt, Instant updatedAt) {}
