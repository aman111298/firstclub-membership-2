package com.aman.firstclubmembership2.model;

import java.math.BigDecimal;
import java.util.Set;

/** Snapshot of a user's activity used for dynamic tier evaluation. */
public record UserMetrics(
        String userId,
        int monthlyOrderCount,
        BigDecimal monthlyOrderValue,
        Set<String> cohortTags
) {
}
