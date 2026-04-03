package com.payroll.backend.month;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollMonthService {

    private final PayrollMonthRepository payrollMonthRepository;

    public List<PayrollMonth> getAllMonths() {
        return payrollMonthRepository.findAllByOrderByIdDesc();
    }

    public PayrollMonth getLatestMonth() {
        return payrollMonthRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No payroll months found"));
    }

    public PayrollMonth getMonthByLabel(String label) {
        return payrollMonthRepository.findByLabel(label)
                .orElseThrow(() -> new RuntimeException("Payroll month not found: " + label));
    }

    public PayrollMonth createMonth(String label) {
        if (payrollMonthRepository.findByLabel(label).isPresent()) {
            throw new RuntimeException("Payroll month already exists: " + label);
        }

        PayrollMonth payrollMonth = new PayrollMonth();
        payrollMonth.setLabel(label);
        payrollMonth.setCreatedAt(LocalDateTime.now());

        return payrollMonthRepository.save(payrollMonth);
    }
}
