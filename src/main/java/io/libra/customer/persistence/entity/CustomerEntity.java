package io.libra.customer.persistence.entity;

import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "country_of_residence", nullable = false, length = 2)
    private String countryOfResidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false, length = 20)
    private KycLevel kycLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", nullable = false, length = 20)
    private RiskProfile riskProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_category", nullable = false, length = 30)
    private ClientCategory clientCategory;

    @Column(name = "onboarded_at", nullable = false)
    private Instant onboardedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
