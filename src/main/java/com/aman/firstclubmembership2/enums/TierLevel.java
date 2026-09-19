package com.aman.firstclubmembership2.enums;

public enum TierLevel {
    SILVER(1),
    GOLD(2),
    PLATINUM(3);

    private final int rank;

    TierLevel(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
