package com.temadison.stockdash.backend.exception;

public class CsvImportException extends RuntimeException {
    public CsvImportException(String message) {
        super(message);
    }
}
