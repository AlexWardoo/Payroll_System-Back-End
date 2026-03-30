package com.payroll.backend.importcsv;

import com.payroll.backend.batch.Batch;
import com.payroll.backend.batch.BatchRepository;
import com.payroll.backend.lineitem.LineItem;
import com.payroll.backend.lineitem.LineItemRepository;
import com.payroll.backend.lineitem.LineItemType;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final BatchRepository batchRepository;
    private final MerchantRepository merchantRepository;
    private final LineItemRepository lineItemRepository;

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

        Map<Long, MerchantAggregate> merchantMap = new HashMap<>();
        List<ParsedLineItem> parsedAdditions = new ArrayList<>();
        List<ParsedLineItem> parsedDeductions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            parseCsv(reader, merchantMap, parsedAdditions, parsedDeductions);
        }

        Long previousBatchId = batchRepository.findTopByOrderByIdDesc()
                .filter(b -> !b.getId().equals(batch.getId()))
                .map(Batch::getId)
                .orElseGet(() -> {
                    List<Batch> batches = batchRepository.findAll().stream()
                            .filter(b -> !b.getId().equals(batch.getId()))
                            .sorted(Comparator.comparing(Batch::getId).reversed())
                            .toList();
                    return batches.isEmpty() ? null : batches.get(0).getId();
                });

        Set<Long> previousMerchantIds = new HashSet<>();
        if (previousBatchId != null) {
            previousMerchantIds = merchantRepository.findByBatchId(previousBatchId).stream()
                    .map(Merchant::getExternalMerchantId)
                    .collect(Collectors.toSet());
        }

        List<Merchant> merchantsToSave = new ArrayList<>();
        for (MerchantAggregate agg : merchantMap.values()) {
            Merchant merchant = new Merchant();
            merchant.setExternalMerchantId(agg.externalMerchantId);
            merchant.setBatch(batch);
            merchant.setName(agg.name);
            merchant.setTransactions(agg.transactions);
            merchant.setSalesAmount(agg.salesAmount);
            merchant.setIncome(agg.income);
            merchant.setExpenses(agg.expenses);
            merchant.setNet(agg.net);
            merchant.setBps(agg.bps);
            merchant.setPercentage(agg.percentage);
            merchant.setAgentNet(agg.agentNet);
            merchant.setProcessor(agg.processor);
            merchant.setIsNew(!previousMerchantIds.contains(agg.externalMerchantId));
            merchantsToSave.add(merchant);
        }

        List<Merchant> savedMerchants = merchantRepository.saveAll(merchantsToSave);

        Map<Long, Merchant> savedMerchantByExternalId = savedMerchants.stream()
                .collect(Collectors.toMap(Merchant::getExternalMerchantId, m -> m));

        List<LineItem> lineItemsToSave = new ArrayList<>();

        for (ParsedLineItem item : parsedDeductions) {
            Merchant merchant = savedMerchantByExternalId.get(item.externalMerchantId);
            if (merchant != null) {
                LineItem lineItem = new LineItem();
                lineItem.setMerchant(merchant);
                lineItem.setType(LineItemType.DEDUCTION);
                lineItem.setDescription(item.description);
                lineItem.setAmount(item.amount);
                lineItemsToSave.add(lineItem);
            }
        }

        for (ParsedLineItem item : parsedAdditions) {
            Merchant merchant = savedMerchantByExternalId.get(item.externalMerchantId);
            if (merchant != null) {
                LineItem lineItem = new LineItem();
                lineItem.setMerchant(merchant);
                lineItem.setType(LineItemType.ADDITION);
                lineItem.setDescription(item.description);
                lineItem.setAmount(item.amount);
                lineItemsToSave.add(lineItem);
            }
        }

        lineItemRepository.saveAll(lineItemsToSave);

        int newMerchantCount = (int) savedMerchants.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsNew()))
                .count();

        return new ImportSummaryResponse(
                batch.getId(),
                batch.getName(),
                savedMerchants.size(),
                newMerchantCount,
                parsedAdditions.size(),
                parsedDeductions.size()
        );
    }

    private void parseCsv(
            BufferedReader reader,
            Map<Long, MerchantAggregate> merchantMap,
            List<ParsedLineItem> additions,
            List<ParsedLineItem> deductions
    ) throws IOException {

        String line;
        boolean isHeaderRow = false;
        String mode = "NONE";

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()
                    || line.contains("Records exported:")
                    || line.startsWith("\"\"")
                    || line.startsWith("\"Type\"")
                    || line.contains("Grand Total")) {
                continue;
            }

            if (line.contains("Residuals -")) {
                mode = "RESIDUALS";
                isHeaderRow = true;
                continue;
            } else if (line.contains("Lineitems - Deductions")) {
                mode = "DEDUCTIONS";
                isHeaderRow = true;
                continue;
            } else if (line.contains("Lineitems - Additions")) {
                mode = "ADDITIONS";
                isHeaderRow = true;
                continue;
            }

            if (line.startsWith("\"Merchant ID") || line.startsWith("\"MID") || isHeaderRow) {
                isHeaderRow = false;
                continue;
            }

            String[] fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (fields.length < 6) {
                continue;
            }

            try {
                String idRaw = clean(fields[0]);
                if (idRaw.isEmpty()) {
                    continue;
                }

                long externalMerchantId = Long.parseLong(idRaw);

                if ("RESIDUALS".equals(mode)) {
                    MerchantAggregate agg = merchantMap.computeIfAbsent(
                            externalMerchantId,
                            id -> new MerchantAggregate()
                    );

                    agg.externalMerchantId = externalMerchantId;
                    agg.name = fields.length > 1 ? clean(fields[1]) : "";
                    agg.transactions += parseInt(fields, 2);
                    agg.salesAmount += parseDouble(fields, 3);
                    agg.income += parseDouble(fields, 4);
                    agg.expenses += parseDouble(fields, 5);
                    agg.net += parseDouble(fields, 6);
                    agg.bps += parseDouble(fields, 7);
                    agg.percentage += parseDouble(fields, 8);
                    agg.agentNet += parseDouble(fields, 9);
                } else if ("DEDUCTIONS".equals(mode) || "ADDITIONS".equals(mode)) {
                    String description = fields.length > 2 ? clean(fields[2]) : "";
                    double amount = parseDouble(fields, 5);

                    ParsedLineItem item = new ParsedLineItem();
                    item.externalMerchantId = externalMerchantId;
                    item.description = description;
                    item.amount = amount;

                    if ("DEDUCTIONS".equals(mode)) {
                        deductions.add(item);
                    } else {
                        additions.add(item);
                    }
                }

            } catch (Exception ignored) {
            }
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\"", "").trim();
    }

    private int parseInt(String[] fields, int index) {
        if (index >= fields.length) return 0;
        String value = clean(fields[index]);
        if (value.isEmpty()) return 0;
        return Integer.parseInt(value);
    }

    private double parseDouble(String[] fields, int index) {
        if (index >= fields.length) return 0.0;
        String value = clean(fields[index]);
        if (value.isEmpty()) return 0.0;
        return Double.parseDouble(value);
    }

    private static class MerchantAggregate {
        Long externalMerchantId;
        String name;
        int transactions;
        double salesAmount;
        double income;
        double expenses;
        double net;
        double bps;
        double percentage;
        double agentNet;
        String processor;
    }

    private static class ParsedLineItem {
        Long externalMerchantId;
        String description;
        double amount;
    }
}