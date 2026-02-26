package com.temadison.stockdash.backend.api.dto;

import java.util.List;

public record CsvUploadResultDto(
        int importedCount,
        int skippedCount,
        List<String> accountsAffected
) {
}
