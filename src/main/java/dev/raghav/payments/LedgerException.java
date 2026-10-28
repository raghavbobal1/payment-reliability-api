package dev.raghav.payments;
public class LedgerException extends RuntimeException {
    public enum Kind { INVALID_INPUT, NOT_FOUND, CONFLICT, INSUFFICIENT_FUNDS }
    private final Kind kind;
    LedgerException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
