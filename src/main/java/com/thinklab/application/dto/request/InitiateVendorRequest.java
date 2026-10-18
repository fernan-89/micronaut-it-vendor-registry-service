package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Vendor.Category;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO for {@code initiate}: a vendor is a name and a category, nothing about a person (ADR-030). */
@Serdeable
public record InitiateVendorRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "The category is required") Category category
) {}
