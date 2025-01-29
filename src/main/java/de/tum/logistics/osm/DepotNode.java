package de.tum.logistics.osm;

import java.util.List;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.TraCIRoadPosition;

public record DepotNode(
    double longitude,
    double latitude,
    List<OsmNode> nodes,
    String edgeId,
    double roadPosition
) {
  public DepotNode withoutUnroutableNodes(String vehicleType) {
    if (edgeId == null) {
      return this.withEdgeData(vehicleType).withoutUnroutableNodes(vehicleType);
    }
    return new DepotNode(longitude, latitude, nodes.stream()
        .filter(node -> node.canBeReachedBy(edgeId, vehicleType))
        .toList(), edgeId, roadPosition);
  }

  public DepotNode withEdgeData(String vehicleType) {
    TraCIRoadPosition depotPosition = Simulation.convertRoad(longitude, latitude, true, vehicleType);
    return new DepotNode(longitude, latitude, nodes, depotPosition.getEdgeID(), depotPosition.getPos());
  }

}