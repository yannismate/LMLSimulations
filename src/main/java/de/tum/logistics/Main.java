package de.tum.logistics;

import org.eclipse.sumo.libtraci.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static final int SIMULATION_STEPS = 24 * 60 * 60;
  public static final int SPEEDUP_FACTOR = 60;
  public static final int SIMULATION_STEP_BEGINNING = 7 * 60 * 60;
  public static final int SIMULATION_PARCEL_DELIVERY_BEGINNING = (int) (7 * 60 * 60 + 6 * 60);
  public static final File RESOURCE_FOLDER = new File("src/main/resources");
  public static TraCIPosition fromBoundary, toBoundary;

  static Set<String> parcelServices = new HashSet<>();
  static List<String> deliveryVehicleIds = new ArrayList<>();
  static Map<String, DeliveryVehicleStats> deliveryVehicleStats = new HashMap<>();
  static Map<String, String> vehicleIdToParcelService = new HashMap<>();
  static {

    parcelServices.add("dhl");
    parcelServices.add("ups");
    parcelServices.add("dpd");
  }

  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File configFile = new File(RESOURCE_FOLDER, "the.sumocfg");
    Simulation.start(new StringVector(new String[]{"sumo-gui", "-c", configFile.getAbsolutePath()}));
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

    for (String s : VehicleType.getIDList()) {
      System.out.println(s);
    }

    VehicleType.copy("DEFAULT_VEHTYPE", "passenger");
    VehicleType.copy("DEFAULT_BIKETYPE", "bicycle");

    RandomRouteGenerator pkwRouteGen = new RandomRouteGenerator("passenger", 10000, fromBoundary, toBoundary);
    RandomRouteGenerator bikeRouteGen = new RandomRouteGenerator("bicycle", 5000, fromBoundary, toBoundary);
    pkwRouteGen.startPopulatingThread();
    bikeRouteGen.startPopulatingThread();

    // populate with some vehicles so simulation doesn't stop
    for (int i = 0; i < 5; i++) {
      String routeId = pkwRouteGen.fetchRandomRouteBlocking();
      if (routeId != null) {
        Vehicle.add("start_" + i, routeId, "passenger");
      }
    }

    // Ende:
    // - Co2 Verbrauch
    // - Straßen Blockiert Zeit
    // - Manhours pro Vehikel
    // - Anfahrtszeit

    AtomicLong totalVehicles = new AtomicLong(0);
    Map<String, AtomicLong> activeVehiclesByType = new HashMap<>();
    Map<String, String> vehicleIdToType = new HashMap<>();

    int vehicleId = 0;
    for (int seconds = SIMULATION_STEP_BEGINNING; seconds < SIMULATION_STEPS; seconds++) {
      if (deliveryVehicleIds.isEmpty() && SIMULATION_PARCEL_DELIVERY_BEGINNING < seconds) {
        populateDeliveryVehicle();
      }
      Simulation.step();
      String timeOfDay = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
      long motorVehiclesOnRoad = activeVehiclesByType.computeIfAbsent("passenger", k -> new AtomicLong(0)).get();
      long expectedMotorVehicles = (int) expectedNumber((double) seconds / SIMULATION_STEPS, 3000);
      long bikeVehiclesOnRoad = activeVehiclesByType.computeIfAbsent("bicycle", k -> new AtomicLong(0)).get();
      long expectedBikeVehicles = (int) expectedNumber((double) seconds / SIMULATION_STEPS, 1000);
      if (seconds % 30 == 0) {
        System.out.println(timeOfDay + ": " + motorVehiclesOnRoad + "/" + expectedMotorVehicles + " cars, " + bikeVehiclesOnRoad + "/" + expectedBikeVehicles + " bikes");
      }

      StringVector arrived = Simulation.getArrivedIDList();
      for (String arrivedVehicleId : arrived) {
        String type = vehicleIdToType.get(arrivedVehicleId);
        if (type != null) {
          activeVehiclesByType.computeIfAbsent(type, k -> new AtomicLong(1)).decrementAndGet();
        }
        totalVehicles.decrementAndGet();
        vehicleIdToType.remove(arrivedVehicleId);
        DeliveryVehicleStats vehicleStats = deliveryVehicleStats.get(arrivedVehicleId);
        if (vehicleStats != null) {
          vehicleStats.arrivalTime = seconds;
        }
      }

      int added = 0;
      while (motorVehiclesOnRoad < expectedMotorVehicles && added < 20) {
        String routeId = pkwRouteGen.fetchRandomRouteBlocking();
        if (routeId == null) {
          continue;
        }
        String vehID = "vehicle_passenger_" + vehicleId++;
        String vehicleClass = "passenger";
        try {
          Vehicle.add(vehID, routeId, vehicleClass);
          int gray = ThreadLocalRandom.current().nextInt(130, 256);
          Vehicle.setColor(vehID, new TraCIColor(gray, gray, gray, 255));
        } catch (Exception tryAgain) {
          continue;
        }
        motorVehiclesOnRoad++;
        added++;
        totalVehicles.incrementAndGet();
        activeVehiclesByType.computeIfAbsent(vehicleClass, k -> new AtomicLong(0)).incrementAndGet();
        vehicleIdToType.put(vehID, vehicleClass);
      }
      while (bikeVehiclesOnRoad < expectedBikeVehicles && added < 5) {
        String routeId = bikeRouteGen.fetchRandomRouteBlocking();
        if (routeId == null) {
          continue;
        }
        String vehID = "vehicle_bike_" + vehicleId++;
        String vehicleClass = "bicycle";
        try {
          Vehicle.add(vehID, routeId, vehicleClass);
        } catch (Exception tryAgain) {
          continue;
        }
        bikeVehiclesOnRoad++;
        added++;
        totalVehicles.incrementAndGet();
        activeVehiclesByType.computeIfAbsent(vehicleClass, k -> new AtomicLong(0)).incrementAndGet();
        vehicleIdToType.put(vehID, vehicleClass);
      }
//      try {
//        Thread.sleep(1000 / SPEEDUP_FACTOR);
//      } catch (InterruptedException e) {
//        throw new RuntimeException(e);
//      }
      double totalCO2 = 0;
      double addedCO2 = 0;
      for (String deliveryVehicleId : deliveryVehicleIds) {
        DeliveryVehicleStats stats = deliveryVehicleStats.computeIfAbsent(deliveryVehicleId, k -> new DeliveryVehicleStats());
        if (stats.stops == -1) {
          stats.stops = Vehicle.getStops(deliveryVehicleId).size();
        }
        double co2Grams;
        if (Double.isNaN(stats.lastCO2Value)) {
          co2Grams = Vehicle.getCO2Emission(deliveryVehicleId) / 1000d;
          stats.lastCO2Value = co2Grams;
        } else {
          co2Grams = stats.lastCO2Value;
          stats.lastCO2Value = Double.NaN;
        }
        co2Grams = Math.max(0, co2Grams);
        stats.co2Consumption += co2Grams;
        totalCO2 += stats.co2Consumption;
        addedCO2 += co2Grams;
      }
      if (seconds % 60 == 0) {
        System.out.println("Added " + addedCO2 / 1000d + " kg CO2");
        System.out.println("Total CO2: " + totalCO2 / (1000d) + " kg");
      }
    }
    Simulation.close();
  }

  private static void populateDeliveryVehicle() {
    System.out.println("Populating delivery vehicles");
    for (String parcelService : parcelServices) {
      for (int i = 0; i < 1000; i++) {
        String vehicleIdToCheck = "delivery_"+parcelService+"_"+i;
        try {
          double mass = Vehicle.getMass(vehicleIdToCheck);
          if (mass > 0) {
            deliveryVehicleIds.add(vehicleIdToCheck);
            vehicleIdToParcelService.put(vehicleIdToCheck, parcelService);
            System.out.println("Found delivery vehicle: " + vehicleIdToCheck);
          }
        } catch (Exception e) {
//          e.printStackTrace();
          // ignore
          System.out.println("Stopping " + parcelService + " lookup at " + i);
          break;
        }
      }
    }
  }

  private static class DeliveryVehicleStats {
    public double co2Consumption;
    public double lastCO2Value;
    public Map<String, Integer> arrivedAt = new HashMap<>();
    public String nextStop;
    public long blockedTime;
    public long stops = -1;
    public long arrivalTime;
  }

  public static double expectedNumber(
    double linTime,
    long maxInPeak
  ) {
    double a = -5, b = -2.6, c = -0.1, d = 1.125, e = 0.01;
    double coeff = Math.max(Math.sin(b * Math.sin(a * linTime + d) + c), 0) + e;
    return coeff * maxInPeak;
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