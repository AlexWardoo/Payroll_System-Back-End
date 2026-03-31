package com.payroll.backend.lineitem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LineItemRepository extends JpaRepository<LineItem, Long> {

    List<LineItem> findByBatchId(Long batchId);

    List<LineItem> findByMerchantIdIn(Collection<Long> merchantIds);
}
