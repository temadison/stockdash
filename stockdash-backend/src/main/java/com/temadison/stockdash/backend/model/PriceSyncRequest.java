package com.temadison.stockdash.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PriceSyncRequest(
        @NotEmpty(message = "stocks is required and must contain at least one symbol.")
        List<@NotBlank(message = "stocks must not contain blank symbols.") String> stocks
) {
}
