package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public record Route(String id, List<String> edges, List<Stop> stops) {

  public void writeXML(BufferedWriter writer) throws IOException {
    writer.write("    <trip id=\"" + id + "\" type=\"delivery\" from=\"" + edges.getFirst() + "\" to=\"" + edges.getLast() + "\" depart=\"0\">");
    writer.newLine();
    for (Stop stop : stops) {
      stop.writeXML(writer);
      writer.newLine();
    }
    writer.write("    </trip>");
  }

}
