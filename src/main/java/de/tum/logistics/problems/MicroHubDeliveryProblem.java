package de.tum.logistics.problems;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.TraCIRoadPosition;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.IterationEndsListener;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;

import de.tum.logistics.osm.DepotNode;
import de.tum.logistics.osm.OsmNode;
import de.tum.logistics.problems.serialization.Route;
import de.tum.logistics.problems.serialization.Stop;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class MicroHubDeliveryProblem {

  private static final double OVERCAPACITY_FACTOR = 1.3;
  private static final int CARGO_BIKE_CAPACITY = 40;
  private static final int ITERATIONS = 5000;

  private final List<VehicleRoutingProblem> problems = new ArrayList<>();
  private final List<VehicleRoutingProblemSolution> solutions = new ArrayList<>();

  public void init(int totalNumParcels, List<DepotNode> hubs) {
    int totalAddresses = hubs.stream().map(d -> d.nodes().size()).reduce(0, Integer::sum);

    for (DepotNode hub : hubs) {
      VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

      VehicleTypeImpl cargoBikeVehicle = VehicleTypeImpl.Builder.newInstance("cargoBikeBuilder")
          .addCapacityDimension(0, CARGO_BIKE_CAPACITY)
          .build();

      vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

      int parcels = (int) Math.round((double)totalNumParcels * ((double)hub.nodes().size() / (double)totalAddresses));
      int numBikes = (int) Math.ceil(((double)parcels / (double)CARGO_BIKE_CAPACITY) * OVERCAPACITY_FACTOR);
      System.out.println("Using " + numBikes + " bikes for " + parcels + " parcels at " + hub.nodes().size() + " possible addresses");
      for (int i = 0; i < numBikes; i++) {
        VehicleImpl cargoBike = VehicleImpl.Builder.newInstance("cargoBike" + i)
            .setType(cargoBikeVehicle)
            .setReturnToDepot(true)
            .setStartLocation(Location.Builder.newInstance()
                .setCoordinate(new Coordinate(hub.longitude(), hub.latitude()))
                .build())
            .build();
        vrpBuilder.addVehicle(cargoBike);
      }

      Map<OsmNode, Integer> demandMap = new HashMap<>();
      Random rand = new Random();

      for (int i = 0; i < parcels; i++) {
        OsmNode location = hub.nodes().get(rand.nextInt(hub.nodes().size()));
        demandMap.put(location, demandMap.getOrDefault(location, 0) + 1);
      }

      demandMap = clusterDemand(demandMap);

      for (Map.Entry<OsmNode, Integer> entry : ProgressBar.wrap(demandMap.entrySet(),
          "Converting geo coordinates to jsprit jobs")) {
        OsmNode location = entry.getKey();
        Integer demand = entry.getValue();
        Service delivery = Service.Builder.newInstance("delivery" + location.hashCode())
            .addSizeDimension(0, demand)
            .setLocation(Location.newInstance(location.longitude(), location.latitude()))
            .build();
        vrpBuilder.addJob(delivery);
      }

      problems.add(vrpBuilder.build());
    }

  }

  public void solve() {
    if (problems.isEmpty()) {
      throw new IllegalStateException("Vehicle routing problems not initialized");
    }
    Instant timeBeforeSolve = Instant.now();
    int threads = Runtime.getRuntime().availableProcessors();

    for (VehicleRoutingProblem vrp : problems) {
      System.out.println("Using " + threads + " threads for solving " + ITERATIONS + " iterations");
      System.out.println("Solving vehicle routing problem for " + vrp.getJobs().size() + " stops...");
      VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(vrp).setExecutorService(
          Executors.newFixedThreadPool(threads), threads).buildAlgorithm();
      algorithm.setMaxIterations(ITERATIONS);
      ProgressBar pb = new ProgressBarBuilder().setInitialMax(ITERATIONS).setTaskName("Solving VRP").build();
      algorithm.addListener((IterationEndsListener) (i, problem, solutions) -> {
        pb.step();
        pb.setExtraMessage("Best solution cost: " + Solutions.bestOf(solutions).getCost());
      });
      pb.maxHint(ITERATIONS);

      VehicleRoutingProblemSolution solution = Solutions.bestOf(algorithm.searchSolutions());

      System.out.println();
      System.out.println("Solution found after " + Duration.between(timeBeforeSolve, Instant.now()).toSeconds()
          + " seconds, skipped " + solution.getUnassignedJobs().size() + " jobs");
      solutions.add(solution);
    }

  }

  public void writeRouteXML(File targetFile, String carrierName, String carrierColor) {
    if (solutions.isEmpty()) {
      throw new IllegalStateException("No solution available");
    }

    List<Route> routes = new ArrayList<>();

    System.out.println("Writing " + carrierName + " routes to " + targetFile.getAbsolutePath());

    int numRoute = 0;
    int totalVehicleCount = solutions.stream().mapToInt(s -> s.getRoutes().size()).sum();

    for (VehicleRoute vhRoute : solutions.stream().flatMap(s -> s.getRoutes().stream()).toList()) {
      System.out.println("Vehicle " + (numRoute+1) + " of " + totalVehicleCount + " has " + vhRoute.getTourActivities().getJobs().size() + " stops");
      List<Stop> stops = new ArrayList<>();
      List<String> edges = new ArrayList<>();

      Location hubLocation = vhRoute.getActivities().getFirst().getLocation();
      String hubEdge = Simulation.convertRoad(hubLocation.getCoordinate().getX(), hubLocation.getCoordinate().getY(), true, "bicycle").getEdgeID();

      edges.add(hubEdge);
      for (TourActivity activity : vhRoute.getActivities()) {
        Coordinate stopCoords = activity.getLocation().getCoordinate();
        TraCIRoadPosition roadPos = Simulation.convertRoad(stopCoords.getX(), stopCoords.getY(), true, "bicycle");

        edges.add(roadPos.getEdgeID());

        int packets = activity.getSize().get(0);
        boolean willBeHome = ThreadLocalRandom.current().nextDouble() <= 0.95;
        int approachTime = (int) (ThreadLocalRandom.current().nextGaussian(10, 5));// 5-15 seconds
        int singleStairTime = (int) (ThreadLocalRandom.current().nextGaussian(15 + packets*2.5d, 5));// 15-25 seconds
        int stairs = ThreadLocalRandom.current().nextDouble() < 0.4 ? 0 : (ThreadLocalRandom.current().nextInt(1, 6));
        int stairTime = stairs * singleStairTime;
        int waitingTime = (int) (ThreadLocalRandom.current().nextGaussian(willBeHome ? 20 : 120, 10));// 20-40 seconds
        int loadUnloadTime = (int) (ThreadLocalRandom.current().nextGaussian(60 + packets*20, 20));// 40-80 seconds
        int totalDuration = 2 * approachTime + 2 * stairTime + waitingTime + loadUnloadTime;
        stops.add(new Stop(roadPos.getEdgeID(), roadPos.getPos(), totalDuration, true));
      }
      edges.add(hubEdge);
      Route route = new Route("delivery_" + carrierName + "_" + numRoute, edges, stops);
      route = route.withSortedStopsOnSameEdge();
      routes.add(route);
      numRoute++;
    }

    System.out.println("Done computing routes, writing to file...");

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
      writer.write("<routes>");
      writer.newLine();
      writer.write("    <vType id=\"delivery_bike_" + carrierName + "\" vClass=\"bicycle\" color=\"" + carrierColor + "\"/>");
      writer.newLine();
      for (Route route : routes) {
        route.writeXML(writer, "delivery_bike_" + carrierName);
        writer.newLine();
      }
      writer.write("</routes>");
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Done writing routes to " + targetFile.getAbsolutePath());
  }

  public void plotSolution(File targetFolder) {
    if (solutions.isEmpty()) {
      throw new IllegalStateException("No solutions available");
    }
    double latMin = Double.MAX_VALUE, latMax = Double.MIN_VALUE, longMin = Double.MAX_VALUE, longMax = Double.MIN_VALUE;
    for (VehicleRoutingProblemSolution solution : solutions) {
      for (VehicleRoute route : solution.getRoutes()) {
        for (TourActivity activity : route.getActivities()) {
          Coordinate coordinate = activity.getLocation().getCoordinate();
          longMin = Math.min(longMin, coordinate.getX());
          longMax = Math.max(longMax, coordinate.getX());
          latMin = Math.min(latMin, coordinate.getY());
          latMax = Math.max(latMax, coordinate.getY());
        }
      }
    }

    for (int i = 0; i < solutions.size(); i++) {
      Plotter plotter = new Plotter(problems.get(i), solutions.get(i));
      plotter.setBoundingBox(longMin, latMin, longMax, latMax);
      plotter.plot(new File(targetFolder, "solution_hub_" + i + ".png").getAbsolutePath(), "Solution");
    }
  }

  public Map<OsmNode, Integer> clusterDemand(Map<OsmNode, Integer> demandMap) {
    Map<OsmNode, Integer> clusteredDemand = new HashMap<>(demandMap);
    for (OsmNode location : demandMap.keySet()) {
      if (!clusteredDemand.containsKey(location) || clusteredDemand.get(location) >= 10) {
        continue;
      }
      for (OsmNode otherLocation : demandMap.keySet()) {
        if (location == otherLocation) {
          continue;
        }
        if (location.distanceMetersL2(otherLocation) < 5) {
          clusteredDemand.put(location, clusteredDemand.get(location) + demandMap.get(otherLocation));
          clusteredDemand.remove(otherLocation);
        }
      }
    }
    return clusteredDemand;
  }

}