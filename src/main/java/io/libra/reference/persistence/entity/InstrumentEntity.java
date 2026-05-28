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

import java.util.UUID;

// Parent table for the Instrument polymorphism (Security | CurrencyPair). securities.id and
// currency_pairs.id both FK to instruments(id), so a new instrument is registered here first.
@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "instruments")
public class InstrumentEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    // SECURITY | CURRENCY_PAIR
    @Column(name = "instrument_type", nullable = false, length = 20)
    private String instrumentType;
}
