package com.payroll.backend.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByMerchantMerchantIdAndActiveTrue(String merchantId);

    List<Assignment> findByMerchantMerchantIdAndActiveTrueOrderByIdAsc(String merchantId);

    @Query("""
            select a
            from Assignment a
            join MerchantReport mr on mr.merchant = a.merchant
            where mr.month.id = :monthId and a.active = true
            """)
    List<Assignment> findActiveByMonthId(Long monthId);

    List<Assignment> findByUserIdAndActiveTrue(Long userId);

    @Query("""
            select a
            from Assignment a
            join MerchantReport mr on mr.merchant = a.merchant
            where a.user.id = :userId and mr.month.id = :monthId and a.active = true
            """)
    List<Assignment> findByUserIdAndMonthId(Long userId, Long monthId);

    Optional<Assignment> findByMerchantMerchantIdAndUserIdAndActiveTrue(String merchantId, Long userId);

    List<Assignment> findByMerchantMerchantIdAndUserIdAndActiveTrueOrderByIdAsc(String merchantId, Long userId);

    Optional<Assignment> findByMerchantMerchantIdAndUserIdAndBasisTypeAndSourceUserIdAndActiveTrue(
            String merchantId,
            Long userId,
            PayoutBasis basisType,
            Long sourceUserId
    );

    boolean existsByMerchantMerchantIdAndUserIdAndBasisTypeAndSourceUserIdAndActiveTrue(
            String merchantId,
            Long userId,
            PayoutBasis basisType,
            Long sourceUserId
    );
}
