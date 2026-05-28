package io.libra.validation.port;

import io.libra.validation.domain.ValidationRequest;
import io.libra.validation.domain.ValidationResult;

// Module port : the pre-trade gate. Returns Approved or Rejected (with the aggregated reasons),
// and publishes ValidationFailed on rejection for regulatory audit.
public interface ValidationService {

    ValidationResult validate(ValidationRequest request);
}
