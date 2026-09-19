package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

public interface MembershipBenefit {

    String getBenefitCode();

    void apply(OrderContext context, OrderBenefitsResult result);
}
