- Bandit
Both the epsilon-Greedy algorithm and the Softmax algorithm share the following broad properties:

The algorithm’s default choice is to select the arm that currently has the highest estimated value.
The algorithm sometimes decides to explore and chooses an option that isn’t the one that currently seems best:

The epsilon-Greedy algorithm explores by selecting from all of the arms completely at random. It makes one of these random exploratory decisions with probability epsilon.
The Softmax algorithm explores by randomly selecting from all of the available arms with probabilities that are more-or-less proportional to the estimated value of each of the arms. If the other arms are noticeably worse than the best arm, they’re chosen with very low probability. If the arms all have similar values, they’re each chosen nearly equally often.
In order to achieve better performance by making an effort to have these two algorithms explore less over time, both algorithms can be set up to modify their basic parameters dynamically over time. We called this modification annealing.

How sure are you that you won’t subtly corrupt your deployment code?
How many different tests are you planning to run simultaneously? Will these tests interfere with each other? Will starting a new test while another one is already running corrupt its results?
How long do you plan to run your tests?
How many users are you willing to expose to non-preferred versions of your site?
How well-chosen is your metric of success?
How are the arms you’re measuring related to one another?
What additional information about context do you have when choosing arms? Do you have demographics based on browser information? Does your site have access to external information about people’s tastes in products you might advertise to them?
How much traffic does your site receive? Is the system you’re building going to scale up? How much traffic can your algorithm handle before it starts to slow your site down?
How much will you have to distort the setup we’ve introduced when you admit that visitors to real websites are concurrent and aren’t arriving sequentially as in our simulations?
