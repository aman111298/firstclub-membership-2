# FirstClub Membership (v2)

A plain-Java (no framework) implementation of a membership program: plans, tiers, subscriptions, criteria-based tier movement, and configurable per-tier benefits.

This README exists so that anyone new to the repo — including future-you — can understand what the system does and how the code is organized without reading every file first.

---

## 1. Problem Statement

### 1.1 As given

> **1. Membership Plans:**
> - Users can choose from Monthly, Quarterly, and Yearly membership plans.
> - Each plan comes with specific pricing.
>
> **3. User Actions:**
> - Get Membership Plans and Tier to be selected by the user.
> - Subscribe to a plan (plan + tier).
> - Upgrade, downgrade (Membership Tier), or cancel a subscription.
> - Track current membership and expiry.
>
> **4. Membership Tiers:**
> - Users move through tiers (e.g., Silver, Gold, Platinum) based on criteria like:
>   - Number of Orders more than X
>   - Total Order value in a month
>   - User belonging to a certain cohort
>
> **Membership Benefits:** *{Optional}*
> - Free delivery on eligible orders.
> - Extra X% discount on selected items or categories.
> - Access to exclusive deals and early access to sales.
> - Optional: Priority support for premium members.
> - Each tier unlocks additional perks (e.g., higher discounts, faster delivery, exclusive coupons) — should be configurable.

### 1.2 Restated

Build a membership system with:

**1. Membership Plans**
- Users can choose from Monthly, Quarterly, and Yearly plans.
- Each plan has its own price and billing cycle length.

**2. Membership Tiers**
- Users move through tiers (Silver, Gold, Platinum) based on configurable criteria:
  - Number of orders above a threshold
  - Total order value in a month
  - Membership in a specific cohort (e.g. `VIP_CLUB`, `EMPLOYEES`)
- A tier qualifies a user if the user satisfies **any one** of its configured criteria (OR basis) — e.g. Platinum requires 15+ orders **or** the `VIP_CLUB` cohort tag, not both.

**3. User Actions**
- Get the available plans and tiers, with the price of each plan + tier combination.
- Buy a plan + tier (price = plan price × tier multiplier). Criteria are not evaluated at purchase — the user gets exactly the tier they paid for.
- Upgrade the purchased tier (paid, prorated), or cancel a subscription. Downgrade is admin-only, used to penalize a user.
- After purchase, a scheduled job / order-event consumer can lift the user to a higher *earned* tier based on criteria. The system only ever upgrades — a drop in activity never lowers a tier; only an admin can downgrade.
- Track current membership status and expiry.

**4. Membership Benefits** (configurable per tier)
- Free delivery on eligible orders.
- An extra X% discount, optionally restricted to specific categories.
- Early access to exclusive deals.
- Priority support (typically reserved for the top tier).
- Higher tiers unlock more/better benefits, without needing new code to configure them.

---

## 2. Solution — approach at a glance

- **No framework, no persistence** — this is a plain Maven/Java 17 project. State lives in in-memory repositories behind interfaces, so a real database could be swapped in later without touching business logic.
- **Two independent Strategy-pattern engines**, because the two problems ("should this user be in this tier?" and "what does this tier grant?") are genuinely different shapes:
  - **Tier qualification** (`rule` package) — a tier holds a list of `TierQualificationRule`s; a user qualifies if *any* rule passes.
  - **Tier benefits** (`benefit` package) — a tier holds a list of `MembershipBenefit`s; each one is applied to an order and accumulates its effect (discount, free delivery, entitlement flags) into one result.
- **Constructor injection everywhere** — `MembershipService` depends on repository *interfaces*, not concrete storage, so the in-memory implementations can be swapped for real ones later.
- **A tier can be bought or earned** — `UserSubscription` keeps a `purchasedTier` (changes only via paid upgrade or admin downgrade) and an `earnedTier` (only ever raised by the system re-evaluation, cleared only by an admin downgrade). Benefits use `effectiveTier = max(purchased, earned)`. Pricing is a third Strategy (`TierPricingStrategy`), currently plan price × the tier's multiplier.
- **Per-user locking for state changes** — `UserLockManager` gives each user their own `ReentrantLock`; every method that reads-then-writes one user's subscription holds that lock for the whole operation, closing races like two concurrent `subscribe()` calls both charging the same user.
- Adding a new qualification rule or a new benefit type means adding one new class that implements an existing interface — nothing else in the codebase changes.

---

## 3. High-Level Design (HLD)

