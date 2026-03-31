package com.payroll.backend.lineitem;

import com.payroll.backend.batch.Batch;
import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.user.User;
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
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineItemType type;

    private String description;

    @Column(name = "subject_name")
    private String subjectName;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Double income;

    @Column(nullable = false)
    private Double expenses;

    @Column(nullable = false)
    private Double net;

    @Column(name = "agent_net", nullable = false)
    private Double agentNet;

    private Double percentage;

    private String notes;
}
