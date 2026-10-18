package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Vendor.Category;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO for {@code update}: the whole definition again. */
@Serdeable
public record UpdateVendorRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must not exceed 80 characters")
        String name,
        @NotNull(message = "The category is required") Category category
) {}
