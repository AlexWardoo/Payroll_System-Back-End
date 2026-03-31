package com.payroll.backend.report;

public record AdminTotalsResponse(
        long merchantCount,
        double salesVolume,
        double grossProfit,
        double deductions,
        double netProfit,
        double agentNet,
        double repPayouts,
        double adminOnlyAdjustments
) {
}
