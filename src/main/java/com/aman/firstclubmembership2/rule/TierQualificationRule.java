package com.aman.firstclubmembership2.rule;

import com.aman.firstclubmembership2.model.UserMetrics;

public interface TierQualificationRule {
    boolean isEligible(UserMetrics metrics);
}
