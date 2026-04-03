package com.payroll.backend.report;

import java.util.List;

public record MerchantReportResponse(
        String merchantId,
        String name,
        String processor,
        boolean isNew,
        double salesVolume,
        double grossProfit,
        double deductions,
        double netProfit,
        double agentNet,
        double additionsTotal,
        double deductionAdjustments,
        List<MerchantAssignmentResponse> assignments
) {
}
