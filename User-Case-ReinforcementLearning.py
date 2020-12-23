
## Model based
def optimal_value_iteration(mdp, V0, num_iterations, epsilon=0.0001):
    V = np.zeros((num_iterations+1, mdp.S))
    V[0][:] = np.ones(mdp.S)*V0
    X = np.zeros((num_iterations+1, mdp.A, mdp.S))
    star = np.zeros((num_iterations+1,mdp.S))
    for k in range(num_iterations):
        for s in range(mdp.S):
            for a in range(mdp.A):
                X[k+1][a][s] = mdp.R[a][s] + mdp.discount*np.sum(mdp.P[a][s].dot(V[k]))
            star[k+1][s] = (np.argmax(X[k+1,:,s]))
            V[k+1][s] = np.max(X[k+1,:,s])

        if (np.max(V[k+1]-V[k])-np.min(V[k+1]-V[k])) < epsilon:
            V[k+1:] = V[k+1]
            star[k+1:] = star[k+1]
            X[k+1:] = X[k+1]
            break
        else: pass

    return star, V, X

start, V, X = optimal_value_iteration()


## e-greedy
def q_learning(mdp, num_episodes, T_max, epsilon=0.01):
    Q = np.zeros((mdp.S, mdp.A))
    episode_rewards = np.zeros(num_episodes)
    policy = np.ones(mdp.S)
    V = np.zeros((num_episodes, mdp.S))
    N = np.zeros((mdp.S, mdp.A))
    for i_episode in range(num_episodes): 
        # epsilon greedy exploration
        greedy_probs = epsilon_greedy_exploration(Q, epsilon, mdp.A)
        state = np.random.choice(np.arange(mdp.S))
        for t in range(T_max):
            # epsilon greedy exploration
            action_probs = greedy_probs(state)
            action = np.random.choice(np.arange(len(action_probs)), p=action_probs)
            next_state, reward = playtransition(mdp, state, action)
            episode_rewards[i_episode] += reward
            N[state, action] += 1
            alpha = 1/(t+1)**0.8
            best_next_action = np.argmax(Q[next_state])    
            td_target = reward + mdp.discount * Q[next_state][best_next_action]
            td_delta = td_target - Q[state][action]
            Q[state][action] += alpha * td_delta
            state = next_state
        V[i_episode,:] = Q.max(axis=1)
        policy = Q.argmax(axis=1)

    return V, policy, episode_rewards, N


## e-greedy exploration
def epsilon_greedy_exploration(Q, epsilon, num_actions):
    def policy_exp(state):
        probs = np.ones(num_actions, dtype=float) * epsilon / num_actions
        best_action = np.argmax(Q[state])
        probs[best_action] += (1.0 - epsilon)
        return probs
    return policy_exp

## e-greedy
def UCB_exploration(Q, num_actions, beta=1):
    def UCB_exp(state, N, t):
        probs = np.zeros(num_actions, dtype=float)
        Q_ = Q[state,:]/max(Q[state,:]) + np.sqrt(beta*np.log(t+1)/(2*N[state]))
        best_action = Q_.argmax()
        probs[best_action] = 1
        return probs
    return UCB_exp