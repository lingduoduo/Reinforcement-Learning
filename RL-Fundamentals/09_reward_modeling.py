import math
import random
from collections import Counter, defaultdict


# Reward modeling + RLHF, from scratch with a tiny bag-of-words world.
#
# When the true reward is unknown (e.g. "be helpful"), we cannot hand-code it.
# RLHF replaces the reward function with a *learned* reward model (RM) trained
# from human preference pairs, then optimizes a policy against that RM.
#
# Stage 1 -- Reward model (Bradley-Terry / pairwise logistic):
#   Humans label which of two responses is better, giving pairs (y+ beats y-).
#   The RM assigns a scalar score r(y). Under the Bradley-Terry model the
#   probability that y+ is preferred is
#       P(y+ > y-) = sigmoid(r(y+) - r(y-)).
#   We maximize the log-likelihood of the observed preferences; the gradient
#   pushes the score of the winner up and the loser down, scaled by (1 - P),
#   so confidently-correct pairs contribute little and hard pairs contribute
#   most. Here r(y) = w . bag(y) is linear in token counts.
#
# Stage 2 -- Policy optimization against the RM (PPO-style with KL penalty):
#   The policy is rewarded by the RM but penalized for drifting from a frozen
#   reference (the SFT model):
#       reward = r_RM(y) - beta * KL(pi || pi_ref).
#   The KL term is the safety leash: without it the policy "reward-hacks" the
#   RM, piling probability onto whatever tokens the RM happens to love. beta
#   trades alignment-to-RM against staying close to the reference.
#
# The production recipe with TRL (RewardTrainer + PPOTrainer) is sketched as
# commented pseudocode at the bottom -- the same two stages at scale.


# ---------------------------------------------------------------------------
# Shared setup: a toy vocabulary of "good" and "bad" tokens.
# ---------------------------------------------------------------------------
PROMPTS = ("help me", "answer me", "explain this")
GOOD = ("clear", "specific", "kind", "thorough", "precise", "helpful")
BAD = ("vague", "rude", "wrong", "short", "cold", "careless")
VOCAB = tuple(sorted(set(GOOD + BAD)))


def bag(tokens):
    return Counter(tokens)


def sigmoid(x):
    # numerically stable logistic
    if x >= 0:
        return 1.0 / (1.0 + math.exp(-x))
    ex = math.exp(x)
    return ex / (1.0 + ex)


def softmax(z):
    m = max(z)
    exps = [math.exp(zi - m) for zi in z]
    Z = sum(exps)
    return [e / Z for e in exps]


def kl(p, q):
    total = 0.0
    for pi, qi in zip(p, q):
        if pi <= 0:
            continue
        total += pi * (math.log(pi) - math.log(max(qi, 1e-12)))
    return total


# ---------------------------------------------------------------------------
# Stage 1: Bradley-Terry reward model from preference pairs.
# ---------------------------------------------------------------------------
def score(w, tokens):
    # r(y) = w . bag(y), a linear reward over token counts.
    return sum(w.get(t, 0.0) * c for t, c in bag(tokens).items())


def sample_pair(rng):
    # synthetic human preference: a "good" response beats a "bad" one.
    x = rng.choice(PROMPTS)
    y_pos = (rng.choice(GOOD), rng.choice(GOOD))
    y_neg = (rng.choice(BAD), rng.choice(BAD))
    return x, y_pos, y_neg


def train_rm(n_pairs=500, lr=0.1, rng=None):
    rng = rng or random.Random(0)
    w = defaultdict(float)
    for _ in range(n_pairs):
        _, y_pos, y_neg = sample_pair(rng)
        # P(y+ > y-) = sigmoid(r(y+) - r(y-)); gradient scale = (1 - P).
        p = sigmoid(score(w, y_pos) - score(w, y_neg))
        grad_scale = 1.0 - p
        for t, c in bag(y_pos).items():
            w[t] += lr * grad_scale * c
        for t, c in bag(y_neg).items():
            w[t] -= lr * grad_scale * c
    return w


