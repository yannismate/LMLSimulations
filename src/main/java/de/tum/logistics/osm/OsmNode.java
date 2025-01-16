package de.tum.logistics.osm;

import com.graphhopper.jsprit.core.util.Coordinate;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.TraCIRoadPosition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record OsmNode(
  double latitude,
  double longitude,
  String street,
  String houseNum,
  String postCode
) {
  private final static int METERS_PER_DEGREE = 111_111;

  public double distanceMetersL1(OsmNode other) {
    double distanceCoord = Math.abs(latitude - other.latitude) + Math.abs(longitude - other.longitude);
    return distanceCoord * METERS_PER_DEGREE;
  }

  public double distanceMetersL2(OsmNode other) {
    double distanceCoord = Math.hypot(latitude - other.latitude, longitude - other.longitude);
    return distanceCoord * METERS_PER_DEGREE;
  }

  public boolean canBeReachedBy(String edgeId, String vehicleType) {
    return Simulation.findRoute(edgeId, searchNextRoadEdgeFor(vehicleType), vehicleType).getCost() > 0;
  }

  public boolean canRouteTo(String edgeId, String vehicleType) {
    return Simulation.findRoute(searchNextRoadEdgeFor(vehicleType), edgeId, vehicleType).getCost() > 0;
  }

  private record RoadPosition(String edgeId, double pos, int laneIndex) {
    public static RoadPosition from(TraCIRoadPosition roadPos) {
      return new RoadPosition(roadPos.getEdgeID(), roadPos.getPos(), roadPos.getLaneIndex());
    }
  }
  private final static Map<OsmNode, RoadPosition> cache = new ConcurrentHashMap<>();

  public String searchNextRoadEdgeFor(String vehicleType) {
    return cache.computeIfAbsent(this, node -> {
      TraCIRoadPosition nodeRoadPos = Simulation.convertRoad(node.longitude, node.latitude, true, vehicleType);
      RoadPosition from = RoadPosition.from(nodeRoadPos);
      nodeRoadPos.delete();
      return from;
    }).edgeId();
  }
}
