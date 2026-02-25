package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.model.CsvUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface CsvImportService {

    CsvUploadResult importCsv(MultipartFile file);

    CsvUploadResult importCsv(InputStream inputStream);
}
