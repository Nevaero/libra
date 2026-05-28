package io.libra.validation.entities;

import io.libra.validation.entities.enums.ValidationFailureCode;

// `code` = enum machine-readable (typé, switch exhaustif côté consumer audit).
// `detail` = String human-readable contextuel (e.g. "available 7065 USD < required 10000 USD").
public record ValidationFailureReason(ValidationFailureCode code, String detail) {
}
