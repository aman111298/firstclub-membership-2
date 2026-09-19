package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.model.PaymentContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentLogRepository implements PaymentLogRepository {

    private final Map<String, PaymentContext> paymentLogsById = new ConcurrentHashMap<>();

    @Override
    public void save(PaymentContext paymentContext) {
        paymentLogsById.put(paymentContext.paymentId(), paymentContext);
    }
}
