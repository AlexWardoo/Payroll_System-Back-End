package com.payroll.backend.report;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final PayrollReportService payrollReportService;

    @GetMapping("/admin")
    public AdminReportResponse getAdminReport(@RequestParam Long batchId) {
        return payrollReportService.getAdminReport(batchId);
    }

    @GetMapping("/employee/{userId}")
    public EmployeeReportResponse getEmployeeReport(@PathVariable Long userId, @RequestParam Long batchId) {
        return payrollReportService.getEmployeeReport(batchId, userId);
    }

    @GetMapping("/admin/export")
    public ResponseEntity<byte[]> exportAdminReport(@RequestParam Long batchId) {
        return csvResponse("admin-report-" + batchId + ".csv", payrollReportService.exportAdminCsv(batchId));
    }

    @GetMapping("/employee/{userId}/export")
    public ResponseEntity<byte[]> exportEmployeeReport(@PathVariable Long userId, @RequestParam Long batchId) {
        return csvResponse("employee-report-" + userId + "-" + batchId + ".csv",
                payrollReportService.exportEmployeeCsv(batchId, userId));
    }

    private ResponseEntity<byte[]> csvResponse(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
