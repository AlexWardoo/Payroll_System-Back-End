package com.payroll.backend.report;

public record EmployeeHistoryPointResponse(
        Long monthId,
        String monthLabel,
        double totalPayout
) {
}
