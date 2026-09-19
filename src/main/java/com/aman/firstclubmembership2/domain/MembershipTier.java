package com.aman.firstclubmembership2.domain;

import com.aman.firstclubmembership2.enums.TierLevel;
import com.aman.firstclubmembership2.model.UserMetrics;
import com.aman.firstclubmembership2.rule.TierQualificationRule;

import java.util.List;

public class MembershipTier {

    private final String tierId;
    private final TierLevel level;
    private final String name;
    private final List<String> perks;
    private final List<TierQualificationRule> qualificationRules;

    public MembershipTier(String tierId, TierLevel level, String name, List<String> perks,
                           List<TierQualificationRule> qualificationRules) {
        this.tierId = tierId;
        this.level = level;
        this.name = name;
        this.perks = List.copyOf(perks);
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

    public List<String> getPerks() {
        return perks;
    }

    public List<TierQualificationRule> getQualificationRules() {
        return qualificationRules;
    }
}
