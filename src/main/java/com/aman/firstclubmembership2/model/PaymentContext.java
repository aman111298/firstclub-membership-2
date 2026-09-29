package com.aman.firstclubmembership2.model;

import com.aman.firstclubmembership2.enums.PaymentMethod;
import com.aman.firstclubmembership2.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable payment record embedded into a {@code UserSubscription}. */
public record PaymentContext(
        String paymentId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        LocalDateTime transactionTimestamp
) {
}
