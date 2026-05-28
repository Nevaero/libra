package io.libra.reference.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode(of = "code")
@ToString(of = "code")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "currencies")
public class CurrencyEntity {

    @Id
    @Column(nullable = false, length = 3)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private int decimals;
}
