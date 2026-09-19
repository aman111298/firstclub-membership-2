package com.aman.firstclubmembership2.repository;

import com.aman.firstclubmembership2.domain.MembershipPlan;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPlanRepository implements PlanRepository {

    private final Map<String, MembershipPlan> plansById = new ConcurrentHashMap<>();

    @Override
    public Optional<MembershipPlan> findById(String planId) {
        return Optional.ofNullable(plansById.get(planId));
    }

    @Override
    public Collection<MembershipPlan> findAll() {
        return List.copyOf(plansById.values());
    }

    @Override
    public void save(MembershipPlan plan) {
        plansById.put(plan.getPlanId(), plan);
    }
}
