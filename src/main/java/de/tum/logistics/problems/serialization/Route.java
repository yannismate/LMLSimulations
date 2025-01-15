package de.tum.logistics.problems.serialization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public void writeXML(BufferedWriter writer) throws IOException {
    String first = edges.getFirst();
    writer.write("    <trip id=\"" + id + "\" depart=\"0\" from=\"" + first + "\" to=\"" + first + "\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </trip>");
  }

  public void serialize(BufferedWriter writer) throws IOException {
    writer.write(id + "\n");
    writer.write(edges.size() + "\n");
    for (String edge : edges) {
      writer.write(edge + "\n");
    }
    writer.write(stops.size() + "\n");
    for (Stop stop : stops) {
      stop.serialize(writer);
    }
  }

  public static Route deserialize(BufferedReader reader) throws IOException {
    String id = reader.readLine();
    int edgeCount = Integer.parseInt(reader.readLine());
    List<String> edges = new ArrayList<>();
    for (int i = 0; i < edgeCount; i++) {
      edges.add(reader.readLine());
    }
    int stopCount = Integer.parseInt(reader.readLine());
    List<Stop> stops = new ArrayList<>();
    for (int i = 0; i < stopCount; i++) {
      stops.add(Stop.deserialize(reader));
    }
    return new Route(id, edges, stops);
  }

}
