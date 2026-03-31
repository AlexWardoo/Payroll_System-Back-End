package com.payroll.backend.report;

import com.payroll.backend.auth.AuthUserResponse;

import java.util.List;

public record AdminReportResponse(
        BatchSummaryResponse batch,
        AdminTotalsResponse totals,
        List<AuthUserResponse> employees,
        List<AgentSummaryResponse> agentSummaries,
        List<MerchantReportResponse> merchants
) {
}
