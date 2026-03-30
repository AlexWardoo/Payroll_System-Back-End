package com.payroll.backend.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public List<Merchant> getMerchantsForBatch(Long batchId) {
        return merchantRepository.findByBatchIdOrderByNameAsc(batchId);
    }

    public Merchant getMerchant(Long id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
    }

}