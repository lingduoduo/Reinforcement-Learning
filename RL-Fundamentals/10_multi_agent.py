import random
from collections import defaultdict


# Multi-agent RL (MARL), from scratch on a cooperative 2-agent GridWorld.
#
# The new difficulty over single-agent RL is *non-stationarity*: from any one
# agent's view the environment now includes the other learning agents, whose
# policies keep changing. So the "fixed MDP" assumption behind Q-learning is
# violated, and naive approaches can oscillate or fail to converge. Two ends of
# the design spectrum, both implemented below on the same task:
#
#   1. Independent Q-learning (IQL): each agent runs its own Q-learner and
#      treats the others as part of the environment. Fully decentralized and
#      cheap (|A| actions per agent), but each agent's target drifts as the
#      other learns -- the non-stationarity shows up as a noisier learning
#      curve and occasional miscoordination.
#
#   2. Centralized joint-action Q-learning: a single Q over the *joint* action
#      (a1, a2) sees the true combined effect, so it coordinates correctly --
#      but the action space is |A|^2 (and |A|^n for n agents), so it does not
#      scale.
#
# The practical middle ground is CTDE (centralized training, decentralized
# execution) -- value decomposition (VDN/QMIX) and MAPPO -- sketched as
# commented pseudocode at the bottom.
#
# Task: a shared-reward cooperative game. Two agents must BOTH reach the goal;
# the team gets -1 per step until both arrive, then +10.

GRID = 5
GOAL = (4, 4)
ACTIONS = ("up", "down", "left", "right")
DELTAS = {"up": (-1, 0), "down": (1, 0), "left": (0, -1), "right": (0, 1)}


def move(pos, action):
    dr, dc = DELTAS[action]
    r, c = pos
    return (min(max(r + dr, 0), GRID - 1), min(max(c + dc, 0), GRID - 1))


def reset():
    # two agents start at opposite corners of the bottom edge.
    return ((0, 0), (GRID - 1, 0))


def step(state, action_pair):
    a1_pos, a2_pos = state
    new1 = move(a1_pos, action_pair[0])
    new2 = move(a2_pos, action_pair[1])
    done = (new1 == GOAL) and (new2 == GOAL)
    reward = 10.0 if done else -1.0
    return (new1, new2), reward, done


def default_q():
    return {a: 0.0 for a in ACTIONS}


def epsilon_greedy(q_table, state, rng, epsilon):
    if rng.random() < epsilon:
        return rng.choice(ACTIONS)
    q = q_table[state]
    return max(ACTIONS, key=lambda a: q[a])


# ---------------------------------------------------------------------------
# Approach 1: Independent Q-learning (decentralized, |A| per agent).
# Each agent keeps its own Q-table keyed on the FULL joint state but updated
# only with its own action -- the other agent is folded into the environment.
# ---------------------------------------------------------------------------
def independent_q(episodes=1500, alpha=0.1, gamma=0.95, epsilon=0.15, rng=None):
    rng = rng or random.Random(0)
    Q1 = defaultdict(default_q)
    Q2 = defaultdict(default_q)
    returns_log = []
    for _ in range(episodes):
        s = reset()
        total = 0.0
        for _ in range(100):
            a1 = epsilon_greedy(Q1, s, rng, epsilon)
            a2 = epsilon_greedy(Q2, s, rng, epsilon)
            s_next, r, done = step(s, (a1, a2))
            total += r
            if done:
                Q1[s][a1] += alpha * (r - Q1[s][a1])
                Q2[s][a2] += alpha * (r - Q2[s][a2])
                break
            # each agent bootstraps off its own greedy value -- it cannot see
            # the other's intended next move, the root of non-stationarity.
            target1 = r + gamma * max(Q1[s_next].values())
            target2 = r + gamma * max(Q2[s_next].values())
            Q1[s][a1] += alpha * (target1 - Q1[s][a1])
            Q2[s][a2] += alpha * (target2 - Q2[s][a2])
            s = s_next
        returns_log.append(total)
    return Q1, Q2, returns_log


# ---------------------------------------------------------------------------
# Approach 2: Centralized joint-action Q-learning (|A|^2 actions).
# One Q over the joint action coordinates correctly but does not scale.
# ---------------------------------------------------------------------------
def joint_q_learning(episodes=1500, alpha=0.1, gamma=0.95, epsilon=0.15, rng=None):
    rng = rng or random.Random(0)
    joint_actions = [(a, b) for a in ACTIONS for b in ACTIONS]
    Q = defaultdict(lambda: {ja: 0.0 for ja in joint_actions})
    returns_log = []
    for _ in range(episodes):
        s = reset()
        total = 0.0
        for _ in range(100):
            if rng.random() < epsilon:
                ja = rng.choice(joint_actions)
            else:
                ja = max(joint_actions, key=lambda a: Q[s][a])
            s_next, r, done = step(s, ja)
            total += r
            if done:
                Q[s][ja] += alpha * (r - Q[s][ja])
                break
            best_next = max(Q[s_next].values())
            Q[s][ja] += alpha * ((r + gamma * best_next) - Q[s][ja])
            s = s_next
        returns_log.append(total)
    return Q, returns_log


