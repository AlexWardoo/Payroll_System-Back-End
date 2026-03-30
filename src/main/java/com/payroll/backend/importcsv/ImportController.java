package com.payroll.backend.importcsv;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryResponse importBatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam("batchName") String batchName
    ) throws IOException {
        return csvImportService.importCsv(file, batchName);
    }
}