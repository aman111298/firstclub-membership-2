package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.benefit.MembershipBenefit;
import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.rule.TierQualificationRule;

import java.util.List;

public class MembershipTier {

    private final String tierId;
    private final TierLevel level;
    private final String name;
    private final List<MembershipBenefit> benefits;
    private final List<TierQualificationRule> qualificationRules;

    public MembershipTier(String tierId, TierLevel level, String name, List<MembershipBenefit> benefits,
                           List<TierQualificationRule> qualificationRules) {
        this.tierId = tierId;
        this.level = level;
        this.name = name;
        this.benefits = List.copyOf(benefits);
        this.qualificationRules = List.copyOf(qualificationRules);
    }

    /** Returns true if at least one rule defined for this tier is satisfied (OR basis). */
    public boolean qualifies(UserMetrics metrics) {
        if (qualificationRules.isEmpty()) {
            return true; // Default baseline tier
        }
        return qualificationRules.stream().anyMatch(rule -> rule.isEligible(metrics));
    }

    public String getTierId() {
        return tierId;
    }

    public TierLevel getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public List<MembershipBenefit> getBenefits() {
        return benefits;
    }

    public List<TierQualificationRule> getQualificationRules() {
        return qualificationRules;
    }
}
