package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.MembershipTier;
import com.aman.firstclubmembership2.enums.TierLevel;

import java.util.Collection;
import java.util.Optional;

public interface TierRepository {
    Optional<MembershipTier> findByLevel(TierLevel level);

    Collection<MembershipTier> findAll();

    void save(MembershipTier tier);
}
