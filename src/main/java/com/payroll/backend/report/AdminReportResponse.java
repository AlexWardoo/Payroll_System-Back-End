package com.payroll.backend.report;

import com.payroll.backend.auth.AuthUserResponse;

import java.util.List;

public record AdminReportResponse(
        PayrollMonthSummaryResponse month,
        AdminTotalsResponse totals,
        List<AuthUserResponse> employees,
        List<AgentSummaryResponse> agentSummaries,
        List<MerchantReportResponse> merchants
) {
}
