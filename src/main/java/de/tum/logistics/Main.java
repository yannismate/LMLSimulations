package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;
import org.joml.Vector2f;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static final int SIMULATION_STEPS = 4 * 60 * 60;
  public static final int SPEEDUP_FACTOR = 20;
  public static TraCIPosition fromBoundary, toBoundary;

  private static File resourceFolder = new File("src/main/resources");
  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(resourceFolder, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo-gui", "-n", netFile.getAbsolutePath()}));
    TraCPositionVector boundary = Simulation.getNetBoundary().getValue();
    fromBoundary = boundary.get(0);
    toBoundary = boundary.get(1);

    Map<String, Set<String>> streetNameToEdge = new HashMap<>();
    StringVector edgeIDs = Edge.getIDList();
    for (String edgeID : edgeIDs) {
      String streetName = Edge.getStreetName(edgeID);
      if (streetName == null || streetName.isEmpty()) {
        continue;
      }
      streetNameToEdge.computeIfAbsent(streetName, k -> new HashSet<>()).add(edgeID);
    }

    VehicleType.copy("DEFAULT_VEHTYPE", "passenger");

    streetNameToEdge.forEach((s, strings) -> {
      System.out.println(s + " has " + strings.size() + " edges");
    });

    String fromStreet = streetNameToEdge.keySet().stream().filter(s -> s.startsWith("Dachauer")).findFirst().get();
    String toStreet = streetNameToEdge.keySet().stream().filter(s -> s.contains("Geschwister")).findFirst().get();

    System.out.println("From street: " + fromStreet);
    System.out.println("To street: " + toStreet);

    Set<String> fromEdges = streetNameToEdge.get(fromStreet);
    Set<String> toEdges = streetNameToEdge.get(toStreet);

    String fromEdge = pickAllowedEdge(fromEdges, new ArrayList<>(Arrays.asList("passenger")));
    String toEdge = pickAllowedEdge(toEdges, new ArrayList<>(Arrays.asList("passenger")));

    System.out.println("From edge: " + fromEdge);
    System.out.println("To edge: " + toEdge);

    Route.add("route", new StringVector(new String[]{fromEdge, toEdge}));
    Vehicle.add("car", "route", "passenger");

    for (int i = 0; i < SIMULATION_STEPS; i++) {
      Simulation.step();
      if (i % 5 == 0) {
        Vehicle.add("car"+i, "route", "passenger");
      }
      try {
        Thread.sleep(1000 / SPEEDUP_FACTOR);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    Simulation.close();
  }

  private static String pickAllowedEdge(Set<String> edges, List<String> vTypes) {
    for (String edge : edges) {
      List<String> lanes = lanesFromEdge(edge);
      for (String lane : lanes) {
        StringVector allowed = Lane.getAllowed(lane);
        for (String allowedVType : allowed) {
          if (vTypes.contains(allowedVType)) {
            return edge;
          }
        }
      }
    }
    return null;
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