### 3.1 Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  Application.java  (demo entry point / composition root)        │
│  wires concrete repositories + MembershipService, then runs      │
│  a scripted demo through every capability                        │
└───────────────────────────────┬─────────────────────────────────┘
                                 │ constructs & calls
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  service/MembershipService.java                                  │
│  the ONE orchestrator: subscribe, upgrade/downgrade, cancel,     │
│  tier evaluation, benefit evaluation                              │
└───────┬───────────────┬───────────────┬───────────────┬─────────┘
        │                │               │               │
        ▼                ▼               ▼               ▼
┌───────────────┐ ┌──────────────┐ ┌─────────────┐ ┌─────────────────┐
│ repository/*   │ │ domain/*     │ │ rule/*      │ │ benefit/*        │
│ storage        │ │ entities     │ │ tier         │ │ tier benefit     │
│ interfaces +   │ │ (Plan, Tier, │ │ qualification│ │ strategies       │
│ in-memory impl │ │ Subscription)│ │ strategies   │ │ (Strategy)       │
└───────────────┘ └──────────────┘ └─────────────┘ └─────────────────┘
```

Supporting packages: `model` (small immutable data carriers — `UserMetrics`, `PaymentContext`, `OrderContext`, `OrderBenefitsResult`), `enums` (`BillingCycle`, `TierLevel`, `SubscriptionStatus`), `exception` (`PaymentFailedException`), `store` (`IdGenerator`), `config` (`CatalogSeeder` — seeds demo data), `benefit` and `rule` (the two Strategy families).

### 3.2 Package map

| Package | Responsibility |
|---|---|
| `domain` | Core entities: `MembershipPlan`, `MembershipTier`, `UserSubscription` |
| `enums` | `BillingCycle`, `TierLevel`, `SubscriptionStatus` |
| `model` | Small immutable value objects passed between layers |
| `rule` | Tier qualification Strategy: `TierQualificationRule` + `OrderCountRule`, `OrderValueRule`, `CohortRule` |
| `benefit` | Tier benefit Strategy: `MembershipBenefit` + `PercentageDiscountBenefit`, `FreeDeliveryBenefit`, `EarlyAccessBenefit`, `PrioritySupportBenefit` |
| `pricing` | Plan + tier pricing Strategy: `TierPricingStrategy` + `MultiplierTierPricingStrategy` |
| `repository` | Storage interfaces (`PlanRepository`, `TierRepository`, `SubscriptionRepository`, `PaymentLogRepository`) + in-memory implementations |
| `service` | `MembershipService` — the single orchestrator; all mutations and queries go through it |
| `concurrency` | `UserLockManager` — one `ReentrantLock` per user, so state-changing operations for that user serialize |
| `store` | `IdGenerator` — sequential IDs for plans/tiers/subscriptions/payments |
| `config` | `CatalogSeeder` — builds the demo catalog (plans, tiers, criteria, benefits), shared by `Application` and the tests |
| `exception` | `PaymentFailedException` |

### 3.3 Why two Strategy engines instead of one config-driven system

An earlier version stored benefits as `(type, Map<String,Object> params)` data. It was replaced with the current Strategy-pattern approach because:
- It mirrors the existing `TierQualificationRule` shape, so the codebase has one recurring pattern instead of two.
- Each benefit's parameters are typed constructor fields, not an untyped map you have to cast out of.
- Benefits can be *combined*: `MembershipService.evaluateBenefits()` runs every benefit a tier grants against one `OrderContext`, accumulating their combined effect into one `OrderBenefitsResult` — a foundation for future rules like "total discount can't exceed 25% across stacked benefits."

The cost: adding a genuinely new benefit *shape* means a new Java class (same cost as adding a new `TierQualificationRule`) rather than pure config. Tuning existing values (a discount %, a threshold) is still just changing the seed data in `CatalogSeeder` — no new class needed.

---

## 4. Low-Level Design (LLD)

### 4.1 Domain model

```
MembershipPlan                     MembershipTier
─────────────────                  ───────────────────────────
planId : String                    tierId : String
name : String                      level : TierLevel
billingCycle : BillingCycle        name : String
price : BigDecimal                 benefits : List<MembershipBenefit>
                                    qualificationRules : List<TierQualificationRule>
                                    priceMultiplier : BigDecimal        // 1.0 / 1.5 / 2.0
                                    + qualifies(UserMetrics) : boolean   // OR across rules


UserSubscription
──────────────────────────────
subscriptionId, userId, planId : String
purchasedTier : TierLevel          (paid upgrade / admin downgrade only)
earnedTier : TierLevel             (null until activity first upgrades; only ever raised)
status : SubscriptionStatus        (mutable — cancel(), setStatus())
startDate, endDate : LocalDateTime (immutable, set at construction)
paymentContext : PaymentContext
+ getEffectiveTier() : TierLevel   // max(purchased, earned) — drives benefits
+ isExpired() : boolean            // true if past endDate OR status != ACTIVE
```

Pricing: `TierPricingStrategy.priceFor(plan, tier)`; the default `MultiplierTierPricingStrategy` returns plan price × tier multiplier (e.g. Monthly 199 → Silver 199, Gold 298.50, Platinum 398). Swapping in a fixed add-on or a full plan × tier price matrix is a new implementation, no service change.

`isExpired()` deliberately treats *any* non-`ACTIVE` status as "expired" — so a `CANCELLED` subscription immediately stops blocking a new `subscribe()` call, and immediately stops counting for tier-evaluation purposes, even though `endDate` itself is untouched by cancellation.

### 4.2 Tier qualification (Strategy)

```
        <<interface>>
        TierQualificationRule
        + isEligible(UserMetrics) : boolean
                 △
    ┌────────────┼────────────┐
    │            │            │
OrderCountRule OrderValueRule CohortRule
(min orders)   (min spend)    (must have cohort tag)
```

`MembershipTier.qualifies(metrics)` is **OR across the rule list** — a tier qualifies if *any one* configured rule passes:

```java
// Gold: orders >= 5  OR  spend >= $2000
List.of(new OrderCountRule(5), new OrderValueRule(new BigDecimal("2000.00")))

// Platinum: orders >= 15  OR  cohort == VIP_CLUB
List.of(new OrderCountRule(15), new CohortRule("VIP_CLUB"))
```

`MembershipService.evaluateEligibleTier(metrics)` checks every tier and returns the **highest-ranked** one whose rules pass, defaulting to `SILVER` (which has no rules, so it always qualifies).

### 4.3 Tier benefits (Strategy + accumulator)

```
        <<interface>>
        MembershipBenefit
        + getBenefitCode() : String
        + apply(OrderContext, OrderBenefitsResult) : void
                 △
    ┌────────────┼────────────┬──────────────────┐
    │            │            │                  │
PercentageDiscount FreeDelivery EarlyAccess   PrioritySupport
Benefit             Benefit      Benefit        Benefit
(% off, optional    (waives fee  (flags early   (flags priority
 category list)      above a min  access for     support if
                      order value) exclusive     requested)
                                   deals)
```

- `OrderContext` — the minimal input a benefit needs to decide whether it applies: `subtotal`, `category`, `standardDeliveryFee`, `isExclusiveDeal`, `isPrioritySupportRequested`. It is **not** a persisted `Order` entity — just an evaluation-time snapshot.
- `OrderBenefitsResult` — a mutable accumulator every benefit writes into: `discountAmount` (summed across benefits), `deliveryFee` (starts at the standard fee, benefits may waive it), `earlyAccessGranted`, `prioritySupportGranted`, and a human-readable `summary` list.

`MembershipService.evaluateBenefits(userId, context)` resolves the user's tier, then runs `apply()` for every configured benefit against the same context/result pair:

```java
public OrderBenefitsResult evaluateBenefits(String userId, OrderContext context) {
    OrderBenefitsResult result = new OrderBenefitsResult(context.standardDeliveryFee());
    resolveBenefits(userId).forEach(benefit -> benefit.apply(context, result));
    return result;
}
```

### 4.4 Service layer — `MembershipService`

Constructor-injected with four repository interfaces + `IdGenerator` + `UserLockManager` + `TierPricingStrategy` (no concrete storage type is ever referenced):

| Method | Caller | What it does |
|---|---|---|
| `getPlansAndTiers()` | user | Full catalog for browsing |
| `getPrice(planId, tier)` | user | Price of a plan + tier combination |
| `subscribe(userId, planId, tier, paymentMethod)` | user | Validates plan/tier, blocks a second *usable* subscription, charges plan × tier price, creates the subscription with `purchasedTier = tier`. Criteria are **not** evaluated here |
| `upgradeTier(userId, newTier, paymentMethod)` | user | Paid upgrade of the purchased tier; charges the price difference prorated for the remaining period |
| `adminDowngradeTier(userId, newTier)` | **admin only** | Penalty: lowers the purchased tier immediately, no refund, clears the earned tier. There is no user-facing downgrade |
| `reevaluateEarnedTier(metrics)` | **system** (cron / order event) | Upgrade-only: if criteria qualify the user above their effective tier, records it as `earnedTier`; otherwise no change. Never downgrades, never charges, never touches `purchasedTier` |
| `reevaluateEarnedTiers(allMetrics)` | **system** (cron batch) | Runs the above per user, each under its own lock |
| `evaluateEligibleTier(metrics)` | internal / system | Pure computation: highest tier whose rules currently pass |
| `getSubscription(userId)` | user | Current subscription; lazily flips `ACTIVE` → `EXPIRED` if `endDate` has passed |
| `cancelSubscription(userId)` | user | Marks `CANCELLED`; `endDate` untouched |
| `getBenefits(userId)` | checkout | Configured benefit list for the user's effective tier |
| `evaluateBenefits(userId, context)` | checkout | Runs the effective tier's benefits against one order, returns the combined result |

Buying vs. earning: a user can buy any tier (e.g. Platinum with zero orders). Activity can then lift the effective tier above what was bought. Tiers only go down through `adminDowngradeTier()` — never automatically.

### 4.5 Concurrency — per-user locking

Every method that reads a user's subscription and then decides + writes based on what it read holds that user's lock for the entire operation, acquired from `UserLockManager.lockFor(userId)`:

```java
ReentrantLock lock = userLockManager.lockFor(userId);
lock.lock();
try {
    // read current state -> decide -> write
} finally {
    lock.unlock();
}
```

| Method | Why it's locked |
|---|---|
| `subscribe()` | Without it, two concurrent calls for the same user could both pass the "no active subscription" check and both charge + create a subscription before either writes (double charge). |
| `upgradeTier()` | Read purchased tier → charge → write must be atomic, or two concurrent upgrades could both charge. |
| `adminDowngradeTier()` | Same read-decide-write race, from the admin side. |
| `reevaluateEarnedTier()` | Read → evaluate → write earned tier shouldn't interleave with an upgrade/downgrade on the same user. The batch version locks each user individually, never holding one lock across the whole run. |
| `getSubscription()` | Its lazy-expiry write (`ACTIVE` → `EXPIRED`) shouldn't race with a concurrent cancel or tier change on the same subscription. |
| `cancelSubscription()` | Consistency with the others - state changes for one user should all serialize the same way. |

**Not locked** (deliberately): `getPlansAndTiers()`, `evaluateEligibleTier()`, `getBenefits()`, `evaluateBenefits()` — none of these mutate per-user subscription state, so locking them would only add contention with no correctness benefit.

`UserLockManager` hands out one `ReentrantLock` per userId (lazily created via `ConcurrentHashMap.computeIfAbsent`), so different users never block each other - only concurrent operations on the *same* user serialize.

Proven, not just asserted: `UserLockManagerTest` runs 50 threads incrementing a shared counter under the same user's lock (would lose updates without it) and confirms two different users' locks don't block each other. `MembershipServiceConcurrencyTest` fires 50 concurrent `subscribe()` calls at the same user through the real service and asserts exactly 1 succeeds and 49 get `IllegalStateException` - the actual race this mechanism exists to close.

### 4.6 Storage — Repository pattern

```
<<interface>> PlanRepository          <<interface>> TierRepository
findById(id) : Optional<Plan>         findByLevel(TierLevel) : Optional<Tier>
findAll() : Collection<Plan>          findAll() : Collection<Tier>
save(Plan)                            save(Tier)
        △                                     △
InMemoryPlanRepository                InMemoryTierRepository
(ConcurrentHashMap<String,Plan>)      (ConcurrentHashMap<TierLevel,Tier>)
```

Same shape for `SubscriptionRepository` (`findActiveByUserId` / `save` — internally keeps the active-by-user map and a permanent history-by-subscription-id map) and `PaymentLogRepository` (write-only audit log). Every repository is an interface + one in-memory implementation, so a real persistence layer (JPA, etc.) can be dropped in later by writing a new implementation class — `MembershipService` would not change.

### 4.7 Flow: subscribing a user (sequence)

```
Application/caller
   │  subscribe(userId, planId, tier, method)
   ▼
MembershipService
   │  userLockManager.lockFor(userId).lock()    ──► acquire this user's lock
   │  planRepository.findById(planId)          ──► 404-style IllegalArgumentException if missing
   │  tierRepository.findByLevel(tier)          ──► IllegalArgumentException if not offered
   │  subscriptionRepository.findActiveByUserId ──► IllegalStateException if already active
   │  tierPricingStrategy.priceFor(plan, tier)  ──► plan price × tier multiplier
   │  processPayment(...)                       ──► PaymentFailedException if it fails
   │  new UserSubscription(...)                 ──► purchasedTier = tier, earnedTier = null
   │  subscriptionRepository.save(subscription)
   │  lock.unlock()                              ──► always runs, in a finally block
   ▼
returns UserSubscription
```

### 4.8 Flow: earned-tier re-evaluation (system-triggered)

```
cron job / order-event consumer has fresh UserMetrics (orders, spend, cohorts)
   │
   ▼
reevaluateEarnedTier(metrics)          (or reevaluateEarnedTiers(all) for the batch)
   │  acquire userLockManager.lockFor(metrics.userId())
   │  no active subscription? → log + return empty, nothing changes
   │  evaluateEligibleTier(metrics)
   │      for each tier: tier.qualifies(metrics)   [OR across that tier's rules]
   │      pick highest-ranked tier that qualifies, else SILVER
   │  qualified > current effective tier?
   │      yes → sub.setEarnedTier(qualified), log UPGRADE   [purchasedTier untouched, no charge]
   │      no  → log "retained" — never downgrades (admin-only)
   │  release lock (finally block)
   ▼
returns the subscription; benefits follow max(purchased, earned)
```

---

## 5. Testing

70 JUnit 5 tests across six classes, run with `mvn test`:

| Test class | Covers |
|---|---|
| `rule.TierQualificationRuleTest` | Each qualification rule in isolation (count, value, cohort) |
| `domain.MembershipTierTest` | `qualifies()`'s OR semantics, baseline tier default |
| `benefit.MembershipBenefitTest` | Each benefit strategy in isolation (discount math, category restriction, free-delivery threshold, entitlement flags) |
| `service.MembershipServiceTest` | End-to-end service behavior: catalog, plan × tier pricing, subscribe (all billing cycles, invalid input, duplicate subscription, payment failure, no criteria at purchase), paid prorated upgrade, admin penalty downgrade, cancel, lazy expiry, earned-tier re-evaluation (single + cron batch, upgrade-only, never charges), full benefits evaluation per effective tier |
| `concurrency.UserLockManagerTest` | Same-user lock serializes concurrent critical sections with zero lost updates; different users' locks don't block each other |
| `service.MembershipServiceConcurrencyTest` | 50 concurrent `subscribe()` calls for the same user through the real service: exactly 1 succeeds, the rest are rejected |

---

## 6. Running the demo

Requires Java 17 and Maven.

```bash
mvn test                                                 # run the full suite
mvn compile                                               # build
java -cp target/classes com.aman.firstclubmembership2.Application   # run the scripted demo
```

The demo (`Application.main`) walks through every scenario in the problem statement, each section labeled with the requirement it demonstrates: browsing the catalog with plan × tier prices, buying Monthly + Silver, cron re-evaluation lifting the user to Gold (orders/spend) then Platinum (cohort), activity dropping without any automatic downgrade, a paid prorated upgrade, tracking membership, an admin penalty downgrade, benefits evaluation for two different tiers against the same order shape, subscribing to a different billing cycle, a cron batch re-evaluation, and cancelling.

---

## 7. Known simplifications (by design, not oversights)

- **No `Order`/checkout subsystem.** `evaluateBenefits()` takes a lightweight `OrderContext` you construct yourself — there's no persisted order history or real checkout flow.
- **Earned tiers are sticky for the subscription period.** Since the system never downgrades, a user who qualified for Platinum once keeps it until the period ends (a new subscription starts with no earned tier) or an admin downgrades them.
- **Admin penalty lasts until the next re-evaluation.** `adminDowngradeTier()` clears the earned tier, but if the user still meets the criteria the next scheduled run upgrades them again; a lasting penalty would need a hold/cap flag.
- **No refunds.** Paid upgrades are prorated; there is no user-facing downgrade and no refund path.
- **Mock payments.** A negative amount is the only way to simulate a failed charge.
- **Cancellation is immediate, not "until period end."** `isExpired()` treats `CANCELLED` as expired right away, even though `endDate` isn't changed.
- **No injectable `Clock`.** Time-based logic (`isExpired()`, `endDate` calculation) calls `LocalDateTime.now()` directly, which is simple but makes deterministic time-travel tests harder to write.
- **In-memory only.** All repositories are `ConcurrentHashMap`-backed; nothing survives a restart. The repository interfaces exist specifically so this can change later without touching `MembershipService`.
