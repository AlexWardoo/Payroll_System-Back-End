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
    public AdminReportResponse getAdminReport(@RequestParam Long monthId) {
        return payrollReportService.getAdminReport(monthId);
    }

    @GetMapping("/employee/{userId}")
    public EmployeeReportResponse getEmployeeReport(@PathVariable Long userId, @RequestParam Long monthId) {
        return payrollReportService.getEmployeeReport(monthId, userId);
    }

    @GetMapping("/admin/export")
    public ResponseEntity<byte[]> exportAdminReport(@RequestParam Long monthId) {
        return csvResponse("admin-report-" + monthId + ".csv", payrollReportService.exportAdminCsv(monthId));
    }

    @GetMapping("/employee/{userId}/export")
    public ResponseEntity<byte[]> exportEmployeeReport(@PathVariable Long userId, @RequestParam Long monthId) {
        return csvResponse("employee-report-" + userId + "-" + monthId + ".csv",
                payrollReportService.exportEmployeeCsv(monthId, userId));
    }

    private ResponseEntity<byte[]> csvResponse(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
