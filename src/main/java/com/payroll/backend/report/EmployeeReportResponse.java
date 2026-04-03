package com.payroll.backend.report;

import com.payroll.backend.auth.AuthUserResponse;

import java.util.List;

public record EmployeeReportResponse(
        PayrollMonthSummaryResponse month,
        AuthUserResponse employee,
        EmployeeSummaryResponse summary,
        List<EmployeeMerchantReportResponse> merchants,
        List<EmployeeHistoryPointResponse> history
) {
}
