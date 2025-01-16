package de.tum.logistics.problems;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.listener.IterationEndsListener;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem.FleetSize;
import com.graphhopper.jsprit.core.problem.job.Activity;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import de.tum.logistics.osm.OsmNode;
import de.tum.logistics.problems.serialization.Route;
import de.tum.logistics.problems.serialization.Stop;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.TraCIRoadPosition;
import org.eclipse.sumo.libtraci.TraCIStage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class TruckDeliveryProblem {
  private final static int VEHICLE_CAPACITY = 150;
  private final static int ITERATIONS = 10_000;
  private final static double OVERCAPACITY_FACTOR = 1.3;
  public final static String ENTRY_EDGE = "265616622#0";
  public final static String EXIT_EDGE = "315225707";
  private final static Location ORIGIN_LOCATION = Location.newInstance(11.584762300907554, 48.18316907203498);

  private VehicleRoutingProblem vrp;
  private VehicleRoutingProblemSolution solution;

  public void init(List<OsmNode> possibleLocations, int numParcels) {
    VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
    VehicleTypeImpl truckVehicle = VehicleTypeImpl.Builder.newInstance("truckBuilder")
        .addCapacityDimension(0, VEHICLE_CAPACITY)
        .build();

    vrpBuilder.setFleetSize(FleetSize.FINITE);
    int vehicleCount = (int)Math.round(((double)numParcels / (double)VEHICLE_CAPACITY) * OVERCAPACITY_FACTOR);
    for (int i = 0; i < vehicleCount; i++) {
      VehicleImpl vehicle = VehicleImpl.Builder.newInstance("truck" + i)
          .setStartLocation(ORIGIN_LOCATION)
          .setType(truckVehicle)
          .setReturnToDepot(true)
          .build();
      vrpBuilder.addVehicle(vehicle);
    }

    Map<OsmNode, Integer> demandMap = new HashMap<>();
    Random rand = new Random();

    for (int i = 0; i < numParcels; i++) {
      OsmNode location = possibleLocations.get(rand.nextInt(possibleLocations.size()));
      demandMap.put(location, demandMap.getOrDefault(location, 0) + 1);
    }

    demandMap = clusterDemand(demandMap);
    Set<OsmNode> osmNodes = demandMap.keySet();

    System.out.println("Filtered out an additional " + (osmNodes.size() - demandMap.size()) + " unroutable of " + osmNodes.size() + " stops");
    System.out.println("Clustered demand contains " + demandMap.size() + " stops");

    for (Map.Entry<OsmNode, Integer> entry : ProgressBar.wrap(demandMap.entrySet(), "Converting geo coordinates to jsprit jobs")) {
      OsmNode location = entry.getKey();
      Integer demand = entry.getValue();
      Service delivery = Service.Builder.newInstance("delivery" + location.hashCode())
        .addSizeDimension(0, demand)
        .setLocation(Location.newInstance(location.longitude(), location.latitude()))
        .build();
      vrpBuilder.addJob(delivery);
    }
    vrp = vrpBuilder.build();
  }

  public void solve() {
    if (vrp == null) {
      throw new IllegalStateException("Vehicle routing problem not initialized");
    }
    Instant timeBeforeSolve = Instant.now();
    int threads = Runtime.getRuntime().availableProcessors();

    System.out.println("Using " + threads + " threads for solving " + ITERATIONS + " iterations");
    System.out.println("Solving vehicle routing problem for " + vrp.getJobs().size() + " stops...");
    VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(vrp).setExecutorService(
      Executors.newFixedThreadPool(threads), threads
    ).buildAlgorithm();
    algorithm.setMaxIterations(ITERATIONS);
    ProgressBar pb = new ProgressBarBuilder().setInitialMax(ITERATIONS).setTaskName("Solving VRP").build();
    algorithm.addListener((IterationEndsListener) (i, problem, solutions) -> {
      pb.step();
      pb.setExtraMessage("Best solution cost: " + Solutions.bestOf(solutions).getCost());
    });
    pb.maxHint(ITERATIONS);

    solution = Solutions.bestOf(algorithm.searchSolutions());
    System.out.println("Solution found after " + Duration.between(timeBeforeSolve, Instant.now()).toSeconds() + " seconds, skipped " + solution.getUnassignedJobs().size() + " jobs");
  }

  public void writeRouteXML(File targetFile, String carrierName, String carrierColor) {
    if (solution == null) {
      throw new IllegalStateException("No solution available");
    }

    List<Route> routes = new ArrayList<>();

    System.out.println("Writing " + carrierName + " routes to " + targetFile.getAbsolutePath());

    Random rand = new Random();
    int numRoute = 0;
    int totalVehicleCount = solution.getRoutes().size();

    for (VehicleRoute vhRoute : solution.getRoutes()) {
      System.out.println("Vehicle " + (numRoute+1) + " of " + totalVehicleCount + " has " + vhRoute.getTourActivities().getJobs().size() + " stops");
      List<Stop> stops = new ArrayList<>();
      List<String> edges = new ArrayList<>();
      edges.add(ENTRY_EDGE);
      for (TourActivity activity : vhRoute.getActivities()) {
        Coordinate stopCoords = activity.getLocation().getCoordinate();
        TraCIRoadPosition roadPos = Simulation.convertRoad(stopCoords.getX(), stopCoords.getY(), true, "passenger");

        edges.add(roadPos.getEdgeID());
        // 90% chance for "parking" (allowing other vehicles to pass)

        // https://medium.com/the-post-grad-survival-guide/to-jeff-bezos-from-an-amazon-delivery-driver-5ccf39d5df7d
        int packets = activity.getSize().get(0);
        boolean willBeHome = ThreadLocalRandom.current().nextDouble() <= 0.95;
        int findParkingTime = (int) (ThreadLocalRandom.current().nextExponential() * 12);// 0-60 seconds
        int approachTime = (int) (ThreadLocalRandom.current().nextGaussian(30, 10));// 20-40 seconds
        int singleStairTime = (int) (ThreadLocalRandom.current().nextGaussian(15 + packets*2.5d, 5));// 15-25 seconds
        int stairs = ThreadLocalRandom.current().nextDouble() < 0.4 ? 0 : (ThreadLocalRandom.current().nextInt(1, 6));
        int stairTime = stairs * singleStairTime;
        int waitingTime = (int) (ThreadLocalRandom.current().nextGaussian(willBeHome ? 20 : 120, 10));// 20-40 seconds
        int loadUnloadTime = (int) (ThreadLocalRandom.current().nextGaussian(60 + packets*20, 20));// 40-80 seconds
        int totalDuration = findParkingTime + 2 * approachTime + 2 * stairTime + waitingTime + loadUnloadTime;
        stops.add(new Stop(roadPos.getEdgeID(), roadPos.getPos(), totalDuration, rand.nextDouble() <= 0.8));
      }
      edges.add(EXIT_EDGE);
      Route route = new Route("delivery_" + carrierName + "_" + numRoute, edges, stops);
      routes.add(route);
      numRoute++;
    }

    System.out.println("Done computing routes, writing to file...");

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
      writer.write("<routes>");
      writer.newLine();
      writer.write("    <vType id=\"delivery_" + carrierName + "\" vClass=\"delivery\" color=\"" + carrierColor + "\"/>");
      writer.newLine();
      for (Route route : routes) {
        route.writeXML(writer, "delivery");
        writer.newLine();
      }
      writer.write("</routes>");
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Done writing routes to " + targetFile.getAbsolutePath());
  }

  public void plotSolution(File targetFile) {
    if (solution == null) {
      throw new IllegalStateException("No solution available");
    }
    double latMin = Double.MAX_VALUE, latMax = Double.MIN_VALUE, longMin = Double.MAX_VALUE, longMax = Double.MIN_VALUE;
    for (VehicleRoute route : solution.getRoutes()) {
      for (TourActivity activity : route.getActivities()) {
        Coordinate coordinate = activity.getLocation().getCoordinate();
        longMin = Math.min(longMin, coordinate.getX());
        longMax = Math.max(longMax, coordinate.getX());
        latMin = Math.min(latMin, coordinate.getY());
        latMax = Math.max(latMax, coordinate.getY());
      }
    }
    Plotter plotter = new Plotter(vrp, solution);
    plotter.setBoundingBox(longMin, latMin, longMax, latMax);
    plotter.plot(targetFile.getAbsolutePath(), "Solution");
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
        if (location.distanceMetersL2(otherLocation) < 20) {
          clusteredDemand.put(location, clusteredDemand.get(location) + demandMap.get(otherLocation));
          clusteredDemand.remove(otherLocation);
        }
      }
    }
    return clusteredDemand;
  }

}
