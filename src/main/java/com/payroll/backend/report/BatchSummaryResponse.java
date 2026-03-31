package com.payroll.backend.report;

import com.payroll.backend.batch.Batch;

import java.time.LocalDateTime;

public record BatchSummaryResponse(
        Long id,
        String name,
        LocalDateTime createdAt
) {
    public static BatchSummaryResponse from(Batch batch) {
        return new BatchSummaryResponse(batch.getId(), batch.getName(), batch.getCreatedAt());
    }
}
