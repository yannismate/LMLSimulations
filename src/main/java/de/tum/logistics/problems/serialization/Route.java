package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public void writeXML(BufferedWriter writer) throws IOException {
    String first = edges.getFirst();
    writer.write("    <trip id=\"" + id + "\" from=\"" + first + "\" to=\"" + first + "\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </trip>");
  }

}
