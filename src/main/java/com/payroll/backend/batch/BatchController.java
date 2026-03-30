package com.payroll.backend.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @GetMapping
    public List<Batch> getAllBatches() {
        return batchService.getAllBatches();
    }

    @GetMapping("/latest")
    public Batch getLatestBatch() {
        return batchService.getLatestBatch();
    }

    @GetMapping("/by-name")
    public Batch getBatchByName(@RequestParam String name) {
        return batchService.getBatchByName(name);
    }

    @PostMapping
    public Batch createBatch(@RequestParam String name) {
        return batchService.createBatch(name);
    }
}