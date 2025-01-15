package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public void writeXML(BufferedWriter writer) throws IOException {
    writer.write("    <route id=\"" + id + "\" edges=\"" + String.join(" ", edges) + "\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </route>");
  }

}
