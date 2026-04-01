package com.payroll.backend.report;

public record EmployeeMerchantReportResponse(
        Long merchantId,
        Long externalMerchantId,
        String name,
        String processor,
        double salesVolume,
        Double grossProfit,
        Double netProfit,
        Double agentNet,
        double deductions,
        double payout,
        double overridePayout,
        String relationship
) {
}