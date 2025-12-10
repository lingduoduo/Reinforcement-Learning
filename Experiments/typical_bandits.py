
### Method 1: Epsilon-greedy Greedy

import numpy as np

N = 3                # 物品总数
T = 100              # 试验次数/轮数
epsilon = 0.1        # 贪婪系数
P = [0.5, 0.6, 0.55] # 每个物品的真实被转化率
Round = [0, 0, 0]    # 每个物品被选中的次数


def pull(N, epsilon, P):
    """通过epsilon-greedy来选择物品

    Args:
        N(int) :- 物品总数
        epsilon(float) :- 贪婪系数
        P(iterables) :- 每个物品被转化率
    Returns:
        本次选择的物品
    """
    # 通过一致分布的随机数来确定是搜索还是利用
    exploration_flag = True if np.random.uniform() <= epsilon else False

    # 如果选择探索
    if exploration_flag:
        i = int(min(N-1, np.floor(N*np.random.uniform())))

    # 如果选择利用
    else:
        i = np.argmax(P)
    return i


def trial_vanilla(rounds=T):
    """做rounds轮试验

    rewards来记录从头到位的奖励数
    """
    rewards = 0
    for t in range(rounds):
        i = pull(N, epsilon, P)
        reward = np.random.binomial(1, P[i])
        rewards += reward
    return rewards

# Method 2: UCB

def calculate_delta(Round, i):
    """利用所有物品被选中的次数和i物品被选中的次数来计算delta
    
    Args:
        Round(iterables） ：- 每个物品被选择的次数
        i(int) :- 物品序号
    Returns:
        使得置信度为1-2/ sum(Round) ** 2的delta
    """
    round_i = Round[i]
    if round_i == 0:
        return 1
    else:
        return np.sqrt(np.log(sum(Round))/round_i)

def calculate_empirical(Round, Reward, i):
    """利用所有物品被选中和获得奖励的次数来计算实证参数
    
    Args:
        Round(iterables） ：- 每个物品被选择的次数
        Reward(iterables) :- 每个物品获得奖励的次数
        i(int) :- 物品序号
    Returns:
        i物品的实证参数
    """
    round_i = Round[i]
    if round_i == 0:
        return 1
    else:
        return Reward[i]/round_i

def trial_ucb(Reward, Round, rounds=T):
    """做rounds轮试验
    
    Args:
        Reward(iterables) :- 每个物品的被转化次数
        Round(iterables） ：- 每个物品被选择的次数
        rounds(int) :- 一共试验的次数
    Returns:
        一共的转化数

    rewards来记录从头到位的奖励数
    """
    rewards = 0
    for t in range(rounds):
        P_ucb = [calculate_empirical(Round, Reward, i) + calculate_delta(Round, i) for i in range(len(Round))]
        i = pull(N, epsilon, P_ucb)
        Round[i] += 1
        reward = np.random.binomial(1, P[i])
        Reward[i] += reward
        rewards += reward
    return rewards

    

### Method 3: Thompson Sampling


# 每个物品被转化率的Beta先验参数
Alpha = [25, 50, 75] 
Beta = [75, 50, 25]

def trial_thompson(Alpha, Beta, rounds=T):
    """做rounds轮试验

    Args:
        Alpha, Beta(iterables) :- 每个物品被转化率的Beta分布的参数
        rounds(int) :- 一共试验的次数
    Returns:
        一共的转化数

    rewards来记录从头到位的奖励数
    """
    rewards = 0
    for t in range(rounds):
        P_thompson = [np.random.beta(Alpha[i], Beta[i]) for i in range(len(Round))]
        i = pull(N, epsilon, P_thompson)
        Round[i] += 1
        reward = np.random.binomial(1, P[i])
        Alpha[i] += reward
        Beta[i] += 1 - reward
        rewards += reward
    return rewards