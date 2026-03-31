package com.payroll.backend.report;

public record EmployeeHistoryPointResponse(
        Long batchId,
        String batchName,
        double totalPayout
) {
}
