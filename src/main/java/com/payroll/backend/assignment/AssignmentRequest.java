package com.payroll.backend.assignment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentRequest {
    private Long userId;
    private Double percentage;
    private PayoutBasis basisType;
    private Long sourceUserId;
}
