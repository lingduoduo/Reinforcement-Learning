import math
import random

# Step 1: a tiny verifier environment
QUESTIONS = (
    {"prompt": "what is 1+2",      "correct": 2, "n_answers": 4},
    {"prompt": "what is 3*3",      "correct": 0, "n_answers": 4},
    {"prompt": "capital of France", "correct": 3, "n_answers": 4},
)
N_PROMPTS = len(QUESTIONS)
N_ANSWERS = 4

# Step 2: policy: softmax over K answer tokens per prompt
def softmax(z):
    m = max(z)
    exps = [math.exp(zi - m) for zi in z]
    Z = sum(exps)
    return [e / Z for e in exps]


def policy_probs(theta, p_idx):
    return softmax(theta[p_idx])


def entropy(probs):
    # Shannon entropy in nats; high = exploratory, low = collapsed/deterministic.
    return -sum(p * math.log(max(p, 1e-12)) for p in probs)


def mean_entropy(theta):
    # average policy entropy across all prompts -- the global collapse signal.
    return sum(entropy(policy_probs(theta, p)) for p in range(N_PROMPTS)) / N_PROMPTS


def kl_to(theta, reference, p_idx):
    # KL(pi || reference) on one prompt: how far the policy has drifted.
    probs = policy_probs(theta, p_idx)
    ref = policy_probs(reference, p_idx)
    return sum(p * (math.log(max(p, 1e-12)) - math.log(max(q, 1e-12)))
               for p, q in zip(probs, ref))


def sample(probs, rng):
    x = rng.random()
    cum = 0.0
    for i, p in enumerate(probs):
        cum += p
        if x <= cum:
            return i
    return len(probs) - 1


def verify(p_idx, answer):
    return 1.0 if answer == QUESTIONS[p_idx]["correct"] else 0.0

# Step 3: group sampling and group-relative advantage
def grpo_step(theta, reference, rng, G=8, beta=0.01, lr=0.1):
    p_idx = rng.randrange(N_PROMPTS)
    probs = policy_probs(theta, p_idx)
    samples = [sample(probs, rng) for _ in range(G)]
    rewards = [verify(p_idx, s) for s in samples]
    mean_r = sum(rewards) / G
    var_r = sum((r - mean_r) ** 2 for r in rewards) / G
    std_r = math.sqrt(var_r) + 1e-8
    advs = [(r - mean_r) / std_r for r in rewards]

    probs_ref = policy_probs(reference, p_idx)
    kl = sum(p * (math.log(max(p, 1e-12)) - math.log(max(q, 1e-12))) for p, q in zip(probs, probs_ref))

    for a, A in zip(samples, advs):
        for i in range(N_ANSWERS):
            grad_logpi = (1.0 if i == a else 0.0) - probs[i]
            theta[p_idx][i] += (lr / G) * A * grad_logpi

    for i in range(N_ANSWERS):
        theta[p_idx][i] -= beta * (probs[i] - probs_ref[i])

    return mean_r, kl, mean_entropy(theta)

# Step 4: compare to REINFORCE baseline (value-free, no KL leash)
def reinforce_step(theta, rng, reference, lr=0.1):
    p_idx = rng.randrange(N_PROMPTS)
    probs = policy_probs(theta, p_idx)
    a = sample(probs, rng)
    r = verify(p_idx, a)
    for i in range(N_ANSWERS):
        grad_logpi = (1.0 if i == a else 0.0) - probs[i]
        theta[p_idx][i] += lr * r * grad_logpi
    # reference is only measured here, never used in the update -- so KL drift
    # is unconstrained, unlike GRPO's beta term.
    return r, kl_to(theta, reference, p_idx), mean_entropy(theta)


def train_grpo(updates=500, rng=None, beta=0.01):
    rng = rng or random.Random(0)
    theta = [[0.0] * N_ANSWERS for _ in range(N_PROMPTS)]
    reference = [row[:] for row in theta]
    history = []
    for t in range(updates):
        mean_r, kl, ent = grpo_step(theta, reference, rng, beta=beta)
        history.append((mean_r, kl, ent))
    return theta, history


def train_reinforce(updates=500, rng=None):
    rng = rng or random.Random(0)
    theta = [[0.0] * N_ANSWERS for _ in range(N_PROMPTS)]
    reference = [row[:] for row in theta]   # frozen init, for measuring drift
    history = []
    for t in range(updates):
        r, kl, ent = reinforce_step(theta, rng, reference)
        history.append((r, kl, ent))
    return theta, history

