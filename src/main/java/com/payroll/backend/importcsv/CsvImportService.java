package com.payroll.backend.importcsv;

import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.merchantreport.MerchantReport;
import com.payroll.backend.merchantreport.MerchantReportRepository;
import com.payroll.backend.month.PayrollMonth;
import com.payroll.backend.month.PayrollMonthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final Pattern CSV_SPLIT_PATTERN =
            Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    private static final Pattern TRAILING_ID_PATTERN =
            Pattern.compile("\\s*\\([^)]*\\d[^)]*\\)\\s*$");

    private final PayrollMonthRepository payrollMonthRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantReportRepository merchantReportRepository;

    @Transactional
    public ImportSummaryResponse importCsv(MultipartFile file, Long monthId, String newMonthLabel) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Uploaded file is empty");
        }

        PayrollMonth payrollMonth = resolveMonth(monthId, newMonthLabel);

        Map<String, MerchantAggregate> merchantMap = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            parseCsv(reader, merchantMap);
        }

        Long previousMonthId = payrollMonthRepository.findTopByIdLessThanOrderByIdDesc(payrollMonth.getId())
                .map(PayrollMonth::getId)
                .orElse(null);

        Set<String> previousMerchantIds = previousMonthId == null
                ? Set.of()
                : merchantReportRepository.findByMonthId(previousMonthId).stream()
                .map(report -> report.getMerchant().getMerchantId())
                .collect(Collectors.toSet());

        int newMerchantCount = 0;

        for (MerchantAggregate aggregate : merchantMap.values()) {
            Merchant merchant = merchantRepository.findById(aggregate.merchantId)
                    .orElseGet(() -> buildMerchantIdentity(aggregate));

            merchant.setName(
                    isBlank(merchant.getName()) ? fallbackMerchantName(aggregate) : merchant.getName()
            );

            if (isBlank(merchant.getProcessor()) && !isBlank(aggregate.processor)) {
                merchant.setProcessor(aggregate.processor);
            }

            merchant = merchantRepository.save(merchant);

            boolean isNew = !previousMerchantIds.contains(aggregate.merchantId);
            if (isNew) {
                newMerchantCount++;
            }

            MerchantReport report = merchantReportRepository
                    .findByMerchantMerchantIdAndMonthId(aggregate.merchantId, payrollMonth.getId())
                    .orElseGet(MerchantReport::new);

            boolean existingReport = report.getId() != null;

            report.setMerchant(merchant);
            report.setMonth(payrollMonth);
            report.setMerchantNameSnapshot(firstNonBlank(fallbackMerchantName(aggregate), report.getMerchantNameSnapshot()));
            report.setProcessorSnapshot(firstNonBlank(aggregate.processor, report.getProcessorSnapshot()));
            report.setTransactions((existingReport ? report.getTransactions() : 0) + aggregate.transactions);
            report.setSalesVolume(sumMoney(report.getSalesVolume(), aggregate.salesVolume));
            report.setGrossProfit(sumMoney(report.getGrossProfit(), aggregate.grossProfit));
            report.setTotalAdditions(sumMoney(report.getTotalAdditions(), aggregate.totalAdditions));
            report.setTotalDeductions(sumMoney(report.getTotalDeductions(), aggregate.totalDeductions));
            report.setNetProfit(sumMoney(report.getNetProfit(), aggregate.netProfit));
            report.setAgentNet(sumMoney(report.getAgentNet(), aggregate.agentNet));
            report.setIsNew(Boolean.TRUE.equals(report.getIsNew()) || isNew);

            merchantReportRepository.save(report);
        }

        return new ImportSummaryResponse(
                payrollMonth.getId(),
                payrollMonth.getLabel(),
                merchantMap.size(),
                newMerchantCount,
                merchantMap.values().stream().mapToInt(a -> a.additionCount).sum(),
                merchantMap.values().stream().mapToInt(a -> a.deductionCount).sum()
        );
    }

    private PayrollMonth resolveMonth(Long monthId, String newMonthLabel) {
        boolean hasExistingMonth = monthId != null;
        boolean hasNewMonthLabel = !isBlank(newMonthLabel);

        if (hasExistingMonth == hasNewMonthLabel) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose exactly one payroll month option"
            );
        }

        if (hasExistingMonth) {
            return payrollMonthRepository.findById(monthId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Payroll month not found: " + monthId
                    ));
        }

        String normalizedLabel = newMonthLabel.trim();
        if (payrollMonthRepository.findByLabel(normalizedLabel).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payroll month already exists: " + normalizedLabel
            );
        }

        PayrollMonth payrollMonth = new PayrollMonth();
        payrollMonth.setLabel(normalizedLabel);
        payrollMonth.setCreatedAt(LocalDateTime.now());
        return payrollMonthRepository.save(payrollMonth);
    }

    private void parseCsv(BufferedReader reader, Map<String, MerchantAggregate> merchantMap) throws IOException {
        String mode = "NONE";
        String currentProcessor = null;
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("Records exported:")) {
                continue;
            }

            if (trimmed.startsWith("Grand Total")) {
                break;
            }

            if (trimmed.startsWith("Residuals -")) {
                mode = "RESIDUALS";
                currentProcessor = extractProcessor(trimmed);
                continue;
            }

            if (trimmed.startsWith("Lineitems - Additions")) {
                mode = "ADDITIONS";
                continue;
            }

            if (trimmed.startsWith("Lineitems - Deductions")) {
                mode = "DEDUCTIONS";
                continue;
            }

            if (trimmed.startsWith("\"Merchant ID\"")
                    || trimmed.startsWith("\"MID\"")
                    || trimmed.startsWith("\"Type\"")) {
                continue;
            }

            String[] fields = CSV_SPLIT_PATTERN.split(line, -1);

            switch (mode) {
                case "RESIDUALS" -> parseResidualRow(fields, merchantMap, currentProcessor);
                case "ADDITIONS" -> parseLineItemRow(fields, merchantMap, true);
                case "DEDUCTIONS" -> parseLineItemRow(fields, merchantMap, false);
                default -> {
                }
            }
        }
    }

    private void parseResidualRow(String[] fields, Map<String, MerchantAggregate> merchantMap, String processor) {
        String merchantId = clean(fields, 0);
        if (merchantId.isBlank()) {
            return;
        }

        MerchantAggregate aggregate = merchantMap.computeIfAbsent(
                merchantId,
                ignored -> new MerchantAggregate(merchantId)
        );

        aggregate.name = chooseName(aggregate.name, clean(fields, 1));
        aggregate.transactions += parseInt(fields, 2);
        aggregate.salesVolume = aggregate.salesVolume.add(parseMoney(fields, 3));
        aggregate.grossProfit = aggregate.grossProfit.add(parseMoney(fields, 4));

        BigDecimal residualDeductions = parseMoney(fields, 5);
        aggregate.totalDeductions = aggregate.totalDeductions.add(residualDeductions);

        aggregate.netProfit = aggregate.netProfit.add(parseMoney(fields, 6));
        aggregate.agentNet = aggregate.agentNet.add(parseMoney(fields, 9));
        aggregate.processor = chooseName(aggregate.processor, processor);
    }

    private void parseLineItemRow(String[] fields, Map<String, MerchantAggregate> merchantMap, boolean isAddition) {
        String subjectRaw = clean(fields, 1);
        if (subjectRaw.isBlank() || subjectRaw.equalsIgnoreCase("Total")) {
            return;
        }

        String merchantId = clean(fields, 0);
        if (merchantId.isBlank()) {
            return;
        }

        MerchantAggregate aggregate = merchantMap.computeIfAbsent(
                merchantId,
                ignored -> new MerchantAggregate(merchantId)
        );

        String subjectName = stripSubjectName(subjectRaw);
        aggregate.name = chooseName(aggregate.name, subjectName);

        if (isBlank(aggregate.processor)) {
            aggregate.processor = "Line Item Only";
        }

        BigDecimal income = parseMoney(fields, 3);
        BigDecimal expenses = parseMoney(fields, 4);
        BigDecimal net = parseMoney(fields, 5);
        BigDecimal agentNet = parseMoney(fields, 7);

        BigDecimal amount = !isZero(net) ? net : agentNet;
        if (isZero(amount)) {
            amount = income.subtract(expenses);
        }

        if (isAddition) {
            aggregate.totalAdditions = aggregate.totalAdditions.add(abs(amount));
            aggregate.additionCount++;
        } else {
            aggregate.totalDeductions = aggregate.totalDeductions.add(abs(amount));
            aggregate.deductionCount++;
        }
    }

    private Merchant buildMerchantIdentity(MerchantAggregate aggregate) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(aggregate.merchantId);
        merchant.setName(fallbackMerchantName(aggregate));
        merchant.setProcessor(aggregate.processor);
        merchant.setActive(true);
        return merchant;
    }

    private static String fallbackMerchantName(MerchantAggregate aggregate) {
        return isBlank(aggregate.name) ? aggregate.merchantId : aggregate.name;
    }

    private static String chooseName(String currentValue, String candidate) {
        if (!isBlank(currentValue)) {
            return currentValue;
        }
        return isBlank(candidate) ? currentValue : candidate;
    }

    private static String clean(String[] fields, int index) {
        if (index >= fields.length) {
            return "";
        }
        return fields[index].replace("\"", "").trim();
    }

    private static int parseInt(String[] fields, int index) {
        String value = clean(fields, index).replace(",", "");
        if (value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static BigDecimal parseMoney(String[] fields, int index) {
        String value = clean(fields, index).replace(",", "");
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumMoney(BigDecimal existingValue, BigDecimal incomingValue) {
        return scaleMoney(
                (existingValue == null ? BigDecimal.ZERO : existingValue)
                        .add(incomingValue == null ? BigDecimal.ZERO : incomingValue)
        );
    }

    private static BigDecimal abs(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return fallback;
    }

    private static String stripSubjectName(String subject) {
        return TRAILING_ID_PATTERN.matcher(subject).replaceFirst("").trim();
    }

    private static String extractProcessor(String header) {
        String value = header.replaceFirst("^Residuals -\\s*", "");
        int idx = value.lastIndexOf('(');
        return idx > 0 ? value.substring(0, idx).trim() : value.trim();
    }

    private static final class MerchantAggregate {
        private final String merchantId;
        private String name;
        private int transactions;
        private BigDecimal salesVolume = BigDecimal.ZERO;
        private BigDecimal grossProfit = BigDecimal.ZERO;
        private BigDecimal totalAdditions = BigDecimal.ZERO;
        private BigDecimal totalDeductions = BigDecimal.ZERO;
        private BigDecimal netProfit = BigDecimal.ZERO;
        private BigDecimal agentNet = BigDecimal.ZERO;
        private String processor;
        private int additionCount;
        private int deductionCount;

        private MerchantAggregate(String merchantId) {
            this.merchantId = merchantId;
        }
    }
}