# ---------------------------------------------------------------------------
# Evaluation helpers.
# ---------------------------------------------------------------------------
def block_mean(xs, block):
    return [sum(xs[i : i + block]) / block for i in range(0, len(xs) - block + 1, block)]


def evaluate_ind(Q1, Q2, episodes=100, rng=None):
    rng = rng or random.Random(42)
    total = 0.0
    for _ in range(episodes):
        s = reset()
        for _ in range(100):
            a1 = epsilon_greedy(Q1, s, rng, 0.0)
            a2 = epsilon_greedy(Q2, s, rng, 0.0)
            s, r, done = step(s, (a1, a2))
            total += r
            if done:
                break
    return total / episodes


def evaluate_joint(Q, episodes=100, rng=None):
    rng = rng or random.Random(42)
    joint_actions = [(a, b) for a in ACTIONS for b in ACTIONS]
    total = 0.0
    for _ in range(episodes):
        s = reset()
        for _ in range(100):
            ja = max(joint_actions, key=lambda a: Q[s][a])
            s, r, done = step(s, ja)
            total += r
            if done:
                break
    return total / episodes


def main():
    print(f"=== Cooperative 2-agent GridWorld ({GRID}x{GRID}, shared reward) ===")
    print(f"agents start at (0,0) and ({GRID-1}, 0); must both reach {GOAL}")
    print()

    Q1, Q2, log_ind = independent_q(episodes=1500, rng=random.Random(1))
    Q_joint, log_joint = joint_q_learning(episodes=1500, rng=random.Random(1))

    print("learning curves (mean return per 150 episodes):")
    for i, (a, b) in enumerate(zip(block_mean(log_ind, 150), block_mean(log_joint, 150))):
        print(f"  block {i+1}: independent-Q = {a:7.2f}   joint-Q = {b:7.2f}")

    print()
    print(f"final greedy evaluation (100 eps):")
    print(f"  independent-Q mean return = {evaluate_ind(Q1, Q2):.2f}")
    print(f"  joint-Q       mean return = {evaluate_joint(Q_joint):.2f}")

    print()
    print("note: joint-Q factors the global view correctly, but its action space is |A|^2.")
    print("CTDE methods (MAPPO, QMIX) keep decentralized actors but use a centralized critic.")


if __name__ == "__main__":
    main()


# ===========================================================================
# Beyond IQL vs joint-Q (pseudocode) -- how MARL scales past 2 agents.
# Left commented so this file stays dependency-free and runnable.
# ===========================================================================
#
# A) Value decomposition (VDN / QMIX) -- CTDE for cooperative teams.
# -------------------------------------------------------------------------
#   Each agent keeps a decentralized Q_i(o_i, a_i) used at execution time.
#   During *training* a mixer combines them into a joint Q_tot that is
#   regressed against the shared team reward -- so credit assignment is
#   centralized but acting stays local (|A| per agent, not |A|^n).
#
#   VDN:  Q_tot(s, a) = sum_i Q_i(o_i, a_i)                  # additive mixer
#   QMIX: Q_tot = Mixer(Q_1, ..., Q_n; s),  with the mixer constrained to
#         dQ_tot/dQ_i >= 0  (monotonic), which guarantees
#             argmax_a Q_tot = ( argmax_{a_i} Q_i )_i
#         so the greedy joint action decomposes into per-agent greedy actions.
#
#   for batch in replay:
#       q_i      = [Q_i(o_i, a_i)        for i in agents]          # chosen
#       q_i_next = [max_a' Q_i_target(o_i', a') for i in agents]   # bootstrap
#       q_tot      = mixer(q_i, state)
#       q_tot_next = mixer_target(q_i_next, state_next)
#       target = r_team + gamma * q_tot_next
#       loss   = (q_tot - target) ** 2          # one TD loss for the whole team
#       backprop through mixer into every Q_i   # centralized credit assignment
#
# B) MAPPO -- CTDE actor-critic (cooperative, the PPO of MARL).
# -------------------------------------------------------------------------
#   Decentralized actors pi_i(a_i | o_i); ONE centralized critic V(s) that
#   sees the global state (or all observations) only during training.
#   for rollout in collect(env, actors):
#       adv = gae(rollout, V_central)           # critic uses global state
#       for each agent i:
#           ppo_clip_update(pi_i, adv, eps=0.2) # same clipped surrogate as PPO
#       value_update(V_central, returns)
#
# C) Self-play -- competitive / zero-sum (the Step-4 "adversarial" case).
# -------------------------------------------------------------------------
#   Train a single policy by having it play copies of itself; periodically
#   freeze snapshots to form an opponent pool and avoid chasing a moving
#   target (and strategy cycling).
#   pool = [random_policy]
#   for it in range(iters):
#       opponent = sample(pool)                 # often a past snapshot
#       data     = play(policy, opponent)       # one side's reward = -other's
#       rl_update(policy, data)                 # PPO / Q-learning on policy
#       if it % snapshot_every == 0:
#           pool.append(freeze(policy))         # league grows over time
#   # extensions: prioritized/“league” opponents (AlphaStar), Nash averaging.
