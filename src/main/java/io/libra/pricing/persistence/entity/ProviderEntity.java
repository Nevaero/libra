package io.libra.pricing.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "providers")
public class ProviderEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 32, unique = true)
    private String code;
}
