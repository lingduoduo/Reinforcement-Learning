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

