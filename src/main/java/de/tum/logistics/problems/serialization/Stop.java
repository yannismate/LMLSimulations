package de.tum.logistics.problems.serialization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

public record Stop(String edgeId, double positionOnEdge, double duration, boolean usesParking) {

  public void writeXML(BufferedWriter writer) throws IOException {
    writer.write("        <stop edge=\"" + edgeId + "\" endPos=\"" + positionOnEdge + "\" duration=\""
        + duration + "\" parking=\"" + usesParking + "\"/>");
  }

  public void serialize(BufferedWriter writer) throws IOException {
    writer.write(edgeId + "\n");
    writer.write(positionOnEdge + "\n");
    writer.write(duration + "\n");
    writer.write(usesParking + "\n");
  }

  public static Stop deserialize(BufferedReader reader) throws IOException {
    String edgeId = reader.readLine();
    double positionOnEdge = Double.parseDouble(reader.readLine());
    double duration = Double.parseDouble(reader.readLine());
    boolean usesParking = Boolean.parseBoolean(reader.readLine());
    return new Stop(edgeId, positionOnEdge, duration, usesParking);
  }
}
