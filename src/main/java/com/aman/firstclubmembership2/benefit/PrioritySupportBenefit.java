package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

/** Grants priority support routing when requested. */
public class PrioritySupportBenefit implements MembershipBenefit {

    @Override
    public String getBenefitCode() {
        return "PRIORITY_SUPPORT";
    }

    @Override
    public void apply(OrderContext context, OrderBenefitsResult result) {
        if (context.isPrioritySupportRequested()) {
            result.setPrioritySupportGranted(true);
            result.addSummary("Priority support enabled");
        }
    }

    @Override
    public String toString() {
        return getBenefitCode();
    }
}
