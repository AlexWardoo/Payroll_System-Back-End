package com.payroll.backend.month;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/months")
@RequiredArgsConstructor
public class PayrollMonthController {

    private final PayrollMonthService payrollMonthService;

    @GetMapping
    public List<PayrollMonth> getAllMonths() {
        return payrollMonthService.getAllMonths();
    }

    @GetMapping("/latest")
    public PayrollMonth getLatestMonth() {
        return payrollMonthService.getLatestMonth();
    }

    @GetMapping("/by-label")
    public PayrollMonth getMonthByLabel(@RequestParam String label) {
        return payrollMonthService.getMonthByLabel(label);
    }

    @PostMapping
    public PayrollMonth createMonth(@RequestParam String label) {
        return payrollMonthService.createMonth(label);
    }
}
