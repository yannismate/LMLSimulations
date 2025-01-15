package de.tum.logistics.problems.serialization;

import java.io.BufferedWriter;
import java.io.IOException;

public record Stop(String edgeId, double positionOnEdge, double duration, boolean usesParking) {

  public void writeXML(BufferedWriter writer) throws IOException {
    writer.write("        <stop edge=\"" + edgeId + "\" endPos=\"" + positionOnEdge + "\" duration=\""
        + duration + "\" parking=\"" + usesParking + "\"/>");
  }

}
