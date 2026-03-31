package com.payroll.backend.report;

public record MerchantAssignmentResponse(
        Long userId,
        String username,
        String displayName,
        double percentage,
        double payoutAmount,
        double overrideAmount,
        double totalPayout
) {
}
