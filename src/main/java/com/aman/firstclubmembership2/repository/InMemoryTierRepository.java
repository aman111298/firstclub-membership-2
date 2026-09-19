package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.enums.TierLevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTierRepository implements TierRepository {

    private final Map<TierLevel, MembershipTier> tiersByLevel = new ConcurrentHashMap<>();

    @Override
    public Optional<MembershipTier> findByLevel(TierLevel level) {
        return Optional.ofNullable(tiersByLevel.get(level));
    }

    @Override
    public Collection<MembershipTier> findAll() {
        return List.copyOf(tiersByLevel.values());
    }

    @Override
    public void save(MembershipTier tier) {
        tiersByLevel.put(tier.getLevel(), tier);
    }
}