def rm_accuracy(w, n_pairs=200, rng=None):
    # pairwise accuracy: does the RM rank the preferred response higher?
    rng = rng or random.Random(1)
    correct = 0
    for _ in range(n_pairs):
        _, y_pos, y_neg = sample_pair(rng)
        if score(w, y_pos) > score(w, y_neg):
            correct += 1
    return correct / n_pairs


# ---------------------------------------------------------------------------
# Stage 2: PPO-style RLHF -- optimize a token policy against the RM, leashed
# to a frozen reference by a KL penalty.
# ---------------------------------------------------------------------------
def policy_probs(theta, prompt_idx):
    return softmax(theta[prompt_idx])


def sample_token(probs, rng):
    x = rng.random()
    cum = 0.0
    for i, p in enumerate(probs):
        cum += p
        if x <= cum:
            return i
    return len(probs) - 1


def rlhf_loop(w, updates=300, beta=0.1, lr=0.05, eps=0.2, batch=16, rng=None):
    rng = rng or random.Random(7)
    # policy logits per prompt over the vocabulary; reference is a frozen copy.
    theta = [[0.0 for _ in VOCAB] for _ in PROMPTS]
    reference = [row[:] for row in theta]

    history = []
    for it in range(updates):
        # --- rollout: sample tokens and score reward = RM - beta * KL ---
        rollouts = []
        for _ in range(batch):
            p_idx = rng.randrange(len(PROMPTS))
            probs_new = policy_probs(theta, p_idx)
            token = sample_token(probs_new, rng)
            probs_ref = policy_probs(reference, p_idx)
            rm_score = w.get(VOCAB[token], 0.0)
            kl_term = kl(probs_new, probs_ref)
            reward = rm_score - beta * kl_term
            log_pi_old = math.log(max(probs_new[token], 1e-12))
            rollouts.append((p_idx, token, reward, log_pi_old, kl_term))

        # --- normalize rewards into advantages (whitening) ---
        rewards = [rec[2] for rec in rollouts]
        mean_r = sum(rewards) / len(rewards)
        var_r = sum((r - mean_r) ** 2 for r in rewards) / len(rewards)
        sd_r = math.sqrt(var_r) + 1e-8
        advs = [(r - mean_r) / sd_r for r in rewards]

        # --- PPO clipped policy-gradient update ---
        for (p_idx, token, _r, log_pi_old, _kl), adv in zip(rollouts, advs):
            probs = policy_probs(theta, p_idx)
            logp = math.log(max(probs[token], 1e-12))
            ratio = math.exp(logp - log_pi_old)
            # zero the gradient once the ratio leaves the trust region.
            clipped = (adv > 0 and ratio > 1 + eps) or (adv < 0 and ratio < 1 - eps)
            if clipped:
                continue
            for i in range(len(VOCAB)):
                grad = (1.0 if i == token else 0.0) - probs[i]
                theta[p_idx][i] += lr * ratio * adv * grad

        mean_kl = sum(rec[4] for rec in rollouts) / len(rollouts)
        # report the raw RM score (reward with the KL penalty added back).
        mean_rm = sum(rec[2] + beta * rec[4] for rec in rollouts) / len(rollouts)
        history.append((it + 1, mean_rm, mean_kl))
    return theta, history


def top_tokens(theta, prompt_idx, k=3):
    probs = policy_probs(theta, prompt_idx)
    order = sorted(range(len(VOCAB)), key=lambda i: -probs[i])
    return [(VOCAB[i], probs[i]) for i in order[:k]]


