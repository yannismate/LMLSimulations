import numpy as np
def sample_duration():
    packets = np.random.poisson(2)  # 1-2 packets
    will_be_home = np.random.random() <= 0.95
    duration = 0
    find_parking_time = int(np.random.exponential() * 12)  # 0-60 seconds
    approach_time = int(np.random.normal(30, 10))  # 20-40 seconds
    single_stair_time = int(np.random.normal(15 + packets * 2.5, 5))  # 15-25 seconds
    stairs = np.random.random() < 0.4
    stair_time = int(stairs * single_stair_time)
    waiting_time = int(np.random.normal(20 if will_be_home else 120, 10))  # 20-40 seconds
    load_unload_time = int(np.random.normal(60 + packets * 20, 20))  # 40-80 seconds
    duration = find_parking_time + 2 * approach_time + 2 * stair_time + waiting_time + load_unload_time
    return duration

# Example usage
samples = [sample_duration() for _ in range(10000)]

# plot
import matplotlib.pyplot as plt
plt.hist(samples, bins=30, edgecolor='black')
plt.xlabel('Duration (seconds)')
plt.ylabel('Frequency')
plt.title('Distribution of Sample Durations')
plt.show()