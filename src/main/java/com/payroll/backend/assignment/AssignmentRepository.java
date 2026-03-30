package com.payroll.backend.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByMerchantId(Long merchantId);

    List<Assignment> findByMerchantBatchId(Long batchId);

    List<Assignment> findByUserId(Long userId);

    List<Assignment> findByUserIdAndMerchantBatchId(Long userId, Long batchId);

    Optional<Assignment> findByMerchantIdAndUserId(Long merchantId, Long userId);

    boolean existsByMerchantIdAndUserId(Long merchantId, Long userId);
}