package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {

  public static final int SIMULATION_STEPS = 4 * 60 * 60;
  public static final int SPEEDUP_FACTOR = 20;
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

    Random random = new Random();

    for (int i = 0; i < 100; i++) {
      System.out.println("Generating route route" + i + "...");
      String fromEdge = allowedEdges.stream().skip(random.nextInt(allowedEdges.size())).findFirst().get();
      String toEdge = allowedEdges.stream().skip(random.nextInt(allowedEdges.size())).findFirst().get();

      TraCIStage route = Simulation.findRoute(fromEdge, toEdge, "passenger");
      if (route.getLength() <= 1.0) {
        i--;
        continue;
      }

      System.out.println("From edge: " + fromEdge);
      System.out.println("To edge: " + toEdge);

      Route.add("route" + i, new StringVector(new String[]{fromEdge, toEdge}));
      Vehicle.add("car" + i, "route" + i, "passenger");
    }

    for (int i = 0; i < SIMULATION_STEPS; i++) {
      Simulation.step();
      if (i == 3000) {
        System.out.println("BREAK");
      }
      try {
        Thread.sleep(1000 / SPEEDUP_FACTOR);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    Simulation.close();
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