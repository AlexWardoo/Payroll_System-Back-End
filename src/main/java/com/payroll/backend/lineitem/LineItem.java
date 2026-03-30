package com.payroll.backend.lineitem;

import com.payroll.backend.merchant.Merchant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "line_items")
@Getter
@Setter
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineItemType type;

    private String description;

    @Column(nullable = false)
    private Double amount;

    private String notes;
}