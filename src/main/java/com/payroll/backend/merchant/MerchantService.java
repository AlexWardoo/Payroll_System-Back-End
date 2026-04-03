package com.payroll.backend.merchant;

import com.payroll.backend.merchantreport.MerchantReport;
import com.payroll.backend.merchantreport.MerchantReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantReportRepository merchantReportRepository;

    public List<Merchant> getMerchantsForMonth(Long monthId) {
        return merchantReportRepository.findByMonthIdOrderByMerchantNameSnapshotAsc(monthId).stream()
                .map(MerchantReport::getMerchant)
                .distinct()
                .toList();
    }

    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
    }

}
