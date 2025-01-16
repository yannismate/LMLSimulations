package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;

import java.util.Deque;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class RandomRouteGenerator {
  private final static AtomicLong generatorIdCounter = new AtomicLong(0);
  private final long generatorId = generatorIdCounter.getAndIncrement();
  private final String vehicleType;
  private final int requestedRoutes;
  private final Deque<String> availableRoutes = new LinkedBlockingDeque<>();
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
          Thread.sleep(250);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    },"RouteCompiler").start();
  }

  public String fetchRandomRouteBlocking() {
    String take = availableRoutes.poll();
    double availableFactor = (double) availableRoutes.size() / (requestedRoutes/3d);
    double recycleFactor = Math.max(0, 1 - availableFactor);
    if (take != null && uniformBelow(recycleFactor)) {
      availableRoutes.add(take);
    }
    return take;
  }

  private void generateRouteFrom(String routeId, String vehicleType) {
    for (int attempts = 0; attempts < 100; attempts++) {
      String fromEdge = randomEntryEdgeFor(vehicleType);
      String toEdge = randomExitEdgeFor(vehicleType);
      TraCIStage route = Simulation.findRoute(fromEdge, toEdge, vehicleType);
      if (route.getLength() <= 1.0) {
        continue;
      }
      Route.add(routeId, new StringVector(new String[]{fromEdge, toEdge}));
      double populationFactor = (double) availableRoutes.size() / (requestedRoutes);
      if (availableRoutes.size() % 100 == 0 && populationFactor < 0.3 && System.currentTimeMillis() - lastPopulationNotification > 2000) {
        System.out.println(availableRoutes.size() + "/" + requestedRoutes + " routes available");
        lastPopulationNotification = System.currentTimeMillis();
      }
      return;
    }
    throw new RuntimeException("Could not generate a route");
  }

  private final static Function<String, Double> FIXED_ENTRY_EDGE_PROBABILITY = vehicleType ->
    switch (vehicleType) {
      case "passenger" -> 0.3;
      case "bicycle" -> 0.0;
      default -> 0.1;
  };

  private final static Function<String, Double> FIXED_EXIT_EDGE_PROBABILITY = vehicleType ->
    switch (vehicleType) {
      case "passenger" -> 0.3;
      case "bicycle" -> 0.0;
      default -> 0.1;
  };

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
    if (uniformBelow(FIXED_ENTRY_EDGE_PROBABILITY.apply(vehicleType))) {
      int index = ThreadLocalRandom.current().nextInt(CACHED_ENTRY_POSITIONS.length);
      return CACHED_ENTRY_POSITIONS[index];
    } else {
      return randomEdgeFor(vehicleType);
    }
  }

  private synchronized String randomExitEdgeFor(String vehicleType) {
    if (uniformBelow(FIXED_EXIT_EDGE_PROBABILITY.apply(vehicleType))) {
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
