import random
from collections import defaultdict


# Sim-to-real transfer, from scratch on a slippery GridWorld.
#
# A policy trained in a simulator that fails on hardware is a policy that
# *memorized the simulator*. The single unobservable parameter here is `slip`
# (probability that an action is replaced by a random perpendicular one) -- a
# stand-in for every un-modeled contact / friction / latency term that makes
# real hardware diverge from the sim. The "sim" is whatever slip we train on;
# the "real" robot has a fixed, initially-unknown slip we are not allowed to
# train on freely (real time is scarce and breaking hardware is expensive).
#
# Three tools close the reality gap, all implemented below on the same task:
#
#   1. Domain randomization (DR). Train over a *distribution* of slips instead
#      of one value, so the policy is forced to be robust rather than to
#      exploit one sim's quirks. Needs zero real data; degrades gracefully
#      out-of-distribution but is optimal for no single real slip.
#
#   2. System identification (SI). Spend a little real time measuring the real
#      slip from observed transitions, then calibrate the sim to that estimate
#      and train against it. Near-optimal *if* the measurement is accurate --
#      and it tells you when DR's range is over-randomized.
#
#   3. Domain adaptation (DA). Warm-start from the DR sim policy, then
#      fine-tune on a small budget of real episodes. Best of both: the sim
#      prior means the scarce real data only has to *correct*, not learn from
#      scratch -- so it beats from-scratch real training at the same budget.
#
# The three are complementary, not rival: DR gives a safe prior, SI calibrates
# it, DA polishes it on the real system. The planner spec below frames the
# decisions (which params to randomize, what to measure, the safety envelope)
# that this toy makes concrete.

# --- sim2real-planner skill spec (kept as comments so the file stays runnable)
# name: sim2real-planner
# description: Plan a sim-to-real transfer pipeline for a given robot + task, covering DR, SI, and safety.
# version: 1.0.0
# phase: 9
# lesson: 11
# tags: [rl, sim2real, robotics, domain-randomization]
# ---

# Given a robot platform, a task, and access to real hardware time, output:

# 1. Reality gap inventory. Suspected sources ranked by expected impact (contact, sensing, actuation delay, vision).
# 2. DR parameters. Exact list, ranges, distribution. Justify each range against real measurements.
# 3. SI steps. Which parameters to measure; measurement method.
# 4. Teacher/student split. What privileged info the teacher uses; what obs the student uses.
# 5. Safety envelope. Low-level limits, emergency stops, backup controller.

# Refuse to deploy without (a) a zero-shot sim-variant test, (b) a safety shield, (c) a rollback plan. Flag any DR range wider than 3x measured real variability as likely over-randomized.

GRID = 5
TERMINAL = (GRID - 1, GRID - 1)
ACTIONS = ("up", "down", "left", "right")
DELTAS = {"up": (-1, 0), "down": (1, 0), "left": (0, -1), "right": (0, 1)}
PERP = {"up": ("left", "right"), "down": ("left", "right"),
        "left": ("up", "down"), "right": ("up", "down")}


def clamp(cell):
    r, c = cell
    return (min(max(r, 0), GRID - 1), min(max(c, 0), GRID - 1))


def apply(state, action):
    dr, dc = DELTAS[action]
    r, c = state
    return clamp((r + dr, c + dc))


def step(state, action, slip, rng):
    if state == TERMINAL:
        return state, 0.0, True
    if rng.random() < slip:
        action = rng.choice(PERP[action])
    nxt = apply(state, action)
    return nxt, -1.0, nxt == TERMINAL


def default_q():
    return {a: 0.0 for a in ACTIONS}


def epsilon_greedy(Q, s, rng, eps):
    if rng.random() < eps:
        return rng.choice(ACTIONS)
    q = Q[s]
    return max(ACTIONS, key=lambda a: q[a])


# ---------------------------------------------------------------------------
# Core learner. `slip_low/slip_high` define the training distribution:
#   - equal       -> fixed-sim training (the over-fit baseline)
#   - a range     -> domain randomization
#   - init_q given -> warm-start fine-tuning (domain adaptation)
# Resampling the slip per episode is the whole of DR in this toy.
# ---------------------------------------------------------------------------
def q_learn(slip_low, slip_high, episodes=3000, alpha=0.1, gamma=0.95,
            eps=0.15, rng=None, init_q=None):
    rng = rng or random.Random(0)
    Q = defaultdict(default_q)
    if init_q is not None:
        for s, row in init_q.items():
            Q[s] = dict(row)
    for _ in range(episodes):
        slip_ep = rng.uniform(slip_low, slip_high)
        s = (0, 0)
        for _ in range(100):
            a = epsilon_greedy(Q, s, rng, eps)
            s_next, r, done = step(s, a, slip_ep, rng)
            if done:
                Q[s][a] += alpha * (r - Q[s][a])
                break
            best_next = max(Q[s_next].values())
            Q[s][a] += alpha * ((r + gamma * best_next) - Q[s][a])
            s = s_next
    return Q


def train_fixed(slip, **kw):
    return q_learn(slip, slip, **kw)


def train_dr(slip_low, slip_high, **kw):
    return q_learn(slip_low, slip_high, **kw)


