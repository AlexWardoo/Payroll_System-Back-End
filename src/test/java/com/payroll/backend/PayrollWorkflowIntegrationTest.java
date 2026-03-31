package com.payroll.backend;

import com.payroll.backend.assignment.AssignmentRequest;
import com.payroll.backend.assignment.AssignmentService;
import com.payroll.backend.assignment.MerchantAssignmentsUpdateRequest;
import com.payroll.backend.assignment.PayoutBasis;
import com.payroll.backend.importcsv.CsvImportService;
import com.payroll.backend.importcsv.ImportSummaryResponse;
import com.payroll.backend.lineitem.LineItem;
import com.payroll.backend.lineitem.LineItemRepository;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.report.PayrollReportService;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PayrollWorkflowIntegrationTest {

    private static final Path SAMPLE_CSV = Path.of("src/test/resources/samples/houseview-may-2025.csv");

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private PayrollReportService payrollReportService;

    @Test
    void importsHouseviewCsvAndCapturesMerchantAndLineItemData() throws IOException {
        ImportSummaryResponse response = importSample("May 2025 Import");

        assertThat(response.getBatchId()).isNotNull();
        assertThat(response.getBatchName()).isEqualTo("May 2025 Import");
        assertThat(response.getMerchantsImported()).isEqualTo(100);
        assertThat(response.getAdditionsImported()).isEqualTo(1);
        assertThat(response.getDeductionsImported()).isEqualTo(35);

        Merchant pourErnies = merchantRepository.findAll().stream()
                .filter(merchant -> merchant.getExternalMerchantId().equals(927540159639402L))
                .findFirst()
                .orElseThrow();

        assertThat(pourErnies.getName()).isEqualTo("POUR ERNIES");
        assertThat(pourErnies.getProcessor()).isEqualTo("TSYS");
        assertThat(pourErnies.getSalesAmount()).isEqualTo(20467.82);
        assertThat(pourErnies.getIncome()).isEqualTo(398.94);
        assertThat(pourErnies.getExpenses()).isEqualTo(119.68);
        assertThat(pourErnies.getNet()).isEqualTo(279.26);

        long merchantLineItems = lineItemRepository.findByBatchId(response.getBatchId()).stream()
                .filter(lineItem -> lineItem.getMerchant() != null)
                .count();

        assertThat(merchantLineItems).isEqualTo(30);

        LineItem deduction = lineItemRepository.findByBatchId(response.getBatchId()).stream()
                .filter(lineItem -> lineItem.getMerchant() != null)
                .filter(lineItem -> lineItem.getMerchant().getExternalMerchantId().equals(927540159639402L))
                .findFirst()
                .orElseThrow();

        assertThat(deduction.getExpenses()).isEqualTo(225.76);
        assertThat(deduction.getNet()).isEqualTo(-225.76);
        assertThat(deduction.getDescription()).contains("RTO payment # 30");
    }

    @Test
    void calculatesDirectAndLayeredPayoutsAndAppliesAgentVisibility() throws IOException {
        ImportSummaryResponse response = importSample("May 2025 Payouts");

        Merchant brickhouse = merchantRepository.findAll().stream()
                .filter(merchant -> merchant.getExternalMerchantId().equals(927540158491524L))
                .findFirst()
                .orElseThrow();

        Merchant diRoma = merchantRepository.findAll().stream()
                .filter(merchant -> merchant.getExternalMerchantId().equals(4625693214766648L))
                .findFirst()
                .orElseThrow();

        User victor = userRepository.findByUsername("victor").orElseThrow();
        User simeon = userRepository.findByUsername("simeon").orElseThrow();
        User caesar = userRepository.findByUsername("caesar").orElseThrow();

        assignmentService.replaceAssignmentsForMerchant(
                brickhouse.getId(),
                new MerchantAssignmentsUpdateRequest(java.util.List.of(
                        assignment(victor.getId(), 100.0, PayoutBasis.MERCHANT_NET, null)
                )).getAssignments()
        );
        assignmentService.replaceAssignmentsForMerchant(
                diRoma.getId(),
                new MerchantAssignmentsUpdateRequest(java.util.List.of(
                        assignment(caesar.getId(), 100.0, PayoutBasis.MERCHANT_NET, null)
                )).getAssignments()
        );

        var adminReport = payrollReportService.getAdminReport(response.getBatchId());
        var merchantReport = adminReport.merchants().stream()
                .filter(merchant -> merchant.externalMerchantId().equals(927540158491524L))
                .findFirst()
                .orElseThrow();

        assertThat(merchantReport.netProfit()).isEqualTo(1371.48);
        assertThat(merchantReport.assignments()).hasSize(1);

        var victorAssignment = merchantReport.assignments().stream()
                .filter(assignment -> assignment.username().equals("victor"))
                .findFirst()
                .orElseThrow();

        var diRomaReport = adminReport.merchants().stream()
                .filter(merchant -> merchant.externalMerchantId().equals(4625693214766648L))
                .findFirst()
                .orElseThrow();
        var caesarAssignment = diRomaReport.assignments().stream()
                .filter(assignment -> assignment.username().equals("caesar"))
                .findFirst()
                .orElseThrow();

        assertThat(victorAssignment.payoutAmount()).isEqualTo(445.73);
        assertThat(victorAssignment.overrideAmount()).isEqualTo(0.0);
        assertThat(caesarAssignment.payoutAmount()).isEqualTo(47.57);
        assertThat(findAgent(adminReport, "simeon").overridePayout()).isEqualTo(23.79);

        var victorReport = payrollReportService.getEmployeeReport(response.getBatchId(), victor.getId());
        var victorMerchant = victorReport.merchants().stream()
                .filter(merchant -> merchant.externalMerchantId().equals(927540158491524L))
                .findFirst()
                .orElseThrow();

        assertThat(victorMerchant.grossProfit()).isEqualTo(1371.48);
        assertThat(victorMerchant.netProfit()).isEqualTo(1371.48);
        assertThat(victorMerchant.payout()).isEqualTo(445.73);

        var caesarReport = payrollReportService.getEmployeeReport(response.getBatchId(), caesar.getId());
        var caesarMerchant = caesarReport.merchants().stream()
                .filter(merchant -> merchant.externalMerchantId().equals(4625693214766648L))
                .findFirst()
                .orElseThrow();

        assertThat(caesarMerchant.grossProfit()).isNull();
        assertThat(caesarMerchant.netProfit()).isNull();
        assertThat(caesarMerchant.payout()).isEqualTo(47.57);

        var simeonReport = payrollReportService.getEmployeeReport(response.getBatchId(), simeon.getId());
        var simeonMerchant = simeonReport.merchants().stream()
                .filter(merchant -> merchant.externalMerchantId().equals(4625693214766648L))
                .findFirst()
                .orElseThrow();

        assertThat(simeonMerchant.overridePayout()).isEqualTo(23.79);
        assertThat(simeonMerchant.relationship()).contains("Override from Caesar");
    }

    private ImportSummaryResponse importSample(String batchName) throws IOException {
        byte[] content = Files.readAllBytes(SAMPLE_CSV);
        MockMultipartFile file = new MockMultipartFile("file", "houseview-may-2025.csv", "text/csv", content);
        return csvImportService.importCsv(file, batchName);
    }

    private AssignmentRequest assignment(Long userId, double percentage, PayoutBasis basis, Long sourceUserId) {
        AssignmentRequest request = new AssignmentRequest();
        request.setUserId(userId);
        request.setPercentage(percentage);
        request.setBasisType(basis);
        request.setSourceUserId(sourceUserId);
        return request;
    }

    private com.payroll.backend.report.AgentSummaryResponse findAgent(
            com.payroll.backend.report.AdminReportResponse report,
            String username
    ) {
        return report.agentSummaries().stream()
                .filter(agent -> agent.username().equals(username))
                .findFirst()
                .orElseThrow();
    }
}
