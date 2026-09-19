package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.rule.OrderCountRule;
import com.aman.firstclubmembership2.rule.OrderValueRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipTierTest {

    private static UserMetrics metrics(int orderCount, String orderValue) {
        return new UserMetrics("USER_1", orderCount, new BigDecimal(orderValue), Set.of());
    }

    @Test
    void tierWithNoRules_alwaysQualifies() {
        MembershipTier silver = new MembershipTier("tier_1", TierLevel.SILVER, "Silver", List.of("5% Discount"), List.of());

        assertTrue(silver.qualifies(metrics(0, "0.00")));
        assertTrue(silver.qualifies(null));
    }

    @Test
    void tierWithMultipleRules_qualifiesIfAnyOnePasses() {
        MembershipTier gold = new MembershipTier("tier_2", TierLevel.GOLD, "Gold", List.of("10% Discount"),
                List.of(new OrderCountRule(5), new OrderValueRule(new BigDecimal("2000.00"))));

        assertTrue(gold.qualifies(metrics(5, "2000.00")), "meets both rules");
        assertTrue(gold.qualifies(metrics(5, "0.00")), "meets the count rule alone");
        assertTrue(gold.qualifies(metrics(0, "2000.00")), "meets the value rule alone");
        assertFalse(gold.qualifies(metrics(4, "1999.99")), "meets neither rule");
    }
}
