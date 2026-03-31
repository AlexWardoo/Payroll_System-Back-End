package com.payroll.backend.assignment;

import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "assignments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "user_id"})
)
@Getter
@Setter
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double percentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis_type", nullable = false, length = 20)
    private PayoutBasis basisType = PayoutBasis.MERCHANT_NET;

    @ManyToOne
    @JoinColumn(name = "source_user_id")
    private User sourceUser;
}
