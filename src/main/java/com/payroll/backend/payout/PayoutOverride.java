package com.payroll.backend.payout;

import com.payroll.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "payout_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"beneficiary_user_id", "source_user_id"})
)
@Getter
@Setter
public class PayoutOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "beneficiary_user_id", nullable = false)
    private User beneficiaryUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_user_id", nullable = false)
    private User sourceUser;

    @Column(nullable = false)
    private Double percentage;
}
