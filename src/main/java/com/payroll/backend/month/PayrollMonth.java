package com.payroll.backend.month;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_months")
@Getter
@Setter
public class PayrollMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
