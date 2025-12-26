### Fundamentals

- Agent
- Enviornment
- State
- Action
- Reward
- State Transition
- Return
- Value Function
- Action Value Function
- State Value Function

Suppose we have a good policy f(a|s)
- Upon observe the state s_t, ramdom sampling : a_t ~ f(.|s_t)

Suppose we know the optimal action-value fucntion Q(s, a)
- Upone observe the state s_t, choose the action that maximizes the value: a_t = argmax_a Q(s_t, a)


https://github.com/DeepRLChinese/DeepRL-Chinese
```
conda create -n  reinforcement-learning python=3.10
pip install -r requirement.txt
conda install -c conda-forge jupyter notebook
```

### Disclaimer

This repository and its contents are collected and shared solely for academic and research purposes.
All code, data, and related materials are intended to support independent study, experimentation, and learning.

If you believe any part of this repository inadvertently includes content that should not be shared publicly or may cause concern, please contact me immediately. I will review and, if necessary, remove the material without delay.

I do not claim ownership of any third-party data or content and have made every effort to respect intellectual property and privacy rights.
