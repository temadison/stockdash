package com.temadison.stockdash.backend.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PriceSyncRequestDto(
        @NotEmpty(message = "stocks is required and must contain at least one symbol.")
        List<String> stocks
) {
}
