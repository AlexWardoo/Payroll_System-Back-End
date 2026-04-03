package com.payroll.backend.month;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollMonthRepository extends JpaRepository<PayrollMonth, Long> {

    Optional<PayrollMonth> findByLabel(String label);

    Optional<PayrollMonth> findTopByOrderByIdDesc();

    List<PayrollMonth> findAllByOrderByIdDesc();

    Optional<PayrollMonth> findTopByIdLessThanOrderByIdDesc(Long id);
}
