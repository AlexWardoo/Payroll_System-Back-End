package com.payroll.backend.report;

public record AgentSummaryResponse(
        Long userId,
        String username,
        String displayName,
        long assignedAccounts,
        double ownPayout,
        double overridePayout,
        double directAdjustments,
        double totalPayout
) {
}
