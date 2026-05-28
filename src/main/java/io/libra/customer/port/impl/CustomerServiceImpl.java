package io.libra.customer.port.impl;

import io.libra.customer.commands.OnboardCustomerCommand;
import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.customer.events.CustomerOnboarded;
import io.libra.customer.events.CustomerStatusChanged;
import io.libra.customer.events.KycLevelChanged;
import io.libra.customer.events.RiskProfileChanged;
import io.libra.customer.persistence.mapper.CustomerMapper;
import io.libra.customer.port.CustomerService;
import io.libra.customer.repository.CustomerRepository;
import io.libra.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    private final CustomerMapper customerMapper;

    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public Customer onboard(OnboardCustomerCommand cmd) {
        customerRepository.findByEmail(cmd.email()).ifPresent(existing -> {
            throw new IllegalStateException("A customer already exists for email: " + cmd.email());
        });

        Instant now = Instant.now();
        Customer customer = new Customer(
                Uuids.newId(),
                cmd.email(),
                cmd.firstName(),
                cmd.lastName(),
                cmd.birthDate(),
                cmd.countryOfResidence(),
                CustomerStatus.PENDING_KYC,   // not tradable until KYC done + activation
                KycLevel.NONE,
                cmd.riskProfile(),
                cmd.clientCategory(),
                now,
                null);
        customerRepository.save(customerMapper.toEntity(customer));

        events.publishEvent(new CustomerOnboarded(customer, now));
        return customer;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id).map(customerMapper::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email).map(customerMapper::toDomain);
    }

    @Override
    @Transactional
    public Customer activate(UUID id) {
        Customer current = load(id);
        if (current.kycLevel() == KycLevel.NONE) {
            throw new IllegalStateException(
                    "Cannot activate customer " + id + " : KYC not completed (level NONE)");
        }
        return transition(current, CustomerStatus.ACTIVE,
                EnumSet.of(CustomerStatus.PENDING_KYC), "activated", false);
    }

    @Override
    @Transactional
    public Customer suspend(UUID id, String reason) {
        return transition(load(id), CustomerStatus.SUSPENDED,
                EnumSet.of(CustomerStatus.ACTIVE), reason, false);
    }

    @Override
    @Transactional
    public Customer reactivate(UUID id) {
        return transition(load(id), CustomerStatus.ACTIVE,
                EnumSet.of(CustomerStatus.SUSPENDED), "reactivated", false);
    }

    @Override
    @Transactional
    public Customer close(UUID id, String reason) {
        return transition(load(id), CustomerStatus.CLOSED,
                EnumSet.of(CustomerStatus.PENDING_KYC, CustomerStatus.ACTIVE, CustomerStatus.SUSPENDED),
                reason, true);
    }

    @Override
    @Transactional
    public Customer updateKycLevel(UUID id, KycLevel newLevel) {
        Customer current = load(id);
        KycLevel previous = current.kycLevel();
        if (previous == newLevel) {
            return current;
        }
        Customer updated = withKycLevel(current, newLevel);
        customerRepository.save(customerMapper.toEntity(updated));
        events.publishEvent(new KycLevelChanged(id, previous, newLevel, Instant.now()));
        return updated;
    }

    @Override
    @Transactional
    public Customer updateRiskProfile(UUID id, RiskProfile newProfile) {
        Customer current = load(id);
        RiskProfile previous = current.riskProfile();
        if (previous == newProfile) {
            return current;
        }
        Customer updated = withRiskProfile(current, newProfile);
        customerRepository.save(customerMapper.toEntity(updated));
        events.publishEvent(new RiskProfileChanged(id, previous, newProfile, Instant.now()));
        return updated;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Customer load(UUID id) {
        return findById(id).orElseThrow(() -> new NoSuchElementException("Customer not found: " + id));
    }

    private Customer transition(Customer current, CustomerStatus target,
                                Set<CustomerStatus> allowedFrom, String reason, boolean setClosedAt) {
        if (!allowedFrom.contains(current.status())) {
            throw new IllegalStateException(
                    "Cannot transition customer " + current.id() + " from " + current.status()
                            + " to " + target + " (allowed from " + allowedFrom + ")");
        }
        Instant closedAt = setClosedAt ? Instant.now() : current.closedAt();
        Customer updated = withStatus(current, target, closedAt);
        customerRepository.save(customerMapper.toEntity(updated));
        events.publishEvent(new CustomerStatusChanged(
                current.id(), current.status(), target, reason, Instant.now()));
        return updated;
    }

    private Customer withStatus(Customer c, CustomerStatus status, Instant closedAt) {
        return new Customer(c.id(), c.email(), c.firstName(), c.lastName(), c.birthDate(),
                c.countryOfResidence(), status, c.kycLevel(), c.riskProfile(), c.clientCategory(),
                c.onboardedAt(), closedAt);
    }

    private Customer withKycLevel(Customer c, KycLevel kycLevel) {
        return new Customer(c.id(), c.email(), c.firstName(), c.lastName(), c.birthDate(),
                c.countryOfResidence(), c.status(), kycLevel, c.riskProfile(), c.clientCategory(),
                c.onboardedAt(), c.closedAt());
    }

    private Customer withRiskProfile(Customer c, RiskProfile riskProfile) {
        return new Customer(c.id(), c.email(), c.firstName(), c.lastName(), c.birthDate(),
                c.countryOfResidence(), c.status(), c.kycLevel(), riskProfile, c.clientCategory(),
                c.onboardedAt(), c.closedAt());
    }
}
