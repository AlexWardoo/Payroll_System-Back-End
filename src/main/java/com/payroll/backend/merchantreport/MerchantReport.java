package com.payroll.backend.merchantreport;

import com.payroll.backend.month.PayrollMonth;
import com.payroll.backend.merchant.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "merchant_reports")
@Getter
@Setter
public class MerchantReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "month_id", nullable = false)
    private PayrollMonth month;

    @Column(name = "merchant_name_snapshot")
    private String merchantNameSnapshot;

    @Column(name = "processor_snapshot")
    private String processorSnapshot;

    @Column(nullable = false)
    private Integer transactions = 0;

    @Column(name = "sales_volume", nullable = false, precision = 14, scale = 2)
    private BigDecimal salesVolume = BigDecimal.ZERO;

    @Column(name = "gross_profit", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossProfit = BigDecimal.ZERO;

    @Column(name = "total_additions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAdditions = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_profit", nullable = false, precision = 14, scale = 2)
    private BigDecimal netProfit = BigDecimal.ZERO;

    @Column(name = "agent_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal agentNet = BigDecimal.ZERO;

    @Column(name = "is_new", nullable = false)
    private Boolean isNew = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
