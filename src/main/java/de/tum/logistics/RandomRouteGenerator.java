package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;

import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomRouteGenerator {
  private final static AtomicLong generatorIdCounter = new AtomicLong(0);
  private final long generatorId = generatorIdCounter.getAndIncrement();
  private final String vehicleType;
  private final int requestedRoutes;
  private final Deque<String> availableRoutes = new LinkedBlockingDeque<>();
  private final Executor executor = Executors.newFixedThreadPool(1);
  private final double fromX, fromY, toX, toY;

  private int generatedRoutes = 0;
  private long lastPopulationNotification = 0;

  public RandomRouteGenerator(
    String vehicleType,
    int requestedRouteBuffer,
    TraCIPosition fromBoundary,
    TraCIPosition toBoundary
  ) {
    this.vehicleType = vehicleType;
    this.requestedRoutes = requestedRouteBuffer;
//    this.fromBoundary = fromBoundary;
//    this.toBoundary = toBoundary;
    this.fromX = fromBoundary.getX();
    this.fromY = fromBoundary.getY();
    this.toX = toBoundary.getX();
    this.toY = toBoundary.getY();
  }

  public void startPopulatingThread() {
    new Thread(() -> {
      while (true) {
        while (availableRoutes.size() < requestedRoutes) {
          int routeNum = generatedRoutes;
          String routeId = "route_" + generatorId + "_" + routeNum;
          generateRouteFrom(routeId, vehicleType);
          availableRoutes.add(routeId);
          generatedRoutes++;
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    },"RouteCompiler").start();
  }

  public String fetchRandomRouteBlocking() {
    return availableRoutes.poll();
  }

  private void generateRouteFrom(String routeId, String vehicleType) {
    for (int attempts = 0; attempts < 100; attempts++) {
      String fromEdge = randomEntryEdgeFor(vehicleType);//randomEdgeFor(vehicleType);
      String toEdge = randomExitEdgeFor(vehicleType);//randomEdgeFor(vehicleType);
      TraCIStage route = Simulation.findRoute(fromEdge, toEdge, vehicleType);
      if (route.getLength() <= 1.0) {
        continue;
      }
      Route.add(routeId, new StringVector(new String[]{fromEdge, toEdge}));
      if (availableRoutes.size() % 100 == 0 && System.currentTimeMillis() - lastPopulationNotification > 2000) {
        System.out.println(availableRoutes.size() + "/" + requestedRoutes + " routes available");
        lastPopulationNotification = System.currentTimeMillis();
      }
      return;
    }
    throw new RuntimeException("Could not generate a route");
  }

  private final static double FIXED_ENTRY_EDGE_PROBABILITY = 0.3;
  private final static double FIXED_EXIT_EDGE_PROBABILITY = 0.3;

  private final String[] CACHED_ENTRY_POSITIONS = {
    "283871244",
    "209452708#0",
    "310823109#0",
    "292767093#0",
    "33548773#0",
    "244456569#2",
    "365762134#0",
    "4273045",
    "48339065"
  };
  private final String[] CACHED_EXIT_POSITIONS = {
    "145433978",
    "276604363#1",
    "27822842",
    "923916236#1",
    "85004762#1",
    "40317919#0",
    "144814421",
    "429424687"
  };

  private synchronized String randomEntryEdgeFor(String vehicleType) {
    if (uniformBelow(FIXED_ENTRY_EDGE_PROBABILITY)) {
      // special case
//      if (CACHED_ENTRY_POSITIONS[0] == null) {
//        // populate cache
//        for (int i = 0; i < CACHED_ENTRY_POSITIONS.length; i++) {
//          double[] coords = ENTRY_COORDINATES[i];
//          CACHED_ENTRY_POSITIONS[i] = Simulation.convertRoad(coords[0], coords[1], true, vehicleType).getEdgeID();
//        }
//      }
      int index = ThreadLocalRandom.current().nextInt(CACHED_ENTRY_POSITIONS.length);
      return CACHED_ENTRY_POSITIONS[index];
    } else {
      return randomEdgeFor(vehicleType);
    }
  }

  private synchronized String randomExitEdgeFor(String vehicleType) {
    if (uniformBelow(FIXED_EXIT_EDGE_PROBABILITY)) {
      // special case
//      if (CACHED_EXIT_POSITIONS[0] == null) {
//        // populate cache
//        for (int i = 0; i < CACHED_EXIT_POSITIONS.length; i++) {
//          double[] coords = EXIT_COORDINATES[i];
//          try {
//            CACHED_EXIT_POSITIONS[i] = Simulation.convertRoad(coords[0], coords[1], true, vehicleType).getEdgeID();
//          } catch (Exception e) {
//            throw new RuntimeException("Could not convert " + coords[0] + ", " + coords[1], e);
//          }
//        }
//      }
      int index = ThreadLocalRandom.current().nextInt(CACHED_EXIT_POSITIONS.length);
      return CACHED_EXIT_POSITIONS[index];
    } else {
      return randomEdgeFor(vehicleType);
    }
  }

  private boolean uniformBelow(double prop) {
    return ThreadLocalRandom.current().nextDouble() < prop;
  }

  private String randomEdgeFor(String vehicleType) {
    // sample from boundary
    double x = ThreadLocalRandom.current().nextDouble(fromX, toX);
    double y = ThreadLocalRandom.current().nextDouble(fromY, toY);
    TraCIRoadPosition edge = Simulation.convertRoad(x, y, false, vehicleType);
    return edge.getEdgeID();
  }
}
