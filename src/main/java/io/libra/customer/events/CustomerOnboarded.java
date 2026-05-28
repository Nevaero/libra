package io.libra.customer.events;

import io.libra.customer.entities.Customer;

import java.time.Instant;

public record CustomerOnboarded(Customer customer, Instant occurredAt) {
}
