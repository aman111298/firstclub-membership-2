package com.aman.firstclubmembership2.enums;

public enum BillingCycle {
    MONTHLY(30),
    QUARTERLY(90),
    ANNUAL(365);

    private final int days;

    BillingCycle(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
