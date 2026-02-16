package com.temadison.stockdash.backend.model;

import java.util.List;

public record CsvUploadResult(int importedCount, int skippedCount, List<String> accountsAffected) {
}
