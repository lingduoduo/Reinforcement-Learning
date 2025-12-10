
##########################################################
# BanditsBook/python/algorithms/softmax/annealing.py
##########################################################
import math
import random

def ind_max(x):
    m = max(x)
    return x.index(m)

import math
import random


def categorical_draw(probs):
    z = random.random()
    cum_prob = 0.0
    for i in range(len(probs)):
        prob = probs[i]
        cum_prob += prob
        if cum_prob > z:
            return i
    
    return len(probs) - 1


class AnnealingSoftmax:
    def __init__(self, counts, values):
        self.counts = counts
        self.values = values
        return
    
    def initialize(self, n_arms):
        self.counts = [0 for col in range(n_arms)]
        self.values = [0.0 for col in range(n_arms)]
        return
    
    def select_arm(self):
        t = sum(self.counts) + 1
        temperature = 1 / math.log(t + 0.0000001)
        
        z = sum([math.exp(v / temperature) for v in self.values])
        probs = [math.exp(v / temperature) / z for v in self.values]
        return categorical_draw(probs)
    
    def update(self, chosen_arm, reward):
        self.counts[chosen_arm] = self.counts[chosen_arm] + 1
        n = self.counts[chosen_arm]
        
        value = self.values[chosen_arm]
        new_value = ((n - 1) / float(n)) * value + (1 / float(n)) * reward
        self.values[chosen_arm] = new_value
        return
    
algo = AnnealingSoftmax(1.0, []);
algo.initialize(2)


##########################################################
# BanditsBook/python/arms/bernoulli.py
##########################################################
class BernoulliArm():
    def __init__(self, p):
        self.p = p
    
    def draw(self):
        if random.random() > self.p:
            return 0.0
        else:
            return 1.0

random.seed(1)
means = [0.1, 0.1, 0.1, 0.1, 0.9]
n_arms = len(means)
random.shuffle(means)
print(means)
print("Best arm is " + str(ind_max(means)))

# arms = map(lambda mu: BernoulliArm(mu), means)
arms = []
for i in range(len(means)):
    arms.append(BernoulliArm(means[i]))
print(arms[0].draw())
print(arms[1].draw())
print(arms[2].draw())
print(arms[3].draw())
print(arms[4].draw())


##########################################################
# BanditsBook/python/testing_framework/tests.py
##########################################################
def test_algorithm(algo, arms, num_sims, horizon):
    chosen_arms = [0.0 for i in range(num_sims * horizon)]
    rewards = [0.0 for i in range(num_sims * horizon)]
    cumulative_rewards = [0.0 for i in range(num_sims * horizon)]
    sim_nums = [0.0 for i in range(num_sims * horizon)]
    times = [0.0 for i in range(num_sims * horizon)]

    for sim in range(num_sims):
        sim = sim + 1
        algo.initialize(len(arms))

        for t in range(horizon):
            t = t + 1
            index = (sim - 1) * horizon + t - 1
            sim_nums[index] = sim
            times[index] = t

            chosen_arm = algo.select_arm()
            chosen_arms[index] = chosen_arm

            reward = arms[chosen_arms[index]].draw()
            rewards[index] = reward

            if t == 1:
                cumulative_rewards[index] = reward
            else:
                cumulative_rewards[index] = cumulative_rewards[index - 1] + reward

            algo.update(chosen_arm, reward)

    return [sim_nums, times, chosen_arms, rewards, cumulative_rewards]


##########################################################
# Book/python/algorithms/softmax/test_annealing.py
##########################################################
algo = AnnealingSoftmax([], [])
algo.initialize(n_arms)
results = test_algorithm(algo, arms, 5000, 250)

f = open("/Users/ling/Desktop/Git/Python/Bandit/annealing_softmax_results.tsv", "w")

for i in range(len(results[0])):
  f.write("\t".join([str(results[j][i]) for j in range(len(results))]) + "\n")

f.close()