package com.payroll.backend.payout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutOverrideRepository extends JpaRepository<PayoutOverride, Long> {

    List<PayoutOverride> findByBeneficiaryUserId(Long beneficiaryUserId);

    List<PayoutOverride> findBySourceUserIdIn(Iterable<Long> sourceUserIds);
}