# Step 5: observe entropy and KL
def evaluate(theta, episodes=200, rng=None):
    rng = rng or random.Random(42)
    total = 0.0
    for _ in range(episodes):
        p_idx = rng.randrange(N_PROMPTS)
        probs = policy_probs(theta, p_idx)
        a = max(range(N_ANSWERS), key=lambda i: probs[i])
        total += verify(p_idx, a)
    return total / episodes


def main():
    print("=== GRPO in miniature: tiny verifier bandit ===")
    print(f"prompts: {[q['prompt'] for q in QUESTIONS]}")
    print(f"correct answers: {[q['correct'] for q in QUESTIONS]}")
    print()

    theta_grpo, hist_grpo = train_grpo(updates=400, rng=random.Random(3))
    theta_rf, hist_rf = train_reinforce(updates=400, rng=random.Random(3))

    def block_mean(xs, block):
        return [sum(xs[i : i + block]) / block for i in range(0, len(xs) - block + 1, block)]

    # Step 5: observe entropy and KL alongside reward for both methods.
    g_r = block_mean([m for m, _k, _h in hist_grpo], 50)
    g_kl = block_mean([k for _m, k, _h in hist_grpo], 50)
    g_h = block_mean([h for _m, _k, h in hist_grpo], 50)
    rf_r = block_mean([m for m, _k, _h in hist_rf], 50)
    rf_kl = block_mean([k for _m, k, _h in hist_rf], 50)
    rf_h = block_mean([h for _m, _k, h in hist_rf], 50)

    print(f"{'block':<7}{'GRPO r':<9}{'GRPO KL':<10}{'GRPO H':<9}"
          f"{'RF r':<9}{'RF KL':<10}{'RF H':<9}")
    for i in range(len(g_r)):
        print(f"{i+1:<7}{g_r[i]:<9.3f}{g_kl[i]:<10.4f}{g_h[i]:<9.4f}"
              f"{rf_r[i]:<9.3f}{rf_kl[i]:<10.4f}{rf_h[i]:<9.4f}")

    print()
    grpo_acc = evaluate(theta_grpo)
    rf_acc = evaluate(theta_rf)
    print(f"greedy evaluation accuracy:")
    print(f"  GRPO       = {grpo_acc*100:.1f}%")
    print(f"  REINFORCE  = {rf_acc*100:.1f}%")

    print()
    print(f"final policy entropy:  GRPO H = {mean_entropy(theta_grpo):.4f} nats,"
          f"  REINFORCE H = {mean_entropy(theta_rf):.4f} nats")
    print(f"final KL from init:    GRPO   = {g_kl[-1]:.4f},  REINFORCE = {rf_kl[-1]:.4f}")
    print()
    print("Step 5 reading: both reach 100% greedy accuracy, but entropy and KL show")
    print("HOW they got there. Entropy H collapses in both as the policy sharpens")
    print("onto the correct token; KL from init grows as the policy drifts. At the")
    print("default beta=0.01 the KL leash is negligible, so GRPO's stronger group-")
    print("relative signal actually sharpens MORE than REINFORCE (lower H, higher KL).")
    print()

    # The beta knob: KL penalty strength directly controls drift vs sharpening.
    print("beta sweep -- the KL leash is a knob, not decoration (400 updates each):")
    print(f"  {'beta':<8}{'final_r':<10}{'KL':<10}{'entropy H':<12}{'greedy acc':<10}")
    for beta in (0.01, 0.1, 0.5):
        th, hist = train_grpo(updates=400, rng=random.Random(3), beta=beta)
        print(f"  {beta:<8}{hist[-1][0]:<10.3f}{hist[-1][1]:<10.4f}"
              f"{mean_entropy(th):<12.4f}{evaluate(th)*100:<10.0f}")
    print("  -> larger beta holds KL near 0 and keeps entropy high (stays close to")
    print("     the reference) while still solving the task -- this is the knob that")
    print("     prevents reward hacking and entropy collapse in real RLHF.")
    print()
    print("GRPO uses the group-mean as baseline and group-std for normalization —")
    print("no critic, no reward model. This is the DeepSeek-R1 recipe in one page.")


if __name__ == "__main__":
    main()