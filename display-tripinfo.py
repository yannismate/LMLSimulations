# load large xml file
import xml.etree.ElementTree as ET
import numpy as np

class Histogram:
    def __init__(self, bins, max):
        self.bins = bins
        self.max = max
        self.step = max / bins
        self.histogram = {}
        for i in range(bins):
            self.histogram[i] = 0

    def add(self, value):
        binNum = int(value / self.step)
        if binNum >= self.bins or binNum <= 0:
            return
        self.histogram[binNum] += 1

    def plot(self, title, x_label):
        import matplotlib.pyplot as plt
        x = np.arange(0, self.max, self.step)
        y = [self.histogram[i] for i in range(self.bins)]
        plt.bar(x, y, width=self.step)
        plt.title(title)
        plt.xlabel(x_label)
        plt.ylabel('Frequency')
        plt.show()

    def max_cnt(self):
        return max(self.histogram.values())

def plot_info_for(file_name):
    tree = ET.parse(file_name)
    root = tree.getroot()

    duration_histogram = Histogram(400, 2000)
    wait_histogram = Histogram(400, 2000)
    reroute_histogram = Histogram(10, 10)

    delivery_duration_histogram = Histogram(25, 50000)
    delivery_wait_histogram = Histogram(25, 2000)
    delivery_stop_time_minutes = Histogram(25, 500)

    delivery_car_info = []

    for trip in root:
        vehicle_id = trip.attrib['id']
        depart = float(trip.attrib['depart'])
        arrival = float(trip.attrib['arrival'])
        duration = float(trip.attrib['duration'])
        route_length = float(trip.attrib['routeLength'])
        waiting_time = float(trip.attrib['waitingTime'])
        time_loss = float(trip.attrib['timeLoss'])
        reroute_no = int(trip.attrib['rerouteNo'])
        stop_time = float(trip.attrib['stopTime'])

        duration_histogram.add(duration)
        wait_histogram.add(waiting_time)
        reroute_histogram.add(reroute_no)

        is_delivery_vehicle = vehicle_id.startswith('delivery')

        if is_delivery_vehicle:
            delivery_duration_histogram.add(duration)
            delivery_wait_histogram.add(waiting_time)
            delivery_stop_time_minutes.add(stop_time / 60)

            delivery_car_info.append({
                'vehicle_id': vehicle_id,
                'waiting_time': waiting_time / 60,
                'stop_time': stop_time / 60,
                'duration': duration / 60,
                'route_length': route_length / 1000,
            })

    duration_histogram.plot('Duration Histogram', 'Duration (s)')
    wait_histogram.plot('Waiting Time Histogram', 'Waiting Time (s)')
    reroute_histogram.plot('Reroute Histogram', 'Reroute No.')

    delivery_duration_histogram.plot('Accumulated Delivery Duration Histogram', 'Duration (s)')
    delivery_wait_histogram.plot('Accumulated Traffic Waiting Time Histogram for Delivery Vehicles', 'Waiting Time (s)')
    delivery_stop_time_minutes.plot('Delivery Stop Time Histogram', 'Stop Time (min)')

    total_duration = sum([info['duration'] for info in delivery_car_info])
    total_duration_hours = total_duration / 60
    money_per_hour = 15

    total_route_length = sum([info['route_length'] for info in delivery_car_info])
    liters_per_100km = 10
    fuel_price = 1.5
    total_fuel_cost = total_route_length * liters_per_100km / 100 * fuel_price

    # Round to 2 decimal places
    print("")
    print("File Name: ", file_name)
    print("Total Delivery Vehicles: ", len(delivery_car_info))
    print("Total Duration: ", round(total_duration_hours, 2), "hours")
    print("Personnel Cost: ", round(total_duration_hours * money_per_hour, 2), "EUR")
    print("Total Route Length: ", round(total_route_length, 2), "km")
    print("Theoretical Fuel Cost: ", round(total_fuel_cost, 2), "EUR", " (10L/100km, 1.5EUR/L, ignore bikes)")
    print("Total Cost: ", round(total_duration_hours * money_per_hour + total_fuel_cost, 2), "EUR")


    import matplotlib.pyplot as plt
    import numpy as np

    delivery_vehicle_ids = [info['vehicle_id'] for info in delivery_car_info]
    time_spent = {
        'on_route_time': [info['duration'] - info['stop_time'] for info in delivery_car_info],
        'stop_time': [info['stop_time'] for info in delivery_car_info],
        'waiting_time': [info['waiting_time'] for info in delivery_car_info],
        # 'stop_time': [info['stop_time'] for info in delivery_car_info],
        # 'duration': [info['duration'] for info in delivery_car_info]
    }

    x = np.arange(len(delivery_vehicle_ids))
    fig, ax = plt.subplots()
    bar_width = 0.3
    # rects1 = ax.bar(x - bar_width, time_spent['waiting_time'], bar_width, label='Waiting Time')
    # rects2 = ax.bar(x, time_spent['stop_time'], bar_width, label='Stop Time')
    # rects3 = ax.bar(x + bar_width, time_spent['duration'], bar_width, label='Duration')
    rects1 = ax.bar(x, time_spent['on_route_time'], bar_width, label='Road Time')
    rects2 = ax.bar(x + bar_width, time_spent['stop_time'], bar_width, label='Unload Time')
    ax.set_xlabel('Vehicle')
    ax.set_ylabel('Time (m)')
    ax.set_title('Time Spent for Delivery Vehicles')
    # ax.set_xticks(x)
    # ax.set_xticklabels(delivery_vehicle_ids)
    ax.legend()
    plt.show()

    # pie chart
    fig, ax = plt.subplots()
    explode = (0, 0.1, 0)
    labels = ['Driving', 'Waiting in traffic', 'Unloading Time']
    # Driving yellow, waiting in traffic red, unloading time green
    colors = ['#ff9999', '#66b3ff', '#99ff99']
    sizes = [sum(time_spent['on_route_time']) - sum(time_spent['waiting_time']), sum(time_spent['waiting_time']),
             sum(time_spent['stop_time'])]
    ax.pie(sizes, explode=explode, labels=labels, colors=colors, autopct='%1.1f%%', shadow=True, startangle=90)
    ax.axis('equal')
    plt.title('Time Spent for Delivery Vehicles')
    plt.show()

plot_info_for('tripinfo.xml')
plot_info_for('tripinfo2.xml')