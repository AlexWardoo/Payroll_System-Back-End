package com.payroll.backend.assignment;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class AssignmentRequest {
    private Long userId;
    private BigDecimal percentage;
    private PayoutBasis basisType;
    private Long sourceUserId;
}
