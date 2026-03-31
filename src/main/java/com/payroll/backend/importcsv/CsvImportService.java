package com.payroll.backend.importcsv;

import com.payroll.backend.batch.Batch;
import com.payroll.backend.batch.BatchRepository;
import com.payroll.backend.lineitem.LineItem;
import com.payroll.backend.lineitem.LineItemRepository;
import com.payroll.backend.lineitem.LineItemType;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final Pattern CSV_SPLIT_PATTERN = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final Pattern TRAILING_ID_PATTERN = Pattern.compile("\\s*\\([^)]*\\d[^)]*\\)\\s*$");

    private final BatchRepository batchRepository;
    private final MerchantRepository merchantRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public ImportSummaryResponse importCsv(MultipartFile file, String batchName) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Uploaded file is empty");
        }

        if (batchRepository.findByName(batchName).isPresent()) {
            throw new RuntimeException("Batch already exists: " + batchName);
        }

        Batch newBatch = new Batch();
        newBatch.setName(batchName);
        newBatch.setCreatedAt(LocalDateTime.now());
        Batch batch = batchRepository.save(newBatch);

        Map<Long, MerchantAggregate> merchantMap = new LinkedHashMap<>();
        List<ParsedLineItem> parsedLineItems = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            parseCsv(reader, merchantMap, parsedLineItems);
        }

        Long previousBatchId = batchRepository.findTopByOrderByIdDesc()
                .filter(existing -> !existing.getId().equals(batch.getId()))
                .map(Batch::getId)
                .orElse(null);

        Set<Long> previousMerchantIds = previousBatchId == null
                ? Set.of()
                : merchantRepository.findByBatchId(previousBatchId).stream()
                .map(Merchant::getExternalMerchantId)
                .collect(Collectors.toSet());

        List<Merchant> merchantsToSave = merchantMap.values().stream()
                .map(aggregate -> toMerchant(batch, aggregate, previousMerchantIds))
                .toList();
        List<Merchant> savedMerchants = merchantRepository.saveAll(merchantsToSave);
        Map<Long, Merchant> merchantsByExternalId = savedMerchants.stream()
                .collect(Collectors.toMap(Merchant::getExternalMerchantId, merchant -> merchant));

        Map<String, User> usersByName = userRepository.findAll().stream()
                .collect(Collectors.toMap(
                        user -> canonicalizeName(user.getDisplayName()),
                        user -> user,
                        (left, right) -> left
                ));
        userRepository.findAll().forEach(user -> usersByName.putIfAbsent(canonicalizeName(user.getUsername()), user));

        List<LineItem> lineItems = parsedLineItems.stream()
                .map(item -> toLineItem(batch, item, merchantsByExternalId, usersByName))
                .toList();
        lineItemRepository.saveAll(lineItems);

        int additionsImported = (int) lineItems.stream().filter(item -> item.getType() == LineItemType.ADDITION).count();
        int deductionsImported = (int) lineItems.stream().filter(item -> item.getType() == LineItemType.DEDUCTION).count();
        int newMerchantCount = (int) savedMerchants.stream().filter(merchant -> Boolean.TRUE.equals(merchant.getIsNew())).count();

        return new ImportSummaryResponse(
                batch.getId(),
                batch.getName(),
                savedMerchants.size(),
                newMerchantCount,
                additionsImported,
                deductionsImported
        );
    }

    private void parseCsv(
            BufferedReader reader,
            Map<Long, MerchantAggregate> merchantMap,
            List<ParsedLineItem> parsedLineItems
    ) throws IOException {
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
            if (trimmed.startsWith("\"Merchant ID\"") || trimmed.startsWith("\"MID\"") || trimmed.startsWith("\"Type\"")) {
                continue;
            }

            String[] fields = CSV_SPLIT_PATTERN.split(line, -1);
            switch (mode) {
                case "RESIDUALS" -> parseResidualRow(fields, merchantMap, currentProcessor);
                case "ADDITIONS" -> parseLineItemRow(fields, merchantMap, parsedLineItems, LineItemType.ADDITION);
                case "DEDUCTIONS" -> parseLineItemRow(fields, merchantMap, parsedLineItems, LineItemType.DEDUCTION);
                default -> {
                }
            }
        }
    }

    private void parseResidualRow(String[] fields, Map<Long, MerchantAggregate> merchantMap, String processor) {
        String merchantIdRaw = clean(fields, 0);
        if (!isNumeric(merchantIdRaw)) {
            return;
        }

        long merchantId = Long.parseLong(merchantIdRaw);
        MerchantAggregate aggregate = merchantMap.computeIfAbsent(merchantId, ignored -> new MerchantAggregate());
        aggregate.externalMerchantId = merchantId;
        aggregate.name = chooseName(aggregate.name, clean(fields, 1));
        aggregate.transactions += parseInt(fields, 2);
        aggregate.salesAmount += parseDouble(fields, 3);
        aggregate.grossProfit += parseDouble(fields, 4);
        aggregate.deductions += parseDouble(fields, 5);
        aggregate.netProfit += parseDouble(fields, 6);
        aggregate.agentNet += parseDouble(fields, 9);
        aggregate.processor = chooseName(aggregate.processor, processor);
    }

    private void parseLineItemRow(
            String[] fields,
            Map<Long, MerchantAggregate> merchantMap,
            List<ParsedLineItem> parsedLineItems,
            LineItemType type
    ) {
        String subjectRaw = clean(fields, 1);
        if (subjectRaw.equalsIgnoreCase("Total") || subjectRaw.isEmpty()) {
            return;
        }

        String merchantIdRaw = clean(fields, 0);
        Long merchantId = isNumeric(merchantIdRaw) ? Long.parseLong(merchantIdRaw) : null;

        ParsedLineItem item = new ParsedLineItem();
        item.externalMerchantId = merchantId;
        item.subjectName = stripSubjectName(subjectRaw);
        item.description = clean(fields, 2);
        item.income = parseDouble(fields, 3);
        item.expenses = parseDouble(fields, 4);
        item.net = parseDouble(fields, 5);
        item.percentage = parsePercentage(fields, 6);
        item.agentNet = parseDouble(fields, 7);
        item.type = type;
        parsedLineItems.add(item);

        if (merchantId != null) {
            MerchantAggregate aggregate = merchantMap.computeIfAbsent(merchantId, ignored -> new MerchantAggregate());
            aggregate.externalMerchantId = merchantId;
            aggregate.name = chooseName(aggregate.name, item.subjectName);
            if (aggregate.processor == null) {
                aggregate.processor = "Line Item Only";
            }
        }
    }

    private Merchant toMerchant(Batch batch, MerchantAggregate aggregate, Set<Long> previousMerchantIds) {
        Merchant merchant = new Merchant();
        merchant.setExternalMerchantId(aggregate.externalMerchantId);
        merchant.setBatch(batch);
        merchant.setName(aggregate.name == null ? String.valueOf(aggregate.externalMerchantId) : aggregate.name);
        merchant.setTransactions(aggregate.transactions);
        merchant.setSalesAmount(aggregate.salesAmount);
        merchant.setIncome(aggregate.grossProfit);
        merchant.setExpenses(aggregate.deductions);
        merchant.setNet(aggregate.netProfit);
        merchant.setAgentNet(aggregate.agentNet);
        merchant.setProcessor(aggregate.processor);
        merchant.setBps(aggregate.salesAmount == 0 ? 0.0 : (aggregate.netProfit / aggregate.salesAmount) * 10000);
        merchant.setPercentage(aggregate.netProfit == 0 ? 0.0 : (aggregate.agentNet / aggregate.netProfit) * 100);
        merchant.setIsNew(!previousMerchantIds.contains(aggregate.externalMerchantId));
        return merchant;
    }

    private LineItem toLineItem(
            Batch batch,
            ParsedLineItem item,
            Map<Long, Merchant> merchantsByExternalId,
            Map<String, User> usersByName
    ) {
        LineItem lineItem = new LineItem();
        lineItem.setBatch(batch);
        lineItem.setMerchant(item.externalMerchantId == null ? null : merchantsByExternalId.get(item.externalMerchantId));
        lineItem.setUser(resolveUser(item, usersByName));
        lineItem.setType(item.type);
        lineItem.setSubjectName(item.subjectName);
        lineItem.setDescription(item.description);
        lineItem.setAmount(legacyAmount(item));
        lineItem.setIncome(item.income);
        lineItem.setExpenses(item.expenses);
        lineItem.setNet(item.net);
        lineItem.setAgentNet(item.agentNet);
        lineItem.setPercentage(item.percentage);
        lineItem.setNotes(null);
        return lineItem;
    }

    private User resolveUser(ParsedLineItem item, Map<String, User> usersByName) {
        if (item.externalMerchantId != null) {
            return null;
        }
        return usersByName.get(canonicalizeName(item.subjectName));
    }

    private static String chooseName(String currentValue, String candidate) {
        return (currentValue == null || currentValue.isBlank()) ? candidate : currentValue;
    }

    private static double legacyAmount(ParsedLineItem item) {
        if (item.net != 0) {
            return item.net;
        }
        return item.agentNet;
    }

    private static String clean(String[] fields, int index) {
        if (index >= fields.length) {
            return "";
        }
        return fields[index].replace("\"", "").trim();
    }

    private static int parseInt(String[] fields, int index) {
        String value = clean(fields, index);
        return value.isEmpty() ? 0 : Integer.parseInt(value.replace(",", ""));
    }

    private static double parseDouble(String[] fields, int index) {
        String value = clean(fields, index);
        return value.isEmpty() ? 0.0 : Double.parseDouble(value.replace(",", ""));
    }

    private static Double parsePercentage(String[] fields, int index) {
        String value = clean(fields, index).replace("%", "");
        return value.isEmpty() ? null : Double.parseDouble(value);
    }

    private static boolean isNumeric(String value) {
        return value != null && NUMERIC_PATTERN.matcher(value).matches();
    }

    private static String stripSubjectName(String subject) {
        return TRAILING_ID_PATTERN.matcher(subject).replaceFirst("").trim();
    }

    private static String canonicalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String extractProcessor(String header) {
        String value = header.replaceFirst("^Residuals -\\s*", "");
        int idx = value.lastIndexOf('(');
        return idx > 0 ? value.substring(0, idx).trim() : value.trim();
    }

    private static final class MerchantAggregate {
        private Long externalMerchantId;
        private String name;
        private int transactions;
        private double salesAmount;
        private double grossProfit;
        private double deductions;
        private double netProfit;
        private double agentNet;
        private String processor;
    }

    private static final class ParsedLineItem {
        private Long externalMerchantId;
        private String subjectName;
        private String description;
        private double income;
        private double expenses;
        private double net;
        private Double percentage;
        private double agentNet;
        private LineItemType type;
    }
}
