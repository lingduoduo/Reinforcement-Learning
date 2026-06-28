import random
from collections import defaultdict


GRID = 4
TERMINAL = (3, 3)
ACTIONS = ("up", "down", "left", "right")
DELTAS = {"up": (-1, 0), "down": (1, 0), "left": (0, -1), "right": (0, 1)}


def reset():
    return (0, 0)


# Unlike DP, MC needs no model p(s',r|s,a) — it only samples the environment.
def step(state, action):
    if state == TERMINAL:
        return state, 0.0, True
    dr, dc = DELTAS[action]
    r, c = state
    nr = min(max(r + dr, 0), GRID - 1)
    nc = min(max(c + dc, 0), GRID - 1)
    return (nr, nc), -1.0, (nr, nc) == TERMINAL


def states():
    return [(r, c) for r in range(GRID) for c in range(GRID)]


# Generate one episode S0,A0,R1, ..., S_{T-1},A_{T-1},R_T following policy.
def rollout(policy, rng, max_steps=200):
    trajectory = []
    state = reset()
    for _ in range(max_steps):
        action = policy(state, rng)
        state_next, reward, done = step(state, action)
        trajectory.append((state, action, reward))
        state = state_next
        if done:
            break
    return trajectory


# Reverse sweep to get the return G_t = R_{t+1} + gamma * G_{t+1} for each step.
def returns_from(trajectory, gamma):
    returns = []
    G = 0.0
    for _, _, r in reversed(trajectory):
        G = r + gamma * G
        returns.append(G)
    returns.reverse()
    return returns


def uniform_policy(_state, rng):
    return rng.choice(ACTIONS)


# First-visit MC prediction (Sutton & Barto, Section 5.1).
#   For each episode, for the first occurrence of s, append G_t to Returns(s)
#   and set V(s) = average. We keep that average incrementally:
#   V(s) <- V(s) + (G - V(s)) / N(s).
def mc_policy_evaluation(policy, episodes, gamma=0.99, rng=None):
    rng = rng or random.Random(0)
    V = defaultdict(float)
    counts = defaultdict(int)
    for _ in range(episodes):
        trajectory = rollout(policy, rng)
        returns = returns_from(trajectory, gamma)
        seen = set()
        for (s, _, _), G in zip(trajectory, returns):
            if s in seen:        # first-visit: skip later visits to s
                continue
            seen.add(s)
            counts[s] += 1
            V[s] += (G - V[s]) / counts[s]
    return V, counts


# On-policy first-visit MC control with an epsilon-soft policy (Section 5.4).
#   Behaviour policy is epsilon-greedy in Q, so every action keeps some
#   probability and exploration never stops. After each episode:
#   Q(s,a) <- Q(s,a) + (G - Q(s,a)) / N(s,a) on first visits to (s,a).
def mc_control(episodes, gamma=0.99, epsilon=0.1, rng=None):
    rng = rng or random.Random(0)
    Q = defaultdict(lambda: {a: 0.0 for a in ACTIONS})
    counts = defaultdict(lambda: {a: 0 for a in ACTIONS})

    def policy(state, local_rng):
        if local_rng.random() < epsilon:
            return local_rng.choice(ACTIONS)
        return max(Q[state], key=Q[state].get)

    returns_log = []
    for ep in range(episodes):
        trajectory = rollout(policy, rng)
        returns = returns_from(trajectory, gamma)
        seen = set()
        for (s, a, _), G in zip(trajectory, returns):
            if (s, a) in seen:   # first-visit on the (state, action) pair
                continue
            seen.add((s, a))
            counts[s][a] += 1
            Q[s][a] += (G - Q[s][a]) / counts[s][a]
        if returns:
            returns_log.append(returns[0])
    greedy = {s: max(Q[s], key=Q[s].get) for s in Q}
    return Q, greedy, returns_log


def print_V(V, title):
    print(f"  {title}")
    for r in range(GRID):
        row = " ".join(f"{V[(r, c)]:7.2f}" for c in range(GRID))
        print("   " + row)


def print_policy(policy, title):
    arrows = {"up": "^", "down": "v", "left": "<", "right": ">"}
    print(f"  {title}")
    for r in range(GRID):
        row = " ".join(
            arrows[policy[(r, c)]] if (r, c) in policy and (r, c) != TERMINAL else ("." if (r, c) == TERMINAL else "?")
            for c in range(GRID)
        )
        print("   " + row)


def main():
    rng = random.Random(1)
    V, counts = mc_policy_evaluation(uniform_policy, episodes=20000, gamma=0.99, rng=rng)
    print(f"=== first-visit MC, uniform-random policy, 20000 episodes, gamma=0.99 ===")
    print_V(V, "V^pi(s)")
    print()
    print(f"V(0,0) MC estimate     = {V[(0,0)]:.2f}")
    print(f"visit counts at (0,0)  = {counts[(0,0)]}")
    print()

    rng2 = random.Random(1)
    _Q, greedy, log = mc_control(episodes=30000, gamma=0.99, epsilon=0.1, rng=rng2)
    print("=== epsilon-greedy MC control, 30000 episodes ===")
    print_policy(greedy, "greedy policy recovered")
    print()
    tail = log[-5000:]
    if tail:
        mean_tail = sum(tail) / len(tail)
        print(f"mean return over last 5000 episodes = {mean_tail:.2f}")
        print("(optimal return on this 4x4 GridWorld = -6.0)")


if __name__ == "__main__":
    main()
