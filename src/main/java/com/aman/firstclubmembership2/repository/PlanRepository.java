package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.MembershipPlan;

import java.util.Collection;
import java.util.Optional;

public interface PlanRepository {
    Optional<MembershipPlan> findById(String planId);

    Collection<MembershipPlan> findAll();

    void save(MembershipPlan plan);
}
