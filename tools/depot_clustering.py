import json
import numpy as np
from sklearn.cluster import KMeans
import argparse

def load_data(input_file):
    with open(input_file, 'r') as f:
        return json.load(f)

def save_centroids(output_file, centroids_data):
    with open(output_file, 'w') as f:
        json.dump(centroids_data, f, indent=4)

def calculate_distances(coordinates, labels, centroids):
    distances = []
    for i, coord in enumerate(coordinates):
        centroid = centroids[labels[i]]
        distance = np.linalg.norm(coord - centroid) * 111111  # Convert to meters
        distances.append(distance)
    return distances

def main(input_file, output_file, num_clusters):
    data = load_data(input_file)
    coordinates = np.array([(entry['longitude'], entry['latitude']) for entry in data])

    kmeans = KMeans(n_clusters=num_clusters, random_state=42).fit(coordinates)
    centroids = kmeans.cluster_centers_

    centroid_list = [{'centroid_id': i, 'longitude': longitude, 'latitude': latitude, 'nodes': []}
                     for i, (longitude, latitude) in enumerate(centroids)]

    for i, label in enumerate(kmeans.labels_):
        centroid_list[label]['nodes'].append(data[i])

    save_centroids(output_file, centroid_list)

    distances = calculate_distances(coordinates, kmeans.labels_, centroids)
    average_distance = np.mean(distances)
    max_distance = np.max(distances)

    print(f'Average distance from nodes to their centroid: {average_distance:.4f} meters')
    print(f'Maximum distance from a node to its centroid: {max_distance:.4f} meters')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Perform K-means clustering on geographical data')
    parser.add_argument('input_file', type=str, help='Path to the input JSON file')
    parser.add_argument('output_file', type=str, help='Path to the output JSON file')
    parser.add_argument('num_clusters', type=int, help='Number of clusters (centroids) to find')

    args = parser.parse_args()
    main(args.input_file, args.output_file, args.num_clusters)
