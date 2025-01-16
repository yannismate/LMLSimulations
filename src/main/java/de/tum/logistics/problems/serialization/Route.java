package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public Route withSortedStopsOnSameEdge() {
    LinkedHashMap<String, List<Stop>> stopsByEdge = new LinkedHashMap<>();

    for (Stop stop : stops) {
      stopsByEdge.computeIfAbsent(stop.edgeId(), k -> new ArrayList<>()).add(stop);
    }

    for (List<Stop> stops : stopsByEdge.values()) {
      stops.sort(Comparator.comparingDouble(Stop::positionOnEdge));
    }

    return new Route(id, edges, stopsByEdge.entrySet().stream().flatMap(e -> e.getValue().stream()).toList());
  }

  public void writeXML(BufferedWriter writer, String vehicleType) throws IOException {
    writer.write("    <trip id=\"" + id + "\" type=\"" + vehicleType + "\" from=\"" + edges.getFirst() + "\" to=\"" + edges.getLast() + "\" depart=\"01:00:00\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </trip>");
  }

}
