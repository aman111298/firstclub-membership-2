package com.aman.firstclubmembership2.enums;

/** Why a subscription's tier changed - one value per path that can change it. */
public enum TierChangeType {
    PAID_UPGRADE,     // user paid to raise the purchased tier
    EARNED_UPGRADE,   // system re-evaluation raised the earned tier
    ADMIN_DOWNGRADE   // admin lowered the tier as a penalty
}
