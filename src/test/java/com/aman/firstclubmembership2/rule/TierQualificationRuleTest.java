package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierQualificationRuleTest {

    private static UserMetrics metrics(int orderCount, String orderValue, String... cohorts) {
        return new UserMetrics("USER_1", orderCount, new BigDecimal(orderValue), Set.of(cohorts));
    }

    @Test
    void orderCountRule_eligible_whenCountMeetsThreshold() {
        assertTrue(new OrderCountRule(5).isEligible(metrics(5, "0.00")));
        assertTrue(new OrderCountRule(5).isEligible(metrics(6, "0.00")));
    }

    @Test
    void orderCountRule_notEligible_whenCountBelowThreshold() {
        assertFalse(new OrderCountRule(5).isEligible(metrics(4, "0.00")));
    }

    @Test
    void orderValueRule_eligible_whenValueMeetsThreshold() {
        assertTrue(new OrderValueRule(new BigDecimal("2000.00")).isEligible(metrics(0, "2000.00")));
    }

    @Test
    void orderValueRule_notEligible_whenValueBelowThreshold() {
        assertFalse(new OrderValueRule(new BigDecimal("2000.00")).isEligible(metrics(0, "1999.99")));
    }

    @Test
    void cohortRule_eligible_whenCohortPresent() {
        assertTrue(new CohortRule("VIP_CLUB").isEligible(metrics(0, "0.00", "VIP_CLUB")));
    }

    @Test
    void cohortRule_notEligible_whenCohortAbsent() {
        assertFalse(new CohortRule("VIP_CLUB").isEligible(metrics(0, "0.00", "REGULAR")));
    }

    @Test
    void anyOfRule_eligible_whenAtLeastOneChildRuleMatches() {
        AnyOfRule rule = new AnyOfRule(List.of(new OrderCountRule(15), new CohortRule("VIP_CLUB")));

        assertTrue(rule.isEligible(metrics(0, "0.00", "VIP_CLUB")), "should match via cohort alone");
        assertTrue(rule.isEligible(metrics(15, "0.00")), "should match via order count alone");
    }

    @Test
    void anyOfRule_notEligible_whenNoChildRuleMatches() {
        AnyOfRule rule = new AnyOfRule(List.of(new OrderCountRule(15), new CohortRule("VIP_CLUB")));

        assertFalse(rule.isEligible(metrics(6, "0.00", "REGULAR")));
    }

    @Test
    void rules_notEligible_whenMetricsIsNull() {
        assertFalse(new OrderCountRule(1).isEligible(null));
        assertFalse(new OrderValueRule(BigDecimal.ONE).isEligible(null));
        assertFalse(new CohortRule("VIP_CLUB").isEligible(null));
    }
}
