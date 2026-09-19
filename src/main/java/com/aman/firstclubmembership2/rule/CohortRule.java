package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;

/** Requires membership in a specific cohort tag. */
public class CohortRule implements TierQualificationRule {

    private final String requiredCohort;

    public CohortRule(String requiredCohort) {
        this.requiredCohort = requiredCohort;
    }

    @Override
    public boolean isEligible(UserMetrics metrics) {
        return metrics != null && metrics.cohortTags() != null
                && metrics.cohortTags().contains(requiredCohort);
    }
}
