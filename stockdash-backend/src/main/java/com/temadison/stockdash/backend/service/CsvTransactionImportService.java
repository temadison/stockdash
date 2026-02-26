package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.AccountEntity;
import com.temadison.stockdash.backend.domain.TradeTransactionEntity;
import com.temadison.stockdash.backend.domain.TransactionType;
import com.temadison.stockdash.backend.exception.CsvImportException;
import com.temadison.stockdash.backend.model.CsvUploadResult;
import com.temadison.stockdash.backend.pricing.SymbolNormalizer;
import com.temadison.stockdash.backend.repository.AccountRepository;
import com.temadison.stockdash.backend.repository.TradeTransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvTransactionImportService implements CsvImportService {

    private static final String HEADER_TRADE_DATE = "trade_date";
    private static final String HEADER_ACCOUNT = "account";
    private static final String HEADER_SYMBOL = "symbol";
    private static final String HEADER_TYPE = "type";
    private static final String HEADER_QUANTITY = "quantity";
    private static final String HEADER_PRICE = "price";
    private static final String HEADER_FEE = "fee";

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            HEADER_TRADE_DATE,
            HEADER_ACCOUNT,
            HEADER_SYMBOL,
            HEADER_TYPE,
            HEADER_QUANTITY,
            HEADER_PRICE,
            HEADER_FEE
    );

    private final AccountRepository accountRepository;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final JobRunRecorder jobRunRecorder;

    public CsvTransactionImportService(
            AccountRepository accountRepository,
            TradeTransactionRepository tradeTransactionRepository,
            JobRunRecorder jobRunRecorder
    ) {
        this.accountRepository = accountRepository;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.jobRunRecorder = jobRunRecorder;
    }

    @Override
    @Transactional
    public CsvUploadResult importCsv(MultipartFile file) {
        long runId = jobRunRecorder.start("csv_import", "source=multipart");
        if (file == null || file.isEmpty()) {
            CsvImportException error = new CsvImportException("CSV file is required and cannot be empty.");
            jobRunRecorder.fail(runId, 0, 0, 1, 0, error, "multipart payload empty");
            throw error;
        }
        try {
            CsvUploadResult result = importCsvStream(file.getInputStream());
            int requested = result.importedCount() + result.skippedCount();
            jobRunRecorder.success(runId, requested, result.importedCount(), 0, result.skippedCount(), "source=multipart");
            return result;
        } catch (IOException e) {
            CsvImportException error = new CsvImportException("Unable to read CSV file.");
            jobRunRecorder.fail(runId, 0, 0, 1, 0, error, "source=multipart");
            throw error;
        } catch (RuntimeException e) {
            jobRunRecorder.fail(runId, 0, 0, 1, 0, e, "source=multipart");
            throw e;
        }
    }

    @Override
    @Transactional
    public CsvUploadResult importCsv(InputStream inputStream) {
        long runId = jobRunRecorder.start("csv_import", "source=stream");
        if (inputStream == null) {
            CsvImportException error = new CsvImportException("CSV input stream is required.");
            jobRunRecorder.fail(runId, 0, 0, 1, 0, error, "source=stream");
            throw error;
        }
        try {
            CsvUploadResult result = importCsvStream(inputStream);
            int requested = result.importedCount() + result.skippedCount();
            jobRunRecorder.success(runId, requested, result.importedCount(), 0, result.skippedCount(), "source=stream");
            return result;
        } catch (RuntimeException e) {
            jobRunRecorder.fail(runId, 0, 0, 1, 0, e, "source=stream");
            throw e;
        }
    }

    private CsvUploadResult importCsvStream(InputStream inputStream) {
        List<TradeTransactionEntity> parsedTransactions = new ArrayList<>();
        Map<String, AccountEntity> accountCache = new HashMap<>();
        Set<String> accountsAffected = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            validateHeaders(parser.getHeaderMap().keySet());

            for (CSVRecord record : parser) {
                int row = (int) record.getRecordNumber() + 1;
                TradeTransactionEntity entity = toEntity(record, row, accountCache);
                parsedTransactions.add(entity);
                accountsAffected.add(entity.getAccount().getName());
            }

        } catch (IOException e) {
            throw new CsvImportException("Unable to read CSV file.");
        }

        if (parsedTransactions.isEmpty()) {
            throw new CsvImportException("CSV file does not contain any transactions.");
        }

        List<TradeTransactionEntity> transactionsToPersist = parsedTransactions.stream()
                .filter(transaction -> !tradeTransactionRepository.existsByAccountAndTradeDateAndSymbolAndTypeAndQuantityAndPriceAndFee(
                        transaction.getAccount(),
                        transaction.getTradeDate(),
                        transaction.getSymbol(),
                        transaction.getType(),
                        transaction.getQuantity(),
                        transaction.getPrice(),
                        transaction.getFee()
                ))
                .toList();

        if (!transactionsToPersist.isEmpty()) {
            tradeTransactionRepository.saveAll(transactionsToPersist);
        }

        List<String> sortedAccounts = accountsAffected.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        int skippedCount = parsedTransactions.size() - transactionsToPersist.size();
        return new CsvUploadResult(transactionsToPersist.size(), skippedCount, sortedAccounts);
    }

    private void validateHeaders(Set<String> providedHeaders) {
        Set<String> normalizedHeaders = providedHeaders.stream()
                .map(header -> header.toLowerCase(Locale.US).trim())
                .collect(Collectors.toSet());

        if (!normalizedHeaders.containsAll(REQUIRED_HEADERS)) {
            throw new CsvImportException("CSV must include headers: " + String.join(", ", REQUIRED_HEADERS));
        }
    }

    private TradeTransactionEntity toEntity(CSVRecord record, int row, Map<String, AccountEntity> accountCache) {
        String accountName = getRequiredValue(record, HEADER_ACCOUNT, row);
        AccountEntity account = accountCache.computeIfAbsent(
                accountName.toLowerCase(Locale.US),
                ignored -> accountRepository.findByNameIgnoreCase(accountName)
                        .orElseGet(() -> createAccount(accountName))
        );

        TradeTransactionEntity entity = new TradeTransactionEntity();
        entity.setAccount(account);
        entity.setTradeDate(parseDate(getRequiredValue(record, HEADER_TRADE_DATE, row), row));
        entity.setSymbol(SymbolNormalizer.normalize(getRequiredValue(record, HEADER_SYMBOL, row)));
        entity.setType(parseType(getRequiredValue(record, HEADER_TYPE, row), row));
        entity.setQuantity(parsePositiveDecimal(getRequiredValue(record, HEADER_QUANTITY, row), row, HEADER_QUANTITY));
        entity.setPrice(parseNonNegativeDecimal(getRequiredValue(record, HEADER_PRICE, row), row, HEADER_PRICE));
        entity.setFee(parseNonNegativeDecimal(getRequiredValue(record, HEADER_FEE, row), row, HEADER_FEE));
        return entity;
    }

    private AccountEntity createAccount(String accountName) {
        AccountEntity account = new AccountEntity();
        account.setName(accountName);
        return accountRepository.save(account);
    }

    private String getRequiredValue(CSVRecord record, String header, int row) {
        String value = record.get(header);
        if (value == null || value.isBlank()) {
            throw new CsvImportException("Row " + row + ": " + header + " is required.");
        }
        return value.trim();
    }

    private LocalDate parseDate(String value, int row) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CsvImportException("Row " + row + ": trade_date must be in ISO format (yyyy-MM-dd).");
        }
    }

    private TransactionType parseType(String value, int row) {
        try {
            return TransactionType.valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            throw new CsvImportException("Row " + row + ": type must be BUY or SELL.");
        }
    }

    private BigDecimal parsePositiveDecimal(String value, int row, String fieldName) {
        BigDecimal parsed = parseDecimal(value, row, fieldName);
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CsvImportException("Row " + row + ": " + fieldName + " must be greater than 0.");
        }
        return parsed;
    }

    private BigDecimal parseNonNegativeDecimal(String value, int row, String fieldName) {
        BigDecimal parsed = parseDecimal(value, row, fieldName);
        if (parsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new CsvImportException("Row " + row + ": " + fieldName + " must be 0 or greater.");
        }
        return parsed;
    }

    private BigDecimal parseDecimal(String value, int row, String fieldName) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new CsvImportException("Row " + row + ": " + fieldName + " must be a valid number.");
        }
    }
}
