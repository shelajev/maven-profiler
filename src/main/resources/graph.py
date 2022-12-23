from plotille import *
from shutil import get_terminal_size
from datetime import datetime

import numpy as np
import random

def _num_formatter(val, chars, delta, left=False):
    align = '<' if left else ''
    return '{:.2f}'.format(val, align, chars)

def ms2datetime(ms):
    return datetime.fromtimestamp(ms/1000)


txt = np.loadtxt("/data.txt")
times = txt[:, 0]
times = np.array(list(map(ms2datetime, times)))

fig = Figure()
fig.width = 160
fig.set_y_limits(min_=0, max_=1)
# fig.color_mode = 'byte'

fig.register_label_formatter(float, _num_formatter)
fig.register_label_formatter(int, _num_formatter)

fig.plot(times, txt[:, 1], lc=25, label='cpu')
fig.plot(times, txt[:, 2], lc=100, label='memory')
print(fig.show())