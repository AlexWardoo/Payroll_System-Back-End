package com.payroll.backend.assignment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MerchantAssignmentsUpdateRequest(
        @NotNull @Valid List<AssignmentRequest> assignments
) {
    public List<AssignmentRequest> getAssignments() {
        return assignments;
    }
}
