package com.aman.firstclubmembership2.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Accumulates the combined effect of every benefit a tier's {@code MembershipBenefit} list applies to one OrderContext. */
public class OrderBenefitsResult {

    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal deliveryFee;
    private boolean earlyAccessGranted = false;
    private boolean prioritySupportGranted = false;
    private final List<String> summary = new ArrayList<>();

    public OrderBenefitsResult(BigDecimal standardDeliveryFee) {
        this.deliveryFee = standardDeliveryFee;
    }

    public void addDiscountAmount(BigDecimal amount) {
        this.discountAmount = this.discountAmount.add(amount);
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public void setEarlyAccessGranted(boolean earlyAccessGranted) {
        this.earlyAccessGranted = earlyAccessGranted;
    }

    public void setPrioritySupportGranted(boolean prioritySupportGranted) {
        this.prioritySupportGranted = prioritySupportGranted;
    }

    public void addSummary(String note) {
        summary.add(note);
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public boolean isEarlyAccessGranted() {
        return earlyAccessGranted;
    }

    public boolean isPrioritySupportGranted() {
        return prioritySupportGranted;
    }

    public List<String> getSummary() {
        return List.copyOf(summary);
    }

    @Override
    public String toString() {
        return "OrderBenefitsResult{" +
                "discountAmount=" + discountAmount +
                ", deliveryFee=" + deliveryFee +
                ", earlyAccessGranted=" + earlyAccessGranted +
                ", prioritySupportGranted=" + prioritySupportGranted +
                ", summary=" + summary +
                '}';
    }
}
