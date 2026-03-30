package com.payroll.backend.merchant;

import com.payroll.backend.batch.Batch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "merchants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"external_merchant_id", "batch_id"})
)
@Getter
@Setter
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_merchant_id", nullable = false)
    private Long externalMerchantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @Column(nullable = false)
    private String name;

    private Integer transactions;

    @Column(name = "sales_amount")
    private Double salesAmount;

    private Double income;
    private Double expenses;
    private Double net;
    private Double bps;
    private Double percentage;

    @Column(name = "agent_net")
    private Double agentNet;

    @Column(name = "is_new", nullable = false)
    private Boolean isNew = false;

    private String processor;
}