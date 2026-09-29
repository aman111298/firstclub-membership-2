package com.aman.firstclubmembership2.benefit;

import com.aman.firstclubmembership2.enums.BenefitType;
import com.aman.firstclubmembership2.model.OrderBenefitsResult;
import com.aman.firstclubmembership2.model.OrderContext;

public interface MembershipBenefit {

    BenefitType getType();

    void apply(OrderContext context, OrderBenefitsResult result);
}
