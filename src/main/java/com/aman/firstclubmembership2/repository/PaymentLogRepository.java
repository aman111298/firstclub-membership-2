package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.model.PaymentContext;

public interface PaymentLogRepository {
    /** Writes a permanent audit record for a payment attempt, success or failure. */
    void save(PaymentContext paymentContext);
}
