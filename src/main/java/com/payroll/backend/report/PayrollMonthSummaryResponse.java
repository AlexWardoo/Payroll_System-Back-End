package com.payroll.backend.report;

import com.payroll.backend.month.PayrollMonth;

import java.time.LocalDateTime;

public record PayrollMonthSummaryResponse(
        Long id,
        String label,
        LocalDateTime createdAt
) {
    public static PayrollMonthSummaryResponse from(PayrollMonth payrollMonth) {
        return new PayrollMonthSummaryResponse(payrollMonth.getId(), payrollMonth.getLabel(), payrollMonth.getCreatedAt());
    }
}
