package com.payroll.backend.report;

public record EmployeeSummaryResponse(
        long assignedAccounts,
        double payout,
        double overridePayout,
        double directAdjustments,
        double salesVolume,
        double deductions
) {
}
