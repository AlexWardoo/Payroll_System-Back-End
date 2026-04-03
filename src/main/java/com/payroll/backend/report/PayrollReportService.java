package com.payroll.backend.report;

import com.payroll.backend.assignment.Assignment;
import com.payroll.backend.assignment.AssignmentRepository;
import com.payroll.backend.assignment.PayoutBasis;
import com.payroll.backend.auth.AuthUserResponse;
import com.payroll.backend.month.PayrollMonth;
import com.payroll.backend.month.PayrollMonthRepository;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchantreport.MerchantReport;
import com.payroll.backend.merchantreport.MerchantReportRepository;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import com.payroll.backend.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollReportService {

    private final PayrollMonthRepository payrollMonthRepository;
    private final MerchantReportRepository merchantReportRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public AdminReportResponse getAdminReport(Long monthId) {
        PayrollMonth payrollMonth = getMonth(monthId);
        MonthSnapshot snapshot = buildSnapshot(payrollMonth);

        List<AuthUserResponse> employees = snapshot.employees().values().stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(AuthUserResponse::from)
                .toList();

        List<AgentSummaryResponse> agentSummaries = snapshot.employees().values().stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(user -> {
                    UserTotals totals = snapshot.userTotals().getOrDefault(user.getId(), UserTotals.empty());
                    return new AgentSummaryResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getDisplayName(),
                            totals.assignedAccounts(),
                            round(totals.directPayout()),
                            round(totals.overridePayout()),
                            round(totals.directAdjustments()),
                            round(totals.totalPayout())
                    );
                })
                .toList();

        List<MerchantReportResponse> merchants = snapshot.merchantReports().stream()
                .sorted(Comparator.comparing(MerchantReportResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        AdminTotalsResponse totals = new AdminTotalsResponse(
                merchants.size(),
                round(snapshot.totalSales()),
                round(snapshot.totalGross()),
                round(snapshot.totalDeductions()),
                round(snapshot.totalNet()),
                round(snapshot.totalAgentNet()),
                round(snapshot.totalRepPayout()),
                round(snapshot.adminOnlyAdjustments())
        );

        return new AdminReportResponse(PayrollMonthSummaryResponse.from(payrollMonth), totals, employees, agentSummaries, merchants);
    }

    public EmployeeReportResponse getEmployeeReport(Long monthId, Long userId) {
        PayrollMonth payrollMonth = getMonth(monthId);
        MonthSnapshot snapshot = buildSnapshot(payrollMonth);

        User user = snapshot.employees().get(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found: " + userId);
        }

        UserTotals totals = snapshot.userTotals().getOrDefault(user.getId(), UserTotals.empty());
        List<EmployeeMerchantReportResponse> merchants = snapshot.employeeMerchants().getOrDefault(user.getId(), List.of());

        List<EmployeeHistoryPointResponse> history = payrollMonthRepository.findAll().stream()
                .sorted(Comparator.comparing(PayrollMonth::getCreatedAt))
                .map(existingMonth -> {
                    MonthSnapshot existingSnapshot = buildSnapshot(existingMonth);
                    double totalPayout = existingSnapshot.userTotals()
                            .getOrDefault(user.getId(), UserTotals.empty())
                            .totalPayout();
                    return new EmployeeHistoryPointResponse(
                            existingMonth.getId(),
                            existingMonth.getLabel(),
                            round(totalPayout)
                    );
                })
                .toList();

        EmployeeSummaryResponse summary = new EmployeeSummaryResponse(
                totals.assignedAccounts(),
                round(totals.totalPayout()),
                round(totals.overridePayout()),
                round(totals.directAdjustments()),
                round(totals.salesVolume()),
                round(totals.deductions())
        );

        return new EmployeeReportResponse(
                PayrollMonthSummaryResponse.from(payrollMonth),
                AuthUserResponse.from(user),
                summary,
                merchants,
                history
        );
    }

    public byte[] exportAdminCsv(Long monthId) {
        AdminReportResponse report = getAdminReport(monthId);
        StringBuilder csv = new StringBuilder();
        csv.append("Merchant ID,Merchant,Processor,Sales Volume,Gross Profit,Deductions,Net Profit,Agent Net,Assignments\n");

        for (MerchantReportResponse merchant : report.merchants()) {
            String assignments = merchant.assignments().stream()
                    .map(item -> item.displayName() + " " + item.percentage() + "% => " + item.totalPayout())
                    .collect(Collectors.joining(" | "));

            csv.append(csvValue(merchant.merchantId()))
                    .append(',').append(csvValue(merchant.name()))
                    .append(',').append(csvValue(merchant.processor()))
                    .append(',').append(merchant.salesVolume())
                    .append(',').append(merchant.grossProfit())
                    .append(',').append(merchant.deductions())
                    .append(',').append(merchant.netProfit())
                    .append(',').append(merchant.agentNet())
                    .append(',').append(csvValue(assignments))
                    .append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportEmployeeCsv(Long monthId, Long userId) {
        EmployeeReportResponse report = getEmployeeReport(monthId, userId);
        StringBuilder csv = new StringBuilder();

        csv.append("Merchant ID,Merchant,Processor,Sales Volume,Deductions,Payout,Override Payout,Relationship");
        if (Boolean.TRUE.equals(report.employee().canViewProfit())) {
            csv.append(",Gross Profit,Net Profit,Agent Net");
        }
        csv.append('\n');

        for (EmployeeMerchantReportResponse merchant : report.merchants()) {
            csv.append(csvValue(merchant.merchantId()))
                    .append(',').append(csvValue(merchant.name()))
                    .append(',').append(csvValue(merchant.processor()))
                    .append(',').append(merchant.salesVolume())
                    .append(',').append(merchant.deductions())
                    .append(',').append(merchant.payout())
                    .append(',').append(merchant.overridePayout())
                    .append(',').append(csvValue(merchant.relationship()));

            if (Boolean.TRUE.equals(report.employee().canViewProfit())) {
                csv.append(',').append(merchant.grossProfit() == null ? "" : merchant.grossProfit())
                        .append(',').append(merchant.netProfit() == null ? "" : merchant.netProfit())
                        .append(',').append(merchant.agentNet() == null ? "" : merchant.agentNet());
            }

            csv.append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private MonthSnapshot buildSnapshot(PayrollMonth payrollMonth) {
        List<MerchantReport> merchantReportsForMonth = merchantReportRepository.findByMonthIdOrderByMerchantNameSnapshotAsc(payrollMonth.getId());
        List<Assignment> assignments = assignmentRepository.findActiveByMonthId(payrollMonth.getId());

        Map<String, MerchantReport> reportByMerchantId = merchantReportsForMonth.stream()
                .collect(Collectors.toMap(report -> report.getMerchant().getMerchantId(), Function.identity()));

        Map<String, List<Assignment>> assignmentsByMerchantId = assignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getMerchant().getMerchantId()));

        List<User> allUsers = userRepository.findAll();
        List<User> employees = allUsers.stream()
                .filter(user -> user.getRole() == UserRole.EMPLOYEE)
                .toList();

        Map<Long, User> employeeMap = employees.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, UserAccumulator> userAccumulators = new HashMap<>();
        for (User employee : employees) {
            userAccumulators.put(employee.getId(), new UserAccumulator());
        }

        List<MerchantReportResponse> merchantReports = new ArrayList<>();
        Map<Long, List<EmployeeMerchantAccumulator>> employeeMerchantMap = new HashMap<>();

        double totalSales = 0;
        double totalGross = 0;
        double totalDeductions = 0;
        double totalNet = 0;
        double totalAgentNet = 0;
        double totalRepPayout = 0;

        for (MerchantReport merchantReport : merchantReportsForMonth) {
            Merchant merchant = merchantReport.getMerchant();

            double additionsTotal = safe(merchantReport.getTotalAdditions());
            double deductionAdjustments = 0;

            double effectiveSales = safe(merchantReport.getSalesVolume());
            double effectiveGross = safe(merchantReport.getGrossProfit());
            double effectiveDeductions = safe(merchantReport.getTotalDeductions());
            double effectiveNet = safe(merchantReport.getNetProfit());
            double effectiveAgentNet = safe(merchantReport.getAgentNet());

            totalSales += effectiveSales;
            totalGross += effectiveGross;
            totalDeductions += effectiveDeductions;
            totalNet += effectiveNet;
            totalAgentNet += effectiveAgentNet;

            List<MerchantAssignmentResponse> assignmentResponses = new ArrayList<>();

            for (Assignment assignment : assignmentsByMerchantId.getOrDefault(merchant.getMerchantId(), List.of())) {
                User user = assignment.getUser();
                UserAccumulator accumulator = userAccumulators.computeIfAbsent(user.getId(), ignored -> new UserAccumulator());

                double percentage = safe(assignment.getPercentage());
                double payoutBase = payoutBase(assignment.getBasisType(), effectiveNet, effectiveAgentNet);
                double payout = payoutBase * (percentage / 100.0);

                boolean overrideAssignment = assignment.getBasisType() == PayoutBasis.AGENT_NET_OVERRIDE;
                double directPayout = overrideAssignment ? 0.0 : payout;
                double overridePayout = overrideAssignment ? payout : 0.0;

                if (!overrideAssignment) {
                    accumulator.directPayout += directPayout;
                    accumulator.addDirectMerchantMetrics(
                            merchant.getMerchantId(),
                            effectiveSales,
                            effectiveDeductions
                    );
                } else {
                    accumulator.overridePayout += overridePayout;
                }

                accumulator.totalPayout += payout;

                totalRepPayout += payout;

                String relationship = buildRelationship(assignment);

                assignmentResponses.add(new MerchantAssignmentResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        round(percentage),
                        round(directPayout),
                        round(overridePayout),
                        round(payout),
                        round(effectiveAgentNet)
                ));

                employeeMerchantMap.computeIfAbsent(user.getId(), ignored -> new ArrayList<>())
                        .add(new EmployeeMerchantAccumulator(
                                merchant,
                                effectiveSales,
                                effectiveGross,
                                effectiveDeductions,
                                effectiveNet,
                                effectiveAgentNet,
                                directPayout,
                                overridePayout,
                                relationship
                        ));
            }

            merchantReports.add(new MerchantReportResponse(
                    merchant.getMerchantId(),
                    firstNonBlank(merchantReport.getMerchantNameSnapshot(), merchant.getName()),
                    firstNonBlank(merchantReport.getProcessorSnapshot(), merchant.getProcessor()),
                    Boolean.TRUE.equals(merchantReport.getIsNew()),
                    round(effectiveSales),
                    round(effectiveGross),
                    round(effectiveDeductions),
                    round(effectiveNet),
                    round(effectiveAgentNet),
                    round(additionsTotal),
                    round(deductionAdjustments),
                    assignmentResponses.stream()
                            .sorted(Comparator.comparing(MerchantAssignmentResponse::displayName, String.CASE_INSENSITIVE_ORDER))
                            .toList()
            ));
        }
        double adminOnlyAdjustments = 0;

        Map<Long, UserTotals> totalsByUser = new HashMap<>();
        for (Map.Entry<Long, UserAccumulator> entry : userAccumulators.entrySet()) {
            UserAccumulator value = entry.getValue();
            totalsByUser.put(entry.getKey(), new UserTotals(
                    value.assignedAccounts,
                    value.directPayout,
                    value.overridePayout,
                    value.directAdjustments,
                    value.totalPayout,
                    value.salesVolume,
                    value.deductions
            ));
        }

        Map<Long, List<EmployeeMerchantReportResponse>> employeeMerchants = new HashMap<>();
        for (Map.Entry<Long, List<EmployeeMerchantAccumulator>> entry : employeeMerchantMap.entrySet()) {
            User user = employeeMap.get(entry.getKey());
            if (user == null) {
                continue;
            }

            employeeMerchants.put(entry.getKey(), entry.getValue().stream()
                    .collect(Collectors.toMap(
                            accumulator -> accumulator.merchant().getMerchantId() + ":" + accumulator.relationship(),
                            Function.identity(),
                            EmployeeMerchantAccumulator::merge
                    ))
                    .values().stream()
                    .sorted(Comparator.comparing(accumulator -> accumulator.merchant().getName(), String.CASE_INSENSITIVE_ORDER))
                    .map(accumulator -> new EmployeeMerchantReportResponse(
                        accumulator.merchant().getMerchantId(),
                        firstNonBlank(
                                reportByMerchantId.get(accumulator.merchant().getMerchantId()).getMerchantNameSnapshot(),
                                accumulator.merchant().getName()
                        ),
                        firstNonBlank(
                                reportByMerchantId.get(accumulator.merchant().getMerchantId()).getProcessorSnapshot(),
                                accumulator.merchant().getProcessor()
                        ),
                        round(accumulator.salesVolume()),
                        Boolean.TRUE.equals(user.getCanViewProfit()) ? round(accumulator.grossProfit()) : null,
                        Boolean.TRUE.equals(user.getCanViewProfit()) ? round(accumulator.netProfit()) : null,
                        Boolean.TRUE.equals(user.getCanViewProfit()) ? round(accumulator.agentNet()) : null,
                        round(accumulator.deductions()),
                        round(accumulator.ownPayout()),
                        round(accumulator.overridePayout()),
                        accumulator.relationship()
                ))
                    .toList());
        }

        return new MonthSnapshot(
                employeeMap,
                totalsByUser,
                merchantReports,
                employeeMerchants,
                totalSales,
                totalGross,
                totalDeductions,
                totalNet,
                totalAgentNet,
                totalRepPayout,
                adminOnlyAdjustments
        );
    }

    private PayrollMonth getMonth(Long monthId) {
        return payrollMonthRepository.findById(monthId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll month not found: " + monthId));
    }

    private static double payoutBase(PayoutBasis basis, double effectiveNet, double effectiveAgentNet) {
        return switch (basis) {
            case MERCHANT_NET -> effectiveNet;
            case AGENT_NET, AGENT_NET_OVERRIDE -> effectiveAgentNet;
        };
    }

    private static String buildRelationship(Assignment assignment) {
        return switch (assignment.getBasisType()) {
            case MERCHANT_NET -> "Merchant Net";
            case AGENT_NET -> "Agent Net";
            case AGENT_NET_OVERRIDE -> {
                String sourceName = assignment.getSourceUser() == null
                        ? "Unknown Source"
                        : assignment.getSourceUser().getDisplayName();
                yield "Override from " + sourceName;
            }
        };
    }

    private static double safe(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record MonthSnapshot(
            Map<Long, User> employees,
            Map<Long, UserTotals> userTotals,
            List<MerchantReportResponse> merchantReports,
            Map<Long, List<EmployeeMerchantReportResponse>> employeeMerchants,
            double totalSales,
            double totalGross,
            double totalDeductions,
            double totalNet,
            double totalAgentNet,
            double totalRepPayout,
            double adminOnlyAdjustments
    ) {
    }

    private record UserTotals(
            long assignedAccounts,
            double directPayout,
            double overridePayout,
            double directAdjustments,
            double totalPayout,
            double salesVolume,
            double deductions
    ) {
        private static UserTotals empty() {
            return new UserTotals(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class UserAccumulator {
        private long assignedAccounts;
        private double directPayout;
        private double overridePayout;
        private double directAdjustments;
        private double totalPayout;
        private double salesVolume;
        private double deductions;
        private final Set<String> directMerchantIds = new HashSet<>();

        private void addDirectMerchantMetrics(String merchantId, double salesVolume, double deductions) {
            if (directMerchantIds.add(merchantId)) {
                assignedAccounts += 1;
                this.salesVolume += salesVolume;
                this.deductions += deductions;
            }
        }
    }

    private record EmployeeMerchantAccumulator(
            Merchant merchant,
            double salesVolume,
            double grossProfit,
            double deductions,
            double netProfit,
            double agentNet,
            double ownPayout,
            double overridePayout,
            String relationship
    ) {
        private EmployeeMerchantAccumulator merge(EmployeeMerchantAccumulator other) {
            return new EmployeeMerchantAccumulator(
                    merchant,
                    salesVolume + other.salesVolume,
                    grossProfit + other.grossProfit,
                    deductions + other.deductions,
                    netProfit + other.netProfit,
                    agentNet + other.agentNet,
                    ownPayout + other.ownPayout,
                    overridePayout + other.overridePayout,
                    relationship
            );
        }
    }
}
