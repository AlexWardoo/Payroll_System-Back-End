package com.payroll.backend.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    List<Merchant> findByBatchId(Long batchId);

    List<Merchant> findByBatchIdOrderByNameAsc(Long batchId);

    List<Merchant> findByExternalMerchantIdInAndBatchId(Collection<Long> externalMerchantIds, Long batchId);
}