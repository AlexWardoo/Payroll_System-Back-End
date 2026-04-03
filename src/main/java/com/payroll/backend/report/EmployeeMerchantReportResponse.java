package com.payroll.backend.report;

public record EmployeeMerchantReportResponse(
        String merchantId,
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
