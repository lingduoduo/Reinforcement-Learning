import numpy as np

N = 3
T = 100
epsilon = 0.1
P = [0.5, 0.6, 0.55]


def pull(n: int, eps: float, p: list) -> int:
    """Select an item via epsilon-greedy.

    Args:
        n: total number of items
        eps: exploration coefficient
        p: estimated conversion rates per item
    Returns:
        index of selected item
    """
    if np.random.uniform() <= eps:
        return int(min(n - 1, np.floor(n * np.random.uniform())))
    return int(np.argmax(p))


def trial_vanilla(rounds: int = T) -> int:
    """Run epsilon-greedy for `rounds` steps using module-level P, return total conversions."""
    rewards = 0
    for _ in range(rounds):
        i = pull(N, epsilon, P)
        rewards += np.random.binomial(1, P[i])
    return rewards


def calculate_delta(round_counts: list, i: int) -> float:
    """UCB confidence half-width for arm i."""
    round_i = round_counts[i]
    if round_i == 0:
        return 1.0
    return np.sqrt(np.log(sum(round_counts)) / round_i)


def calculate_empirical(round_counts: list, reward_counts: list, i: int) -> float:
    """Empirical conversion rate for arm i."""
    round_i = round_counts[i]
    if round_i == 0:
        return 1.0
    return reward_counts[i] / round_i


def trial_ucb(reward_counts: list, round_counts: list, rounds: int = T) -> int:
    """Run UCB for `rounds` steps, return total conversions."""
    rewards = 0
    for _ in range(rounds):
        p_ucb = [
            calculate_empirical(round_counts, reward_counts, i) + calculate_delta(round_counts, i)
            for i in range(len(round_counts))
        ]
        i = pull(N, epsilon, p_ucb)
        round_counts[i] += 1
        reward = np.random.binomial(1, P[i])
        reward_counts[i] += reward
        rewards += reward
    return rewards


Alpha = [25, 50, 75]
Beta_params = [75, 50, 25]


def trial_thompson(alpha: list, beta: list, round_counts: list, rounds: int = T) -> int:
    """Run Thompson sampling for `rounds` steps, return total conversions."""
    rewards = 0
    for _ in range(rounds):
        p_thompson = [np.random.beta(alpha[i], beta[i]) for i in range(len(round_counts))]
        i = pull(N, epsilon, p_thompson)
        round_counts[i] += 1
        reward = np.random.binomial(1, P[i])
        alpha[i] += reward
        beta[i] += 1 - reward
        rewards += reward
    return rewards
