package de.tum.logistics.problems;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.util.Coordinate;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.eclipse.sumo.libtraci.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GraphBasedRoutingCost extends AbstractForwardVehicleRoutingTransportCosts {
  private final String vehicleType;
  private final Map<String, Set<String>> edgeToJunctionMap = new ConcurrentHashMap<>();
  private final Map<String, Integer> vertexIndices = new HashMap<>();
  private RealMatrix distanceMatrix;

  public GraphBasedRoutingCost(String vehicleType) {
    this.vehicleType = vehicleType;
  }

  public void setup(List<String> edges) {
    Set<String> vertices = new HashSet<>();
    for (String edge : edges) {
      vertices.add(Edge.getFromJunction(edge));
      vertices.add(Edge.getToJunction(edge));
    }
    distanceMatrix = new Array2DRowRealMatrix(vertices.size(), vertices.size());
    for (int i = 0; i < vertices.size(); i++) {
      for (int j = 0; j < vertices.size(); j++) {
        distanceMatrix.setEntry(i, j, i == j ? 0 : Double.POSITIVE_INFINITY);
      }
    }
    int vertexIdCounter = 0;
    for (String vertex : vertices) {
      vertexIndices.put(vertex, vertexIdCounter++);
    }
    for (String edge : ProgressBar.wrap(edges, "Building distance matrix")) {
      String from = Edge.getFromJunction(edge);
      String to = Edge.getToJunction(edge);
      TraCIPosition fromPosition = Junction.getPosition(from);
      TraCIPosition toPosition = Junction.getPosition(to);
      double deltaX = toPosition.getX() - fromPosition.getX();
      double deltaY = toPosition.getY() - fromPosition.getY();
      double streetLength = Math.hypot(deltaX, deltaY);
      fromPosition.delete(); toPosition.delete();
      Set<String> connectedJunctions = edgeToJunctionMap.computeIfAbsent(edge, s -> new HashSet<>());
      connectedJunctions.add(from);
      connectedJunctions.add(to);
      streetLength = Math.min(streetLength, distanceMatrix.getEntry(vertexIndices.get(from), vertexIndices.get(to)));
      streetLength = Math.min(streetLength, distanceMatrix.getEntry(vertexIndices.get(to), vertexIndices.get(from)));
      distanceMatrix.setEntry(vertexIndices.get(from), vertexIndices.get(to), streetLength);
      distanceMatrix.setEntry(vertexIndices.get(to), vertexIndices.get(from), streetLength);
    }
    ProgressBar pb = new ProgressBarBuilder().setInitialMax(vertices.size()).setTaskName("Building distance transitive closure").build();

    // Floyd-Warshall algorithm
    for (int k = 0; k < vertices.size(); k++) {
      pb.step();
      for (int i = 0; i < vertices.size(); i++) {
        for (int j = 0; j < vertices.size(); j++) {
          double transitiveDistanceFront = distanceMatrix.getEntry(i, k) + distanceMatrix.getEntry(k, j);
          double transitiveDistanceBack = distanceMatrix.getEntry(j, k) + distanceMatrix.getEntry(k, i);
          double directDistanceFront = distanceMatrix.getEntry(i, j);
          double directDistanceBack = distanceMatrix.getEntry(j, i);
          double minDistanceFront = Math.min(directDistanceFront, transitiveDistanceFront);
          double minDistanceBack = Math.min(directDistanceBack, transitiveDistanceBack);
          double sharedDistance = Math.min(minDistanceFront, minDistanceBack);
          distanceMatrix.setEntry(i, j, sharedDistance);
          distanceMatrix.setEntry(j, i, sharedDistance);
        }
      }
    }
    pb.close();
  }

  private final Map<Location, String> edgeCache = new ConcurrentHashMap<>();

  private String edgeFromLocation(Location location) {
    return edgeCache.computeIfAbsent(location, location1 -> {
      Coordinate coordinate = location1.getCoordinate();
      double x = coordinate.getX();
      double y = coordinate.getY();
      TraCIRoadPosition edge = Simulation.convertRoad(x, y, true, vehicleType);
      return edge.getEdgeID();
    });
  }

  @Override
  public double getDistance(Location from, Location to, double v, Vehicle vehicle) {
    return calculateDistance(from, to);
  }

  @Override
  public double getTransportTime(Location from, Location to, double v, Driver driver, Vehicle vehicle) {
    return calculateDistance(from, to);
  }

  @Override
  public double getTransportCost(Location from, Location to, double v, Driver driver, Vehicle vehicle) {
    double distance = this.calculateDistance(from, to);
    return vehicle != null && vehicle.getType() != null ? distance * vehicle.getType().getVehicleCostParams().perDistanceUnit : distance;
  }

  private record RouteCacheKey(String from, String to) {}
  private final Map<RouteCacheKey, Double> distanceCache = new ConcurrentHashMap<>();

  public double calculateDistance(Location from, Location to) {
    String edgeFrom = edgeFromLocation(from);
    String edgeTo = edgeFromLocation(to);
    RouteCacheKey key = new RouteCacheKey(edgeFrom, edgeTo);
    return distanceCache.computeIfAbsent(key, (routeCacheKey) -> {
      Set<String> fromVertices = edgeToJunctionMap.get(edgeFrom);
      Set<String> toVertices = edgeToJunctionMap.get(edgeTo);
      if (edgeFrom.equals(edgeTo) || fromVertices == null || toVertices == null) {
        double deltaX = to.getCoordinate().getX() - from.getCoordinate().getX();
        double deltaY = to.getCoordinate().getY() - from.getCoordinate().getY();
        return Math.hypot(deltaX, deltaY);
      }
      double minDistance = Double.POSITIVE_INFINITY;
      for (String fromVertex : fromVertices) {
        for (String toVertex : toVertices) {
          int fromIndex = vertexIndices.get(fromVertex);
          int toIndex = vertexIndices.get(toVertex);
          double distance = distanceMatrix.getEntry(fromIndex, toIndex);
          double reverseDistance = distanceMatrix.getEntry(toIndex, fromIndex);
          distance = Math.min(distance, reverseDistance);
          if (distance < minDistance) {
            minDistance = distance;
          }
        }
      }
      // If no road connection is found, calculate the air distance
      if (minDistance == Double.POSITIVE_INFINITY) {
        double penaltyFactor = 1.5;
        double deltaX = to.getCoordinate().getX() - from.getCoordinate().getX();
        double deltaY = to.getCoordinate().getY() - from.getCoordinate().getY();
        minDistance = Math.hypot(deltaX, deltaY) * penaltyFactor;
      }
      return minDistance;
    });
  }
}
