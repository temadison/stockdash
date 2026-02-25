package com.temadison.stockdash.backend.seed;

import com.temadison.stockdash.backend.exception.CsvImportException;
import com.temadison.stockdash.backend.service.CsvImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class SeedDataRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SeedDataRunner.class);

    private final SeedProperties seedProperties;
    private final CsvImportService csvTransactionImportService;

    public SeedDataRunner(SeedProperties seedProperties, CsvImportService csvTransactionImportService) {
        this.seedProperties = seedProperties;
        this.csvTransactionImportService = csvTransactionImportService;
    }

    @Override
    public void run(String... args) {
        if (!seedProperties.enabled()) {
            return;
        }

        String csvResource = seedProperties.csvResource();
        if (!StringUtils.hasText(csvResource)) {
            throw new CsvImportException("Seed is enabled but stockdash.seed.csv-resource is empty.");
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (var inputStream = classLoader.getResourceAsStream(csvResource)) {
            if (inputStream == null) {
                throw new CsvImportException("Seed CSV resource not found: " + csvResource);
            }
            var result = csvTransactionImportService.importCsv(inputStream);
            logger.info("Seed import complete. Imported {}, skipped {} duplicate transaction(s), across {} account(s).",
                    result.importedCount(), result.skippedCount(), result.accountsAffected().size());
        } catch (IOException e) {
            throw new CsvImportException("Unable to read seed CSV resource: " + csvResource);
        }
    }
}
