package com.payroll.backend.report;

import com.payroll.backend.assignment.Assignment;
import com.payroll.backend.assignment.AssignmentRepository;
import com.payroll.backend.assignment.PayoutBasis;
import com.payroll.backend.importcsv.CsvImportService;
import com.payroll.backend.importcsv.ImportSummaryResponse;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PayrollReportServiceIntegrationTest {

    private static final Path SAMPLE_CSV = Path.of("src/test/resources/samples/houseview-may-2025.csv");

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private PayrollReportService payrollReportService;

    @Test
    void calculatesDirectAndLayeredPayoutsFromImportedData() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Residuals_May2025_Houseview.csv",
                "text/csv",
                Files.readAllBytes(SAMPLE_CSV)
        );

        ImportSummaryResponse summary = csvImportService.importCsv(file, "May 2025 Payroll Report");

        Merchant brickhouse = findMerchant(summary.getBatchId(), 927540158491524L);
        Merchant diRoma = findMerchant(summary.getBatchId(), 4625693214766648L);
        User victor = userRepository.findByUsername("victor").orElseThrow();
        User caesar = userRepository.findByUsername("caesar").orElseThrow();
        User simeon = userRepository.findByUsername("simeon").orElseThrow();

        assignmentRepository.save(directAssignment(brickhouse, victor, 100.0));
        assignmentRepository.save(directAssignment(diRoma, caesar, 100.0));

        AdminReportResponse adminReport = payrollReportService.getAdminReport(summary.getBatchId());
        EmployeeReportResponse victorReport = payrollReportService.getEmployeeReport(summary.getBatchId(), victor.getId());
        EmployeeReportResponse caesarReport = payrollReportService.getEmployeeReport(summary.getBatchId(), caesar.getId());
        EmployeeReportResponse simeonReport = payrollReportService.getEmployeeReport(summary.getBatchId(), simeon.getId());

        assertThat(adminReport.totals().repPayouts()).isEqualTo(517.09);
        assertThat(adminReport.totals().adminOnlyAdjustments()).isEqualTo(-512.84);

        assertThat(findAgent(adminReport, "victor").totalPayout()).isEqualTo(445.73);
        assertThat(findAgent(adminReport, "caesar").totalPayout()).isEqualTo(47.57);
        assertThat(findAgent(adminReport, "simeon").overridePayout()).isEqualTo(23.79);

        assertThat(victorReport.merchants()).singleElement().satisfies(merchant -> {
            assertThat(merchant.name()).isEqualTo("BRICKHOUSE ITALIAN RESTAU");
            assertThat(merchant.grossProfit()).isEqualTo(1371.48);
            assertThat(merchant.netProfit()).isEqualTo(1371.48);
            assertThat(merchant.payout()).isEqualTo(445.73);
        });

        assertThat(caesarReport.merchants()).singleElement().satisfies(merchant -> {
            assertThat(merchant.name()).isEqualTo("DI ROMA RESTAURANT");
            assertThat(merchant.grossProfit()).isNull();
            assertThat(merchant.netProfit()).isNull();
            assertThat(merchant.payout()).isEqualTo(47.57);
        });

        assertThat(simeonReport.merchants()).singleElement().satisfies(merchant -> {
            assertThat(merchant.relationship()).isEqualTo("Override from Caesar");
            assertThat(merchant.overridePayout()).isEqualTo(23.79);
        });
    }

    private Merchant findMerchant(Long batchId, Long externalMerchantId) {
        return merchantRepository.findByBatchId(batchId).stream()
                .filter(merchant -> merchant.getExternalMerchantId().equals(externalMerchantId))
                .findFirst()
                .orElseThrow();
    }

    private Assignment directAssignment(Merchant merchant, User user, double percentage) {
        Assignment assignment = new Assignment();
        assignment.setMerchant(merchant);
        assignment.setUser(user);
        assignment.setPercentage(percentage);
        assignment.setBasisType(PayoutBasis.MERCHANT_NET);
        return assignment;
    }

    private AgentSummaryResponse findAgent(AdminReportResponse report, String username) {
        return report.agentSummaries().stream()
                .filter(agent -> agent.username().equals(username))
                .findFirst()
                .orElseThrow();
    }
}