# ---------------------------------------------------------------------------
# Tool 2 -- System identification.
# Spend `episodes` of *real* hardware time under an exploratory policy and
# estimate the slip from observed transitions: a step "slipped" when the
# realized cell matches a perpendicular action rather than the intended one.
# Steps where a wall makes intended and perpendicular outcomes indistinguishable
# are skipped, so the estimate stays unbiased -- this is the measurement method
# the planner's "SI steps" section has to specify for a real robot.
# ---------------------------------------------------------------------------
def measure_slip(slip_real, episodes=40, rng=None):
    rng = rng or random.Random(7)
    slipped = 0
    counted = 0
    for _ in range(episodes):
        s = (0, 0)
        for _ in range(100):
            a = rng.choice(ACTIONS)
            intended = apply(s, a)
            perp_cells = {apply(s, p) for p in PERP[a]}
            s_next, _, done = step(s, a, slip_real, rng)
            # only count steps where intended vs. perpendicular are
            # distinguishable and the agent actually moved.
            if intended != s and intended not in perp_cells:
                counted += 1
                if s_next != intended:
                    slipped += 1
            s = s_next
            if done:
                break
    return slipped / counted if counted else 0.0


def measure_slip_stats(slip_real, batches=8, eps_per_batch=40, rng=None):
    # Repeat the measurement on independent real batches; the spread across
    # batches is our best handle on how well the real parameter is pinned down
    # -- i.e. the "measured real variability" the planner's over-randomization
    # check is supposed to compare the DR range against.
    rng = rng or random.Random(7)
    ests = [measure_slip(slip_real, eps_per_batch, rng) for _ in range(batches)]
    mean = sum(ests) / len(ests)
    var = sum((e - mean) ** 2 for e in ests) / len(ests)
    return mean, var ** 0.5


def evaluate(Q, slip, episodes=200, rng=None):
    rng = rng or random.Random(42)
    total = 0.0
    for _ in range(episodes):
        s = (0, 0)
        ep_total = 0.0
        for _ in range(100):
            a = max(ACTIONS, key=lambda a: Q[s][a])
            s, r, done = step(s, a, slip, rng)
            ep_total += r
            if done:
                break
        total += ep_total
    return total / episodes


def main():
    SLIP_REAL = 0.25          # the real robot's true (unknown to us) slip
    DR_LOW, DR_HIGH = 0.0, 0.3
    REAL_BUDGET = 300         # scarce real episodes we may train on

    print("=== sim-to-real: crossing the reality gap on a slippery GridWorld ===")
    print(f"env: {GRID}x{GRID} GridWorld, slip = P(perpendicular slip)")
    print(f"real robot slip = {SLIP_REAL} (unknown a priori); real-training budget = {REAL_BUDGET} eps")
    print()

    # --- baselines and the three tools, all on the same compute where possible.
    Q_fixed = train_fixed(0.0, rng=random.Random(1))                 # over-fit sim
    Q_dr = train_dr(DR_LOW, DR_HIGH, rng=random.Random(1))           # tool 1: DR

    # tool 2: system identification -> calibrate sim -> train on the estimate.
    slip_hat, slip_std = measure_slip_stats(SLIP_REAL, batches=8, eps_per_batch=40,
                                            rng=random.Random(7))
    Q_si = train_fixed(slip_hat, rng=random.Random(1))

    # tool 3: domain adaptation -> warm-start from DR, fine-tune on real budget.
    Q_da = q_learn(SLIP_REAL, SLIP_REAL, episodes=REAL_BUDGET, eps=0.1,
                   rng=random.Random(2), init_q=Q_dr)
    # control: same real budget, but from scratch (no sim prior).
    Q_scratch = q_learn(SLIP_REAL, SLIP_REAL, episodes=REAL_BUDGET, eps=0.1,
                        rng=random.Random(2))

    print("--- Tool 1: domain randomization (no real data) ---")
    print("zero-shot transfer across a sweep of 'real' slips (200 greedy eps each):")
    print(f"  {'slip':<8}{'fixed-sim':<14}{'DR':<14}")
    for slip in (0.0, 0.1, 0.2, 0.3, 0.5, 0.7):
        tag = "(in DR support)" if slip <= DR_HIGH else "(OOD for DR)"
        print(f"  {slip:<8.2f}{evaluate(Q_fixed, slip):<14.2f}{evaluate(Q_dr, slip):<14.2f}{tag}")
    print("  -> DR degrades gracefully; the fixed-sim policy is brittle OOD.")
    print()

    print("--- Tool 2: system identification ---")
    err = abs(slip_hat - SLIP_REAL)
    print(f"  measured slip_hat = {slip_hat:.3f} +/- {slip_std:.3f} from 8x40 real eps "
          f"(true {SLIP_REAL}, |err| = {err:.3f})")
    width = DR_HIGH - DR_LOW
    flag = "OVER-RANDOMIZED" if width > 3 * slip_std else "ok"
    print(f"  planner check: DR width {width:.2f} vs 3x measured variability "
          f"({3 * slip_std:.3f}) -> {flag}")
    print("  -> once SI pins the real slip to a tight band, the wide DR range is")
    print("     wasting capacity; narrow the sim toward slip_hat.")
    print()

    print(f"--- Tool 3: domain adaptation (warm-start vs scratch, {REAL_BUDGET} real eps) ---")
    print(f"  performance on the real robot (slip = {SLIP_REAL}):")
    print(f"    {'policy':<26}{'real return':>12}")
    rows = [
        ("fixed-sim (no transfer)", Q_fixed),
        ("DR (zero-shot)", Q_dr),
        ("SI (calibrated sim)", Q_si),
        ("scratch (real only)", Q_scratch),
        ("DA (DR + fine-tune)", Q_da),
    ]
    for name, Q in rows:
        print(f"    {name:<26}{evaluate(Q, SLIP_REAL):>12.2f}")
    print()
    print("takeaway: DR buys robustness with no real data, SI calibrates the sim,")
    print("and DA spends the scarce real budget *correcting* a good prior instead of")
    print("learning from zero -- the three tools compound to cross the reality gap.")


if __name__ == "__main__":
    main()
