package com.aman.firstclubmembership2.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable payment record embedded into a {@code UserSubscription}. */
public record PaymentContext(
        String paymentId,
        BigDecimal amount,
        String status, // "SUCCESS", "FAILED"
        String paymentMethod,
        LocalDateTime transactionTimestamp
) {
}
