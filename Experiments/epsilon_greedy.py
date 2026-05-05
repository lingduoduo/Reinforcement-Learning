import random
from pathlib import Path


def ind_max(x: list) -> int:
    m = max(x)
    return x.index(m)


class EpsilonGreedy:
    def __init__(self, epsilon: float, counts: list, values: list):
        self.epsilon = epsilon
        self.counts = counts
        self.values = values

    def initialize(self, n_arms: int):
        self.counts = [0] * n_arms
        self.values = [0.0] * n_arms

    def select_arm(self) -> int:
        if random.random() > self.epsilon:
            return ind_max(self.values)
        return random.randrange(len(self.values))

    def update(self, chosen_arm: int, reward: float):
        self.counts[chosen_arm] += 1
        n = self.counts[chosen_arm]
        value = self.values[chosen_arm]
        self.values[chosen_arm] = ((n - 1) / n) * value + (1 / n) * reward


class BernoulliArm:
    def __init__(self, p: float):
        self.p = p

    def draw(self) -> float:
        return 1.0 if random.random() <= self.p else 0.0


def test_algorithm(algo, arms: list, num_sims: int, horizon: int) -> list:
    chosen_arms = [0.0] * (num_sims * horizon)
    rewards = [0.0] * (num_sims * horizon)
    cumulative_rewards = [0.0] * (num_sims * horizon)
    sim_nums = [0.0] * (num_sims * horizon)
    times = [0.0] * (num_sims * horizon)

    for sim in range(num_sims):
        algo.initialize(len(arms))
        for t in range(horizon):
            index = sim * horizon + t
            sim_nums[index] = sim + 1
            times[index] = t + 1

            chosen_arm = algo.select_arm()
            chosen_arms[index] = chosen_arm

            reward = arms[chosen_arm].draw()
            rewards[index] = reward
            cumulative_rewards[index] = reward if t == 0 else cumulative_rewards[index - 1] + reward

            algo.update(chosen_arm, reward)

    return [sim_nums, times, chosen_arms, rewards, cumulative_rewards]


if __name__ == "__main__":
    random.seed(1)
    means = [0.1, 0.1, 0.1, 0.1, 0.9]
    n_arms = len(means)
    random.shuffle(means)
    print(means)
    print("Best arm is " + str(ind_max(means)))

    arms = [BernoulliArm(mu) for mu in means]

    output_dir = Path(__file__).parent / "results"
    output_dir.mkdir(exist_ok=True)

    with open(output_dir / "standard_results.tsv", "w") as f:
        for epsilon in [0.1, 0.2, 0.3, 0.4, 0.5]:
            algo = EpsilonGreedy(epsilon, [], [])
            algo.initialize(n_arms)
            results = test_algorithm(algo, arms, 5000, 250)
            for i in range(len(results[0])):
                f.write(str(epsilon) + "\t")
                f.write("\t".join([str(results[j][i]) for j in range(len(results))]) + "\n")
