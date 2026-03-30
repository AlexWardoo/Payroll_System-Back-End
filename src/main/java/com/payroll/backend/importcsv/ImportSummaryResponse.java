package com.payroll.backend.importcsv;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportSummaryResponse {
    private Long batchId;
    private String batchName;
    private int merchantsImported;
    private int newMerchants;
    private int additionsImported;
    private int deductionsImported;
}