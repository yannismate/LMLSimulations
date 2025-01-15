package de.tum.logistics;

import de.tum.logistics.problems.serialization.Route;
import de.tum.logistics.problems.serialization.Stop;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class LogisticVehicle {
  private final static AtomicLong logisticVehicleIdCounter = new AtomicLong(0);
  private final long logisticVehicleId = logisticVehicleIdCounter.getAndIncrement();
  private long vehicleGenerationCounter = 0;
  private String currentVehicleId;
  private final String hubId;
  private final Deque<Stop> stops = new LinkedBlockingDeque<>();
  private Status status = Status.UNINITIALIZED;
  private long waitingTicks;
  private String currentEdge;

  public LogisticVehicle(String hubId, List<Stop> stops) {
    this.hubId = hubId;
    this.currentEdge = hubId;
    this.stops.addAll(stops);
  }

  public void tick() {
    switch (status) {
      case UNINITIALIZED -> {
        Stop stop = currentStop();
        if (stop != null) {
          status = Status.DRIVING;
          currentVehicleId = generateNewVehicle(currentEdge, stop.edgeId());
        } else {
          status = Status.COMPLETED;
        }
      }
      case UNLOADING -> {
        if (waitingTicks-- <= 0) {
          // done with stop
          Stop oldStop = stops.removeFirst();
          currentEdge = oldStop.edgeId();
          String nextEdge;
          if (stops.isEmpty()) {
            nextEdge = hubId;
          } else {
            nextEdge = currentStop().edgeId();
          }
          status = Status.DRIVING;
          currentVehicleId = generateNewVehicle(currentEdge, nextEdge);
        }
      }
      case DRIVING, COMPLETED -> {}
    }
  }

  public void checkDeletion(String vehicleId) {
    if (currentVehicleId.equals(vehicleId)) {
      Stop stop = currentStop();
      if (stop != null) {
        status = Status.UNLOADING;
        waitingTicks = (long) stop.duration();
      } else {
        status = Status.COMPLETED;
      }
    }
  }

  private String generateNewVehicle(String from, String to) {
    String vehicleId = "delivery_vehicle_" + logisticVehicleId + "_" + vehicleGenerationCounter++;
    String routeId = "delivery_route_" + logisticVehicleId + "_" + vehicleGenerationCounter;
    currentVehicleId = vehicleId;
    org.eclipse.sumo.libtraci.Route.add(
      routeId,
      new StringVector(new String[]{from, to})
    );
    Vehicle.add(vehicleId, routeId, "passenger");
    return vehicleId;
  }

  private Stop currentStop() {
    return stops.peekFirst();
  }

  public static List<LogisticVehicle> generateLogisticVehiclesFrom(File txtFile) {
    List<LogisticVehicle> vehicles = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(txtFile))) {
      String numberOfTrips = reader.readLine();
      int trips = Integer.parseInt(numberOfTrips);
      while (trips-- > 0) {
        Route route = Route.deserialize(reader);
        vehicles.add(new LogisticVehicle(route.edges().getFirst(), route.stops()));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return vehicles;
  }

  public enum Status {
    UNINITIALIZED,
    DRIVING,
    UNLOADING,
    COMPLETED
  }
}
