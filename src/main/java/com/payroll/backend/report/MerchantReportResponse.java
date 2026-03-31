package com.payroll.backend.report;

import java.util.List;

public record MerchantReportResponse(
        Long merchantId,
        Long externalMerchantId,
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
