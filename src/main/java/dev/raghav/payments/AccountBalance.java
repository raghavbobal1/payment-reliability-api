package dev.raghav.payments;
import java.math.BigDecimal;
public record AccountBalance(String accountId, BigDecimal balance) { }
