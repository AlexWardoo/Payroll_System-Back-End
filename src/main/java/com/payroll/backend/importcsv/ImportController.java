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

    @PostMapping(value = "/month", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryResponse importMonth(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "monthId", required = false) Long monthId,
            @RequestParam(value = "newMonthLabel", required = false) String newMonthLabel
    ) throws IOException {
        return csvImportService.importCsv(file, monthId, newMonthLabel);
    }
}
