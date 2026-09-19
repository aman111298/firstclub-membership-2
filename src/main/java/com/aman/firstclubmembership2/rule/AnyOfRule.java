package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;

import java.util.List;

/**
 * Composite rule that is eligible if ANY of its child rules are eligible.
 * A tier's own {@code qualificationRules} list is ANDed together
 * ({@code MembershipTier.qualifies}), so nesting an {@code AnyOfRule}
 * inside that list is how a tier expresses an OR condition.
 */
public class AnyOfRule implements TierQualificationRule {

    private final List<TierQualificationRule> rules;

    public AnyOfRule(List<TierQualificationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean isEligible(UserMetrics metrics) {
        return rules.stream().anyMatch(rule -> rule.isEligible(metrics));
    }
}
