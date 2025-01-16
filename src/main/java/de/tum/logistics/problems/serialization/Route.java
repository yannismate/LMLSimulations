package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public Route withSortedStopsOnSameEdge() {
      List<Stop> sortedStops = new ArrayList<>(stops);
      for (int i = 0; i < sortedStops.size() - 1; i++) {
        for (int j = 0; j < sortedStops.size() - i - 1; j++) {
          if (sortedStops.get(j).edgeId().equals(sortedStops.get(j + 1).edgeId())) {
            continue;
          }
          if (sortedStops.get(j).positionOnEdge() < sortedStops.get(j + 1).positionOnEdge()) {
            Collections.swap(sortedStops, j, j + 1);
          }
        }
      }
      return new Route(id, edges, sortedStops);
  }

  public void writeXML(BufferedWriter writer, String carrierName) throws IOException {
    writer.write("    <trip id=\"" + id + "\" type=\"delivery_"+carrierName+"\" from=\"" + edges.getFirst() + "\" to=\"" + edges.getLast() + "\" depart=\"00:05:00\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </trip>");
  }

}
