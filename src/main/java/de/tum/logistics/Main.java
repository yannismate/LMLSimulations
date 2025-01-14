package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static final int SIMULATION_STEPS = 24 * 60 * 60;
  public static final int SPEEDUP_FACTOR = 60;
  public static final int SIMULATION_STEP_BEGINNING = 5 * 60 * 60;
  private static final File resourceFolder = new File("src/main/resources");
  public static TraCIPosition fromBoundary, toBoundary;

  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(resourceFolder, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo-gui", "-n", netFile.getAbsolutePath()}));
    TraCPositionVector boundary = Simulation.getNetBoundary().getValue();
    fromBoundary = boundary.get(0);
    toBoundary = boundary.get(1);

    Set<String> allowedEdges = new HashSet<>();
    for (String edgeID : Edge.getIDList()) {
      if (edgeID.startsWith(":") || edgeID.contains("cluster")) {
        continue;
      }
      if(lanesFromEdge(edgeID).stream().anyMatch(laneId -> Lane.getAllowed(laneId).contains("passenger"))) {
        allowedEdges.add(edgeID);
      }
    }

    VehicleType.copy("DEFAULT_VEHTYPE", "passenger");

    RandomRouteGenerator randomRouteGenerator = new RandomRouteGenerator("passenger", 10000, fromBoundary, toBoundary);
    randomRouteGenerator.startPopulatingThread();

    // populate with some vehicles so simulation doesn't stop
    for (int i = 0; i < 5; i++) {
      String routeId = randomRouteGenerator.fetchRandomRouteBlocking();
      if (routeId != null) {
        Vehicle.add("start_" + i, routeId, "passenger");
      }
    }

    int vehicleId = 0;
    for (int seconds = SIMULATION_STEP_BEGINNING; seconds < SIMULATION_STEPS; seconds++) {
      Simulation.step();
      int size = Vehicle.getLoadedIDList().size();
      int expected = (int) expectedCars((double) seconds / SIMULATION_STEPS, 2000);

      String timeOfDay = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
      long start = System.currentTimeMillis();
      if (seconds % 30 == 0 || Math.abs(size - expected) > 50) {
        System.out.println(timeOfDay + ": " + size + "/" + expected + " cars");
      }

      int added = 0;
      while (size < expected && added < 20) {
        String routeId = randomRouteGenerator.fetchRandomRouteBlocking();
        if (routeId == null) {
          continue;
        }
        Vehicle.add("vehicle_" + vehicleId++, routeId, "passenger");
        size++;
        added++;
      }
      try {
        Thread.sleep(1000 / SPEEDUP_FACTOR);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    Simulation.close();
  }

  public static double expectedCars(
    double linTime,
    long maxCarsInPeak
  ) {
    double a = -5, b = -2.6, c = -0.1, d = 1.125, e = 0.01;
    double coeff = Math.max(Math.sin(b * Math.sin(a * linTime + d) + c), 0) + e;
    return coeff * maxCarsInPeak;
  }

  public static List<String> lanesFromEdge(String edgeID) {
    int lanes = Edge.getLaneNumber(edgeID);
    // lane:<edgeid>_<laneIndex>
    return IntStream.range(0, lanes)
      .mapToObj(i -> edgeID + "_" + i)
      .collect(Collectors.toList());
  }

  public static Stream<String> findStreet(String streetName) {
    StringVector stringVector = Edge.getIDList();
    return stringVector.stream().filter(s -> {
      String theStreetName = Edge.getStreetName(s);
      if (theStreetName == null || theStreetName.isEmpty()) {
        return false;
      }
      return theStreetName.trim().startsWith(streetName);
    });
  }
}