def main():
    rng = random.Random(42)
    w = train_rm(n_pairs=600, rng=rng)

    print("=== Stage 1: reward model (Bradley-Terry pairwise logistic) ===")
    print()
    print("top positive-weight tokens:")
    for tok in sorted(w, key=lambda t: -w[t])[:6]:
        print(f"  {tok:<10} w = {w[tok]:+.3f}")
    print()
    print("top negative-weight tokens:")
    for tok in sorted(w, key=lambda t: w[t])[:6]:
        print(f"  {tok:<10} w = {w[tok]:+.3f}")
    print()
    print(f"RM pairwise accuracy on holdout (200 pairs) = {rm_accuracy(w):.3f}")
    print()

    print("=== Stage 2: PPO-RLHF against RM with KL penalty ===")
    print()
    print("beta trades RM-reward against staying near the reference policy:")
    for beta in (0.01, 0.1, 1.0):
        theta, hist = rlhf_loop(w, updates=150, beta=beta, rng=random.Random(0))
        first, last = hist[0], hist[-1]
        print(
            f"  beta={beta:<5} initial: RM={first[1]:+.3f} KL={first[2]:.3f}"
            f"   final: RM={last[1]:+.3f} KL={last[2]:.3f}"
        )
    print()

    # Show what the (low-beta) policy learned to emit per prompt.
    theta, _ = rlhf_loop(w, updates=150, beta=0.01, rng=random.Random(0))
    print("learned policy -- most likely tokens per prompt (beta=0.01):")
    for p_idx, prompt in enumerate(PROMPTS):
        toks = ", ".join(f"{t}({p:.2f})" for t, p in top_tokens(theta, p_idx))
        print(f"  '{prompt}'  ->  {toks}")


if __name__ == "__main__":
    main()


# ===========================================================================
# Production recipe (pseudocode) -- the same two stages with TRL at scale.
# Left commented so this file stays dependency-free and runnable.
# ===========================================================================
#
# Stage 1: reward model from pairwise preferences (Bradley-Terry head)
# -------------------------------------------------------------------------
# from trl import RewardTrainer, RewardConfig
# from transformers import AutoModelForSequenceClassification, AutoTokenizer
#
# tok = AutoTokenizer.from_pretrained("meta-llama/Llama-3.1-8B-Instruct")
# rm = AutoModelForSequenceClassification.from_pretrained(
#     "meta-llama/Llama-3.1-8B-Instruct", num_labels=1   # single scalar reward head
# )
#
# # dataset rows: {"prompt", "chosen", "rejected"} -- Bradley-Terry format.
# # RewardTrainer's loss is -log sigmoid(r(chosen) - r(rejected)), exactly the
# # pairwise logistic objective implemented in train_rm() above.
# trainer = RewardTrainer(
#     model=rm,
#     tokenizer=tok,
#     train_dataset=preference_data,
#     args=RewardConfig(output_dir="./rm", num_train_epochs=1, learning_rate=1e-5),
# )
# trainer.train()
#
# Stage 2: PPO against the RM with a KL penalty to the SFT reference
# -------------------------------------------------------------------------
# from trl import PPOTrainer, PPOConfig, AutoModelForCausalLMWithValueHead
# import torch
#
# policy = AutoModelForCausalLMWithValueHead.from_pretrained("./sft-checkpoint")
# ref    = AutoModelForCausalLMWithValueHead.from_pretrained("./sft-checkpoint")  # frozen
#
# ppo = PPOTrainer(
#     config=PPOConfig(learning_rate=1.41e-5, batch_size=64, init_kl_coef=0.05,
#                      target_kl=6.0, adap_kl_ctrl=True),  # adaptive beta on KL
#     model=policy, ref_model=ref, tokenizer=tok,
# )
#
# for batch in dataloader:
#     responses = ppo.generate(batch["query_ids"], max_new_tokens=128)
#     rewards   = rm(torch.cat([batch["query_ids"], responses], dim=-1)).logits[:, 0]
#     stats     = ppo.step(batch["query_ids"], responses, rewards)
#     # stats includes mean_kl, clip_frac, value_loss -- the PPO health signals.
