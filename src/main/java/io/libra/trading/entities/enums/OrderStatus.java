package io.libra.trading.entities.enums;

public enum OrderStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    EXECUTED,
    CANCELLED,
    SETTLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case SUBMITTED -> next == ACCEPTED || next == REJECTED;
            case ACCEPTED -> next == EXECUTED || next == CANCELLED;
            case EXECUTED -> next == SETTLED;
            case REJECTED, CANCELLED, SETTLED -> false;
        };
    }

    public boolean isTerminal() {
        return switch (this) {
            case REJECTED, CANCELLED, SETTLED -> true;
            case SUBMITTED, ACCEPTED, EXECUTED -> false;
        };
    }
}
