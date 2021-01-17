import pandas as pd
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D

data = pd.read_csv("./verlustraten.csv", delimiter=";")

data["class"] = data.groupby("k").ngroup()

print(data)

fig = plt.figure()
ax = fig.add_subplot(111, projection="3d")

ax.set_xlabel("k")
ax.set_ylabel("loss")
ax.set_zlabel("Pr")

ax.bar3d(data["k"], data["loss"], 0 * data["Pr"], 1, 0.02, data["Pr"])

plt.show()