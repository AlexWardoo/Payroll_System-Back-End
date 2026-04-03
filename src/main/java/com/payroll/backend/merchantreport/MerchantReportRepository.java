package com.payroll.backend.merchantreport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantReportRepository extends JpaRepository<MerchantReport, Long> {

    List<MerchantReport> findByMonthId(Long monthId);

    List<MerchantReport> findByMonthIdOrderByMerchantNameSnapshotAsc(Long monthId);

    java.util.Optional<MerchantReport> findByMerchantMerchantIdAndMonthId(String merchantId, Long monthId);
}
