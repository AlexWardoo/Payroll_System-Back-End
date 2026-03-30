package com.payroll.backend.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;

    public List<Batch> getAllBatches() {
        return batchRepository.findAll();
    }

    public Batch getLatestBatch() {
        return batchRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No batches found"));
    }

    public Batch getBatchByName(String name) {
        return batchRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + name));
    }

    public Batch createBatch(String name) {
        if (batchRepository.findByName(name).isPresent()) {
            throw new RuntimeException("Batch already exists: " + name);
        }

        Batch batch = new Batch();
        batch.setName(name);
        batch.setCreatedAt(LocalDateTime.now());

        return batchRepository.save(batch);
    }
}