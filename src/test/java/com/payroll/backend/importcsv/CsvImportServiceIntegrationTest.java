package com.payroll.backend.importcsv;

import com.payroll.backend.batch.BatchRepository;
import com.payroll.backend.lineitem.LineItemRepository;
import com.payroll.backend.merchant.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CsvImportServiceIntegrationTest {

    private static final Path SAMPLE_CSV = Path.of("src/test/resources/samples/houseview-may-2025.csv");

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Test
    void importsHouseviewCsvIncludingLineItemOnlyMerchants() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Residuals_May2025_Houseview.csv",
                "text/csv",
                Files.readAllBytes(SAMPLE_CSV)
        );

        ImportSummaryResponse summary = csvImportService.importCsv(file, "May 2025 Integration Import");

        assertThat(summary.getMerchantsImported()).isEqualTo(100);
        assertThat(summary.getAdditionsImported()).isEqualTo(1);
        assertThat(summary.getDeductionsImported()).isEqualTo(35);
        assertThat(batchRepository.findById(summary.getBatchId())).isPresent();

        assertThat(merchantRepository.findByBatchId(summary.getBatchId()))
                .extracting(merchant -> merchant.getExternalMerchantId())
                .contains(927540155165683L, 5309611100572210L);

        assertThat(lineItemRepository.findByBatchId(summary.getBatchId()))
                .hasSize(36);
    }
}
