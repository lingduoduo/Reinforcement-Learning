### Bandit
Both the epsilon-Greedy algorithm and the Softmax algorithm share the following broad properties:

- The algorithm’s default choice is to select the arm that currently has the highest estimated value.
- The algorithm sometimes decides to explore and chooses an option that isn’t the one that currently seems best:

The epsilon-Greedy algorithm explores by selecting from all of the arms completely at random. It makes one of these random exploratory decisions with probability epsilon.
The Softmax algorithm explores by randomly selecting from all of the available arms with probabilities that are more-or-less proportional to the estimated value of each of the arms. If the other arms are noticeably worse than the best arm, they’re chosen with very low probability. If the arms all have similar values, they’re each chosen nearly equally often.
In order to achieve better performance by making an effort to have these two algorithms explore less over time, both algorithms can be set up to modify their basic parameters dynamically over time. We called this modification annealing.

- How sure are you that you won’t subtly corrupt your deployment code?
- How many different tests are you planning to run simultaneously? Will these tests interfere with each other? Will starting a new test while another one is already running corrupt its results?
- How long do you plan to run your tests?
- How many users are you willing to expose to non-preferred versions of your site?
- How well-chosen is your metric of success?
- How are the arms you’re measuring related to one another?
- What additional information about context do you have when choosing arms? Do you have demographics based on browser information? Does your site have access to external information about people’s tastes in products you might advertise to them?
- How much traffic does your site receive? Is the system you’re building going to scale up? How much traffic can your algorithm handle before it starts to slow your site down?
- How much will you have to distort the setup we’ve introduced when you admit that visitors to real websites are concurrent and aren’t arriving sequentially as in our simulations?

First, the agent interacts with the environment by performing an action
The agent performs an action and moves from one state to another
And then the agent will receive a reward based on the action it performed
Based on the reward, the agent will understand whether the action was good or bad
If the action was good, that is, if the agent received a positive reward, then the agent will prefer performing that action or else the agent will try performing another action which results in a positive reward. So it is basically a trial and error learning process
Agent - Agents are the software programs that make intelligent decisions and they are basically learners in RL.

Policy - A policy defines the agent's behavior in an environment. The way in which the agent decides which action to perform depends on the policy. These policies represent the way in which we choose to perform an action to reach our goal. A policy is often denoted by the symbol 𝛑. A policy can be in the form of a lookup table or a complex search process.

Value - A value function denotes how good it is for an agent to be in a particular state. It is dependent on the policy and is often denoted by v(s). It is equal to the total expected reward received by the agent starting from the initial state. There can be several value functions; the optimal value function is the one that has the highest value for all the states compared to other value functions. Similarly, an optimal policy is the one that has the optimal value function.

Model - Model is the agent's representation of an environment. The learning can be of two types—model-based learning and model-free learning. In model-based learning, the agent exploits previously learned information to accomplish a task, whereas in model-free learning, the agent simply relies on a trial-and-error experience for performing the right action. 
