package com.payroll.backend.report;

import com.payroll.backend.assignment.Assignment;
import com.payroll.backend.assignment.AssignmentRepository;
import com.payroll.backend.auth.AuthUserResponse;
import com.payroll.backend.batch.Batch;
import com.payroll.backend.batch.BatchRepository;
import com.payroll.backend.lineitem.LineItem;
import com.payroll.backend.lineitem.LineItemRepository;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.payout.PayoutOverride;
import com.payroll.backend.payout.PayoutOverrideRepository;
import com.payroll.backend.user.PayoutBasis;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import com.payroll.backend.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollReportService {

    private final BatchRepository batchRepository;
    private final MerchantRepository merchantRepository;
    private final AssignmentRepository assignmentRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;
    private final PayoutOverrideRepository payoutOverrideRepository;

    public AdminReportResponse getAdminReport(Long batchId) {
        Batch batch = getBatch(batchId);
        BatchSnapshot snapshot = buildSnapshot(batch);

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
                            round(totals.ownPayout()),
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

        return new AdminReportResponse(BatchSummaryResponse.from(batch), totals, employees, agentSummaries, merchants);
    }

    public EmployeeReportResponse getEmployeeReport(Long batchId, Long userId) {
        Batch batch = getBatch(batchId);
        BatchSnapshot snapshot = buildSnapshot(batch);
        User user = snapshot.employees().get(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found: " + userId);
        }

        UserTotals totals = snapshot.userTotals().getOrDefault(user.getId(), UserTotals.empty());
        List<EmployeeMerchantReportResponse> merchants = snapshot.employeeMerchants().getOrDefault(user.getId(), List.of());

        List<EmployeeHistoryPointResponse> history = batchRepository.findAll().stream()
                .sorted(Comparator.comparing(Batch::getCreatedAt))
                .map(existingBatch -> {
                    BatchSnapshot existingSnapshot = buildSnapshot(existingBatch);
                    double totalPayout = existingSnapshot.userTotals()
                            .getOrDefault(user.getId(), UserTotals.empty())
                            .totalPayout();
                    return new EmployeeHistoryPointResponse(existingBatch.getId(), existingBatch.getName(), round(totalPayout));
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
                BatchSummaryResponse.from(batch),
                AuthUserResponse.from(user),
                summary,
                merchants,
                history
        );
    }

    public byte[] exportAdminCsv(Long batchId) {
        AdminReportResponse report = getAdminReport(batchId);
        StringBuilder csv = new StringBuilder();
        csv.append("Merchant ID,Merchant,Processor,Sales Volume,Gross Profit,Deductions,Net Profit,Agent Net,Assignments\n");
        for (MerchantReportResponse merchant : report.merchants()) {
            String assignments = merchant.assignments().stream()
                    .map(item -> item.displayName() + " " + item.percentage() + "% => " + item.totalPayout())
                    .collect(Collectors.joining(" | "));
            csv.append(csvValue(merchant.externalMerchantId()))
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

    public byte[] exportEmployeeCsv(Long batchId, Long userId) {
        EmployeeReportResponse report = getEmployeeReport(batchId, userId);
        StringBuilder csv = new StringBuilder();
        csv.append("Merchant ID,Merchant,Processor,Sales Volume,Deductions,Payout,Override Payout,Relationship");
        if (Boolean.TRUE.equals(report.employee().canViewProfit())) {
            csv.append(",Gross Profit,Net Profit");
        }
        csv.append('\n');
        for (EmployeeMerchantReportResponse merchant : report.merchants()) {
            csv.append(csvValue(merchant.externalMerchantId()))
                    .append(',').append(csvValue(merchant.name()))
                    .append(',').append(csvValue(merchant.processor()))
                    .append(',').append(merchant.salesVolume())
                    .append(',').append(merchant.deductions())
                    .append(',').append(merchant.payout())
                    .append(',').append(merchant.overridePayout())
                    .append(',').append(csvValue(merchant.relationship()));
            if (Boolean.TRUE.equals(report.employee().canViewProfit())) {
                csv.append(',').append(merchant.grossProfit() == null ? "" : merchant.grossProfit())
                        .append(',').append(merchant.netProfit() == null ? "" : merchant.netProfit());
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private BatchSnapshot buildSnapshot(Batch batch) {
        List<Merchant> merchants = merchantRepository.findByBatchIdOrderByNameAsc(batch.getId());
        List<Assignment> assignments = assignmentRepository.findByMerchantBatchId(batch.getId());
        List<LineItem> lineItems = lineItemRepository.findByBatchId(batch.getId());
        Map<Long, List<Assignment>> assignmentsByMerchantId = assignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getMerchant().getId()));

        Set<Long> employeeIds = assignments.stream()
                .map(assignment -> assignment.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        lineItems.stream()
                .map(LineItem::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .forEach(employeeIds::add);

        List<User> allUsers = userRepository.findAll();
        List<User> employees = allUsers.stream()
                .filter(user -> user.getRole() == UserRole.EMPLOYEE)
                .toList();
        Map<Long, User> employeeMap = employees.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, User> allUsersById = allUsers.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, List<LineItem>> merchantLineItems = lineItems.stream()
                .filter(item -> item.getMerchant() != null)
                .collect(Collectors.groupingBy(item -> item.getMerchant().getId()));
        Map<Long, List<LineItem>> directUserAdjustments = lineItems.stream()
                .filter(item -> item.getMerchant() == null && item.getUser() != null)
                .collect(Collectors.groupingBy(item -> item.getUser().getId()));

        Map<Long, List<PayoutOverride>> overridesBySourceUserId = payoutOverrideRepository.findBySourceUserIdIn(employeeIds).stream()
                .collect(Collectors.groupingBy(override -> override.getSourceUser().getId()));

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

        for (Merchant merchant : merchants) {
            List<LineItem> merchantItems = merchantLineItems.getOrDefault(merchant.getId(), List.of());
            double additionsTotal = merchantItems.stream()
                    .filter(item -> item.getType().name().equals("ADDITION"))
                    .mapToDouble(LineItem::getNet)
                    .sum();
            double deductionAdjustments = merchantItems.stream()
                    .filter(item -> item.getType().name().equals("DEDUCTION"))
                    .mapToDouble(item -> Math.abs(item.getNet()))
                    .sum();
            double effectiveSales = safe(merchant.getSalesAmount());
            double effectiveGross = safe(merchant.getIncome()) + merchantItems.stream().mapToDouble(LineItem::getIncome).sum();
            double effectiveDeductions = safe(merchant.getExpenses()) + merchantItems.stream().mapToDouble(LineItem::getExpenses).sum();
            double effectiveNet = safe(merchant.getNet()) + merchantItems.stream().mapToDouble(LineItem::getNet).sum();
            double effectiveAgentNet = safe(merchant.getAgentNet()) + merchantItems.stream().mapToDouble(LineItem::getAgentNet).sum();

            totalSales += effectiveSales;
            totalGross += effectiveGross;
            totalDeductions += effectiveDeductions;
            totalNet += effectiveNet;
            totalAgentNet += effectiveAgentNet;

            List<MerchantAssignmentResponse> assignmentResponses = new ArrayList<>();
            for (Assignment assignment : assignmentsByMerchantId.getOrDefault(merchant.getId(), List.of())) {
                User user = assignment.getUser();
                double allocation = safe(assignment.getPercentage()) / 100.0;
                double ownBase = payoutBase(user.getPayoutBasis(), effectiveNet, effectiveAgentNet);
                double ownPayout = ownBase * allocation * safe(user.getPayoutRate()) / 100.0;
                double overridePayout = 0;

                for (PayoutOverride override : overridesBySourceUserId.getOrDefault(user.getId(), List.of())) {
                    User beneficiary = override.getBeneficiaryUser();
                    UserAccumulator beneficiaryAccumulator = userAccumulators.computeIfAbsent(beneficiary.getId(), ignored -> new UserAccumulator());
                    double payout = ownBase * allocation * safe(override.getPercentage()) / 100.0;
                    overridePayout += payout;
                    beneficiaryAccumulator.overridePayout += payout;
                    beneficiaryAccumulator.totalPayout += payout;
                    beneficiaryAccumulator.salesVolume += effectiveSales;
                    beneficiaryAccumulator.deductions += effectiveDeductions;
                    totalRepPayout += payout;
                    employeeMerchantMap.computeIfAbsent(beneficiary.getId(), ignored -> new ArrayList<>())
                            .add(new EmployeeMerchantAccumulator(merchant, effectiveSales, effectiveGross, effectiveDeductions, effectiveNet, 0, payout, "Override from " + user.getDisplayName()));
                }

                UserAccumulator accumulator = userAccumulators.computeIfAbsent(user.getId(), ignored -> new UserAccumulator());
                accumulator.assignedAccounts += 1;
                accumulator.ownPayout += ownPayout;
                accumulator.totalPayout += ownPayout;
                accumulator.salesVolume += effectiveSales;
                accumulator.deductions += effectiveDeductions;
                totalRepPayout += ownPayout;

                assignmentResponses.add(new MerchantAssignmentResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        round(safe(assignment.getPercentage())),
                        round(ownPayout),
                        round(overridePayout),
                        round(ownPayout + overridePayout)
                ));

                employeeMerchantMap.computeIfAbsent(user.getId(), ignored -> new ArrayList<>())
                        .add(new EmployeeMerchantAccumulator(merchant, effectiveSales, effectiveGross, effectiveDeductions, effectiveNet, ownPayout, 0, "Assigned"));
            }

            merchantReports.add(new MerchantReportResponse(
                    merchant.getId(),
                    merchant.getExternalMerchantId(),
                    merchant.getName(),
                    merchant.getProcessor(),
                    Boolean.TRUE.equals(merchant.getIsNew()),
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

        double adminOnlyAdjustments = lineItems.stream()
                .filter(item -> item.getMerchant() == null && item.getUser() == null)
                .mapToDouble(LineItem::getAgentNet)
                .sum();

        for (Map.Entry<Long, List<LineItem>> entry : directUserAdjustments.entrySet()) {
            double directAdjustment = entry.getValue().stream()
                    .mapToDouble(LineItem::getAgentNet)
                    .sum();
            User adjustedUser = allUsersById.get(entry.getKey());
            if (adjustedUser != null && adjustedUser.getRole() == UserRole.EMPLOYEE) {
                UserAccumulator accumulator = userAccumulators.computeIfAbsent(entry.getKey(), ignored -> new UserAccumulator());
                accumulator.directAdjustments += directAdjustment;
                accumulator.totalPayout += directAdjustment;
                totalRepPayout += directAdjustment;
            } else {
                adminOnlyAdjustments += directAdjustment;
            }
        }

        Map<Long, UserTotals> totalsByUser = new HashMap<>();
        for (Map.Entry<Long, UserAccumulator> entry : userAccumulators.entrySet()) {
            UserAccumulator value = entry.getValue();
            totalsByUser.put(entry.getKey(), new UserTotals(
                    value.assignedAccounts,
                    value.ownPayout,
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
                            accumulator -> accumulator.merchant().getId() + ":" + accumulator.relationship(),
                            Function.identity(),
                            EmployeeMerchantAccumulator::merge
                    ))
                    .values().stream()
                    .sorted(Comparator.comparing(accumulator -> accumulator.merchant().getName(), String.CASE_INSENSITIVE_ORDER))
                    .map(accumulator -> new EmployeeMerchantReportResponse(
                            accumulator.merchant().getId(),
                            accumulator.merchant().getExternalMerchantId(),
                            accumulator.merchant().getName(),
                            accumulator.merchant().getProcessor(),
                            round(accumulator.salesVolume()),
                            Boolean.TRUE.equals(user.getCanViewProfit()) ? round(accumulator.grossProfit()) : null,
                            Boolean.TRUE.equals(user.getCanViewProfit()) ? round(accumulator.netProfit()) : null,
                            round(accumulator.deductions()),
                            round(accumulator.ownPayout()),
                            round(accumulator.overridePayout()),
                            accumulator.relationship()
                    ))
                    .toList());
        }

        return new BatchSnapshot(
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

    private Batch getBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + batchId));
    }

    private static double payoutBase(PayoutBasis basis, double effectiveNet, double effectiveAgentNet) {
        return basis == PayoutBasis.AGENT_NET ? effectiveAgentNet : effectiveNet;
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String csvValue(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record BatchSnapshot(
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
            double ownPayout,
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
        private double ownPayout;
        private double overridePayout;
        private double directAdjustments;
        private double totalPayout;
        private double salesVolume;
        private double deductions;
    }

    private record EmployeeMerchantAccumulator(
            Merchant merchant,
            double salesVolume,
            double grossProfit,
            double deductions,
            double netProfit,
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
                    ownPayout + other.ownPayout,
                    overridePayout + other.overridePayout,
                    relationship
            );
        }
    }
}
